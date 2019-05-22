package com.lucidchart.eyesopen;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/** A utility to compare the equality of two images, with the ability to adjust the definition of equality.
 * This will allow some minor differences in pixels to exist without failing the equality test.
 *
 * The approach is to determine average pixel colors for a given block size (which can be passed as an argument),
 * and then determine the color distance of each average color pixel against each average color pixel of the base image used for comparison.
 * The threshold of acceptability used for color distance can also be passed as an argument.
 */
public class ImageCompare {

    private final static int DEFAULT_BLOCK_SIZE = 5;
    private final static double DEFAULT_MAX_COLOR_DISTANCE = 20.0;
    private final static double DEFAULT_BLOCK_PIXEL_THRESHOLD = 0.67; // Evaluate a block if the number of pixels to average meets this threshold quantity

    /** The max size difference to allow for when checking image equality.
     * This will be used to simply try to find a match of the smaller image somewhere within the larger.
     */
    private static final int MAX_SIZE_DIFFERENCE_ALLOWED = 5;

    public final BufferedImage originalMaster; // The master image
    public final BufferedImage originalSnapshot; // The current test snapshot to compare to the master
    private final BufferedImage master;
    private final BufferedImage snapshot;
    private final boolean sameSize; // If master and the snapshot are different sizes.  We may wish to see side by side, even if different sizes.
    private final boolean snapshotSizeAdjusted; // If size of the snapshot was adjusted to match master.
    private final int masterHeight;
    private final int masterWidth;
    private final int blockSize;
    private final double maxColorDistance;
    private final boolean match;
    private final Status status;

    /** Used to flag the options to perform a standard image to image comparison, or find an image within a region */
    enum ComparisonModel {
        STANDARD,
        FIND_WITH_REGION
    }

    /** There are two principal models implemented for comparing images: Comparing same size images, and finding one image somewhere within defined boundaries in the other. */
    private final ComparisonModel comparisonModel;

    enum ImageType {
        MASTER,
        SNAPSHOT
    }

    /** Map of pixels we need to check, composed from the exclusion and inclusion mask components */
    private final boolean[][] mask;


    /** Blocks we will check, composed from the mask
     * If pixels are available in the mask meet the BLOCK_THRESHOLD we will include it, otherwise disregard, to avoid unwanted failures */
    private final boolean[][] blockMask;
    private final int numVerticalBlocks;
    private final int numHorizontalBlocks;


    /** Block color match
     * Contains a block by block comparison result of the image
     */
    private final boolean[][] blockComparisonMap;
    private double[][] blockColorDistances;

    /**
     * Use for the find within region model:
     */
    private final Region targetDefinition;
    private final Region targetFindRegion;
    private Rectangle targetFound;

    /** Holds the masked image, when and if created */
    private BufferedImage maskedMaster;
    private BufferedImage maskedSnapshot;
    private BufferedImage circledDiff;

    /** Holds the result comparisons, when and if created */
    private BufferedImage pixelDiff;
    private BufferedImage sideBySide;

    /** Provides a way of seeing the current max color difference of corresponding blocks in the images */
    private double largestColorDiff = 0.0;

    /** Provides a way of expanding masks for an expanded snapshot which was originally slightly smaller than master */
    private Set<Rectangle> addedMaskAreas = new HashSet<>();

    /** Record of initial input, used to create the side by side diff */
    public final boolean masterProvided;
    public final boolean snapshotProvided;

    /** The max seconds allowed to perform the image comparison before throwing an exception */
    public final static int MAX_TIME = 100;

    /** Compares two images, with the ability to make adjustments of how closely images must match.
     * Image pixel dimensions must match, or the comparison will fail automatically
     *
     * @param initialMaster The base master to which we will compare the snapshot image.
     * @param initialSnapshot The current test snapshot.  A null snapshot, will automatically match as false, but can enable mask info for the master.
     * @param blockSize The size of a block of pixels, that will be averaged together into a single color
     * @param maxColorDistance The max color distance allowed between the master and test images.
     */

    private ImageCompare(BufferedImage initialMaster, BufferedImage initialSnapshot, int blockSize, double maxColorDistance, Set<Region> maskComponents) {

        // We will prevent image comparison requests which may take a very long time.
        Instant timeLimit = Instant.now().plusSeconds(MAX_TIME);

        require(initialMaster != null || initialSnapshot != null, "At least one image must be provided");
        require(blockSize >= 1, "Block size must be greater than or equal to 1");
        require(maxColorDistance > 0, "Max color distance must be greater than 0");

        // Record what was given, this helps to know if approval is needed, or if it was a comparison failure.
        this.snapshotProvided = (initialSnapshot != null);
        this.masterProvided = (initialMaster != null);
        this.originalSnapshot = initialSnapshot;
        this.originalMaster = initialMaster;

        // Provides a flag which indicates whether or not the original master and snapshot images were the same size
        this.sameSize = (snapshotProvided && masterProvided && originalMaster.getWidth() == originalSnapshot.getWidth() && originalMaster.getHeight() == originalSnapshot.getHeight());

        // Extracts mask components
        Set<Region> focusAndExcludeMask = new HashSet<>();
        List<Region> findThisTargetMask = new ArrayList<>();
        List<Region> withinThisRegionMask = new ArrayList<>();
        if (maskComponents != null) {
            focusAndExcludeMask = maskComponents.stream().filter(mc -> mc.regionAction == RegionAction.EXCLUDE || mc.regionAction == RegionAction.FOCUS).collect(Collectors.toSet());
            findThisTargetMask = maskComponents.stream().filter(mc -> mc.regionAction == RegionAction.FIND_THIS_TARGET).collect(Collectors.toList());
            withinThisRegionMask = maskComponents.stream().filter(mc -> mc.regionAction == RegionAction.WITHIN_THIS_BOUNDING_BOX).collect(Collectors.toList());
            require(findThisTargetMask.isEmpty() && withinThisRegionMask.isEmpty() || !findThisTargetMask.isEmpty() && !withinThisRegionMask.isEmpty(), "A target AND region mask is required to use the 'find within region' model");
        }

        this.comparisonModel = findThisTargetMask.isEmpty() ? ComparisonModel.STANDARD : ComparisonModel.FIND_WITH_REGION;
        this.blockSize = blockSize;
        this.maxColorDistance = maxColorDistance;

        // Provide a working image if none provided
        BufferedImage snapshotBuild = snapshotProvided ? originalSnapshot : new BufferedImage(originalMaster.getWidth(), originalMaster.getHeight(), BufferedImage.TYPE_INT_RGB);
        BufferedImage masterBuild = masterProvided ? originalMaster : new BufferedImage(originalSnapshot.getWidth(), originalSnapshot.getHeight(), BufferedImage.TYPE_INT_RGB);

        // Handle slightly different image sizes.  However we will not currently support the 'find with region' model for different sized snapshots
        Optional<BufferedImage> resizedSnapshot = Optional.empty();
        Optional<Region> resizedSnapshotRegion = Optional.empty();

        if (snapshotBuild.getWidth() != masterBuild.getWidth() || snapshotBuild.getHeight() != masterBuild.getHeight()) {
            if (comparisonModel == ComparisonModel.STANDARD) {
                int workingMasterWidth = masterBuild.getWidth();
                int workingMasterHeight = masterBuild.getHeight();

                // Provides effectively final variables for lambdas
                BufferedImage snapshotHolder = snapshotBuild;
                BufferedImage masterHolder = masterBuild;
                Optional<Rectangle> locationInMaster;
                if (masterBuild.getHeight() != snapshotBuild.getHeight() || masterBuild.getWidth() != snapshotBuild.getWidth() &&

                        // Limited variance before failing
                        Math.abs(snapshotBuild.getHeight() - workingMasterHeight) < MAX_SIZE_DIFFERENCE_ALLOWED &&
                        Math.abs(snapshotBuild.getWidth() - workingMasterWidth) < MAX_SIZE_DIFFERENCE_ALLOWED &&

                        // We will not support checking images if one dimension is larger and the other is smaller (eg. width of snapshot is smaller than master and height of snapshot is larger)
                        !(snapshotBuild.getWidth() * workingMasterWidth < 0 || snapshotBuild.getHeight() * workingMasterHeight < 0)) {

                    boolean[][] tempMask = composeMask(workingMasterWidth, workingMasterHeight, focusAndExcludeMask);
                    boolean[][] tempBlockMask = composeBlockMask(blockSize, DEFAULT_BLOCK_PIXEL_THRESHOLD, workingMasterWidth, workingMasterHeight, tempMask);

                    // If snapshot is bigger than master, check to see if master exists as a subset of snapshot
                    if (snapshotBuild.getWidth() > workingMasterWidth) {
                        Optional<Rectangle> locationOfMatch = imageFoundWithin(masterBuild, null, null, snapshotBuild, tempBlockMask, MaskType.BLOCK, timeLimit);
                        resizedSnapshot = locationOfMatch.map(loc -> snapshotHolder.getSubimage(loc.x, loc.y, workingMasterWidth, workingMasterHeight));
                    }

                    // If master is bigger than snapshot, see if snapshot exists as a subset of master
                    else {
                        Color expandedColor = Color.MAGENTA;
                        locationInMaster = imageFoundWithin(snapshotHolder, null, null, masterHolder, tempMask, MaskType.PIXEL, timeLimit);

                        // Expand snapshot to match master
                        Optional<Set<Rectangle>> addedSnapshotMargins = locationInMaster.map(loc -> {
                            Set<Rectangle> expRegions = new HashSet<>();
                            if (loc.y > 0) {
                                expRegions.add(new Rectangle(0, 0, masterHolder.getWidth(), loc.y));
                                addedMaskAreas.add(new Rectangle(0, 0, masterHolder.getWidth(), getTouchingBlocks(loc.y)));
                            }
                            if (loc.x > 0) {
                                expRegions.add(new Rectangle(0, 0, loc.x, masterHolder.getHeight()));
                                addedMaskAreas.add(new Rectangle(0, 0, getTouchingBlocks(loc.x), masterHolder.getHeight()));
                            }
                            int bottomMargin = masterHolder.getHeight() - loc.y - loc.height;
                            int rightMargin = masterHolder.getWidth() - loc.x - loc.width;
                            if (bottomMargin > 0) {
                                expRegions.add(new Rectangle(0, loc.y + loc.height, masterHolder.getWidth(), bottomMargin));
                                int touchingBlocks = getTouchingBlocks(bottomMargin);
                                addedMaskAreas.add(new Rectangle(0, loc.y + loc.height - touchingBlocks + bottomMargin, masterHolder.getWidth(), touchingBlocks));
                            }
                            if (rightMargin > 0)
                                expRegions.add(new Rectangle(loc.x + loc.width, 0, rightMargin, masterHolder.getHeight()));
                            int touchingBlocks = getTouchingBlocks(rightMargin);
                            addedMaskAreas.add(new Rectangle(loc.x + loc.width - touchingBlocks + rightMargin, 0, touchingBlocks, masterHolder.getHeight()));
                            return expRegions;
                        });

                        resizedSnapshot = locationInMaster.map(loc -> {
                            // Expand snapshot to match master by adding a noticeable expansion color to the buffer.
                            BufferedImage expandedSnapshot = new BufferedImage(masterHolder.getWidth(), masterHolder.getHeight(), BufferedImage.TYPE_INT_RGB);
                            Graphics2D graph = expandedSnapshot.createGraphics();
                            try {
                                graph.setColor(expandedColor);
                                addedSnapshotMargins.ifPresent(set -> set.forEach(rect -> graph.fill(rect)));
                            } finally {
                                graph.dispose();
                            }
                            // Add the snapshot to the image.
                            Graphics g = expandedSnapshot.getGraphics();
                            try {
                                g.drawImage(snapshotHolder, loc.x, loc.y, null);
                            } finally {
                                g.dispose();
                            }
                            return expandedSnapshot;
                        });
                    }
                }
            } else if (comparisonModel == ComparisonModel.FIND_WITH_REGION) {
                // In case of unequal image sizes employing a 'find in region' model, we will blindly chop of any extra on the right or bottom
                // of the snapshot, if it is bigger.  If it is smaller, it will automatically be expanded later.
                if (snapshotBuild.getWidth() > masterBuild.getWidth() || snapshotBuild.getHeight() > masterBuild.getHeight())
                    resizedSnapshot = Optional.of(snapshotBuild.getSubimage(0, 0, Math.min(masterBuild.getWidth(), snapshotBuild.getWidth()), Math.min(masterBuild.getHeight(), snapshotBuild.getHeight())));

                // Whether the snapshot is slightly bigger or smaller, we will silently expand the 'region bounding box' by the small image size difference.
                int expandWidth = Math.abs(snapshotBuild.getWidth() - masterBuild.getWidth());
                int expandHeight = Math.abs(snapshotBuild.getHeight() - masterBuild.getHeight());
                int newX = Math.max(0, withinThisRegionMask.get(0).location.x - expandWidth);
                int newY = Math.max(0, withinThisRegionMask.get(0).location.y - expandHeight);
                int newRight = Math.min(snapshotBuild.getWidth(), withinThisRegionMask.get(0).location.x + withinThisRegionMask.get(0).width + expandWidth);
                int newBottom = Math.min(snapshotBuild.getHeight(), withinThisRegionMask.get(0).location.y + withinThisRegionMask.get(0).height + expandHeight);
                int newWidth = newRight - newX;
                int newHeight = newBottom - newY;

                resizedSnapshotRegion = Optional.of(Region.apply(newX, newY, newWidth, newHeight, RegionAction.WITHIN_THIS_BOUNDING_BOX));
            }
        }

        // Snapshot and master must be equal at this point.
        // Expand the smaller image to make it the same size if it was not.  This will allow a side by side comparison of the two.

        snapshotBuild = resizedSnapshot.orElse(snapshotBuild);
        snapshotSizeAdjusted = resizedSnapshot.isPresent();

        if (!snapshotSizeAdjusted) {
            int maxWidth = Integer.max(snapshotBuild.getWidth(), masterBuild.getWidth());
            int maxHeight = Integer.max(snapshotBuild.getHeight(), masterBuild.getHeight());
            masterBuild = (masterBuild.getWidth() < maxWidth || masterBuild.getHeight() < maxHeight) ? expandImage(maxWidth, maxHeight, copyImage(masterBuild)) : masterBuild;
            snapshotBuild = (snapshotBuild.getWidth() < maxWidth || snapshotBuild.getHeight() < maxHeight) ? expandImage(maxWidth, maxHeight, copyImage(snapshotBuild)) : snapshotBuild;
        }

        // Final assignments
        this.master = masterBuild;
        this.snapshot = snapshotBuild;
        this.masterWidth = masterBuild.getWidth();
        this.masterHeight = masterBuild.getHeight();

        // Add potential silent masks in the event of resizing unequal images.
        Set<Region> finalMaskComponents = focusAndExcludeMask;
        addedMaskAreas.forEach(rect -> finalMaskComponents.add(Region.apply(rect.x, rect.y, rect.width, rect.height, RegionAction.EXCLUDE)));
        if (comparisonModel == ComparisonModel.FIND_WITH_REGION) {
            finalMaskComponents.add(withinThisRegionMask.get(0));
            // The mask contained in the target area will be applied in the 'find in region' search.
            // This makes it possible to mask certain areas in the target definition, if desired.
            finalMaskComponents.add(findThisTargetMask.get(0));
        }

        this.mask = composeMask(masterWidth, masterHeight, finalMaskComponents);
        this.blockMask = composeBlockMask(blockSize, DEFAULT_BLOCK_PIXEL_THRESHOLD, masterWidth, masterHeight, mask);
        this.numHorizontalBlocks = blockMask.length; // Just for clarity
        this.numVerticalBlocks = blockMask[0].length;

        // Perform the 'Find with Region' model comparison, if requested
        if (comparisonModel == ComparisonModel.FIND_WITH_REGION) {
            this.targetFindRegion = resizedSnapshotRegion.orElse(withinThisRegionMask.get(0));
            this.targetDefinition = findThisTargetMask.get(0);
            require(targetFindRegion.location.x >= 0 && targetFindRegion.location.y < master.getWidth() &&
                    targetFindRegion.location.y >= 0 && targetFindRegion.location.y < master.getHeight(), "Location of desired range " + targetFindRegion.location + " must be located within the size of the snapshot (" + master.getWidth() + "," + master.getHeight() + ")");
            require(targetFindRegion.location.x + targetFindRegion.width <= master.getWidth() &&
                    targetFindRegion.location.y + targetFindRegion.height <= master.getHeight(), "Boundaries of specified find limits " + targetFindRegion.location + " with dimensions (w" + targetFindRegion.width + ",h" + targetFindRegion.height + ") exceed the snapshot image (" + master.getWidth() + "," + master.getHeight() + ")");
            require(targetDefinition.location.x >= 0 && targetDefinition.location.y < master.getWidth() &&
                    targetDefinition.location.y >= 0 && targetDefinition.location.y < master.getHeight(), "Location of target sub-image " + targetDefinition.location + " must be located within the size of the image (" + master.getWidth() + "," + master.getHeight() + ")");
            require(targetDefinition.location.x + targetDefinition.width <= master.getWidth() &&
                    targetDefinition.location.y + targetDefinition.height <= master.getHeight(), "Boundaries of target sub-image " + targetDefinition.location + " with dimensions (w" + targetDefinition.width + ",h" + targetDefinition.height + ") exceed the snapshot image (" + master.getWidth() + "," + master.getHeight() + ")");

            // Block mask of the subRegion area
            boolean[][] subRegionMask = composeSubRegionMask(targetDefinition, mask);
            boolean[][] subRegionBlockMask = composeBlockMask(blockSize, DEFAULT_BLOCK_PIXEL_THRESHOLD, targetDefinition.width, targetDefinition.height, subRegionMask);

            // Unused, but needs initialization
            this.blockComparisonMap = null;

            Optional<Rectangle> targetLocationFound = imageFoundWithin(master, targetDefinition, targetFindRegion, snapshot, subRegionBlockMask, MaskType.BLOCK, timeLimit);
            this.targetFound = targetLocationFound.orElse(null);
            this.match = (targetLocationFound.isPresent());

        // Standard comparison model
        } else {
            // Unused, but need initialization
            this.targetFindRegion = null;
            this.targetDefinition = null;

            // The grid contains a positive/negative comparison for every averaged color block in the image.
            this.blockColorDistances = new double[numHorizontalBlocks][numVerticalBlocks];
            this.blockComparisonMap = getAverageColorComparisonMap(blockMask, maxColorDistance, blockColorDistances);

            // Returns true if every color comparison of averaged block colors is within the color distance threshold
            this.match = allBlocksMatch(blockComparisonMap);
        }

        // Assign final status
        status = (match) ? Status.PASSED :
                    (!sameSize && !snapshotSizeAdjusted) ? Status.DIFFERENT_SIZE :
                            (masterProvided && !snapshotProvided) ? Status.MISSING :
                                    (!masterProvided && snapshotProvided) ? Status.NEEDS_APPROVAL :
                                            (comparisonModel == ComparisonModel.FIND_WITH_REGION) ? Status.FAILED_TO_FIND_IMAGE_IN_REGION :
                                                    Status.FAILED;
    }



    /*
       ___          _                                 _   _               _
      / __\_ _  ___| |_ ___  _ __ _   _    /\/\   ___| |_| |__   ___   __| |___
     / _\/ _` |/ __| __/ _ \| '__| | | |  /    \ / _ \ __| '_ \ / _ \ / _` / __|
    / / | (_| | (__| || (_) | |  | |_| | / /\/\ \  __/ |_| | | | (_) | (_| \__ \
    \/   \__,_|\___|\__\___/|_|   \__, | \/    \/\___|\__|_| |_|\___/ \__,_|___/
                                  |___/
     */

    /** Compare two images, with mask, using the default block size and color proximity
     *
     * @param master the gold master
     * @param snapshot the current test snapshot
     * @param mask  Regions to include/exclude.  Null if the whole image is desired.
     */
    public static ImageCompare apply(BufferedImage master, BufferedImage snapshot, Set<Region> mask) {
        return new ImageCompare(master, snapshot, DEFAULT_BLOCK_SIZE, DEFAULT_MAX_COLOR_DISTANCE, mask);
    }

    /** Compare two images, with mask, using the matchLevel specifications */
    public static ImageCompare apply(BufferedImage master, BufferedImage snapshot, MatchLevel matchLevel, Set<Region> mask) {
        require(matchLevel != null, "A match level must be supplied");
        return new ImageCompare(master, snapshot, matchLevel.blockSize, matchLevel.maxColorDistance, mask);
    }

    /** Compare two images, with mask, using the default color distance */
    public static ImageCompare apply(BufferedImage master, BufferedImage snapshot, int blockSize, Set<Region> mask) {
        return new ImageCompare(master, snapshot, blockSize, DEFAULT_MAX_COLOR_DISTANCE, mask);
    }

    /** Compare two images, with mask, using the default block size */
    public static ImageCompare apply(BufferedImage master, BufferedImage snapshot, double maxColorDistance, Set<Region> mask) {
        return new ImageCompare(master, snapshot, DEFAULT_BLOCK_SIZE, maxColorDistance, mask);
    }

    /** Only a snapshot */
    public static ImageCompare apply(byte[] snapshot) {
        return new ImageCompare(null, getImage(snapshot), DEFAULT_BLOCK_SIZE, DEFAULT_MAX_COLOR_DISTANCE, null);
    }


    /* ---ENTIRE IMAGE---

    /** Compare two images using the default block size and color proximity */
    public static ImageCompare apply(BufferedImage master, BufferedImage snapshot) {
        return new ImageCompare(master, snapshot, DEFAULT_BLOCK_SIZE, DEFAULT_MAX_COLOR_DISTANCE, null);
    }

    /** Compare two images using the result of a selenium snapshot */
    public static ImageCompare apply(BufferedImage master, byte[] snapshot) {
        return ImageCompare.apply(master, getImage(snapshot));
    }

    public static ImageCompare apply(Path master, byte[] snapshot) {
        return ImageCompare.apply(getImage(master), getImage(snapshot));
    }

    public static ImageCompare apply(File master, byte[] snapshot) {
        return ImageCompare.apply(getImage(master), getImage(snapshot));
    }

    /** Compare two images using the matchLevel specifications */
    public static ImageCompare apply(BufferedImage master, BufferedImage snapshot, MatchLevel matchLevel) {
        return new ImageCompare(master, snapshot, matchLevel.blockSize, matchLevel.maxColorDistance, null);
    }

    public static ImageCompare apply(BufferedImage master, byte[] snapshot, MatchLevel matchLevel) {
        return new ImageCompare(master, getImage(snapshot), matchLevel.blockSize, matchLevel.maxColorDistance, null);
    }

    public static ImageCompare apply(Path master, byte[] snapshot, MatchLevel matchLevel) {
        return ImageCompare.apply(getImage(master), getImage(snapshot), matchLevel);
    }

    public static ImageCompare apply(File master, byte[] snapshot, MatchLevel matchLevel) {
        return ImageCompare.apply(getImage(master), getImage(snapshot), matchLevel);
    }

    /** Compare two images using the default color distance */
    public static ImageCompare apply(BufferedImage master, BufferedImage snapshot, int blockSize) {
        return new ImageCompare(master, snapshot, blockSize, DEFAULT_MAX_COLOR_DISTANCE, null);
    }

    public static ImageCompare apply(BufferedImage master, byte[] snapshot, int blockSize) {
        return new ImageCompare(master, getImage(snapshot), blockSize, DEFAULT_MAX_COLOR_DISTANCE, null);
    }

    /** Compare two images using the default block size */
    public static ImageCompare apply(BufferedImage master, BufferedImage snapshot, double maxColorDistance) {
        return new ImageCompare(master, snapshot, DEFAULT_BLOCK_SIZE, maxColorDistance, null);
    }

    public static ImageCompare apply(BufferedImage master, byte[] snapshot, double maxColorDistance) {
        return new ImageCompare(master, getImage(snapshot), DEFAULT_BLOCK_SIZE, maxColorDistance, null);
    }



    /*
       ___       _     _ _              _   _ _ _ _   _
      / _ \_   _| |__ | (_) ___   /\ /\| |_(_) (_) |_(_) ___  ___
     / /_)/ | | | '_ \| | |/ __| / / \ \ __| | | | __| |/ _ \/ __|
    / ___/| |_| | |_) | | | (__  \ \_/ / |_| | | | |_| |  __/\__ \
    \/     \__,_|_.__/|_|_|\___|  \___/ \__|_|_|_|\__|_|\___||___/

     */

    /** Returns the master image used in the comparison */
    public BufferedImage getOriginalMaster() {
        return originalMaster;
    }

    /** Returns the snapshot image use din the comparison */
    public BufferedImage getOriginalSnapshot() {
        return originalSnapshot;
    }

    /** Returns the master darkened with the current exclusion mask */
    public BufferedImage getMasterWithMask() {
        if (maskedMaster == null) maskedMaster = getMaskedImage(master, DEFAULT_DIVISOR_TO_DARKEN_MASK, ImageType.MASTER);
        return maskedMaster;
    }

    /** Returns the snapshot darkened with the current exclusion mask */
    public BufferedImage getSnapshotWithMask() {
        if (maskedSnapshot == null) maskedSnapshot = getMaskedImage(snapshot, DEFAULT_DIVISOR_TO_DARKEN_MASK, ImageType.SNAPSHOT);
        return maskedSnapshot;
    }

    /** Debugging utility to get image of block level mask */
    public BufferedImage getBlockMask() {
        return comparisonModel == ComparisonModel.STANDARD ?
                getImageOfDoubleArray(blockMask, blockSize, Color.BLACK, Color.WHITE) :
                createMessageImage(commonError("Block mask"));
    }

    /** Debbuging utility to get image of color comparison results */
    public BufferedImage getBlockColorComparisonResults() {
        return comparisonModel == ComparisonModel.STANDARD ?
                getImageOfDoubleArray(blockComparisonMap, blockSize, Color.WHITE, Color.RED) :
                createMessageImage(commonError("Block comparison map"));
    }

    /** Get circled differences on the snapshot image. */
    public BufferedImage getCircledDiff() {
        if (comparisonModel == ComparisonModel.STANDARD) {
            // no differences present
            if (match) return getSnapshotWithMask();

            // Create the markup only if not done before
            if (circledDiff == null) circledDiff = createMarkUp(blockComparisonMap, blockSize);
            return circledDiff;
        } else return createMessageImage(commonError("Circled diff report"));
    }

    /** Get a side by side image comparison result */
    public BufferedImage getSideBySide(Status status, String testName, String snapshotName) {
        if (sideBySide == null) sideBySide = createSideBySide(status, testName, snapshotName);
        return sideBySide;
    }

    public BufferedImage getSideBySide(String testName, String snapshotName) {
        if (sideBySide == null) sideBySide = createSideBySide(status, testName, snapshotName);
        return sideBySide;
    }

    public BufferedImage getSideBySide() {
        if (sideBySide == null) sideBySide = createSideBySide(status, "", "");
        return sideBySide;
    }

    /** Obtain a pixel-diff overlay **/
    public BufferedImage getPixelDiff() {
        if (comparisonModel == ComparisonModel.STANDARD && pixelDiff == null) {
            pixelDiff = createPixelDiffImage(master, snapshot, blockComparisonMap, blockMask, blockSize, maxColorDistance);
            return pixelDiff;
        } else return createMessageImage(commonError("Pixel diff report"));
    }

    /** True if the snapshot matches the master, based on block size, color proximity, image size, and mask */
    public boolean isMatch() {
        return match;
    }

    /** Obtain status of the comparison */
    public Status getStatus() {
        return status;
    }

    /** True if both original snapshot and master images are the same pixel dimension */
    public boolean isSameSize() {
        return sameSize;
    }

    /** Indicates this test is trying to find a certain image match anywhere within a region */
    public boolean isFindInRegionModel() {
        return comparisonModel == ComparisonModel.FIND_WITH_REGION;
    }

    /** Indicates if snapshot size was adjusted to the same size as master */
    public boolean isSnapshotSizeAdjusted() {
        return snapshotSizeAdjusted;}

    /** When using the 'find in region' model, this returns the rectangle of the location on the snapshot image.
     * This can be helpful when needing to find and interact with a location */
    public Optional<Rectangle> getTargetLocationFound() {
        return Optional.ofNullable(targetFound);
    }


    /*
      _____       _                        _         _   _ _ _ _   _
      \_   \_ __ | |_ ___ _ __ _ __   __ _| |  /\ /\| |_(_) (_) |_(_) ___  ___
       / /\/ '_ \| __/ _ \ '__| '_ \ / _` | | / / \ \ __| | | | __| |/ _ \/ __|
    /\/ /_ | | | | ||  __/ |  | | | | (_| | | \ \_/ / |_| | | | |_| |  __/\__ \
    \____/ |_| |_|\__\___|_|  |_| |_|\__,_|_|  \___/ \__|_|_|_|\__|_|\___||___/

     */

    /** Adds blank margins to an image to create an image of a larger size.
     * This is used to make images of different sizes available for a side by side comparison, as images must be of the same size to make comparison possible. */
    private BufferedImage expandImage(int maxWidth, int maxHeight, BufferedImage baseImage) {
        require(baseImage.getWidth() <= maxWidth && baseImage.getHeight() <= maxHeight, "This method does not crop. It only expands images to meet a certain size");
        BufferedImage newImage = new BufferedImage(maxWidth, maxHeight, BufferedImage.TYPE_INT_RGB);

        // Superimpose base image on blank image
        for (int x = 0; x < baseImage.getWidth(); x++)
            for (int y = 0; y < baseImage.getHeight(); y++)
                newImage.setRGB(x, y, baseImage.getRGB(x, y));

        // Paint bright color on expanded areas to make it obvious it's not part of the original image
        Color brightColor = Color.YELLOW;
        for (int x = baseImage.getWidth(); x < maxWidth; x++)
            for (int y = 0; y < maxHeight; y++)
                newImage.setRGB(x, y, brightColor.getRGB());
        for (int y = baseImage.getHeight(); y < maxHeight; y++)
            for (int x = 0; x < maxWidth; x++)
                newImage.setRGB(x, y, brightColor.getRGB());
        return newImage;
    }


    /** Copies a buffered image
     *  https://stackoverflow.com/questions/3514158/how-do-you-clone-a-bufferedimage
     */
    public static BufferedImage copyImage(BufferedImage source){
        BufferedImage b = new BufferedImage(source.getWidth(), source.getHeight(), source.getType());
        Graphics g = b.getGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return b;
    }

    private static String commonError(String feature) {
        return feature + " is not available for 'find image within area' model.";
    }

    /** Returns the width of all blocks required to cover this width.
     * This is used to force ignore areas that are expanded in the snapshot in the event of a slightly mis-sized snapshot.
     */
    private int getTouchingBlocks(int width) {
        return (width/blockSize + 1) * blockSize;
    }

    /** Creates a simple message image */
    private static BufferedImage createMessageImage(String message) {

        // https://stackoverflow.com/questions/18800717/convert-text-content-to-image
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        try {
            Font font = new Font("Arial", Font.PLAIN, 16);
            g2d.setFont(font);
            FontMetrics fm = g2d.getFontMetrics();
            int width = fm.stringWidth(message);
            int height = fm.getHeight();
            g2d.dispose();

            img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            g2d = img.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
            g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2d.setFont(font);
            fm = g2d.getFontMetrics();
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, img.getWidth(), img.getHeight());
            g2d.setColor(Color.BLACK);
            g2d.drawString(message, 0, fm.getAscent());
        } finally {
            g2d.dispose();
        }
        return img;
    }

    /** When checking master for a sub-image, we must use the base master pixel mask to recreate a new block mask each time for the new crop.
     * When checking the snapshot for a sub-image from master, we can use the same block mask each time.
     * This is because the block is DEFINED for master.
     */
    enum MaskType {
        PIXEL,
        BLOCK
    }

    /** Determines if a subset of an image is found within a defined bounding box.
     * @param targetImage the master image from which the subset image is derived.
     * @param targetDef the region of the master image which defines the desired target image required in the snapshot
     * @param withinDef defines the boundaries on the snapshot within which the target image from master must be found
     * @param withinImage the full snapshot image on which we are looking for the target image from master
     * @param mask is the composed block mask of the target sub-image we desire to find within the larger region.  If no blockMask is provided, a new mask will be composed at each position checked. */
    private Optional<Rectangle> imageFoundWithin(BufferedImage targetImage, Region targetDef, Region withinDef, BufferedImage withinImage, boolean[][] mask, MaskType maskType, Instant timeLimit) {

        Region workingWithinDef = (withinDef == null) ? Region.apply(0, 0, withinImage.getWidth(), withinImage.getHeight(), RegionAction.WITHIN_THIS_BOUNDING_BOX): withinDef;

        // Get cropped working images
        BufferedImage requirement = (targetDef == null) ? targetImage : targetImage.getSubimage(targetDef.location.x, targetDef.location.y, targetDef.width, targetDef.height);
        BufferedImage within = (withinDef == null) ? withinImage : withinImage.getSubimage(withinDef.location.x, withinDef.location.y, withinDef.width, withinDef.height);

        // If the cropped working images are the same size, find out immediately if they are the same.
        if (requirement.getHeight() == within.getHeight() && requirement.getWidth() == within.getWidth())
            return ImageCompare.apply(requirement, within).isMatch() ? Optional.of(new Rectangle(0, 0, requirement.getWidth(), requirement.getHeight())) : Optional.empty();

        // The desired image must be at least as big as the limiting bounding box.
        if (requirement.getHeight() > within.getHeight() || requirement.getWidth() > within.getWidth()) return Optional.empty();

        // If working images are not the same size, our approach is to attempt each possibility with quick fails, until all valid attempts are exhausted.
        int xRange = within.getWidth() - requirement.getWidth();
        int yRange = within.getHeight() - requirement.getHeight();

        for (int x = 0; x <= xRange; x++)
            for (int y = 0; y <= yRange; y++) {
                if (Instant.now().isAfter(timeLimit))
                    throw new ImageCompareTimeOutException("Unable to find a match within " + MAX_TIME + "s.");
                BufferedImage matchAttempt = within.getSubimage(x, y, requirement.getWidth(), requirement.getHeight());
                boolean[][] effectiveBlockMask =
                        maskType == MaskType.BLOCK ?
                                mask :
                                composeBlockMask(
                                        blockSize,
                                        DEFAULT_BLOCK_PIXEL_THRESHOLD,
                                        requirement.getWidth(), requirement.getHeight(),
                                        extractRegionPixelMask(mask, new Rectangle(x, y, requirement.getWidth(), requirement.getHeight()))
                        );
                if (quickCompare(requirement, matchAttempt, maxColorDistance, effectiveBlockMask))
                    return Optional.of(new Rectangle(x + workingWithinDef.location.x, y + workingWithinDef.location.y, requirement.getWidth(), requirement.getHeight()));
            }
        return Optional.empty();
    }


    /** Extracts a region pixel mask from the base.
     * This is used when attempting to compare various options on the master image to the smaller snapshot image.
     * With this we are able to preserve the ability to use masks in the comparison, while trying to resolve
     * a snapshot image of a slightly different size.
     */
    private boolean[][] extractRegionPixelMask(boolean[][] pixelMask, Rectangle boundingBox) {

        Rectangle bb = boundingBox;
        boolean[][] subRegionPixelMask = new boolean[bb.width][bb.height];

        // Create a new pixel mask subset
        for (int x = 0; x < bb.width; x++)
            for (int y = 0; y < bb.height; y++) {
                subRegionPixelMask[x][y] = pixelMask[bb.x + x][bb.y + y];
            }
        return subRegionPixelMask;
    }

    /** Extracts a subregion mask from the parent mask, used for applying the mask in a find within region model */
    private boolean[][] composeSubRegionMask(Region masterSubRegion, boolean[][] imageMask) {
        boolean[][] subRegionMask = new boolean[masterSubRegion.width][masterSubRegion.height];
        int width = masterSubRegion.width;
        int height = masterSubRegion.height;
        for (int w = 0 ; w < width; w++)
            for (int h = 0; h < height; h++) {
                subRegionMask[w][h] = imageMask[masterSubRegion.location.x + w][masterSubRegion.location.y + h];
            }
        return subRegionMask;
    }

    /** Marks up the snapshot to highlight differences */
    private BufferedImage createMarkUp(boolean[][] blockComparisonMap, int blockSize) {

        // Make a copy of the block comparison map
        BufferedImage markup = cloneBufferedImage(getSnapshotWithMask());

        // https://stackoverflow.com/questions/1564832/how-do-i-do-a-deep-copy-of-a-2d-array-in-java
        // This copy will be used to mark off contiguous areas as they are considered
        boolean[][] blockMapCopy = Arrays.stream(blockComparisonMap).map(boolean[]::clone).toArray($ -> blockComparisonMap.clone());

        // These BLOCK rectangles will be used to draw highlights around the problems.
        // It's important to remember these are BLOCK coordinates, that will later be translated into image pixel coordinates.
        Set<BoundingBox> contiguousProblemBlocks = new HashSet<>();

        // Search until all problem blocks are considered
        while (true) {
            Point startBlock = getProblemBlock(blockMapCopy);
            if (startBlock == null) break; // All blocks are considered
            BoundingBox boundingBox = findContiguousBlocksOutline(blockMapCopy, startBlock);
            contiguousProblemBlocks.add(boundingBox);
        }

        return drawHighlights(markup, contiguousProblemBlocks, blockSize);
    }


    /* Returns the image with highlights to focus attention on problem areas */
    private BufferedImage drawHighlights(BufferedImage snapshot, Set<BoundingBox> contiguousProblemBlocks, int blockSize) {

        // Add a highlight around each contiguous problem block
        for (BoundingBox limits : contiguousProblemBlocks) {
            Graphics2D g = (Graphics2D) snapshot.getGraphics();

            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int x = limits.minX * blockSize;
            int y = limits.minY * blockSize;
            int width = (limits.maxX + 1 - limits.minX) * blockSize;
            int height = (limits.maxY + 1 - limits.minY) * blockSize;

            // Makes the circle slightly larger than the problem area
            int increase = (int)(.15 * (width>height ? width : height));

            Color neonPink = new Color(246, 24, 127);

            g.setColor(neonPink);
            g.drawOval(x-increase, y-increase, width+(increase * 2), height+(increase * 2));

            // Make it thicker for better readability
            g.setColor(neonPink);
            g.drawOval(x-1-increase, y-1-increase, width+2+(increase * 2), height+2+(increase * 2));
            g.drawOval(x+1-increase, y+1-increase, width-2+(increase * 2), height-2+(increase * 2));

            // Add a halo for better readability
            g.setColor(Color.white);
            g.drawOval(x-2-increase, y-2-increase, width+4+(increase * 2), height+4+(increase * 2));
            g.drawOval(x+2-increase, y+2-increase, width-4+(increase * 2), height-4+(increase * 2));

            g.dispose();
        }
        return snapshot;
    }


    /* Used to find the bounding box of contiguous problem areas */
    private class BoundingBox {
        private int maxX;
        private int minX;
        private int maxY;
        private int minY;

        BoundingBox(int maxX, int minX, int maxY, int minY) {
            this.maxX = maxX;
            this.minX = minX;
            this.maxY = maxY;
            this.minY = minY;
        }

        void proposePoint(Point point) {
            if (point.x < this.minX) this.minX = point.x;
            if (point.x > this.maxX) this.maxX = point.x;
            if (point.y < this.minY) this.minY = point.y;
            if (point.y > this.maxY) this.maxY = point.y;
        }

        public int getMaxX() {
            return maxX;
        }
        public int getMinX() {
            return minX;
        }
        public int getMaxY() {
            return maxY;
        }
        public int getMinY() {
            return minY;
        }
    }


    /** Returns the inclusive block rectangle of contiguous problem blocks */
    private BoundingBox findContiguousBlocksOutline(boolean [][] blockMapCopy, Point startBlock) {
            BoundingBox currentLimit = new BoundingBox(startBlock.x, startBlock.x, startBlock.y, startBlock.y);
            findLimits(blockMapCopy, startBlock.x, startBlock.y, currentLimit);
            return currentLimit;
    }

    /** An iterative function to find the limits of a contiguous problem area */
    private void findLimits(boolean[][] blockMapCopy, int x, int y, BoundingBox currentLimits) {

        Deque<Point> blocks = new LinkedList<>();
        blocks.push(new Point(x,y));
        do {
            Point block = blocks.pop();
            if (block.x >= 0 && block.y >=0 && block.x < blockMapCopy.length && block.y < blockMapCopy[0].length && !blockMapCopy[block.x][block.y]) {

                blockMapCopy[block.x][block.y] = true;
                currentLimits.proposePoint(block);

                // Check up
                blocks.push(new Point(block.x, block.y - 1));

                // Check down
                blocks.push(new Point(block.x, block.y + 1));

                // Check left
                blocks.push(new Point(block.x - 1, block.y));

                // Check right
                blocks.push(new Point(block.x + 1, block.y));
            }
        } while (!blocks.isEmpty());
    }

    /** Get problem block, if one exists in the block comparison map.  If none found, null is returned */
    private Point getProblemBlock(boolean[][] blockMapCopy) {
        for (int x = 0; x < blockMapCopy.length; x++)
            for (int y = 0; y < blockMapCopy[0].length; y++) {
                if (!blockMapCopy[x][y]) return new Point(x,y);
            }
        return null;
    }

    /** Returns true if all block color comparisons passed */
    private boolean allBlocksMatch(boolean[][] colorCompareMap) {
        for(boolean[] lineOfBlocks : colorCompareMap) {
            if(!arrayIsAllTrue(lineOfBlocks)) return false;
        }
        return true;
    }

    /** Returns true only if an entire array of boolean values are true */
    private boolean arrayIsAllTrue(boolean[] array) {
        for(boolean b: array) if(!b) return false;
        return true;
    }


    /** Makes a quick failing attempt to compare two images.
     * This is helpful when we don't care about reports on the two images and we want to fail fast.
     * @return true if every corresponding block in the two images averages to a color difference below the threshold.
     */
    private boolean quickCompare(BufferedImage required, BufferedImage attempt, double maxColorDistance, boolean[][] imageBlockMask) {
        int numHorizontalBlocks = getNumHorizontalBlocks(imageBlockMask);
        int numVerticalBlocks = getNumVerticalBlocks(imageBlockMask);

        for (int vB = 0; vB < numVerticalBlocks; vB++)
            for (int hB = 0; hB < numHorizontalBlocks; hB++) {
                if (!imageBlockMask[hB][vB] && distance(averageBlockPixels(hB, vB, blockSize, required), averageBlockPixels(hB, vB, blockSize, attempt)) > maxColorDistance)
                    return false;
            }
        return true;
    }


    /** Compares the color distance block by block and returns a boolean map of the results
     * This map can be useful in constructing an overlay to highlight image comparison failures
     * @return a map of boolean comparison results for each color block.  TRUE means the block color comparison is under the acceptable threshold
     */
    private boolean[][] getAverageColorComparisonMap(boolean[][] blockMask, double maxColorDistance, double [][] blockColorDistances) {

        int numHorizontalBlocks = getNumHorizontalBlocks(blockMask);
        int numVerticalBlocks = getNumVerticalBlocks(blockMask);

        Color[][] masterImageAverage = getBlockColorAverages(master, blockSize, blockMask);
        Color[][] testImageAverage = getBlockColorAverages(snapshot, blockSize, blockMask);

        boolean[][] blockColorMatch = new boolean[numHorizontalBlocks][numVerticalBlocks];
        for (int hB = 0; hB < numHorizontalBlocks; hB++)
            for (int vB = 0; vB < numVerticalBlocks; vB++) {
                // Only check for valid blocks
                double colorDistance = 0;
                if (!blockMask[hB][vB]) {
                    colorDistance = distance(masterImageAverage[hB][vB], testImageAverage[hB][vB]);
                    blockColorMatch[hB][vB] = colorDistance <= maxColorDistance;
                    if (!blockColorMatch[hB][vB]) blockColorDistances[hB][vB] = colorDistance;  //todo maybe take this out after debugging for performance, or only make if requested.
                    if (colorDistance > largestColorDiff)
                        largestColorDiff = colorDistance;

                // If a block is masked, it passes.
                } else blockColorMatch[hB][vB] = true;
            }
        return blockColorMatch;
    }

    /** A debugging utility to view the double array */
    private BufferedImage getImageOfDoubleArray(boolean[][] blockMask, int blockSize, Color trueColor, Color falseColor) {
        BufferedImage maskedBlockImage = new BufferedImage(blockMask.length * blockSize, blockMask[0].length * blockSize, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < blockMask.length; x++) {
            for (int y = 0; y < blockMask[0].length; y++) {
                Graphics2D graph = maskedBlockImage.createGraphics();
                graph.setColor(blockMask[x][y] ? trueColor : falseColor);
                graph.fill(new Rectangle(x * blockSize, y * blockSize, blockSize - 1, blockSize - 1));
                graph.dispose();
            }
        }
        return maskedBlockImage;
    }

    /** Compose the mask at the block level based on the pixel mask and available pixels.
     * If the number of available pixels in a block do not meet BLOCK_PIXEL_THRESHOLD, we will ignore these pixels and mask them..
     * @return a mask at the block level, a block containing the value TRUE means we will NOT consider this block in the image analysis.
     */
    private boolean[][] composeBlockMask(int blockSize, double blockPixelThreshold, int imageWidth, int imageHeight, boolean[][] mask) {

        // The number of pixels required to be able to consider a block
        int pixelThreshold = (int)(blockSize * blockSize * blockPixelThreshold);


        // Establish the number of blocks in the grid

        int numHorizontalBlocks = imageWidth / blockSize;
        int numVerticalBlocks = imageHeight / blockSize;

        // Add another block column if image left over is greater than the DEFAULT_BLOCK_PIXEL_THRESHOLD of a block...
        if ((imageWidth % blockSize) >= blockSize * blockPixelThreshold) numHorizontalBlocks++;

        // Add another block row if image left over is greater than the DEFAULT_BLOCK_PIXEL_THRESHOLD of a block...
        if ((imageHeight % blockSize) >= blockSize * blockPixelThreshold) numVerticalBlocks++;

        boolean blockMask[][] = new boolean[numHorizontalBlocks][numVerticalBlocks];

        // Determine if blocks contain the threshold number of unmasked pixels.
        for (int x = 0; x < numHorizontalBlocks; x++)
            for (int y = 0; y < numVerticalBlocks; y++) {
                int unmaskedValidPixelsCount = 0;
                for (int px = x * blockSize; px < (x + 1) * blockSize; px++)
                    for (int py = y * blockSize; py < (y + 1) * blockSize; py ++) {
                        if (px < imageWidth && py < imageHeight && !mask[px][py]) unmaskedValidPixelsCount++;
                        blockMask[x][y] = !(unmaskedValidPixelsCount >= pixelThreshold);

                        // There is no longer any need to continue if we know there is sufficient valid pixels
                        if (!blockMask[x][y]) break;
                    }
            }

        return blockMask;
    }


    /** Utility to obtain the number of horizontal blocks.
     * This helps make it standard, by always following the same convention of the first array being horizontal
     */
    private int getNumHorizontalBlocks(boolean [][] blockMask) {
        return blockMask.length;
    }

    private int getNumVerticalBlocks(boolean [][] blockMask) {
        return blockMask[0].length;
    }


    /** Returns the average RGB color for a block of pixels.
     * This is to reduce the reliance on any single pixel and instead generalizes the comparison more as a human might do.
     * This also allows us to alter the size of the block to tweak desired results.
     * The size of the block can be altered by passing in a custom blockSize constructor argument.
     * This should also speed the distance calculations, because we will only compare the color distance of the average, not every pixel :)
     */
    private Color[][] getBlockColorAverages(BufferedImage image, int blockSize, boolean[][] blockMask) {

        int numHorizontalBlocks = getNumHorizontalBlocks(blockMask);
        int numVerticalBlocks = getNumVerticalBlocks(blockMask);

        Color[][] averagedColorBlock = new Color[numHorizontalBlocks][numVerticalBlocks];

        // We are assuming test snapshots will not contain any transparency and ignoring alpha values.
        for (int vB = 0; vB < numVerticalBlocks; vB++)
        for (int hB = 0; hB < numHorizontalBlocks; hB++) {

            // Only calculate averages if the block has met the pixel threshold and is not masked.
            if (!blockMask[hB][vB]) {
                averagedColorBlock[hB][vB] = averageBlockPixels(hB, vB, blockSize, image);
            }
        }
        return averagedColorBlock;
    }


    /** Returns the average pixel of a single block */
    private Color averageBlockPixels(int horizontalBlock, int verticalBlock, int blockSize, BufferedImage image) {
        int rSum = 0;
        int gSum = 0;
        int bSum = 0;
        for (int y = verticalBlock * blockSize; (y < (verticalBlock + 1) * blockSize) && y < image.getHeight(); y++)
            for (int x = horizontalBlock * blockSize; x < (horizontalBlock + 1) * blockSize && x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                // https://stackoverflow.com/questions/2615522/java-bufferedimage-getting-red-green-and-blue-individually
                rSum += getRed(rgb);
                gSum += getGreen(rgb);
                bSum += getBlue(rgb);}
        int pixelsInBlock = blockSize * blockSize;
        return new Color(rSum / pixelsInBlock, gSum / pixelsInBlock, bSum / pixelsInBlock, 0);
    }


    /** Returns the 'distance' between rgb values based on three dimensional distance of RGB values */
    private static double distance(Color c1, Color c2){
        int c1RGB = c1.getRGB();
        int c2RGB = c2.getRGB();
        return Math.sqrt(
                Math.pow(getRed(c1RGB) - getRed(c2RGB), 2) +
                        Math.pow(getGreen(c1RGB) - getGreen(c2RGB), 2) +
                        Math.pow(getBlue(c1RGB) - getBlue(c2RGB), 2)
        );
    }

    /** An efficient way of extracting red from the rgb int value obtained from getRGB() */
    private static int getRed(int rgb) {
        return (rgb >> 16) & 0x000000FF;
    }

    /** An efficient way of extracting red from the rgb int value obtained from getRGB() */
    private static int getGreen(int rgb) {
        return (rgb >> 8) & 0x000000FF;
    }

    /** An efficient way of extracting red from the rgb int value obtained from getRGB() */
    private static int getBlue(int rgb) {
        return (rgb) & 0x000000FF;
    }


    /** Allows us determine a final map of pixels we need to check after applying the mask.
     * Rather than doing some fancy math to add and subtract rectangles, We're simply coloring a boolean map by first adding inclusive areas, then subtracting exclusive areas.
     *
     * @return a final map of the mask (true means it's part of the mask, and we will not check this pixel).
     */
    private boolean[][] composeMask(int imageWidth, int imageHeight, Set<Region> maskComponents) {

        // We are composing the MASK: the areas of the image which we want to disregard
        boolean[][] mask = new boolean[imageWidth][imageHeight];

        // Divide mask
        Set<Region> include = new HashSet<>();
        Set<Region> exclude = new HashSet<>();
        if (maskComponents != null) {
            for (Region region : maskComponents)
                if (region.regionAction == RegionAction.FOCUS || region.regionAction == RegionAction.WITHIN_THIS_BOUNDING_BOX || region.regionAction == RegionAction.FIND_THIS_TARGET) include.add(region);
                else exclude.add(region);
        }

        // If there are inclusive elements, we want to START with the entire image as the mask, then subtract the areas we want to look at.
        if (!include.isEmpty()) {
            for (boolean[] col : mask)
                Arrays.fill(col, true);
            for (Region includeRegion : include) mask = updatePixelMap(mask, includeRegion);
        }
        // Exclusive regions are subtracted AFTER all the included regions are considered
        for (Region excludeRegion : exclude) updatePixelMap(mask, excludeRegion);
        return mask;
    }


    /**
     * A private function that adds or subtracts a mask component to update final pixel map
     *
     * @param mask The mask (which will be updated), which contains a boolean pixel map of the mask, the area we want to ignore.
     * @param maskComponent the region used to update the pixel map
     * @return the updated mask which now reflects the maskComponent given
     */
    private boolean[][] updatePixelMap(boolean[][] mask, Region maskComponent) {
        require((maskComponent != null), "A region of inclusion/exclusion must be provided.");
        for (int x = maskComponent.location.x;
                 x <= maskComponent.getBottomRight().x && x < mask.length;
                 x++)
            for (int y = maskComponent.location.y;
                 y <= maskComponent.getBottomRight().y && y < mask[0].length;
                 y++)
                mask[x][y] = maskComponent.regionAction == RegionAction.EXCLUDE;
        return mask;
    }


    /** A scala-like argument check */
    private static void require(boolean requirement, String message) {
        if (!requirement) throw new IllegalArgumentException(message);
    }


    /**
     * The amount to divide each color component by, to darken exclusion mask,
     * This will lower each RBG component, dividing it by this number.
     */
    private static final int DEFAULT_DIVISOR_TO_DARKEN_MASK = 2;


    /** Obtain image with mask darkened */
    private BufferedImage getMaskedImage(BufferedImage image, int divisorToDarkenMask, ImageType imageType) {
        BufferedImage maskedImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < image.getWidth(); x++)
            for (int y = 0; y < image.getHeight(); y++) {
                int originalPixel = image.getRGB(x, y);
                int newPixel;

                // If blocked out, make the pixels darker
                if (mask[x][y]) {
                    int red = getRed(originalPixel) / divisorToDarkenMask;
                    int green = getGreen(originalPixel) / divisorToDarkenMask;
                    int blue = getBlue(originalPixel) / divisorToDarkenMask;
                    newPixel = new Color(red, green, blue).getRGB();
                } else newPixel = originalPixel;

                maskedImage.setRGB(x, y, newPixel);
            }

        // If a 'Find within Region' model we will outline the desired image on master, and outline where it was found on the snapshot
        if (comparisonModel == ComparisonModel.FIND_WITH_REGION) {
            Rectangle r = (imageType == ImageType.MASTER) ? new Rectangle(targetDefinition.location.x, targetDefinition.location.y, targetDefinition.width, targetDefinition.height) : targetFound;
            if (r != null) {
                Graphics2D graph = maskedImage.createGraphics();
                try {
                    graph.setColor(neonPink);
                    graph.drawRect(r.x, r.y, r.width, r.height);
                    graph.setColor(new Color(246, 24, 127, 100));
                    graph.fill(new Rectangle(r.x + 2, r.y + 2, r.width - 4, r.height - 4));
                    graph.setColor(Color.WHITE);
                    graph.drawRect(r.x - 1, r.y - 1, r.width + 2, r.height + 2);
                    graph.drawRect(r.x + 1, r.y + 1, r.width - 2, r.height - 2);
                } finally {
                    graph.dispose();
                }
            }
        }
        return maskedImage;
    }

    private static final Color neonPink = new Color(246, 24, 127);

    /**
     * Obtain a pixel diff image.  This will probably be one of the more useful features :)
     * The master image will be greyed out, with an overlay of an alternating grid corresponding to the actual blocks sizes.
     * Any pixels that are different but under the threshold will be yellow
     * Any pixels that are different and over the threshold, in a non-failing block, will be orange.
     * Pixels that exceed the threshold in a failing block will be red.
     */
    private BufferedImage createPixelDiffImage(BufferedImage master, BufferedImage snapshot, boolean[][] blockComparisonMap, boolean[][] blockMask, int blockSize, double maxColorDistance) {

        // Create transparent alternating grid overlay on master image.
        BufferedImage gridOverlay = cloneBufferedImage(master);
        for (int x = 0; x < blockMask.length; x++) {
            for (int y = 0; y < blockMask[0].length; y++) {
                Graphics2D graph = gridOverlay.createGraphics();
                graph.setColor(blockMask[x][y] ? new Color(50,50,50,210) : (x + y)% 2 == 0 ? new Color(220,220,220,170) : new Color(180,180,180, 170));
                graph.fillRect(x * blockSize, y * blockSize, blockSize, blockSize);
                graph.dispose();
            }
        }

        // YELLOW: Pixels that are different, but under the threshold
        int diffUnderThreshold = new Color(255, 251, 41).getRGB();
        int diffUnderThresholdUnderMask = new Color (121, 117, 16).getRGB();

        // ORANGE: Pixels that are different, over the threshold, but in a passing block
        int diffOverThreshold = new Color(255, 153, 41).getRGB();
        int diffOverThresholdUnderMask = new Color(121, 70, 16).getRGB();

        // RED: Pixels that are different, over the threshold, but in a failing block
        int diffInFailingBlockOverThreshold = new Color(255, 56, 41).getRGB();

        for (int x = 0; x < master.getWidth(); x++)
            for (int y = 0; y < master.getHeight(); y++) {
                double pixelColorDistance = distance(new Color(master.getRGB(x, y)), new Color(snapshot.getRGB(x, y)));
                if (pixelColorDistance != 0) {
                    Optional<Point> blockOpt = getBlock(new Point(x,y), blockSize, blockMask);
                    int setX = x;
                    int setY = y;
                    blockOpt.ifPresent(block -> {
                        boolean underMask = blockMask[block.x][block.y];
                        gridOverlay.setRGB(setX, setY,
                                pixelColorDistance <= maxColorDistance ? (underMask ? diffUnderThresholdUnderMask : diffUnderThreshold) :
                                        blockComparisonMap[block.x][block.y] ? (underMask ? diffOverThresholdUnderMask : diffOverThreshold) : diffInFailingBlockOverThreshold);
                    });
                }
            }
        return gridOverlay;
    }

    /** Returns the block coordinates of a pixel */
    private static Optional<Point> getBlock(Point pixelPoint, int blockSize, boolean[][] blockMask) {
        int x = pixelPoint.x / blockSize;
        int y = pixelPoint.y / blockSize;
        // Sometime pixels may exist that didn't reach the block threshold, and are thus ignored.
        return (x >= blockMask.length || y >= blockMask[0].length ? Optional.empty(): Optional.of(new Point(x,y)));
    }


    /** Utility to copy a buffered image
     * https://stackoverflow.com/questions/3514158/how-do-you-clone-a-bufferedimage
     */
    private static BufferedImage cloneBufferedImage(BufferedImage original) {
        ColorModel cm = original.getColorModel();
        boolean isAlphaPreMultiplied = cm.isAlphaPremultiplied();
        WritableRaster raster = original.copyData(null);
        return new BufferedImage(cm, raster, isAlphaPreMultiplied, null);
    }

    /** Obtain an image from an array of bytes */
    public static BufferedImage getImage(byte[] image) {
        try {
            return ImageIO.read(new ByteArrayInputStream(image));
        } catch (IOException ioe) {
            throw new RuntimeException("Unable to obtain image from array of bytes", ioe);
        }
    }

    /** Obtain an image from a path */
    private static BufferedImage getImage(Path image) {
        try {
            return ImageIO.read(image.toFile());
        } catch (IOException ioe) {
            throw new RuntimeException("Unable to obtain image from path.", ioe);
        }
    }

    /** Obtain an image from a file */
    private static BufferedImage getImage(File image) {
        try {
            return ImageIO.read(image);
        } catch (IOException ioe) {
            throw new RuntimeException("Unable to obtain image from file.", ioe);
        }
    }

    /** Provides some finesse in rendering the side by side comparison. */
    enum SnapshotSize {
        XS(70, 10, 1),
        S(300, 15, 2),
        M(800, 20, 3),
        L(1500, 24, 5),
        XL(Integer.MAX_VALUE, 40, 7);

        public int maxPixelWidth;
        public int fontSize;
        public int outlineStroke;

        SnapshotSize(int maxPixelWidth, int fontSize, int outlineStroke) {
            this.maxPixelWidth = maxPixelWidth;
            this.fontSize = fontSize;
            this.outlineStroke = outlineStroke;
        }
    }

    /** Determines the relative size of the snapshot to determine fonts and borders */
    private SnapshotSize getSnapshotSize(int snapshotWidth) {
        for (SnapshotSize category : SnapshotSize.values())
            if (snapshotWidth <= category.maxPixelWidth) return category;
        return SnapshotSize.XL;
    }


    /** Creates a side by side image comparison of the snapshot and master image */
    private BufferedImage createSideBySide(Status imageTag, String currentTestName, String currentSnapshotName) {


        require(snapshotProvided || masterProvided, "At a minimum, one image must be provided for a side by side comparison image");
        require(currentSnapshotName != null, "A snapshot name must be provided");
        require(currentTestName != null, "A test name must be provided");
        require(imageTag != null, "An image tag must be provided");

        SnapshotSize snapshotSize = getSnapshotSize(snapshot.getWidth());
        BufferedImage master = masterProvided ? getMasterWithMask() : null;
        // Circling the snapshot diffs is not effective on very small snapshots, as the circle covers up too much of the snapshot, and it's small enough to notice the differences anyway
        BufferedImage snapshot = snapshotProvided ?
                snapshotSize == SnapshotSize.XS || !masterProvided || !sameSize || comparisonModel == ComparisonModel.FIND_WITH_REGION ? getSnapshotWithMask() :
                        getCircledDiff() :
                null;

        Font standardFont = new Font(Font.SANS_SERIF, Font.PLAIN, snapshotSize.fontSize);
        Font boldFont = new Font(Font.SANS_SERIF, Font.BOLD, snapshotSize.fontSize);
        int outlineStroke = snapshotSize.outlineStroke;

        // Start with a black background.
        Color backgroundColor = Color.BLACK;

        // We're making the border with constant no matter what the image sizes.
        int border = 130;

        int snapshotWidth = snapshot != null ? snapshot.getWidth(): master.getWidth();
        int snapshotHeight = snapshot != null ? snapshot.getHeight() : master.getHeight();
        int masterWidth = master != null ? master.getWidth() : snapshotWidth;
        int masterHeight = master != null ? master.getHeight() : snapshotHeight;

        // We'll add an additional 1/6 at the top for title and status to form the final dimension of the side by side image
        int maxHeight = masterHeight > snapshotHeight ? masterHeight : snapshotHeight;
        int finalWidth = border + masterWidth + border + snapshotWidth + border;
        int finalHeight = border * 2 + maxHeight + border;

        // Create the base for the new side by side image :)
        BufferedImage sideBySide = new BufferedImage(finalWidth, finalHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graph = sideBySide.createGraphics();
        graph.setColor(backgroundColor);
        graph.fill(new java.awt.Rectangle(0, 0, finalWidth, finalHeight));

        // Add the snapshot image :)
        Point snapshotLocation = new Point(border + snapshotWidth + border, border * 2 + (maxHeight - snapshotHeight) / 2);
        if (snapshot != null) {
            graph.drawImage(
                    snapshot,
                    null,
                    snapshotLocation.x,
                    snapshotLocation.y);
        } else {
            drawCenteredText(graph, standardFont, "No Snapshot Image", new java.awt.Rectangle(snapshotLocation.x, snapshotLocation.y, snapshotWidth, snapshotHeight), Color.LIGHT_GRAY);
        }
        drawRectangle(graph, outlineStroke, snapshotLocation.x, snapshotLocation.y, snapshotWidth, snapshotHeight, imageTag.tagColor);

        // Add the master image :)
        Point masterLocation = new Point(border, border * 2 + (maxHeight - masterHeight) / 2);
        if (master != null) {
            graph.drawImage(
                    master,
                    null,
                    masterLocation.x,
                    masterLocation.y);
        } else {
            drawCenteredText(graph, standardFont, "No Master Image", new java.awt.Rectangle(masterLocation.x, masterLocation.y, masterWidth, masterHeight), Color.LIGHT_GRAY);
        }
        drawRectangle(graph, outlineStroke, masterLocation.x, masterLocation.y, masterWidth, masterHeight, master != null ? Status.MASTER_IMAGE.tagColor : Color.DARK_GRAY);

        // Add test and snapshot names
        int halfFontSize = snapshotSize.fontSize / 2 + 2;
        drawText(graph, standardFont, currentTestName, border, border - halfFontSize, Color.LIGHT_GRAY);
        drawText(graph, boldFont, currentSnapshotName, border, border + halfFontSize, Color.WHITE);

        // Current Status Tag
        drawText(graph, standardFont, imageTag.text, border, border + halfFontSize * 3, imageTag.tagColor);

        // Add expected and actual labels
        drawText(graph, standardFont, "Expected" + (!sameSize ? " (w" + originalMaster.getWidth() + ", h" + originalMaster.getHeight() + ")" : ""), border, border * 2 - halfFontSize, Color.LIGHT_GRAY);
        drawText(graph, standardFont, "Actual" + (!sameSize ? " (w" + originalSnapshot.getWidth() + ", h" + originalSnapshot.getHeight() + ")" : "") + (snapshotSizeAdjusted ? " Adjusted" : ""), finalWidth - border - snapshotWidth, border * 2 - halfFontSize, Color.LIGHT_GRAY);
        return sideBySide;
    }

    /** Draws a rectangle on the graphics2d object, and restores the previous graphics2d stroke and color settings after rendering the rectangle. */
    private void drawRectangle(Graphics2D g, float thickness, int x, int y, int width, int height, Color color) {
        Stroke oldStroke = g.getStroke();
        Color oldColor = g.getColor();
        g.setColor(color);
        g.setStroke(new BasicStroke(thickness));
        g.drawRect(x, y, width, height);
        g.setStroke(oldStroke);
        g.setColor(oldColor);
    }

    /** Draws text on a graphics2d object at a point */
    private void drawText(Graphics2D g, Font font, String text, int x, int y, Color color) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(color);
        g.setFont(font);
        g.drawString(text, x, y);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_DEFAULT);
    }

    /**
     * Draw a String centered in the middle of a Rectangle.
     * https://stackoverflow.com/questions/27706197/how-can-i-center-graphics-drawstring-in-java
     *
     * @param g The Graphics instance.
     * @param text The String to draw.
     * @param rect The Rectangle to center the text in.
     */
    private void drawCenteredText(Graphics2D g, Font font, String text, java.awt.Rectangle rect, Color color) {
        FontMetrics metrics = g.getFontMetrics(font);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(color);
        int x = rect.x + (rect.width - metrics.stringWidth(text)) / 2;
        int y = rect.y + ((rect.height - metrics.getHeight()) / 2) + metrics.getAscent();
        g.setFont(font);
        g.drawString(text, x, y);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_DEFAULT);
    }


    @Override
    public String toString() {
        if (comparisonModel == ComparisonModel.STANDARD) {
            StringBuilder blockResults = new StringBuilder();
            for (int x = 0; x < numHorizontalBlocks; x++)
                for (int y = 0; y < numVerticalBlocks; y++) {
                    blockResults.append(String.format("(%d,%d) %s %s| ", x, y, blockMask[x][y] ? "masked" : "unmasked", (!blockMask[x][y] ? String.format("%s<<%.1f>>%s", (blockColorDistances[x][y] > maxColorDistance ? ConsoleColor.ANSI_RED : ""), blockColorDistances[x][y], "" + ConsoleColor.ANSI_RESET) : "")));
                }
            return "Standard Image Comparison: " +
                    "\n\nMaster Size = (w" + masterWidth + ",h" + masterHeight +")" +
                    "\nBlock Size = " + blockSize +
                    "\nNum Vertical Blocks = " + numVerticalBlocks +
                    "\nNum Horizontal Blocks = " + numHorizontalBlocks +
                    "\nMax Color Distance = " + maxColorDistance +
                    "\nLargest Color Diff = " + largestColorDiff +
                    "\nMatch = " + match +
                    "\nSnapshot Size Adjusted = " + snapshotSizeAdjusted +
                    (snapshotSizeAdjusted ? (
                            "\nOriginal snapshot size = (w" + originalSnapshot.getWidth() + ",h" + originalSnapshot.getHeight() + ")") : "")  +
//                    ", blockMask=" + blockResults +
                    "";
        } else {
            return "Find specified image inside a region:" +
                    "\n\nMaster Size = (w" + masterWidth + ",h" + masterHeight +")" +
                    "\nMaster Sub Image Target = " + targetDefinition +
                    "\nLocate Target Within Bounding Box = " + targetFindRegion +
                    "\nMatch Found = " + match +
                    (targetFound != null ? (
                        "\nLocation Target Found on Snapshot = " + targetFound +
                          "\nBlock Size = " + blockSize +
                          "\nNum Vertical Blocks = " + numVerticalBlocks +
                          "\nNum Horizontal Blocks = " + numHorizontalBlocks +
                          "\nMax Color Distance = " + maxColorDistance +
                          "\nLargest Color Diff = " + largestColorDiff) : "") +
                    "";
        }
    }
}

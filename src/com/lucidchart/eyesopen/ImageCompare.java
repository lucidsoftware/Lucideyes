package com.lucidchart.eyesopen;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
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

    private final BufferedImage master; // The master image
    private final BufferedImage snapshot; // The current test snapshot to compare to the master
    private final int imageHeight;
    private final int imageWidth;
    private final int blockSize;
    private final double maxColorDistance;
    private final boolean match;

    /** Used to flag the options to perform a standard image to image comparison, or find an image within a region */
    enum ComparisonModel {
        STANDARD,
        FIND_WITH_REGION
    }
    final ComparisonModel comparisonModel;

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
    private final Region masterSubRegion;
    private final Region snapshotFindLimit;
    private Optional<Rectangle> targetFound = Optional.empty();

    /** Holds the masked image, when and if created */
    private Optional<BufferedImage> maskedMaster = Optional.empty();
    private Optional<BufferedImage> maskedSnapshot = Optional.empty();
    private Optional<BufferedImage> markedDifferences = Optional.empty();
    private Optional<BufferedImage> pixelDiff = Optional.empty();

    /** Provides a way of seeing the current max color difference of corresponding blocks in the images */
    private double largestColorDiff = 0.0;


    /** Compares two images, with the ability to make adjustments of how closely images must match.
     * Image pixel dimensions must match, or the comparison will fail automatically
     *
     * @param master The base master to which we will compare the snapshot image.
     * @param snapshot The current test snapshot.  A null snapshot, will automatically match as false, but can enable mask info for the master.
     * @param blockSize The size of a block of pixels, that will be averaged together into a single color
     * @param maxColorDistance The max color distance allowed between the master and test images.
     */

    private ImageCompare(BufferedImage master, BufferedImage snapshot, int blockSize, double maxColorDistance, Set<Region> maskComponents) {

        require(master != null || snapshot != null, "At least one image must be provided");
        require(blockSize >= 1, "Block size must be greater than or equal to 1");
        require(maxColorDistance > 0, "Max color distance must be greater than 0");

        // Finding an image within a region prevents focus and exclude mask requests
        Set<Region> focusAndExcludeMask = new HashSet<>();
        List<Region> findThisTargetMask = new ArrayList<>();
        List<Region> withinThisRegionMask = new ArrayList<>();
        if (maskComponents != null) {
            focusAndExcludeMask = maskComponents.stream().filter(mc -> mc.regionAction == RegionAction.EXCLUDE || mc.regionAction == RegionAction.FOCUS).collect(Collectors.toSet());
            findThisTargetMask = maskComponents.stream().filter(mc -> mc.regionAction == RegionAction.FIND_THIS_TARGET).collect(Collectors.toList());
            withinThisRegionMask = maskComponents.stream().filter(mc -> mc.regionAction == RegionAction.WITHIN_THIS_BOUNDING_BOX).collect(Collectors.toList());
            require((focusAndExcludeMask.size() > 0 && findThisTargetMask.size() == 0 && withinThisRegionMask.size() == 0) ||
                            (focusAndExcludeMask.size() == 0 && findThisTargetMask.size() == 1 && withinThisRegionMask.size() == 1),
                    "Finding an image within a region prevents use of focus/exclude masks, and requires ONE image target region within ONE bounding box limit region.");
        }
        this.comparisonModel = findThisTargetMask.isEmpty() ? ComparisonModel.STANDARD : ComparisonModel.FIND_WITH_REGION;

        BufferedImage imageToMeasure = master != null ? master : snapshot;
        this.imageWidth = imageToMeasure.getWidth();
        this.imageHeight = imageToMeasure.getHeight();

        // Provide a blank snapshot if none provided
        if (snapshot == null) snapshot = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
        if (master == null) master = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);

        require((snapshot.getHeight() == imageHeight && snapshot.getWidth() == imageWidth), "Master and snapshot images MUST be the same size");

        this.master = master;
        this.snapshot = snapshot;
        this.blockSize = blockSize;
        this.maxColorDistance = maxColorDistance;

        if (comparisonModel == ComparisonModel.FIND_WITH_REGION) {
            this.snapshotFindLimit = withinThisRegionMask.get(0);
            this.masterSubRegion = findThisTargetMask.get(0);
            require(snapshotFindLimit.location.x >= 0 && snapshotFindLimit.location.y < master.getWidth() &&
                    snapshotFindLimit.location.y >= 0 && snapshotFindLimit.location.y < master.getHeight(), "Location of desired range " + snapshotFindLimit.location + " must be located within the size of the snapshot (" + master.getWidth() + "," + master.getHeight() + ")");
            require(snapshotFindLimit.location.x + snapshotFindLimit.width <= master.getWidth() &&
                    snapshotFindLimit.location.y + snapshotFindLimit.height <= master.getHeight(), "Boundaries of specified find limits " + snapshotFindLimit.location + " with dimensions (w" + snapshotFindLimit.width + ",h" + snapshotFindLimit.height + ") exceed the snapshot image (" + master.getWidth() + "," + master.getHeight() + ")");
            require(masterSubRegion.location.x >= 0 && masterSubRegion.location.y < master.getWidth() &&
                    masterSubRegion.location.y >= 0 && masterSubRegion.location.y < master.getHeight(), "Location of target sub-image " + masterSubRegion.location + " must be located within the size of the image (" + master.getWidth() + "," + master.getHeight() + ")");
            require(masterSubRegion.location.x + masterSubRegion.width <= master.getWidth() &&
                    masterSubRegion.location.y + masterSubRegion.height <= master.getHeight(), "Boundaries of target sub-image " + masterSubRegion.location + " with dimensions (w" + masterSubRegion.width + ",h" + masterSubRegion.height + ") exceed the snapshot image (" + master.getWidth() + "," + master.getHeight() + ")");


            // This paints the mask to include the area wherein to find the target image
            Set<Region> includeRegion = new HashSet<>();
            includeRegion.add(Region.apply(snapshotFindLimit.location, snapshotFindLimit.width, snapshotFindLimit.height, RegionAction.FOCUS));
            this.mask = composeMask(imageWidth, imageHeight, includeRegion);

            // Block mask of the subRegion area
            boolean[][] subRegionMask = composeMask(masterSubRegion.width, masterSubRegion.height, null);
            this.blockMask = composeBlockMask(blockSize, DEFAULT_BLOCK_PIXEL_THRESHOLD, masterSubRegion.width, masterSubRegion.height, subRegionMask);
            this.numHorizontalBlocks = blockMask.length; // Just for clarity
            this.numVerticalBlocks = blockMask[0].length;

            // Unused, but needs initialization
            this.blockComparisonMap = null;

            this.targetFound = imageFoundWithin(master, masterSubRegion, snapshotFindLimit, snapshot);
            this.match = (targetFound.isPresent());

        // Standard comparison model
        } else {
            this.mask = composeMask(imageWidth, imageHeight, focusAndExcludeMask);
            this.blockMask = composeBlockMask(blockSize, DEFAULT_BLOCK_PIXEL_THRESHOLD, imageWidth, imageHeight, mask);
            this.numHorizontalBlocks = blockMask.length; // Just for clarity
            this.numVerticalBlocks = blockMask[0].length;

            // Unused, but need initialization
            this.snapshotFindLimit = null;
            this.masterSubRegion = null;

            // The grid contains a positive/negative comparison for every averaged color block in the image.
            this.blockColorDistances = new double[numHorizontalBlocks][numVerticalBlocks];
            this.blockComparisonMap = getAverageColorComparisonMap(blockMask, maxColorDistance, blockColorDistances);

            // Returns true if every color comparison of averaged block colors is within the color distance threshold
            this.match = allBlocksMatch(blockComparisonMap);
        }
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


    /* ---ENTIRE IMAGE---

    /** Compare two images using the default block size and color proximity */
    public static ImageCompare apply(BufferedImage master, BufferedImage snapshot) {
        return new ImageCompare(master, snapshot, DEFAULT_BLOCK_SIZE, DEFAULT_MAX_COLOR_DISTANCE, null);
    }

    /** Compare two images using the matchLevel specifications */
    public static ImageCompare apply(BufferedImage master, BufferedImage snapshot, MatchLevel matchLevel) {
        return new ImageCompare(master, snapshot, matchLevel.blockSize, matchLevel.maxColorDistance, null);
    }

    /** Compare two images using the default color distance */
    public static ImageCompare apply(BufferedImage master, BufferedImage snapshot, int blockSize) {
        return new ImageCompare(master, snapshot, blockSize, DEFAULT_MAX_COLOR_DISTANCE, null);
    }

    /** Compare two images using the default block size */
    public static ImageCompare apply(BufferedImage master, BufferedImage snapshot, double maxColorDistance) {
        return new ImageCompare(master, snapshot, DEFAULT_BLOCK_SIZE, maxColorDistance, null);
    }



    /*
       ___       _     _ _              _   _ _ _ _   _
      / _ \_   _| |__ | (_) ___   /\ /\| |_(_) (_) |_(_) ___  ___
     / /_)/ | | | '_ \| | |/ __| / / \ \ __| | | | __| |/ _ \/ __|
    / ___/| |_| | |_) | | | (__  \ \_/ / |_| | | | |_| |  __/\__ \
    \/     \__,_|_.__/|_|_|\___|  \___/ \__|_|_|_|\__|_|\___||___/

     */


    /** Returns the master ged with the current exclusion mask */
    public BufferedImage getMasterWithMask() {
        BufferedImage masterMask = maskedMaster.orElseGet(() -> getMaskedImage(master, DEFAULT_DIVISOR_TO_DARKEN_MASK, ImageType.MASTER));
        this.maskedMaster = Optional.of(masterMask);
        return masterMask;
    }

    /** Returns the snapshot darkened with the current exclusion mask */
    public BufferedImage getSnapshotWithMask() {
        BufferedImage snapshotMask = maskedSnapshot.orElseGet(() -> getMaskedImage(snapshot, DEFAULT_DIVISOR_TO_DARKEN_MASK, ImageType.SNAPSHOT));
        this.maskedSnapshot = Optional.of(snapshotMask);
        return snapshotMask;
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
            BufferedImage circledDiff = markedDifferences.orElseGet(() -> createMarkUp(blockComparisonMap, blockSize));
            markedDifferences = Optional.of(circledDiff);
            return circledDiff;
        } else return createMessageImage(commonError("Circled diff report"));
    }

    /** Obtain a pixel-diff overlay **/
    public BufferedImage getPixelDiff() {
        if (comparisonModel == ComparisonModel.STANDARD) {
            BufferedImage pDiff = pixelDiff.orElseGet(() -> createPixelDiffImage(master, snapshot, blockComparisonMap, blockMask, blockSize, maxColorDistance));
            pixelDiff = Optional.of(pDiff);
            return pDiff;
        } else return createMessageImage(commonError("Pixel diff report"));
    }


    /** True if the snapshot matches the master, based on block size, color proximity, image size, and mask */
    public boolean isMatch() {
        return match;
    }


    /** Indicates this test is trying to find a certain image match anywhere within a region */
    public boolean isFindInRegionModel() {
        return comparisonModel == ComparisonModel.FIND_WITH_REGION;
    }


    /** When using the 'find in region' model, this returns the rectangle of the location on the snapshot image.
     * This can be helpful when needing to find and interact with a location */
    public Optional<Rectangle> getLocationOfTargetImageOnSnapshot() {
        return targetFound;
    }


    /*
      _____       _                        _         _   _ _ _ _   _
      \_   \_ __ | |_ ___ _ __ _ __   __ _| |  /\ /\| |_(_) (_) |_(_) ___  ___
       / /\/ '_ \| __/ _ \ '__| '_ \ / _` | | / / \ \ __| | | | __| |/ _ \/ __|
    /\/ /_ | | | | ||  __/ |  | | | | (_| | | \ \_/ / |_| | | | |_| |  __/\__ \
    \____/ |_| |_|\__\___|_|  |_| |_|\__,_|_|  \___/ \__|_|_|_|\__|_|\___||___/

     */

    private static String commonError(String feature) {
        return feature + " is not available for 'find image within area' model.";
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

    /** Determines if a subset of an image is found within a defined bounding box. */
    private Optional<Rectangle> imageFoundWithin(BufferedImage master, Region findThisFromMaster, Region requiredWithinThisRegion, BufferedImage snapshot) {

        // Get cropped working images
        BufferedImage croppedMaster = (findThisFromMaster == null) ? master : master.getSubimage(findThisFromMaster.location.x, findThisFromMaster.location.y, findThisFromMaster.width, findThisFromMaster.height);
        BufferedImage croppedSnapshot = (requiredWithinThisRegion == null) ? snapshot : snapshot.getSubimage(requiredWithinThisRegion.location.x, requiredWithinThisRegion.location.y, requiredWithinThisRegion.width, requiredWithinThisRegion.height);

        // If the cropped working images are the same size, find out immediately if they are the same.
        if (croppedMaster.getHeight() == croppedSnapshot.getHeight() && croppedMaster.getWidth() == croppedSnapshot.getWidth())
            return ImageCompare.apply(croppedMaster, croppedSnapshot).isMatch() ? Optional.of(new Rectangle(0, 0, croppedMaster.getWidth(), croppedMaster.getHeight())) : Optional.empty();

        // The desired image must be at least as big as the limiting bounding box.
        if (croppedMaster.getHeight() > croppedSnapshot.getHeight() || croppedMaster.getWidth() > croppedSnapshot.getWidth()) return Optional.empty();

        // If working images are not the same size, our approach is to attempt each possibility with quick fails, until all valid attempts are exhausted.
        int xRange = croppedSnapshot.getWidth() - croppedMaster.getWidth();
        int yRange = croppedSnapshot.getHeight() - croppedMaster.getHeight();

        for (int x = 0; x <= xRange; x++)
            for (int y = 0; y <= yRange; y++) {
                BufferedImage croppedSnapshotSubsetAttempt = croppedSnapshot.getSubimage(x, y, croppedMaster.getWidth(), croppedMaster.getHeight());
                if (quickCompare(croppedMaster, croppedSnapshotSubsetAttempt, maxColorDistance)) return Optional.of(new Rectangle(x + requiredWithinThisRegion.location.x, y + requiredWithinThisRegion.location.y, croppedMaster.getWidth(), croppedMaster.getHeight()));
            }
        return Optional.empty();
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
    private boolean quickCompare(BufferedImage one, BufferedImage two, double maxColorDistance) {

        int numHorizontalBlocks = getNumHorizontalBlocks(blockMask);
        int numVerticalBlocks = getNumVerticalBlocks(blockMask);

        for (int vB = 0; vB < numVerticalBlocks; vB++)
            for (int hB = 0; hB < numHorizontalBlocks; hB++) {
                if (distance(averageBlockPixels(hB, vB, blockSize, one), averageBlockPixels(hB, vB, blockSize, two)) > maxColorDistance) return false;
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
     * @return a mask at the block level, a block containing the value TRUE means we will not consider this block in the image analysis.
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
     * Rather than doing some fancy math to add and subtract rectangles, I'm simply coloring a boolean map by first adding inclusive areas, then subtracting exclusive areas.
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
                if (region.regionAction == RegionAction.FOCUS) include.add(region);
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
            Optional<Rectangle> rectangle = (imageType == ImageType.MASTER) ? Optional.of(new Rectangle(masterSubRegion.location.x, masterSubRegion.location.y, masterSubRegion.width, masterSubRegion.height)) : targetFound;

            rectangle.ifPresent(r -> {
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
            });
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


    @Override
    public String toString() {
        if (comparisonModel == ComparisonModel.STANDARD) {
            StringBuilder blockResults = new StringBuilder("");
            for (int x = 0; x < numHorizontalBlocks; x++)
                for (int y = 0; y < numVerticalBlocks; y++) {
                    blockResults.append(String.format("(%d,%d) %s %s| ", x, y, blockMask[x][y] ? "masked" : "unmasked", (!blockMask[x][y] ? String.format("%s<<%.1f>>%s", (blockColorDistances[x][y] > maxColorDistance ? ConsoleColor.ANSI_RED : ""), blockColorDistances[x][y], "" + ConsoleColor.ANSI_RESET) : "")));
                }
            return "StandardLucidCompare{" +
                    "imageHeight=" + imageHeight +
                    ", imageWidth=" + imageWidth +
                    ", blockSize=" + blockSize +
                    ", numVerticalBlocks=" + numVerticalBlocks +
                    ", numHorizontalBlocks=" + numHorizontalBlocks +
                    ", maxColorDistance=" + maxColorDistance +
                    ", largestColorDiff=" + largestColorDiff +
                    ", match=" + match +
                    ", blockMask=" + blockResults +
                    '}';
        } else {
            return "ImageFind{" +
                    "imageHeight=" + imageHeight +
                    ", imageWidth=" + imageWidth +
                    ", masterSubImage=" + masterSubRegion +
                    ", snapshotFindRestriction=" + snapshotFindLimit +
                    ", match=" + match +
                    ", targetFound= " + targetFound +
                    "}";
        }
    }
}

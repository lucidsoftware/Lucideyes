package com.lucidchart.eyesopen;

import org.testng.Assert;
import org.testng.annotations.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class ImageCompareTest {

    private File getFile(String fileNameWithExtension) {
        return Paths.get("./test/com/lucidchart/eyesopen/inputImages/" + fileNameWithExtension).toFile();
    }

    private void saveResults(ImageCompare imageTest, String name, String extension) throws IOException {
        saveImage(imageTest.getSnapshotWithMask(), name, "Snapshot-Masked", extension);
        saveImage(imageTest.getMasterWithMask(), name, "Master-Masked", extension);
        saveImage(imageTest.getBlockMask(), name, "Block Mask", extension);
        saveImage(imageTest.getPixelDiff(), name, "Pixel Diff", extension);
        saveImage(imageTest.getBlockColorComparisonResults(), name, "BlockColorComparisonMap", extension);
        saveImage(imageTest.getCircledDiff(), name, "Marked-Up", extension);
    }

    private void saveImage(BufferedImage image, String name, String description, String extension) throws IOException {
        Path testOutputDir = Files.createDirectories(Paths.get("./output/"+name));
        File output = new File(testOutputDir + "/" + description + "." + extension);
        ImageIO.write(image, extension, output);
    }

    @Test
    public void testSelection() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("Selected.PNG"));
        BufferedImage snapshot = ImageIO.read(getFile("Unselected.PNG"));
        Set<Region> mask = new HashSet<>();
        mask.add(Region.apply(0, 0, 2042, 42, RegionAction.EXCLUDE));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.TOLERANT,
                mask
        );
        saveResults(snapshotCompare, "BigDifferences", "jpg");
        Assert.assertFalse(snapshotCompare.isMatch());
    }

    @Test
    public void testSeveralDifferences() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("UnSynced.PNG"));
        BufferedImage snapshot = ImageIO.read(getFile("Synced.PNG"));
        Set<Region> mask = new HashSet<>();
        mask.add(Region.apply(233, 373, 848, 416, RegionAction.FOCUS));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.TOLERANT,
                mask
        );
        saveResults(snapshotCompare, "SeveralDifferences", "jpg");
        Assert.assertFalse(snapshotCompare.isMatch());
    }

    @Test
    public void testCancelAnimation() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("Cancel1.PNG"));
        BufferedImage snapshot = ImageIO.read(getFile("Cancel2.PNG"));
        Set<Region> mask = new HashSet<>();
        mask.add(Region.apply(0, 0, 1241, 55, RegionAction.EXCLUDE));
        mask.add(Region.apply(546, 960, 145, 145, RegionAction.EXCLUDE));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.TOLERANT,
                mask
        );
        saveResults(snapshotCompare, "CancelAnimation", "jpg");
        Assert.assertTrue(snapshotCompare.isMatch());
    }

    @Test
    public void testAVerySmallImage() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("small.jpg"));
        BufferedImage snapshot = ImageIO.read(getFile("smallWithDiff.jpg"));
        Set<Region> mask = new HashSet<>();
        mask.add(Region.apply(0, 25, 41, 16, RegionAction.EXCLUDE));
        mask.add(Region.apply(0, 0, 20, 10, RegionAction.EXCLUDE));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.STRICT,
                mask
        );
        saveResults(snapshotCompare, "Very Small", "PNG");
        Assert.assertFalse(snapshotCompare.isMatch());
    }

    @Test
    public void testInsideFocus() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("apple.jpg"));
        BufferedImage snapshot = ImageIO.read(getFile("worm.jpg"));
        Set<Region> mask = new HashSet<>();
        // Include the area with the problem
        mask.add(Region.apply(80, 25, 400, 400, RegionAction.FOCUS));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.STRICT,
                mask
        );
        saveResults(snapshotCompare, "InsideInclusion", "jpg");
        Assert.assertFalse(snapshotCompare.isMatch());
    }

    @Test
    public void testOutsideFocus() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("apple.jpg"));
        BufferedImage snapshot = ImageIO.read(getFile("worm.jpg"));
        // Do not include the area with the problem
        Set<Region> mask = new HashSet<>();
        mask.add(Region.apply(70, 25, 289, 546, RegionAction.FOCUS));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.TOLERANT,
                mask
        );
        saveResults(snapshotCompare, "OutsideFocus", "jpg");
        Assert.assertTrue(snapshotCompare.isMatch());
    }

    @Test
    public void testInsideFocusAndExcluded() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("apple.jpg"));
        BufferedImage snapshot = ImageIO.read(getFile("worm.jpg"));
        // Include the area with the problem, but explicitly exclude the problem area
        Set<Region> mask = new HashSet<>();
        mask.add(Region.apply(80, 25, 483, 546, RegionAction.FOCUS));
        mask.add(Region.apply(356, 247, 58, 49, RegionAction.EXCLUDE));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.STRICT,
                mask
        );
        saveResults(snapshotCompare, "InsideFocusAndExcluded", "jpg");
        Assert.assertTrue(snapshotCompare.isMatch());
    }

    @Test
    public void testInsideFocusAndOnlyPartiallyExcluded() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("apple.jpg"));
        BufferedImage snapshot = ImageIO.read(getFile("worm.jpg"));
        // Include the area without the problem, but only partially exclude the problem area
        Set<Region> mask = new HashSet<>();
        mask.add(Region.apply(80, 25, 483, 546, RegionAction.FOCUS));
        mask.add(Region.apply(356, 247, 58, 25, RegionAction.EXCLUDE));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.TOLERANT,
                mask
        );
        saveResults(snapshotCompare, "InsideFocusAndPartiallyExcluded", "jpg");
        Assert.assertFalse(snapshotCompare.isMatch());
    }

    @Test
    public void testExcluded() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("apple.jpg"));
        BufferedImage snapshot = ImageIO.read(getFile("worm.jpg"));
        // Explicitly exclude the problem area, without any inclusions
        Set<Region> mask = new HashSet<>();
        mask.add(Region.apply(352, 247, 58, 49, RegionAction.EXCLUDE));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.TOLERANT,
                mask
        );
        saveResults(snapshotCompare, "Excluded", "jpg");
        Assert.assertTrue(snapshotCompare.isMatch());
    }

    @Test
    public void testNotExcluded() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("apple.jpg"));
        BufferedImage snapshot = ImageIO.read(getFile("worm.jpg"));
        // Explicitly exclude the problem area, without any inclusions
        Set<Region> mask = new HashSet<>();
        mask.add(Region.apply(10, 10, 58, 49, RegionAction.EXCLUDE));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.STRICT,
                mask
        );
        saveResults(snapshotCompare, "NotExcluded", "jpg");
        Assert.assertFalse(snapshotCompare.isMatch());
    }

    @Test
    public void testLightColoredDiff() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("apple.jpg"));
        BufferedImage snapshot = ImageIO.read(getFile("extraThinLine.jpg"));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.STRICT
        );
        saveResults(snapshotCompare, "extraThinLine", "jpg");
        Assert.assertFalse(snapshotCompare.isMatch());
    }

    static class TestImages {
        String description;
        String stdImage;
        String testImage;
        boolean exact;
        boolean strict;
        boolean tolerant;

        TestImages(String description, String stdImage, String testImage, boolean exact, boolean strict, boolean tolerant) {
            this.description = description;
            this.stdImage = stdImage;
            this.testImage = testImage;
            this.exact = exact;
            this.strict = strict;
            this.tolerant = tolerant;
        }

        static TestImages apply(String description, String stdImage, String testImage, boolean exact, boolean strict, boolean tolerant) {
            return new TestImages(description, stdImage, testImage, exact, strict, tolerant);
        }


    }

    /**
     * 1. Test Description
     * 2. Standard Image File Name (Located in 'directoryWithImages')
     * 3. Test Image File Name (Located in the same directory)
     *
     * Expected Results
     * 4. Exact
     * 5. Precise
     * 6. Tolerant
     */
    private List<TestImages> test = Arrays.asList(
            TestImages.apply("Self Comparison", "apple", "apple", true, true, true),
            TestImages.apply("Subtle Difference2", "apple", "subtleDiff2", false, true, true),
            TestImages.apply("Subtle Difference4", "base2", "subtleDiff4", false, true, true),
            TestImages.apply("Subtle Difference1", "apple", "subtleDiff1", false, false, true),
            TestImages.apply("Insignificant crop", "apple", "insignificantCrop", false, false, true),
            TestImages.apply("Extra thin light colored line", "apple", "extraThinLine", false, false, true),
            TestImages.apply("Subtle Difference3", "base", "subtleDiff3", false, false, true),
            TestImages.apply("Slightly Miscolored Icon", "base2", "slightlyMiscoloredIcons", false, false, true),
            TestImages.apply("Extra small line", "apple", "extraLine", false, false, false),
            TestImages.apply("Unwanted light colored text", "apple", "unwantedText", false, false, false),
            TestImages.apply("Unwanted crop", "apple", "unwantedCrop", false, false, false),
            TestImages.apply("Clear small difference", "apple", "worm", false, false, false),
            TestImages.apply("Missing text", "base", "missingText", false, false, false),
            TestImages.apply("Missing Icon", "base2", "missingIcon", false, false, false),
            TestImages.apply("Miscolored Icon", "base2", "misColoredIcon", false, false, false)
            );

    @Test
    public void testImageComparisonWithoutMask() throws IOException {
        boolean passed = true;

        for (TestImages testImage : test) {

            // Get Exact
            boolean exactResult = ImageCompare
                    .apply(ImageIO.read(getFile(testImage.stdImage+".jpg")), ImageIO.read(getFile(testImage.testImage+".jpg")), MatchLevel.EXACT)
                    .isMatch();
            boolean exactPassed = testImage.exact && exactResult || !testImage.exact && !exactResult;

            // Get Strict
            boolean strictResult = ImageCompare
                    .apply(ImageIO.read(getFile(testImage.stdImage+".jpg")), ImageIO.read(getFile(testImage.testImage+".jpg")), MatchLevel.STRICT)
                    .isMatch();
            boolean strictPassed = testImage.strict && strictResult || !testImage.strict && !strictResult;

            // Get Tolerant
            boolean tolerantResult = ImageCompare
                    .apply(ImageIO.read(getFile(testImage.stdImage+".jpg")), ImageIO.read(getFile(testImage.testImage+".jpg")), MatchLevel.TOLERANT)
                    .isMatch();
            boolean tolerantPassed = testImage.tolerant && tolerantResult || !testImage.tolerant && !tolerantResult;

            boolean casePassed = exactPassed && strictPassed && tolerantPassed;

//            System.out.format(
//                    "%-35s  EXACT -> %-10s  STRICT -> %-10s  TOLERANT -> %-10s%s%n",
//                    testImage.description + (casePassed ? ConsoleColor.ANSI_GREEN : ConsoleColor.ANSI_RED),
//                    exactResult ? "PASSED" : "FAILED",
//                    strictResult ? "PASSED" : "FAILED",
//                    tolerantResult ? "PASSED" : "FAILED",
//                    ConsoleColor.ANSI_RESET);

        passed = passed && casePassed;
        }
        Assert.assertTrue(passed);
    }

}

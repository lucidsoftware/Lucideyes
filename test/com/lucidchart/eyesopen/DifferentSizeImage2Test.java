package com.lucidchart.eyesopen;

import org.testng.Assert;
import org.testng.annotations.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static com.lucidchart.eyesopen.TestUtil.getFile;
import static com.lucidchart.eyesopen.TestUtil.saveResults;

public class DifferentSizeImage2Test {

    @Test
    public void biggerWidthSmallerHeightTest() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("0pxOriginal.png"));
        BufferedImage snapshot = ImageIO.read(getFile("1pxLargerWidth1pxSmallerHeight.png"));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.TOLERANT
        );
        saveResults(snapshotCompare, "BiggerWidthAndSmallerHeight");
        Assert.assertFalse(snapshotCompare.isMatch());
        Assert.assertFalse(snapshotCompare.isSameSize());
        Assert.assertFalse(snapshotCompare.isSnapshotSizeAdjusted());
        Assert.assertEquals(snapshotCompare.getStatus(), Status.DIFFERENT_SIZE);
    }

    @Test
    public void testExclusionMaskWithSizeDifferences() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("2pxSmaller.png"));
        BufferedImage snapshot = ImageIO.read(getFile("1pxBiggerWithDiff.png"));
        Set<Region> mask = new HashSet<>();
        // Purposely tested overshot of region width/height off the image.
        mask.add(Region.apply(1400, 200, 700, 800, RegionAction.EXCLUDE));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.TOLERANT,
                mask
        );
        saveResults(snapshotCompare, "ExclusionMaskDifferencesWithSizeDiff");
        Assert.assertTrue(snapshotCompare.isMatch());
        Assert.assertEquals(snapshotCompare.getStatus(), Status.PASSED);
    }

    @Test
    public void testCombinedMaskWithSizeDifferences() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("2pxSmaller.png"));
        BufferedImage snapshot = ImageIO.read(getFile("1pxBiggerWithDiff.png"));
        Set<Region> mask = new HashSet<>();
        // Purposely tested overshot of region width/height off the image.
        mask.add(Region.apply(1400, 200, 700, 800, RegionAction.FOCUS));
        mask.add(Region.apply(1590, 390, 400, 500, RegionAction.EXCLUDE));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.TOLERANT,
                mask
        );
        saveResults(snapshotCompare, "CombinedMaskDifferencesWithSizeDiff");
        Assert.assertTrue(snapshotCompare.isMatch());
        Assert.assertEquals(snapshotCompare.getStatus(), Status.PASSED);
    }

    @Test
    public void testFindWithinMaskAndSizeDifferences() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("2pxSmaller.png"));
        BufferedImage snapshot = ImageIO.read(getFile("1pxBiggerWithDiff.png"));
        Set<Region> mask = new HashSet<>();
        // Purposely tested overshot of region width/height off the image.
        mask.add(Region.apply(283, 174, 206, 154, RegionAction.FIND_THIS_TARGET));
        mask.add(Region.apply(220, 150, 300, 300, RegionAction.WITHIN_THIS_BOUNDING_BOX));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.TOLERANT,
                mask
        );
        saveResults(snapshotCompare, "findWithinMaskWithDifferentSizes");
        Assert.assertTrue(snapshotCompare.isMatch());
        Assert.assertFalse(snapshotCompare.isSameSize());
        Assert.assertTrue(snapshotCompare.isSnapshotSizeAdjusted());
        Assert.assertEquals(snapshotCompare.getStatus(), Status.PASSED);
    }

    @Test
    public void testComplexFindWithinMaskAndSizeDifferences() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("2pxSmaller.png"));
        BufferedImage snapshot = ImageIO.read(getFile("1pxBiggerWithDiff.png"));
        Set<Region> mask = new HashSet<>();
        // Purposely tested overshot of region width/height off the image.
        mask.add(Region.apply(283, 174, 206, 154, RegionAction.FIND_THIS_TARGET));
        mask.add(Region.apply(220, 150, 300, 300, RegionAction.WITHIN_THIS_BOUNDING_BOX));
        mask.add(Region.apply(300,200,50,50, RegionAction.EXCLUDE));
        mask.add(Region.apply(100,10,10,10, RegionAction.FOCUS));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.TOLERANT,
                mask
        );
        saveResults(snapshotCompare, "findWithinComplexMaskWithDifferentSizes");
        Assert.assertTrue(snapshotCompare.isMatch());
        Assert.assertFalse(snapshotCompare.isSameSize());
        Assert.assertTrue(snapshotCompare.isSnapshotSizeAdjusted());
        Assert.assertEquals(snapshotCompare.getStatus(), Status.PASSED);
    }

    @Test
    public void testFindWithinComplexMaskAndSizeDifferences() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("OriginalPhoneSize.jpeg"));
        BufferedImage snapshot = ImageIO.read(getFile("LargerPhoneSize.jpeg"));
        Set<Region> mask = new HashSet<>();
        mask.add(Region.apply(376, 1014, 204, 249, RegionAction.FIND_THIS_TARGET));
        mask.add(Region.apply(50, 50, 1100, 1500, RegionAction.WITHIN_THIS_BOUNDING_BOX));
        mask.add(Region.apply(376 + 84, 1014 + 78, 40, 40, RegionAction.EXCLUDE));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.TOLERANT,
                mask
        );
        saveResults(snapshotCompare, "findWithinComplexMaskWithDifferentSizes");
        Assert.assertTrue(snapshotCompare.isMatch());
        Assert.assertFalse(snapshotCompare.isSameSize());
        Assert.assertTrue(snapshotCompare.isSnapshotSizeAdjusted());
        Assert.assertEquals(snapshotCompare.getStatus(), Status.PASSED);
    }

    @Test
    public void testFindWithinComplexMaskAndSmallerSizeDifferences2() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("OriginalPhoneSize.jpeg"));
        BufferedImage snapshot = ImageIO.read(getFile("SmallerPhoneSize.jpeg"));
        Set<Region> mask = new HashSet<>();
        mask.add(Region.apply(376, 1014, 204, 249, RegionAction.FIND_THIS_TARGET));
        mask.add(Region.apply(50, 50, 1100, 1500, RegionAction.WITHIN_THIS_BOUNDING_BOX));
        mask.add(Region.apply(376 + 84, 1014 + 78, 40, 40, RegionAction.EXCLUDE));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.TOLERANT,
                mask
        );
        saveResults(snapshotCompare, "findWithinComplexMaskWithSmallerDifferentSizes");
        Assert.assertTrue(snapshotCompare.isMatch());
        Assert.assertFalse(snapshotCompare.isSameSize());
        Assert.assertFalse(snapshotCompare.isSnapshotSizeAdjusted());
        Assert.assertEquals(snapshotCompare.getStatus(), Status.PASSED);
    }

    @Test
    public void testFindWithinComplexMaskAndSmallerSizeDifferences3() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("OriginalPhoneSize.jpeg"));
        BufferedImage snapshot = ImageIO.read(getFile("LargerPhoneSizeWithDiff.jpeg"));
        Set<Region> mask = new HashSet<>();
        mask.add(Region.apply(376, 1014, 204, 249, RegionAction.FIND_THIS_TARGET));
        mask.add(Region.apply(50, 50, 1100, 1500, RegionAction.WITHIN_THIS_BOUNDING_BOX));
        mask.add(Region.apply(376 + 84, 1014 + 78, 40, 40, RegionAction.EXCLUDE));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.TOLERANT,
                mask
        );
        saveResults(snapshotCompare, "findWithinComplexMaskWithSmallerDifferentSizes3");
        Assert.assertTrue(snapshotCompare.isMatch());
        Assert.assertFalse(snapshotCompare.isSameSize());
        Assert.assertTrue(snapshotCompare.isSnapshotSizeAdjusted());
        Assert.assertEquals(snapshotCompare.getStatus(), Status.PASSED);
    }
}

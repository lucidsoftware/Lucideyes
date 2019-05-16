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

    @Test (expectedExceptions = IllegalArgumentException.class)
    public void biggerWidthSmallerHeightTest() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("0pxOriginal.png"));
        BufferedImage snapshot = ImageIO.read(getFile("1pxLargerWidth1pxSmallerHeight.png"));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.TOLERANT
        );
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
    }

    @Test (expectedExceptions = IllegalArgumentException.class)
    public void testFindWithinMaskAndSizeDifferences() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("2pxSmaller.png"));
        BufferedImage snapshot = ImageIO.read(getFile("1pxBiggerWithDiff.png"));
        Set<Region> mask = new HashSet<>();
        // Purposely tested overshot of region width/height off the image.
        mask.add(Region.apply(283, 174, 206, 154, RegionAction.FIND_THIS_TARGET));
        mask.add(Region.apply(200, 150, 300, 300, RegionAction.WITHIN_THIS_BOUNDING_BOX));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.TOLERANT,
                mask
        );
    }

}

package com.lucidchart.eyesopen;

import org.testng.Assert;
import org.testng.annotations.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static com.lucidchart.eyesopen.TestUtil.getFile;
import static com.lucidchart.eyesopen.TestUtil.saveResults;

public class FindImageWithImageTest {

    @Test
    public void testFindImageWithinRegionWithTightMask() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("Selected.PNG"));
        BufferedImage snapshot = ImageIO.read(getFile("Selected.PNG"));
        Set<Region> mask = new HashSet<>();
        mask.add(Region.apply(254, 340, 24, 32, RegionAction.FIND_THIS_TARGET));
        mask.add(Region.apply(253, 339, 26, 34, RegionAction.WITHIN_THIS_BOUNDING_BOX));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.TOLERANT,
                mask
        );
        saveResults(snapshotCompare, "FindImageWithinRegionWithTightMask");
        Assert.assertTrue(snapshotCompare.isFindInRegionModel());
        Assert.assertEquals(snapshotCompare.getTargetLocationFound().orElseThrow(() -> new RuntimeException("The location of the target on the snapshot was not found.")), new Rectangle(253, 339, 24, 32));
        Assert.assertTrue(snapshotCompare.isMatch());
        Assert.assertEquals(snapshotCompare.getStatus(), Status.PASSED);
    }


    @Test
    public void testFindImageWithinRegionWithLooseMask() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("Selected.PNG"));
        BufferedImage snapshot = ImageIO.read(getFile("Selected.PNG"));
        Set<Region> mask = new HashSet<>();
        mask.add(Region.apply(254, 340, 24, 32, RegionAction.FIND_THIS_TARGET));
        mask.add(Region.apply(253, 330, 26, 60, RegionAction.WITHIN_THIS_BOUNDING_BOX));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.TOLERANT,
                mask
        );
        saveResults(snapshotCompare, "FindImageWithinRegionWithLooseMask");
        Assert.assertTrue(snapshotCompare.isFindInRegionModel());
        Assert.assertEquals(snapshotCompare.getTargetLocationFound().orElseThrow(() -> new RuntimeException("The location of the target on the snapshot was not found.")), new Rectangle(253, 339, 24, 32));
        Assert.assertTrue(snapshotCompare.isMatch());
        Assert.assertEquals(snapshotCompare.getStatus(), Status.PASSED);
    }

    @Test
    public void testFindImageWithinRegionWithVeryLooseMask() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("Selected.PNG"));
        BufferedImage snapshot = ImageIO.read(getFile("Selected.PNG"));
        Set<Region> mask = new HashSet<>();
        mask.add(Region.apply(254, 340, 24, 32, RegionAction.FIND_THIS_TARGET));
        mask.add(Region.apply(235, 325, 220, 220, RegionAction.WITHIN_THIS_BOUNDING_BOX));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.apply(5, 20.0),
                mask
        );
        saveResults(snapshotCompare, "FindImageWithinRegionWithVeryLooseMask");
        Assert.assertTrue(snapshotCompare.isFindInRegionModel());
        Assert.assertEquals(snapshotCompare.getTargetLocationFound().orElseThrow(() -> new RuntimeException("The location of the target on the snapshot was not found.")), new Rectangle(254, 340, 24, 32));
        Assert.assertTrue(snapshotCompare.isMatch());
        Assert.assertEquals(snapshotCompare.getStatus(), Status.PASSED);
    }

    @Test
    public void testNotFindingAnImageWithinRegion() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("Selected.PNG"));
        BufferedImage snapshot = ImageIO.read(getFile("Unselected.PNG"));
        Set<Region> mask = new HashSet<>();
        mask.add(Region.apply(254, 340, 24, 32, RegionAction.FIND_THIS_TARGET));
        mask.add(Region.apply(235, 325, 220, 220, RegionAction.WITHIN_THIS_BOUNDING_BOX));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.TOLERANT,
                mask
        );
        saveResults(snapshotCompare, "NotFindingImageWithinRegion");
        Assert.assertTrue(snapshotCompare.isFindInRegionModel());
        Assert.assertFalse(snapshotCompare.getTargetLocationFound().isPresent());
        Assert.assertFalse(snapshotCompare.isMatch());
        Assert.assertEquals(snapshotCompare.getStatus(), Status.FAILED_TO_FIND_IMAGE_IN_REGION);
    }

    @Test
    public void testFindImageWithinRegion2() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("Selected.PNG"));
        BufferedImage snapshot = ImageIO.read(getFile("Selected.PNG"));
        Set<Region> mask = new HashSet<>();
        mask.add(Region.apply(332, 342, 28, 28, RegionAction.FIND_THIS_TARGET));
        mask.add(Region.apply(150, 410, 200, 60, RegionAction.WITHIN_THIS_BOUNDING_BOX));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.TOLERANT,
                mask
        );
        saveResults(snapshotCompare, "FindImageWithinRegion2");
        Assert.assertTrue(snapshotCompare.isFindInRegionModel());
        Assert.assertEquals(
                snapshotCompare.getTargetLocationFound().orElseThrow(
                        () -> new RuntimeException("The location of the target on the snapshot was not found.")),
                new Rectangle(251, 420, 28, 28));
        Assert.assertTrue(snapshotCompare.isMatch());
        Assert.assertEquals(snapshotCompare.getStatus(), Status.PASSED);
    }

    @Test
    public void testFindLargerImageWithinLargerRegion() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("DocListOriginal.png"));
        BufferedImage snapshot = ImageIO.read(getFile("DocListMixedUp.png"));
        Set<Region> mask = new HashSet<>();
        mask.add(Region.apply(20, 425, 275, 188, RegionAction.FIND_THIS_TARGET));
        mask.add(Region.apply(11, 407, 584, 584, RegionAction.WITHIN_THIS_BOUNDING_BOX));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.TOLERANT,
                mask
        );
        saveResults(snapshotCompare, "FindLargerImageWithinRegion3");
        Assert.assertTrue(snapshotCompare.isFindInRegionModel());
        Assert.assertEquals(
                snapshotCompare.getTargetLocationFound().orElseThrow(
                        () -> new RuntimeException("The location of the target on the snapshot was not found.")),
                new Rectangle(311, 716, 275, 188));
        Assert.assertTrue(snapshotCompare.isMatch());
        Assert.assertEquals(snapshotCompare.getStatus(), Status.PASSED);
    }

    @Test
    public void testFindingWithNoYVariation() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("DocListOriginal.png"));
        BufferedImage snapshot = ImageIO.read(getFile("DocListShifted.png"));
        Set<Region> mask = new HashSet<>();
        mask.add(Region.apply(20, 0, 584, 1082, RegionAction.FIND_THIS_TARGET));
        mask.add(Region.apply(0, 0, 604, 1082, RegionAction.WITHIN_THIS_BOUNDING_BOX));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.TOLERANT,
                mask
        );
        saveResults(snapshotCompare, "FindWithNoYVariation");
        Assert.assertTrue(snapshotCompare.isFindInRegionModel());
        Assert.assertEquals(snapshotCompare.getTargetLocationFound().orElseThrow(() -> new RuntimeException("The location of the target on the snapshot was not found.")), new Rectangle(10, 0, 584, 1082));
        Assert.assertTrue(snapshotCompare.isMatch());
        Assert.assertEquals(snapshotCompare.getStatus(), Status.PASSED);
    }

    @Test
    public void testFindingWithNoXVariation() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("DocListOriginal.png"));
        BufferedImage snapshot = ImageIO.read(getFile("DocListShiftedVertically.png"));
        Set<Region> mask = new HashSet<>();
        mask.add(Region.apply(0, 20, 604, 1010, RegionAction.FIND_THIS_TARGET));
        mask.add(Region.apply(0, 0, 604, 1082, RegionAction.WITHIN_THIS_BOUNDING_BOX));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.TOLERANT,
                mask
        );
        saveResults(snapshotCompare, "FindWithNoXVariation");
        Assert.assertTrue(snapshotCompare.isFindInRegionModel());
        Assert.assertEquals(snapshotCompare.getTargetLocationFound().orElseThrow(() -> new RuntimeException("The location of the target on the snapshot was not found.")), new Rectangle(0, 29, 604, 1010));
        Assert.assertTrue(snapshotCompare.isMatch());
        Assert.assertEquals(snapshotCompare.getStatus(), Status.PASSED);
    }
}

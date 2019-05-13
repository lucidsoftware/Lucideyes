package com.lucidchart.eyesopen;

import org.testng.Assert;
import org.testng.annotations.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class FindImageWithImageTest {

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
        saveResults(snapshotCompare, "FindImageWithinRegionWithTightMask", "jpg");
        Assert.assertTrue(snapshotCompare.isFindInRegionModel());
        Assert.assertEquals(snapshotCompare.getLocationOfTargetImageOnSnapshot().orElseThrow(() -> new RuntimeException("The location of the target on the snapshot was not found.")), new Rectangle(253, 339, 24, 32));
        Assert.assertTrue(snapshotCompare.isMatch());
    }


    @Test
    public void testFindImageWithinRegionWithLooseMask() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("Selected.PNG"));
        BufferedImage snapshot = ImageIO.read(getFile("Selected.PNG"));
        Set<Region> mask = new HashSet<>();
        mask.add(Region.apply(254, 340, 24, 32, RegionAction.FIND_THIS_TARGET));
        //mask.add(Region.apply(235, 325, 220, 220, RegionAction.WITHIN_THIS_BOUNDING_BOX));
        mask.add(Region.apply(253, 330, 26, 60, RegionAction.WITHIN_THIS_BOUNDING_BOX));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.TOLERANT,
                mask
        );
        saveResults(snapshotCompare, "FindImageWithinRegionWithLooseMask", "jpg");
        Assert.assertTrue(snapshotCompare.isFindInRegionModel());
        Assert.assertEquals(snapshotCompare.getLocationOfTargetImageOnSnapshot().orElseThrow(() -> new RuntimeException("The location of the target on the snapshot was not found.")), new Rectangle(253, 339, 24, 32));
        Assert.assertTrue(snapshotCompare.isMatch());
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
        saveResults(snapshotCompare, "FindImageWithinRegionWithVeryLooseMask", "jpg");
        Assert.assertTrue(snapshotCompare.isFindInRegionModel());
        Assert.assertEquals(snapshotCompare.getLocationOfTargetImageOnSnapshot().orElseThrow(() -> new RuntimeException("The location of the target on the snapshot was not found.")), new Rectangle(254, 340, 24, 32));
        Assert.assertTrue(snapshotCompare.isMatch());
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
        saveResults(snapshotCompare, "NotFindingImageWithinRegion", "jpg");
        Assert.assertTrue(snapshotCompare.isFindInRegionModel());
        Assert.assertFalse(snapshotCompare.getLocationOfTargetImageOnSnapshot().isPresent());
        Assert.assertFalse(snapshotCompare.isMatch());
    }

    @Test
    public void testFindImageWithinRegion2() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("Selected.PNG"));
        BufferedImage snapshot = ImageIO.read(getFile("Selected.PNG"));
        Set<Region> mask = new HashSet<>();
        mask.add(Region.apply(332, 342, 28, 28, RegionAction.FIND_THIS_TARGET));
        mask.add(Region.apply(235, 400, 160, 160, RegionAction.WITHIN_THIS_BOUNDING_BOX));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.TOLERANT,
                mask
        );
        saveResults(snapshotCompare, "FindImageWithinRegion2", "jpg");
        Assert.assertTrue(snapshotCompare.isFindInRegionModel());
        Assert.assertEquals(snapshotCompare.getLocationOfTargetImageOnSnapshot().orElseThrow(() -> new RuntimeException("The location of the target on the snapshot was not found.")), new Rectangle(251, 420, 28, 28));
        Assert.assertTrue(snapshotCompare.isMatch());
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
        saveResults(snapshotCompare, "FindLargerImageWithinRegion3", "jpg");
        Assert.assertTrue(snapshotCompare.isFindInRegionModel());
        Assert.assertEquals(snapshotCompare.getLocationOfTargetImageOnSnapshot().orElseThrow(() -> new RuntimeException("The location of the target on the snapshot was not found.")), new Rectangle(311, 716, 275, 188));
        Assert.assertTrue(snapshotCompare.isMatch());
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
        saveResults(snapshotCompare, "FindWithNoYVariation", "jpg");
        Assert.assertTrue(snapshotCompare.isFindInRegionModel());
        Assert.assertEquals(snapshotCompare.getLocationOfTargetImageOnSnapshot().orElseThrow(() -> new RuntimeException("The location of the target on the snapshot was not found.")), new Rectangle(10, 0, 584, 1082));
        Assert.assertTrue(snapshotCompare.isMatch());
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
        saveResults(snapshotCompare, "FindWithNoXVariation", "jpg");
        Assert.assertTrue(snapshotCompare.isFindInRegionModel());
        Assert.assertEquals(snapshotCompare.getLocationOfTargetImageOnSnapshot().orElseThrow(() -> new RuntimeException("The location of the target on the snapshot was not found.")), new Rectangle(0, 29, 604, 1010));
        Assert.assertTrue(snapshotCompare.isMatch());
    }
}

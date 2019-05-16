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

public class DifferentSizeImageTest {

    @Test
    public void testSlightlyLargerSnapshotWithoutMasks() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("SlightlySmaller.PNG"));
        BufferedImage snapshot = ImageIO.read(getFile("SlightlyLarger.PNG"));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.TOLERANT
        );
        saveResults(snapshotCompare, "SlightlyLargerSnapshot");
        Assert.assertTrue(snapshotCompare.isMatch());
    }

    @Test
    public void testSlightlyLargerSnapshotWithoutMasks1() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("0pxOriginal.png"));
        BufferedImage snapshot = ImageIO.read(getFile("1pxBigger.png"));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.TOLERANT
        );
        saveResults(snapshotCompare, "SlightlyLargerSnapshot1");
        Assert.assertTrue(snapshotCompare.isMatch());
    }


    @Test
    public void testSlightlyLargerMasterWithoutMasks1() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("1pxBigger.png"));
        BufferedImage snapshot = ImageIO.read(getFile("0pxOriginal.png"));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.TOLERANT
        );
        saveResults(snapshotCompare, "SlightlyLargerMaster1");
        Assert.assertTrue(snapshotCompare.isMatch());
    }

    @Test
    public void testSlightlyLargerSnapshotWithoutMasks2() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("0pxOriginal.png"));
        BufferedImage snapshot = ImageIO.read(getFile("2pxBigger.png"));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.TOLERANT
        );
        saveResults(snapshotCompare, "SlightlyLargerSnapshot2");
        Assert.assertTrue(snapshotCompare.isMatch());
    }

    @Test
    public void testSlightlyLargerMasterWithoutMasks2() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("2pxBigger.png"));
        BufferedImage snapshot = ImageIO.read(getFile("0pxOriginal.png"));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.TOLERANT
        );
        saveResults(snapshotCompare, "SlightlyLargerMaster2");
        Assert.assertTrue(snapshotCompare.isMatch());
    }

    @Test
    public void testSlightlyLargerSnapshotWithoutMasks3() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("2pxSmaller.png"));
        BufferedImage snapshot = ImageIO.read(getFile("2pxBigger.png"));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.TOLERANT
        );
        saveResults(snapshotCompare, "SlightlyLargerSnapshot3");
        Assert.assertTrue(snapshotCompare.isMatch());
    }

    @Test
    public void testSlightlyLargerMasterWithoutMasks3() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("2pxBigger.png"));
        BufferedImage snapshot = ImageIO.read(getFile("2pxSmaller.png"));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.TOLERANT
        );
        saveResults(snapshotCompare, "SlightlyLargerMaster3");
        Assert.assertTrue(snapshotCompare.isMatch());
    }

    @Test
    public void testSlightlyLargerSnapshotWithMask() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("0pxOriginal.png"));
        BufferedImage snapshot = ImageIO.read(getFile("1pxBiggerWithDiff.png"));
        Set<Region> mask = new HashSet<>();
        mask.add(Region.apply(0, 0, 1382, 1184, RegionAction.FOCUS));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.TOLERANT,
                mask
        );
        saveResults(snapshotCompare, "SlightlyLargerWithFocusMask");
        Assert.assertTrue(snapshotCompare.isMatch());
    }

    @Test
    public void testSlightlyLargerMasterWithMask() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("1pxBiggerWithDiff.png"));
        BufferedImage snapshot = ImageIO.read(getFile("0pxOriginal.png"));
        Set<Region> mask = new HashSet<>();
        mask.add(Region.apply(0, 0, 1382, 1184, RegionAction.FOCUS));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.TOLERANT,
                mask
        );
        saveResults(snapshotCompare, "SlightlyLargerMasterWithFocusMask");
        Assert.assertTrue(snapshotCompare.isMatch());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testSlightlyLargerSnapshotWithPartialMask() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("0pxOriginal.png"));
        BufferedImage snapshot = ImageIO.read(getFile("1pxBiggerWithDiff.png"));
        Set<Region> mask = new HashSet<>();
        mask.add(Region.apply(0, 0, 1760, 1184, RegionAction.FOCUS));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.TOLERANT,
                mask
        );
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testSlightlyLargerMasterWithPartialMask() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("1pxBiggerWithDiff.png"));
        BufferedImage snapshot = ImageIO.read(getFile("0pxOriginal.png"));
        Set<Region> mask = new HashSet<>();
        mask.add(Region.apply(0, 0, 1760, 1184, RegionAction.FOCUS));
        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.TOLERANT,
                mask
        );
    }
}

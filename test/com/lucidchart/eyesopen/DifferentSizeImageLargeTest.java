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

public class DifferentSizeImageLargeTest {

    @Test (expectedExceptions = ImageCompareTimeOutException.class)
    public void largeImageSimilarTest() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("BlueBlankMarked.png"));
        BufferedImage snapshot = ImageIO.read(getFile("BlueBlank.png"));

        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                With.context()
                    .matchLevel(MatchLevel.TOLERANT)
                    .mask(Region.apply(750, 353, 242, 242, RegionAction.FIND_THIS_TARGET))
                    .mask(Region.apply(0, 0, 1000, 600, RegionAction.WITHIN_THIS_BOUNDING_BOX))
                    .maxTimeLimit(5)
        );
    }

    @Test
    public void statusForMissingMasterTest() throws IOException {
        BufferedImage standard = null;
        BufferedImage snapshot = ImageIO.read(getFile("BlueBlank.png"));

        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                With.context()
                    .matchLevel(MatchLevel.TOLERANT)
                    .mask(Region.apply(750, 353, 242, 242, RegionAction.FIND_THIS_TARGET))
                    .mask(Region.apply(0, 0, 1000, 600, RegionAction.WITHIN_THIS_BOUNDING_BOX))
        );

        Assert.assertEquals(snapshotCompare.getStatus(), Status.NEEDS_APPROVAL);
    }

    @Test
    public void statusForMissingSnapshotTest() throws IOException {
        BufferedImage standard = ImageIO.read(getFile("BlueBlank.png"));
        BufferedImage snapshot = null;

        Set<Region> mask = new HashSet<>();
        mask.add(Region.apply(750, 353, 242, 242, RegionAction.FIND_THIS_TARGET));
        mask.add(Region.apply(0, 0, 1000, 600, RegionAction.WITHIN_THIS_BOUNDING_BOX));

        ImageCompare snapshotCompare = ImageCompare.apply(
                standard,
                snapshot,
                MatchLevel.TOLERANT,
                mask
        );

        Assert.assertEquals(snapshotCompare.getStatus(), Status.MISSING);
    }
}

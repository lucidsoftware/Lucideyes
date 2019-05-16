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
}

package com.lucidchart.eyesopen;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TestUtil {

    static File getFile(String fileNameWithExtension) {
        return Paths.get("./test/com/lucidchart/eyesopen/inputImages/" + fileNameWithExtension).toFile();
    }


    static void saveResults(ImageCompare imageTest, String name) throws IOException {
        saveImage(imageTest.getSnapshotWithMask(), name, "Snapshot-Masked");
        saveImage(imageTest.getMasterWithMask(), name, "Master-Masked");
        saveImage(imageTest.getBlockMask(), name, "Block Mask");
        saveImage(imageTest.getPixelDiff(), name, "Pixel Diff");
        saveImage(imageTest.getCircledDiffSideBySide(), name, "Circled Diff SideBySide");
        saveImage(imageTest.getPixelDiffSideBySide(), name, "Pixel Diff SideBySide");
        saveImage(imageTest.getBlockColorComparisonResults(), name, "BlockColorComparisonMap");
        saveImage(imageTest.getCircledDiff(), name, "Marked-Up");

        try (PrintWriter out = new PrintWriter(Paths.get("./output/" + name + "/info.txt").toString())) {
            out.println(imageTest.toString());
        }
    }

    static void saveImage(BufferedImage image, String name, String description) throws IOException {
        Path testOutputDir = Files.createDirectories(Paths.get("./output/" + name));
        File output = new File(testOutputDir + "/" + description + "." + "png");
        ImageIO.write(image, "png", output);
    }
}

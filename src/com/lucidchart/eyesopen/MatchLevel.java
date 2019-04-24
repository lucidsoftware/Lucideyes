package com.lucidchart.eyesopen;

/**
 * Preset levels of image comparison.
 *
 * EXACT is a pixel to pixel comparison.
 * STRICT is inspired by applitools:  Strict compares everything including content
 *      (text), fonts, layout, colors and position of each of the elements.
 *      Strict knows to ignore rendering changes that are not visible to the human (anti-aliasing changes,
 *      small pixel movements and various other changes that are typically caused when running tests on different
 *      machines with different graphic cards, etc.).
 * TOLERANT accepts changes which are still visible, but barely so.
 */
public class MatchLevel {
    public static final MatchLevel EXACT = MatchLevel.apply(1, 1.0);
    public static final MatchLevel STRICT = MatchLevel.apply(5, 14.0);
    public static final MatchLevel TOLERANT = MatchLevel.apply(10, 20.0);

    public int blockSize;
    public double maxColorDistance;

    private MatchLevel(int blockSize, double maxColorDistance) {
        this.blockSize = blockSize;
        this.maxColorDistance = maxColorDistance;
    }

    public static MatchLevel apply(int blockSize, double maxColorDistance) {
        return new MatchLevel(blockSize, maxColorDistance);
    }


}

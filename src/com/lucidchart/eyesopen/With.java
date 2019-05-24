package com.lucidchart.eyesopen;

import java.util.HashSet;
import java.util.Set;

/** A flexible way of specifying arguments in chain for an image comparison
 * This enables semantic image calls and flexible updates of arguments without needing to maintain so many constructor alternatives.
 */
public class With {
    private Set<Region> maskHolder = new HashSet<>();
    private int blockSizeHolder = 5;
    private double maxColorDistanceHolder = 20;
    private double blockPixelThresholdHolder = 0.67; // Evaluate a block if the number of pixels to average meets this threshold quantity

    /** The max size difference to allow for when checking image equality.
     * This will be used to simply try to find a match of the smaller image somewhere within the larger.
     */
    private Integer maxSizeDifferenceAllowedHolder = 0;

    /** The max seconds allowed to perform the image comparison before throwing an exception */
    private Integer maxTimeHolder = 100;

    /** Specify a region to focus on, exclude, or define a find-image-within-region */
    public With mask(Region region) {
        if (region != null) maskHolder.add(region);
        return this;
    }

    private With(){}

    /** Provides a With object to enable easy chaining of context options */
    public static With context() {
        return new With();
    }

    /** Specify a region to focus on, exclude, or define a find-image-within-region */
    public With mask(Set<Region> masks) {
        maskHolder.addAll(masks);
        return this;
    }

    /** Specify a custom block size.  A block is the area averaged together into a single pixel color */
    public With blockSize(int blockSize) {
        blockSizeHolder = blockSize;
        return this;
    }

    /** As dividing an image into blocks of equal sizes may result in partial blocks on the edges, specify the percent threshold of a block to consider when comparing images.
     * For example if the threshold is set to .5, the block will be ignored in the image comparison if it is smaller than half the regular block size.  (Default is 0.67)
     */
    public With blockSizeThreshold(double blockSizeThreshold) {
        blockPixelThresholdHolder = blockSizeThreshold;
        return this;
    }

    /** Specify the max color distance allowed between corresponding block averages. */
    public With maxColorDistance(int maxColorDistance) {
        maxColorDistanceHolder = maxColorDistance;
        return this;
    }

    /** Specify maximum allowable sized difference between images.  This should generally be a very small amount (e.g. 5px), as an attempt will be made to find one image within the subset of the other.
     * By default this is set to 0px. */
    public With maxSizeDifference(int maxSizeDifference) {
        maxSizeDifferenceAllowedHolder = maxSizeDifference;
        return this;
    }

    /** Specify maximum time limit to find a match, before throwing a timeOut exception.  Default is 100s */
    public With maxTimeLimit(int maxTimeLimit) {
        maxTimeHolder = maxTimeLimit;
        return this;
    }

    /** Specify a custom match level */
    public With matchLevel(MatchLevel matchLevel) {
        this.blockSizeHolder = matchLevel.blockSize;
        this.maxColorDistanceHolder = matchLevel.maxColorDistance;
        return this;
    }

    public Set<Region> getMask() {
        return maskHolder;
    }

    public int getBlockSize() {
        return blockSizeHolder;
    }

    public double getMaxColorDistance() {
        return maxColorDistanceHolder;
    }

    public int getMaxSizeDifferenceAllowed() {
        return maxSizeDifferenceAllowedHolder;
    }

    public int getMaxTime() {
        return maxTimeHolder;
    }

    public double getBlockThreshold() {
        return blockPixelThresholdHolder;
    }
}
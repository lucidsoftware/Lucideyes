package com.lucidchart.eyesopen;

import java.awt.Point;
import java.awt.Rectangle;

import java.util.Objects;

/** An area of a snapshot, which can be used to limit the check to this region, or exclude checking this region. */
public class Region {
    public final Point location;
    public final int height;
    public final int width;
    public final RegionAction regionAction;

    private Region(Point location, int width, int height, RegionAction regionAction) {
        this.location = location;
        this.height = height;
        this.width = width;
        this.regionAction = regionAction;
    }

    //*** FACTORY METHODS ***

    public static Region apply(Point location, int width, int height, RegionAction regionAction) {
        return new Region(location, width, height, regionAction);
    }

    public static Region apply(int x, int y, int width, int height, RegionAction regionAction) {
        return new Region(new Point(x,y), width, height, regionAction);
    }

    public static Region apply (Rectangle rectangle, RegionAction regionAction) {
        return new Region(
            rectangle.getLocation(),
                (int)rectangle.getWidth(),
                (int)rectangle.getHeight(),
            regionAction
        );
    }

    /** Returns the bottom right corner of the region */
    Point getBottomRight() {
        return new Point(location.x + width, location.y + height);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Region)) return false;
        Region region = (Region) o;
        return height == region.height &&
                width == region.width &&
                Objects.equals(location, region.location) &&
                regionAction == region.regionAction;
    }

    @Override
    public int hashCode() {
        return Objects.hash(location, width, height, regionAction);
    }
}

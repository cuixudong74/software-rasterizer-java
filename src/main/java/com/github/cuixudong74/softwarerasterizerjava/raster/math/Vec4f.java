package com.github.cuixudong74.softwarerasterizerjava.raster.math;

/** 4D homogeneous vector (clip-space position). */
public final class Vec4f {
    public final double x;
    public final double y;
    public final double z;
    public final double w;

    public Vec4f(double x, double y, double z, double w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }
}

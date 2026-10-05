package com.github.cuixudong74.softwarerasterizerjava.raster.math;

/** 2D vector / texture coordinate. */
public final class Vec2f {
    public final double x;
    public final double y;

    public Vec2f(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public Vec2f add(Vec2f o) { return new Vec2f(x + o.x, y + o.y); }
    public Vec2f sub(Vec2f o) { return new Vec2f(x - o.x, y - o.y); }
    public Vec2f scale(double s) { return new Vec2f(x * s, y * s); }
    public Vec2f lerp(Vec2f o, double t) { return new Vec2f(x + (o.x - x) * t, y + (o.y - y) * t); }
}

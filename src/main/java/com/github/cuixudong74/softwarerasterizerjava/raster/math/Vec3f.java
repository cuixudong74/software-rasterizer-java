package com.github.cuixudong74.softwarerasterizerjava.raster.math;

/** 3D vector, also used for RGB colors. */
public final class Vec3f {
    public final double x;
    public final double y;
    public final double z;

    public Vec3f(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3f add(Vec3f o) { return new Vec3f(x + o.x, y + o.y, z + o.z); }
    public Vec3f sub(Vec3f o) { return new Vec3f(x - o.x, y - o.y, z - o.z); }
    public Vec3f scale(double s) { return new Vec3f(x * s, y * s, z * s); }
    public double dot(Vec3f o) { return x * o.x + y * o.y + z * o.z; }

    public Vec3f cross(Vec3f o) {
        return new Vec3f(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
    }

    public Vec3f normalize() {
        double len = Math.sqrt(x * x + y * y + z * z);
        if (len == 0.0 || !Double.isFinite(len)) {
            throw new ArithmeticException("cannot normalize zero or non-finite vector");
        }
        return new Vec3f(x / len, y / len, z / len);
    }

    public Vec3f lerp(Vec3f o, double t) {
        return new Vec3f(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t);
    }
}

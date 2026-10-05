package com.github.cuixudong74.softwarerasterizerjava.render.math;

/** Immutable 3D vector. */
public record Vec3f(float x, float y, float z) {

    public Vec3f add(Vec3f o) {
        return new Vec3f(x + o.x, y + o.y, z + o.z);
    }

    public Vec3f sub(Vec3f o) {
        return new Vec3f(x - o.x, y - o.y, z - o.z);
    }

    public Vec3f scale(float s) {
        return new Vec3f(x * s, y * s, z * s);
    }

    public float dot(Vec3f o) {
        return x * o.x + y * o.y + z * o.z;
    }

    public Vec3f cross(Vec3f o) {
        return new Vec3f(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
    }

    public Vec3f normalize() {
        float len = (float) Math.sqrt(dot(this));
        return new Vec3f(x / len, y / len, z / len);
    }
}

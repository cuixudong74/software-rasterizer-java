package com.github.cuixudong74.softwarerasterizerjava.render.math;

/**
 * Immutable 4x4 matrix, column-major storage (m[col*4+row]), matching OpenGL conventions.
 */
public final class Mat4f {

    private final float[] m; // 16 elements, column-major

    private Mat4f(float[] m) {
        this.m = m;
    }

    public static Mat4f of(float[] columnMajor16) {
        if (columnMajor16 == null || columnMajor16.length != 16) {
            throw new IllegalArgumentException("need exactly 16 column-major elements");
        }
        return new Mat4f(columnMajor16.clone());
    }

    public static Mat4f identity() {
        float[] m = new float[16];
        m[0] = m[5] = m[10] = m[15] = 1f;
        return new Mat4f(m);
    }

    public float get(int row, int col) {
        return m[col * 4 + row];
    }

    /** this * o (apply o first, then this). */
    public Mat4f mul(Mat4f o) {
        float[] r = new float[16];
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                float s = 0f;
                for (int k = 0; k < 4; k++) {
                    s += m[k * 4 + row] * o.m[col * 4 + k];
                }
                r[col * 4 + row] = s;
            }
        }
        return new Mat4f(r);
    }

    /** Transform a point (w=1), returning the homogeneous result (no division). */
    public Vec4f transform(Vec3f p) {
        return transform(new Vec4f(p.x(), p.y(), p.z(), 1f));
    }

    /** Transform a homogeneous vector. */
    public Vec4f transform(Vec4f v) {
        float x = m[0] * v.x() + m[4] * v.y() + m[8] * v.z() + m[12] * v.w();
        float y = m[1] * v.x() + m[5] * v.y() + m[9] * v.z() + m[13] * v.w();
        float z = m[2] * v.x() + m[6] * v.y() + m[10] * v.z() + m[14] * v.w();
        float w = m[3] * v.x() + m[7] * v.y() + m[11] * v.z() + m[15] * v.w();
        return new Vec4f(x, y, z, w);
    }
}

package com.github.cuixudong74.softwarerasterizerjava.raster.math;

/**
 * Row-major 4x4 matrix. Column-vector convention: result = M * v.
 * Projection maps view space (camera looks down -Z) to OpenGL-style clip space
 * with NDC z in [-1, 1].
 */
public final class Mat4f {
    /** m[row][col]. */
    public final double[][] m = new double[4][4];

    public static Mat4f identity() {
        Mat4f r = new Mat4f();
        for (int i = 0; i < 4; i++) r.m[i][i] = 1.0;
        return r;
    }

    public static Mat4f translation(double tx, double ty, double tz) {
        Mat4f r = identity();
        r.m[0][3] = tx;
        r.m[1][3] = ty;
        r.m[2][3] = tz;
        return r;
    }

    public static Mat4f scaling(double sx, double sy, double sz) {
        Mat4f r = new Mat4f();
        r.m[0][0] = sx;
        r.m[1][1] = sy;
        r.m[2][2] = sz;
        r.m[3][3] = 1.0;
        return r;
    }

    public static Mat4f rotationY(double radians) {
        Mat4f r = identity();
        double c = Math.cos(radians);
        double s = Math.sin(radians);
        r.m[0][0] = c;
        r.m[0][2] = s;
        r.m[2][0] = -s;
        r.m[2][2] = c;
        return r;
    }

    /** View matrix: camera at eye looking at target, camera forward = -Z. */
    public static Mat4f lookAt(Vec3f eye, Vec3f target, Vec3f up) {
        Vec3f f = target.sub(eye).normalize();          // forward
        Vec3f s = f.cross(up).normalize();              // right
        Vec3f u = s.cross(f);                           // true up
        Mat4f r = identity();
        r.m[0][0] = s.x;  r.m[0][1] = s.y;  r.m[0][2] = s.z;  r.m[0][3] = -s.dot(eye);
        r.m[1][0] = u.x;  r.m[1][1] = u.y;  r.m[1][2] = u.z;  r.m[1][3] = -u.dot(eye);
        r.m[2][0] = -f.x; r.m[2][1] = -f.y; r.m[2][2] = -f.z; r.m[2][3] = f.dot(eye);
        return r;
    }

    /**
     * Perspective projection, OpenGL clip conventions.
     * near/far are positive distances in front of the camera.
     */
    public static Mat4f perspective(double fovYRadians, double aspect, double near, double far) {
        double t = 1.0 / Math.tan(fovYRadians / 2.0);
        Mat4f r = new Mat4f();
        r.m[0][0] = t / aspect;
        r.m[1][1] = t;
        r.m[2][2] = (far + near) / (near - far);
        r.m[2][3] = (2.0 * far * near) / (near - far);
        r.m[3][2] = -1.0;
        return r;
    }

    public Mat4f multiply(Mat4f o) {
        Mat4f r = new Mat4f();
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                double sum = 0.0;
                for (int k = 0; k < 4; k++) sum += m[i][k] * o.m[k][j];
                r.m[i][j] = sum;
            }
        }
        return r;
    }

    public Vec4f multiply(Vec4f v) {
        double[] out = new double[4];
        double[] in = {v.x, v.y, v.z, v.w};
        for (int i = 0; i < 4; i++) {
            double sum = 0.0;
            for (int k = 0; k < 4; k++) sum += m[i][k] * in[k];
            out[i] = sum;
        }
        return new Vec4f(out[0], out[1], out[2], out[3]);
    }
}

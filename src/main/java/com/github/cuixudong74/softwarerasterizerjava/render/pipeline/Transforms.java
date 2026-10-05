package com.github.cuixudong74.softwarerasterizerjava.render.pipeline;

import com.github.cuixudong74.softwarerasterizerjava.render.core.Camera;
import com.github.cuixudong74.softwarerasterizerjava.render.math.Mat4f;
import com.github.cuixudong74.softwarerasterizerjava.render.math.Vec3f;

/** Factory for model/view/projection matrices. */
public final class Transforms {

    private Transforms() {
    }

    public static Mat4f translation(float tx, float ty, float tz) {
        return Mat4f.of(new float[]{
                1, 0, 0, 0,
                0, 1, 0, 0,
                0, 0, 1, 0,
                tx, ty, tz, 1});
    }

    public static Mat4f scale(float sx, float sy, float sz) {
        return Mat4f.of(new float[]{
                sx, 0, 0, 0,
                0, sy, 0, 0,
                0, 0, sz, 0,
                0, 0, 0, 1});
    }

    /** Rotation around the Y axis, angle in radians, right-handed. */
    public static Mat4f rotationY(double radians) {
        float c = (float) Math.cos(radians);
        float s = (float) Math.sin(radians);
        return Mat4f.of(new float[]{
                c, 0, -s, 0,
                0, 1, 0, 0,
                s, 0, c, 0,
                0, 0, 0, 1});
    }

    /** Right-handed look-at view matrix (camera looks down -Z in view space). */
    public static Mat4f view(Camera camera) {
        Vec3f eye = camera.eye();
        Vec3f forward = camera.target().sub(eye).normalize();          // view direction
        Vec3f right = forward.cross(camera.up()).normalize();          // +X in view space
        Vec3f up = right.cross(forward);                               // +Y in view space
        // Rows of the rotation part are the view-space basis vectors; camera looks along
        // -Z, so the third row is -forward.
        return Mat4f.of(new float[]{
                right.x(), up.x(), -forward.x(), 0,
                right.y(), up.y(), -forward.y(), 0,
                right.z(), up.z(), -forward.z(), 0,
                -right.dot(eye), -up.dot(eye), forward.dot(eye), 1});
    }

    /**
     * OpenGL-style perspective projection: visible region -w <= x,y,z <= w in clip space,
     * NDC in [-1,1]^3 after perspective division. Camera-space points in front of the
     * camera have z_view < 0; near/far are positive distances.
     */
    public static Mat4f projection(Camera camera, double aspect) {
        double n = camera.near();
        double f = camera.far();
        double t = Math.tan(Math.toRadians(camera.fovYDegrees()) / 2.0) * n;
        double r = t * aspect;
        return Mat4f.of(new float[]{
                (float) (n / r), 0, 0, 0,
                0, (float) (n / t), 0, 0,
                0, 0, (float) (-(f + n) / (f - n)), -1,
                0, 0, (float) (-2 * f * n / (f - n)), 0});
    }
}

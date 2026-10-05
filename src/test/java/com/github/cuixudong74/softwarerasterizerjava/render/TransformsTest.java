package com.github.cuixudong74.softwarerasterizerjava.render;

import com.github.cuixudong74.softwarerasterizerjava.render.core.Camera;
import com.github.cuixudong74.softwarerasterizerjava.render.math.Mat4f;
import com.github.cuixudong74.softwarerasterizerjava.render.math.Vec3f;
import com.github.cuixudong74.softwarerasterizerjava.render.math.Vec4f;
import com.github.cuixudong74.softwarerasterizerjava.render.pipeline.Transforms;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransformsTest {

    private static final float EPS = 1e-5f;

    @Test
    void viewMatrixPlacesTargetOnNegativeZ() {
        Camera cam = new Camera(new Vec3f(1, 2, 3), new Vec3f(1, 2, -3), new Vec3f(0, 1, 0),
                90, 0.5, 50);
        Mat4f view = Transforms.view(cam);
        Vec4f target = view.transform(cam.target());
        assertEquals(0f, target.x(), EPS);
        assertEquals(0f, target.y(), EPS);
        assertEquals(-6f, target.z(), EPS); // distance 6 along -Z
        Vec4f eye = view.transform(cam.eye());
        assertEquals(0f, eye.x(), EPS);
        assertEquals(0f, eye.y(), EPS);
        assertEquals(0f, eye.z(), EPS);
    }

    @Test
    void projectionMapsNearAndFarToNdcBounds() {
        Camera cam = TestScenes.defaultCamera();
        Mat4f proj = Transforms.projection(cam, 1.0);
        // Point on near plane (z_view = -near) -> ndc z = -1.
        Vec4f near = proj.transform(new Vec3f(0, 0, -1));
        assertEquals(-1f, near.z() / near.w(), EPS);
        // Point on far plane -> ndc z = +1.
        Vec4f far = proj.transform(new Vec3f(0, 0, -100));
        assertEquals(1f, far.z() / far.w(), EPS);
        // 90 degree fov, aspect 1: point at x = -z (45 degrees) maps to ndc x = 1.
        Vec4f edge = proj.transform(new Vec3f(5, 0, -5));
        assertEquals(1f, edge.x() / edge.w(), EPS);
    }

    @Test
    void modelMatricesCompose() {
        Mat4f m = Transforms.translation(10, 0, 0)
                .mul(Transforms.rotationY(Math.PI / 2))
                .mul(Transforms.scale(2, 2, 2));
        // scale 2 -> rotate 90 deg about Y -> translate +10 in x.
        Vec4f p = m.transform(new Vec3f(1, 0, 0));
        assertEquals(10f, p.x(), EPS);
        assertEquals(0f, p.y(), EPS);
        assertEquals(-2f, p.z(), EPS); // rotated: +X -> -Z, scaled by 2
    }
}

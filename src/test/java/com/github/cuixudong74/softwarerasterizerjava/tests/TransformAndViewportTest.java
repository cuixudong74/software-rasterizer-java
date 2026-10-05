package com.github.cuixudong74.softwarerasterizerjava.tests;

import com.github.cuixudong74.softwarerasterizerjava.raster.core.Camera;
import com.github.cuixudong74.softwarerasterizerjava.raster.core.Framebuffer;
import com.github.cuixudong74.softwarerasterizerjava.raster.core.Mesh;
import com.github.cuixudong74.softwarerasterizerjava.raster.math.Mat4f;
import com.github.cuixudong74.softwarerasterizerjava.raster.math.Vec4f;
import com.github.cuixudong74.softwarerasterizerjava.raster.pipeline.RenderConfig;
import com.github.cuixudong74.softwarerasterizerjava.raster.pipeline.Renderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Model/view/projection, perspective divide and viewport mapping. */
class TransformAndViewportTest {

    @Test
    void perspectiveDivideAndViewportMapping() {
        // Camera at origin looking down -Z, fovY=90, aspect=1:
        // ndc.x = x / -z, ndc.y = y / -z.
        Camera cam = TestScenes.camera(1.0);
        Mat4f proj = cam.projectionMatrix();
        Vec4f clip = proj.multiply(new Vec4f(1.0, -0.5, -2.0, 1.0));
        assertEquals(0.5, clip.x / clip.w, 1e-12);   // ndc.x = x / -z = 1/2
        assertEquals(-0.25, clip.y / clip.w, 1e-12); // ndc.y = y / -z = -0.5/2
        assertEquals(2.0, clip.w, 1e-12);
        // NDC z must be in [-1,1] for points between near and far.
        double ndcZ = clip.z / clip.w;
        assertTrue(ndcZ > -1.0 && ndcZ < 1.0);
    }

    @Test
    void viewMatrixMovesWorldIntoCameraSpace() {
        Camera cam = new Camera(new com.github.cuixudong74.softwarerasterizerjava.raster.math.Vec3f(0, 0, 5),
                new com.github.cuixudong74.softwarerasterizerjava.raster.math.Vec3f(0, 0, 0),
                new com.github.cuixudong74.softwarerasterizerjava.raster.math.Vec3f(0, 1, 0),
                Math.PI / 2, 1.0, 1.0, 100.0);
        Vec4f p = cam.viewMatrix().multiply(new Vec4f(0, 0, 0, 1));
        assertEquals(0.0, p.x, 1e-12);
        assertEquals(0.0, p.y, 1e-12);
        assertEquals(-5.0, p.z, 1e-12); // origin is 5 units in front of the camera
    }

    @Test
    void modelTransformAffectsOutput() {
        Framebuffer fb1 = new Framebuffer(64, 64);
        Framebuffer fb2 = new Framebuffer(64, 64);
        Mesh tri = TestScenes.singleTriangle(
                TestScenes.v(-0.5, -0.5, -2, TestScenes.RED),
                TestScenes.v(0.5, -0.5, -2, TestScenes.GREEN),
                TestScenes.v(0.0, 0.5, -2, TestScenes.BLUE));
        Renderer r = new Renderer(new RenderConfig(16, 1, false, true, 0));
        r.render(fb1, tri, Mat4f.identity(), TestScenes.camera(1.0), null);
        r.render(fb2, tri, Mat4f.translation(0.4, 0, 0), TestScenes.camera(1.0), null);
        assertNotEquals(fb1.checksum(), fb2.checksum());
    }

    @Test
    void triangleLandsOnExpectedPixels() {
        // Small triangle around NDC (0,0) must cover the image center only.
        Framebuffer fb = new Framebuffer(101, 101);
        Mesh tri = TestScenes.singleTriangle(
                TestScenes.v(-0.05, -0.05, -2, TestScenes.RED),
                TestScenes.v(0.05, -0.05, -2, TestScenes.RED),
                TestScenes.v(0.0, 0.05, -2, TestScenes.RED));
        Renderer r = new Renderer(new RenderConfig(16, 1, false, true, 0));
        r.render(fb, tri, Mat4f.identity(), TestScenes.camera(1.0), null);
        assertTrue(TestScenes.covered(fb.pixel(50, 50)), "center pixel must be covered");
        assertFalse(TestScenes.covered(fb.pixel(10, 10)), "far corner must be empty");
        assertFalse(TestScenes.covered(fb.pixel(90, 90)), "far corner must be empty");
    }
}

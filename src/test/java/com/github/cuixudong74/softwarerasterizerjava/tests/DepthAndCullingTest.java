package com.github.cuixudong74.softwarerasterizerjava.tests;

import com.github.cuixudong74.softwarerasterizerjava.raster.core.Framebuffer;
import com.github.cuixudong74.softwarerasterizerjava.raster.core.Mesh;
import com.github.cuixudong74.softwarerasterizerjava.raster.math.Mat4f;
import com.github.cuixudong74.softwarerasterizerjava.raster.pipeline.RenderConfig;
import com.github.cuixudong74.softwarerasterizerjava.raster.pipeline.Renderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Depth testing (incl. interpenetrating triangles) and backface culling. */
class DepthAndCullingTest {

    private static Renderer renderer(boolean culling, boolean depthTest) {
        return new Renderer(new RenderConfig(16, 1, culling, depthTest, 0));
    }

    @Test
    void nearerTriangleWinsRegardlessOfSubmissionOrder() {
        // Two overlapping quads at different depths, far one submitted first.
        // NDC extents: far quad +/-0.45, near quad +/-0.225 (screen 39..62).
        Mesh scene = TestScenes.mesh(java.util.List.of(
                // far quad (z=-4), red
                TestScenes.v(-1.8, -1.8, -4, TestScenes.RED),
                TestScenes.v(1.8, -1.8, -4, TestScenes.RED),
                TestScenes.v(1.8, 1.8, -4, TestScenes.RED),
                TestScenes.v(-1.8, 1.8, -4, TestScenes.RED),
                // near quad (z=-2), green
                TestScenes.v(-0.45, -0.45, -2, TestScenes.GREEN),
                TestScenes.v(0.45, -0.45, -2, TestScenes.GREEN),
                TestScenes.v(0.45, 0.45, -2, TestScenes.GREEN),
                TestScenes.v(-0.45, 0.45, -2, TestScenes.GREEN)),
                0, 1, 2, 0, 2, 3, 4, 5, 6, 4, 6, 7);
        Framebuffer fb = new Framebuffer(101, 101);
        renderer(false, true).render(fb, scene, Mat4f.identity(), TestScenes.camera(1.0), null);
        int center = fb.pixel(50, 50);
        assertEquals(0xFF00FF00, center, "near green quad must win at the center");
        // (30,30): ndc ~ (-0.396,0.396): inside the far quad, outside the near one.
        int corner = fb.pixel(30, 30);
        assertEquals(0xFFFF0000, corner, "far red quad must be visible outside the near quad");
    }

    @Test
    void interpenetratingTrianglesResolvePerPixel() {
        // Triangle A's NDC z varies across the screen and crosses the constant
        // depth of triangle B near screen x=58 on row y=50: A nearer to the
        // left of the crossing, B nearer to the right.
        Mesh scene = TestScenes.mesh(java.util.List.of(
                // A: NDC (-0.9,-0.9),(0.9,-0.9),(0,1.0); ndc z: -0.327, 0.515, 0.347
                TestScenes.v(-1.35, -1.35, -1.5, TestScenes.RED),
                TestScenes.v(3.6, -3.6, -4.0, TestScenes.RED),
                TestScenes.v(0.0, 3.0, -3.0, TestScenes.RED),
                // B: constant z=-2.75 (ndc z = 0.2856), green, NDC x in [-0.8,0.8]
                TestScenes.v(-2.2, -1.1, -2.75, TestScenes.GREEN),
                TestScenes.v(2.2, -1.1, -2.75, TestScenes.GREEN),
                TestScenes.v(0.0, 1.1, -2.75, TestScenes.GREEN)),
                0, 1, 2, 3, 4, 5);
        Framebuffer fb = new Framebuffer(101, 101);
        renderer(false, true).render(fb, scene, Mat4f.identity(), TestScenes.camera(1.0), null);

        int left = fb.pixel(35, 50);
        int right = fb.pixel(70, 50);
        assertEquals(0xFFFF0000, left, "left of the crossing A must win (red)");
        assertEquals(0xFF00FF00, right, "right of the crossing B must win (green)");
    }

    @Test
    void depthIsPerspectiveCorrectLinearInScreenSpace() {
        // Single triangle; verify the depth buffer equals the analytic
        // screen-space-linear interpolation of NDC z at a specific pixel.
        double zA = -1.0, zB = -1.0, zC = -10.0;
        Mesh tri = TestScenes.singleTriangle(
                TestScenes.v(0.0, 0.9, zA, TestScenes.RED),
                TestScenes.v(-0.9, -0.9, zB, TestScenes.RED),
                TestScenes.v(9.0, -0.9, zC, TestScenes.RED));
        Framebuffer fb = new Framebuffer(100, 100);
        renderer(false, true).render(fb, tri, Mat4f.identity(), TestScenes.camera(1.0), null);

        // Screen positions (fov 90, aspect 1, 100x100):
        // A(50,5) B(5,95) C(95,54.5); pixel (50,51) center (50.5,51.5).
        double px = 50.5, py = 51.5;
        double ax = 50, ay = 5, bx = 5, by = 95, cx = 95, cy = 54.5;
        double denom = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy);
        double wA = ((by - cy) * (px - cx) + (cx - bx) * (py - cy)) / denom;
        double wB = ((cy - ay) * (px - cx) + (ax - cx) * (py - cy)) / denom;
        double wC = 1.0 - wA - wB;
        assertTrue(wA > 0 && wB > 0 && wC > 0, "pixel must be inside the triangle");

        double near = 1.0, far = 100.0;
        java.util.function.DoubleUnaryOperator ndcZ = z -> {
            double a = (far + near) / (near - far);
            double b = 2 * far * near / (near - far);
            return (a * z + b) / (-z);
        };
        double expected = wA * ndcZ.applyAsDouble(zA)
                + wB * ndcZ.applyAsDouble(zB)
                + wC * ndcZ.applyAsDouble(zC);
        assertEquals(expected, fb.depth(50, 51), 1e-9,
                "depth must be the screen-space-linear interpolation of NDC z");
    }

    @Test
    void backfaceCullingDropsReversedTriangles() {
        Mesh ccw = TestScenes.singleTriangle(
                TestScenes.v(-0.5, -0.5, -2, TestScenes.RED),
                TestScenes.v(0.5, -0.5, -2, TestScenes.RED),
                TestScenes.v(0.0, 0.5, -2, TestScenes.RED));
        Mesh cw = TestScenes.singleTriangle(
                TestScenes.v(-0.5, -0.5, -2, TestScenes.RED),
                TestScenes.v(0.0, 0.5, -2, TestScenes.RED),
                TestScenes.v(0.5, -0.5, -2, TestScenes.RED));

        Framebuffer fbCull = new Framebuffer(64, 64);
        renderer(true, true).render(fbCull, cw, Mat4f.identity(), TestScenes.camera(1.0), null);
        assertFalse(TestScenes.covered(fbCull.pixel(32, 32)), "clockwise triangle must be culled");

        Framebuffer fbFront = new Framebuffer(64, 64);
        renderer(true, true).render(fbFront, ccw, Mat4f.identity(), TestScenes.camera(1.0), null);
        assertTrue(TestScenes.covered(fbFront.pixel(32, 32)), "counter-clockwise triangle must be drawn");

        Framebuffer fbNoCull = new Framebuffer(64, 64);
        renderer(false, true).render(fbNoCull, cw, Mat4f.identity(), TestScenes.camera(1.0), null);
        assertTrue(TestScenes.covered(fbNoCull.pixel(32, 32)), "culling off must draw both windings");
    }
}

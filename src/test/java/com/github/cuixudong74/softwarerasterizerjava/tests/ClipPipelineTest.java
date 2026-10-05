package com.github.cuixudong74.softwarerasterizerjava.tests;

import com.github.cuixudong74.softwarerasterizerjava.raster.core.Framebuffer;
import com.github.cuixudong74.softwarerasterizerjava.raster.core.Mesh;
import com.github.cuixudong74.softwarerasterizerjava.raster.math.Mat4f;
import com.github.cuixudong74.softwarerasterizerjava.raster.pipeline.RenderConfig;
import com.github.cuixudong74.softwarerasterizerjava.raster.pipeline.Renderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** End-to-end clipping behavior through the renderer. */
class ClipPipelineTest {

    private static Renderer renderer() {
        return new Renderer(new RenderConfig(16, 1, false, true, 0));
    }

    @Test
    void triangleBehindNearPlaneIsNotRendered() {
        Framebuffer fb = new Framebuffer(64, 64);
        Mesh tri = TestScenes.singleTriangle(
                TestScenes.v(-1, -1, -0.5, TestScenes.RED),
                TestScenes.v(1, -1, -0.5, TestScenes.RED),
                TestScenes.v(0, 1, -0.5, TestScenes.RED));
        renderer().render(fb, tri, Mat4f.identity(), TestScenes.camera(1.0), null);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                assertFalse(TestScenes.covered(fb.pixel(x, y)));
            }
        }
    }

    @Test
    void triangleCrossingNearPlaneIsSplitNotDropped() {
        Framebuffer fb = new Framebuffer(101, 101);
        // Apex behind the near plane (z=-0.5), base well in front (z=-2).
        Mesh tri = TestScenes.singleTriangle(
                TestScenes.v(0, 0.5, -0.5, TestScenes.RED),
                TestScenes.v(-0.5, -0.5, -2, TestScenes.RED),
                TestScenes.v(0.5, -0.5, -2, TestScenes.RED));
        renderer().render(fb, tri, Mat4f.identity(), TestScenes.camera(1.0), null);
        assertTrue(TestScenes.covered(fb.pixel(50, 60)), "visible part must survive near clipping");
    }

    @Test
    void clippedResultMatchesManuallyClippedGeometry() {
        // Triangle crossing the near plane; the renderer must produce exactly the
        // coverage of the analytically clipped quad.
        Framebuffer fbAuto = new Framebuffer(101, 101);
        Mesh crossing = TestScenes.singleTriangle(
                TestScenes.v(0, 0.5, -0.5, TestScenes.RED),
                TestScenes.v(-0.5, -0.5, -2, TestScenes.RED),
                TestScenes.v(0.5, -0.5, -2, TestScenes.RED));
        renderer().render(fbAuto, crossing, Mat4f.identity(), TestScenes.camera(1.0), null);

        // Edge from apex (0,0.5,-0.5) to each base vertex hits z=-1 at t=1/3:
        // boundary points (-1/6, 1/6, -1) and (1/6, 1/6, -1).
        double s = 1.0 / 6.0;
        Mesh manual = TestScenes.mesh(java.util.List.of(
                TestScenes.v(-s, s, -1, TestScenes.RED),
                TestScenes.v(s, s, -1, TestScenes.RED),
                TestScenes.v(-0.5, -0.5, -2, TestScenes.RED),
                TestScenes.v(0.5, -0.5, -2, TestScenes.RED)),
                0, 2, 3, 0, 3, 1);
        Framebuffer fbManual = new Framebuffer(101, 101);
        renderer().render(fbManual, manual, Mat4f.identity(), TestScenes.camera(1.0), null);

        for (int y = 0; y < 101; y++) {
            for (int x = 0; x < 101; x++) {
                assertEquals(TestScenes.covered(fbManual.pixel(x, y)),
                        TestScenes.covered(fbAuto.pixel(x, y)),
                        "coverage mismatch at (" + x + "," + y + ")");
            }
        }
    }

    @Test
    void triangleOutsideFrustumSideIsDropped() {
        Framebuffer fb = new Framebuffer(64, 64);
        // Entirely to the right of the frustum (x/-z > 1 everywhere).
        Mesh tri = TestScenes.singleTriangle(
                TestScenes.v(10, 0, -2, TestScenes.RED),
                TestScenes.v(12, 0, -2, TestScenes.RED),
                TestScenes.v(11, 1, -2, TestScenes.RED));
        renderer().render(fb, tri, Mat4f.identity(), TestScenes.camera(1.0), null);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                assertFalse(TestScenes.covered(fb.pixel(x, y)));
            }
        }
    }

    @Test
    void clippedAndUnclippedVersionsOfVisibleRegionAgree() {
        // A triangle fully inside the frustum, and a larger coplanar triangle that
        // extends past the frustum sides but covers the same on-screen region must
        // agree on every pixel of the inner triangle's coverage.
        Framebuffer fbInner = new Framebuffer(101, 101);
        Mesh inner = TestScenes.singleTriangle(
                TestScenes.v(-0.5, -0.5, -2, TestScenes.RED),
                TestScenes.v(0.5, -0.5, -2, TestScenes.RED),
                TestScenes.v(0, 0.5, -2, TestScenes.RED));
        renderer().render(fbInner, inner, Mat4f.identity(), TestScenes.camera(1.0), null);

        Framebuffer fbBig = new Framebuffer(101, 101);
        Mesh big = TestScenes.singleTriangle(
                TestScenes.v(-50, -50, -2, TestScenes.RED),
                TestScenes.v(50, -50, -2, TestScenes.RED),
                TestScenes.v(0, 50, -2, TestScenes.RED));
        renderer().render(fbBig, big, Mat4f.identity(), TestScenes.camera(1.0), null);

        for (int y = 0; y < 101; y++) {
            for (int x = 0; x < 101; x++) {
                if (TestScenes.covered(fbInner.pixel(x, y))) {
                    assertTrue(TestScenes.covered(fbBig.pixel(x, y)),
                            "big triangle lost a pixel at (" + x + "," + y + ")");
                }
            }
        }
    }
}

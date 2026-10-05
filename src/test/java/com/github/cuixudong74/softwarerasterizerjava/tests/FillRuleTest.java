package com.github.cuixudong74.softwarerasterizerjava.tests;

import com.github.cuixudong74.softwarerasterizerjava.raster.core.Framebuffer;
import com.github.cuixudong74.softwarerasterizerjava.raster.core.Mesh;
import com.github.cuixudong74.softwarerasterizerjava.raster.math.Mat4f;
import com.github.cuixudong74.softwarerasterizerjava.raster.pipeline.RenderConfig;
import com.github.cuixudong74.softwarerasterizerjava.raster.pipeline.Renderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Top-left fill rule: two triangles sharing an edge must cover each pixel
 * exactly once -- no gaps along the shared edge, no double coverage.
 */
class FillRuleTest {

    private static Renderer renderer() {
        return new Renderer(new RenderConfig(16, 1, false, true, 0));
    }

    /** Quad split into two triangles along the (0,2) diagonal. */
    private static Mesh quadHalf(boolean first) {
        // Square at z=-2, corners (+/-0.9, +/-0.9).
        var verts = java.util.List.of(
                TestScenes.v(-0.9, -0.9, -2, TestScenes.RED),
                TestScenes.v(0.9, -0.9, -2, TestScenes.RED),
                TestScenes.v(0.9, 0.9, -2, TestScenes.RED),
                TestScenes.v(-0.9, 0.9, -2, TestScenes.RED));
        return first ? TestScenes.mesh(verts, 0, 1, 2) : TestScenes.mesh(verts, 0, 2, 3);
    }

    @Test
    void sharedEdgeCoveredExactlyOnce() {
        Framebuffer fbA = new Framebuffer(101, 101);
        Framebuffer fbB = new Framebuffer(101, 101);
        renderer().render(fbA, quadHalf(true), Mat4f.identity(), TestScenes.camera(1.0), null);
        renderer().render(fbB, quadHalf(false), Mat4f.identity(), TestScenes.camera(1.0), null);

        for (int y = 0; y < 101; y++) {
            for (int x = 0; x < 101; x++) {
                boolean a = TestScenes.covered(fbA.pixel(x, y));
                boolean b = TestScenes.covered(fbB.pixel(x, y));
                assertFalse(a && b, "double coverage at (" + x + "," + y + ")");
            }
        }
    }

    @Test
    void unionOfHalvesHasNoGaps() {
        Framebuffer fbA = new Framebuffer(101, 101);
        Framebuffer fbB = new Framebuffer(101, 101);
        renderer().render(fbA, quadHalf(true), Mat4f.identity(), TestScenes.camera(1.0), null);
        renderer().render(fbB, quadHalf(false), Mat4f.identity(), TestScenes.camera(1.0), null);

        // The union must be a solid square: every pixel whose center is strictly
        // inside the NDC square [-0.45,0.45]^2 must be covered by exactly one half.
        for (int y = 0; y < 101; y++) {
            for (int x = 0; x < 101; x++) {
                double ndcX = (x + 0.5) / 50.5 - 1.0;
                double ndcY = 1.0 - (y + 0.5) / 50.5;
                boolean inside = Math.abs(ndcX) < 0.44 && Math.abs(ndcY) < 0.44;
                if (inside) {
                    boolean covered = TestScenes.covered(fbA.pixel(x, y))
                            ^ TestScenes.covered(fbB.pixel(x, y));
                    assertTrue(covered, "gap or overlap at (" + x + "," + y + ")");
                }
            }
        }
    }

    @Test
    void combinedRenderMatchesSeparateCoverage() {
        Framebuffer fbA = new Framebuffer(101, 101);
        Framebuffer fbB = new Framebuffer(101, 101);
        Framebuffer fbBoth = new Framebuffer(101, 101);
        renderer().render(fbA, quadHalf(true), Mat4f.identity(), TestScenes.camera(1.0), null);
        renderer().render(fbB, quadHalf(false), Mat4f.identity(), TestScenes.camera(1.0), null);
        Mesh both = TestScenes.mesh(java.util.List.of(
                TestScenes.v(-0.9, -0.9, -2, TestScenes.RED),
                TestScenes.v(0.9, -0.9, -2, TestScenes.RED),
                TestScenes.v(0.9, 0.9, -2, TestScenes.RED),
                TestScenes.v(-0.9, 0.9, -2, TestScenes.RED)), 0, 1, 2, 0, 2, 3);
        renderer().render(fbBoth, both, Mat4f.identity(), TestScenes.camera(1.0), null);

        for (int y = 0; y < 101; y++) {
            for (int x = 0; x < 101; x++) {
                boolean union = TestScenes.covered(fbA.pixel(x, y)) || TestScenes.covered(fbB.pixel(x, y));
                assertEquals(union, TestScenes.covered(fbBoth.pixel(x, y)),
                        "coverage mismatch at (" + x + "," + y + ")");
            }
        }
    }
}

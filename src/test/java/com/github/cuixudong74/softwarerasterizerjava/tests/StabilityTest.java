package com.github.cuixudong74.softwarerasterizerjava.tests;

import com.github.cuixudong74.softwarerasterizerjava.raster.core.Framebuffer;
import com.github.cuixudong74.softwarerasterizerjava.raster.core.Mesh;
import com.github.cuixudong74.softwarerasterizerjava.raster.math.Mat4f;
import com.github.cuixudong74.softwarerasterizerjava.raster.pipeline.RenderConfig;
import com.github.cuixudong74.softwarerasterizerjava.raster.pipeline.RenderException;
import com.github.cuixudong74.softwarerasterizerjava.raster.pipeline.Renderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Robustness on extreme geometry and large workloads. */
class StabilityTest {

    private static Renderer renderer() {
        return new Renderer(new RenderConfig(32, 4, true, true, 0));
    }

    @Test
    void ultraThinTriangleIsStable() {
        Mesh m = TestScenes.singleTriangle(
                TestScenes.v(-0.5, -0.5, -2, TestScenes.RED),
                TestScenes.v(0.5, -0.5 + 1e-9, -2, TestScenes.RED),
                TestScenes.v(0, 0.5, -2, TestScenes.RED));
        Framebuffer fb = new Framebuffer(128, 128);
        renderer().render(fb, m, Mat4f.identity(), TestScenes.camera(1.0), null);
        // Must simply complete and produce a valid buffer.
        assertDoesNotThrow(fb::checksum);
    }

    @Test
    void degenerateZeroAreaTriangleIsSkipped() {
        Mesh m = TestScenes.singleTriangle(
                TestScenes.v(0, 0, -2, TestScenes.RED),
                TestScenes.v(1e-30, 0, -2, TestScenes.RED),
                TestScenes.v(2e-30, 0, -2, TestScenes.RED));
        Framebuffer fb = new Framebuffer(64, 64);
        renderer().render(fb, m, Mat4f.identity(), TestScenes.camera(1.0), null);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                assertFalse(TestScenes.covered(fb.pixel(x, y)));
            }
        }
    }

    @Test
    void triangleFarBeyondScreenIsClippedEfficiently() {
        Mesh m = TestScenes.singleTriangle(
                TestScenes.v(-1e8, -1e8, -2, TestScenes.RED),
                TestScenes.v(1e8, -1e8, -2, TestScenes.RED),
                TestScenes.v(0, 1e8, -2, TestScenes.RED));
        Framebuffer fb = new Framebuffer(64, 64);
        long start = System.nanoTime();
        renderer().render(fb, m, Mat4f.identity(), TestScenes.camera(1.0), null);
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertTrue(millis < 5000, "huge off-screen triangle took " + millis + " ms");
        assertTrue(TestScenes.covered(fb.pixel(32, 32)));
    }

    @Test
    void largeSceneScalesReasonably() {
        Mesh scene = TestScenes.randomScene(20_000, 3);
        Framebuffer fb = new Framebuffer(256, 256);
        long start = System.nanoTime();
        renderer().render(fb, scene, Mat4f.identity(), TestScenes.camera(1.0), null);
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertTrue(millis < 30_000, "20k triangles took " + millis + " ms");
    }

    @Test
    void timeoutAbortsRenderAndLeavesTargetUntouched() {
        // Pathological workload: thousands of screen-covering triangles.
        Mesh scene = TestScenes.randomScene(50_000, 11);
        Framebuffer fb = new Framebuffer(512, 512);
        fb.clear(0xFF112233, 0.5);
        Renderer r = new Renderer(new RenderConfig(512, 1, false, true, 1)); // 1 ms budget
        try {
            r.render(fb, scene, Mat4f.identity(), TestScenes.camera(1.0), null);
            // Completed within the budget: output must be fully valid.
            assertDoesNotThrow(fb::checksum);
        } catch (RenderException e) {
            assertTrue(e.getMessage().contains("timed out"), e.getMessage());
            // No half-rendered bitmap may leak into the caller's framebuffer.
            for (int y = 0; y < 512; y += 64) {
                for (int x = 0; x < 512; x += 64) {
                    assertEquals(0xFF112233, fb.pixel(x, y));
                    assertEquals(0.5, fb.depth(x, y));
                }
            }
        }
    }

    @Test
    void memoryUsageIsBounded() {
        // Framebuffer larger than the hard cap must be rejected up front.
        assertThrows(RenderException.class, () -> new Framebuffer(20_000, 20_000));
    }
}

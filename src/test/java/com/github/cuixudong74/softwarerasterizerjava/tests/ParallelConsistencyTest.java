package com.github.cuixudong74.softwarerasterizerjava.tests;

import com.github.cuixudong74.softwarerasterizerjava.raster.core.Framebuffer;
import com.github.cuixudong74.softwarerasterizerjava.raster.core.Mesh;
import com.github.cuixudong74.softwarerasterizerjava.raster.core.Texture;
import com.github.cuixudong74.softwarerasterizerjava.raster.math.Mat4f;
import com.github.cuixudong74.softwarerasterizerjava.raster.pipeline.RenderConfig;
import com.github.cuixudong74.softwarerasterizerjava.raster.pipeline.Renderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Parallel rendering must be bit-identical to serial rendering. */
class ParallelConsistencyTest {

    private static Framebuffer render(Mesh scene, Texture tex, int tileSize, int threads) {
        Framebuffer fb = new Framebuffer(128, 128);
        new Renderer(new RenderConfig(tileSize, threads, true, true, 0))
                .render(fb, scene, Mat4f.rotationY(0.3), TestScenes.camera(1.0), tex);
        return fb;
    }

    @Test
    void allTileSizesAndThreadCountsAgree() {
        Mesh scene = TestScenes.randomScene(300, 7);
        int[] texData = new int[64 * 64];
        java.util.Random rnd = new java.util.Random(1);
        for (int i = 0; i < texData.length; i++) texData[i] = 0xFF000000 | rnd.nextInt(0xFFFFFF);
        Texture tex = new Texture(64, 64, texData, Texture.WrapMode.REPEAT);

        Framebuffer reference = render(scene, tex, 32, 1);
        int[][] configs = {
                {1, 1}, {7, 3}, {16, 2}, {32, 4}, {64, 8}, {128, 16}, {200, 5}
        };
        for (int[] cfg : configs) {
            Framebuffer fb = render(scene, tex, cfg[0], cfg[1]);
            assertArrayEquals(reference.colorBuffer(), fb.colorBuffer(),
                    "color mismatch for tileSize=" + cfg[0] + " threads=" + cfg[1]);
            assertArrayEquals(reference.depthBuffer(), fb.depthBuffer(),
                    "depth mismatch for tileSize=" + cfg[0] + " threads=" + cfg[1]);
        }
    }

    @Test
    void repeatedRendersAreIdentical() {
        Mesh scene = TestScenes.randomScene(100, 99);
        Framebuffer a = render(scene, null, 32, 4);
        Framebuffer b = render(scene, null, 32, 4);
        assertEquals(a.checksum(), b.checksum());
    }
}

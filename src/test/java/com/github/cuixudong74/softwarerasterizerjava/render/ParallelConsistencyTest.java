package com.github.cuixudong74.softwarerasterizerjava.render;

import com.github.cuixudong74.softwarerasterizerjava.render.core.Framebuffer;
import com.github.cuixudong74.softwarerasterizerjava.render.core.Mesh;
import com.github.cuixudong74.softwarerasterizerjava.render.core.Vertex;
import com.github.cuixudong74.softwarerasterizerjava.render.math.Mat4f;
import com.github.cuixudong74.softwarerasterizerjava.render.math.Vec4f;
import com.github.cuixudong74.softwarerasterizerjava.render.pipeline.RenderConfig;
import com.github.cuixudong74.softwarerasterizerjava.render.pipeline.Renderer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.github.cuixudong74.softwarerasterizerjava.render.TestScenes.*;
import static org.junit.jupiter.api.Assertions.*;

class ParallelConsistencyTest {

    private static final int W = 200, H = 150;

    /** A deterministic pseudo-random scene with overlapping triangles at many depths. */
    private static Mesh randomScene(int triangles, long seed) {
        Random rng = new Random(seed);
        List<Vertex> verts = new ArrayList<>();
        int[] indices = new int[triangles * 3];
        for (int i = 0; i < triangles; i++) {
            float cx = (rng.nextFloat() - 0.5f) * 4f;
            float cy = (rng.nextFloat() - 0.5f) * 4f;
            float z = -1.5f - rng.nextFloat() * 20f;
            float size = 0.2f + rng.nextFloat() * 1.5f;
            for (int k = 0; k < 3; k++) {
                double ang = (k / 3.0) * Math.PI * 2 + rng.nextFloat();
                Vec4f color = new Vec4f(rng.nextFloat(), rng.nextFloat(), rng.nextFloat(), 1f);
                verts.add(v(cx + (float) (size * Math.cos(ang)), cy + (float) (size * Math.sin(ang)),
                        z - rng.nextFloat() * 2f, color));
                indices[i * 3 + k] = i * 3 + k;
            }
        }
        return mesh(verts, indices);
    }

    private static Framebuffer render(Mesh mesh, RenderConfig cfg) {
        Framebuffer fb = new Framebuffer(W, H);
        new Renderer().render(mesh, Mat4f.identity(), defaultCamera(), null, fb, cfg);
        return fb;
    }

    private static void assertIdentical(Framebuffer a, Framebuffer b) {
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                assertEquals(a.colorAt(x, y), b.colorAt(x, y),
                        "color mismatch at (" + x + "," + y + ")");
                assertEquals(Float.floatToRawIntBits(a.depthAt(x, y)), Float.floatToRawIntBits(b.depthAt(x, y)),
                        "depth mismatch at (" + x + "," + y + ")");
            }
        }
    }

    @Test
    void parallelMatchesSingleThreaded() {
        Mesh scene = randomScene(300, 42L);
        Framebuffer reference = render(scene, new RenderConfig(1, 1, 1, true, true,
                com.github.cuixudong74.softwarerasterizerjava.render.core.AddressMode.CLAMP, 0, 100_000));
        int[][] tileConfigs = {{1, 1}, {2, 2}, {8, 8}, {16, 16}, {7, 3}, {64, 64}};
        for (int[] tiles : tileConfigs) {
            for (int threads : new int[]{1, 2, 4, 8}) {
                RenderConfig cfg = new RenderConfig(threads, tiles[0], tiles[1], true, true,
                        com.github.cuixudong74.softwarerasterizerjava.render.core.AddressMode.CLAMP, 0, 100_000);
                assertIdentical(reference, render(scene, cfg));
            }
        }
    }

    @Test
    void repeatedRendersAreIdentical() {
        Mesh scene = randomScene(200, 7L);
        RenderConfig cfg = RenderConfig.defaults().withThreads(4).withTiles(8, 8);
        Framebuffer first = render(scene, cfg);
        for (int i = 0; i < 5; i++) {
            assertIdentical(first, render(scene, cfg));
        }
    }

    @Test
    void emptyMeshRendersClearColor() {
        Mesh empty = mesh(List.of(), new int[0]);
        Framebuffer fb = render(empty, RenderConfig.defaults());
        assertEquals(0, countNonBackground(fb, 0xFF000000));
    }
}

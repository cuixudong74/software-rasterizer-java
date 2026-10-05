package com.github.cuixudong74.softwarerasterizerjava.render;

import com.github.cuixudong74.softwarerasterizerjava.render.core.Framebuffer;
import com.github.cuixudong74.softwarerasterizerjava.render.core.Mesh;
import com.github.cuixudong74.softwarerasterizerjava.render.core.Vertex;
import com.github.cuixudong74.softwarerasterizerjava.render.math.Mat4f;
import com.github.cuixudong74.softwarerasterizerjava.render.math.Vec4f;
import com.github.cuixudong74.softwarerasterizerjava.render.pipeline.RenderConfig;
import com.github.cuixudong74.softwarerasterizerjava.render.pipeline.Renderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.github.cuixudong74.softwarerasterizerjava.render.TestScenes.*;
import static org.junit.jupiter.api.Assertions.*;

/** Stability under large and degenerate inputs. */
class StabilityTest {

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void largeSceneRendersDeterministicallyAndQuickly() {
        // 100k triangles, many huge/sliver/offscreen, at 256x256 with 4 threads.
        Random rng = new Random(123L);
        int triangles = 100_000;
        List<Vertex> verts = new ArrayList<>(triangles * 3);
        int[] indices = new int[triangles * 3];
        for (int i = 0; i < triangles; i++) {
            int kind = i % 10;
            float z = -1.5f - rng.nextFloat() * 50f;
            Vec4f color = new Vec4f(rng.nextFloat(), rng.nextFloat(), rng.nextFloat(), 1f);
            if (kind == 0) {
                // Huge triangle far beyond the screen.
                verts.add(v(-1e4f, -1e4f, z, color));
                verts.add(v(1e4f, -1e4f, z, color));
                verts.add(v(0f, 1e4f, z, color));
            } else if (kind == 1) {
                // Ultra-thin sliver across the screen.
                float y = (rng.nextFloat() - 0.5f) * 2f;
                verts.add(v(-2f, y, z, color));
                verts.add(v(2f, y, z, color));
                verts.add(v(0f, y + 1e-4f, z, color));
            } else if (kind == 2) {
                // Completely offscreen.
                float ox = 1000f + rng.nextFloat() * 1000f;
                verts.add(v(ox, 0f, z, color));
                verts.add(v(ox + 1f, 0f, z, color));
                verts.add(v(ox, 1f, z, color));
            } else {
                // Ordinary small triangle.
                float cx = (rng.nextFloat() - 0.5f) * 3f;
                float cy = (rng.nextFloat() - 0.5f) * 3f;
                float s = 0.05f + rng.nextFloat() * 0.3f;
                verts.add(v(cx, cy, z, color));
                verts.add(v(cx + s, cy, z, color));
                verts.add(v(cx, cy + s, z, color));
            }
            indices[i * 3] = i * 3;
            indices[i * 3 + 1] = i * 3 + 1;
            indices[i * 3 + 2] = i * 3 + 2;
        }
        Mesh scene = mesh(verts, indices);
        RenderConfig cfg = RenderConfig.defaults().withThreads(4).withTiles(8, 8);

        long start = System.nanoTime();
        Framebuffer a = new Framebuffer(256, 256);
        new Renderer().render(scene, Mat4f.identity(), defaultCamera(), null, a, cfg);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        Framebuffer b = new Framebuffer(256, 256);
        new Renderer().render(scene, Mat4f.identity(), defaultCamera(), null, b, cfg);
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) {
                assertEquals(a.colorAt(x, y), b.colorAt(x, y));
            }
        }
        // Generous bound: must stay controlled as input grows (typically ~1-2 s here).
        assertTrue(elapsedMs < 30_000, "100k-triangle render took " + elapsedMs + " ms");
        System.out.println("largeScene render time: " + elapsedMs + " ms");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void extremeCoordinatesDoNotCrash() {
        // Vertices at float extremes but finite: must be handled without exceptions.
        Mesh extreme = mesh(List.of(
                v(-1e30f, -1e30f, -5f, RED), v(1e30f, -1e30f, -5f, RED), v(0f, 1e30f, -5f, RED),
                v(0f, 0f, -1.0000001f, GREEN), v(1e-30f, 0f, -1.0000001f, GREEN), v(0f, 1e-30f, -1.0000001f, GREEN)),
                0, 1, 2, 3, 4, 5);
        Framebuffer fb = new Framebuffer(64, 64);
        new Renderer().render(extreme, Mat4f.identity(), defaultCamera(), null, fb,
                RenderConfig.defaults().withThreads(2).withTiles(4, 4));
        // The huge triangle encloses the whole screen.
        assertTrue(countNonBackground(fb, 0xFF000000) > 0);
    }
}

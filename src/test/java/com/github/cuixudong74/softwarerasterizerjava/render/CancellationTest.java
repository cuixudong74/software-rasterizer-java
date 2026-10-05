package com.github.cuixudong74.softwarerasterizerjava.render;

import com.github.cuixudong74.softwarerasterizerjava.render.core.Framebuffer;
import com.github.cuixudong74.softwarerasterizerjava.render.core.Mesh;
import com.github.cuixudong74.softwarerasterizerjava.render.core.Vertex;
import com.github.cuixudong74.softwarerasterizerjava.render.math.Mat4f;
import com.github.cuixudong74.softwarerasterizerjava.render.pipeline.RenderConfig;
import com.github.cuixudong74.softwarerasterizerjava.render.pipeline.RenderCancelledException;
import com.github.cuixudong74.softwarerasterizerjava.render.pipeline.Renderer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.github.cuixudong74.softwarerasterizerjava.render.TestScenes.*;
import static org.junit.jupiter.api.Assertions.*;

class CancellationTest {

    /** A scene heavy enough that rendering takes far longer than the test timeout. */
    private static Mesh heavyScene(int triangles) {
        List<Vertex> verts = new ArrayList<>(triangles * 3);
        int[] indices = new int[triangles * 3];
        for (int i = 0; i < triangles; i++) {
            // Every triangle covers the whole screen: maximal per-tile rasterization work.
            verts.add(v(-10f, -10f, -5f, RED));
            verts.add(v(10f, -10f, -5f, GREEN));
            verts.add(v(0f, 10f, -5f, BLUE));
            indices[i * 3] = i * 3;
            indices[i * 3 + 1] = i * 3 + 1;
            indices[i * 3 + 2] = i * 3 + 2;
        }
        return mesh(verts, indices);
    }

    private static int workerThreadCount() {
        int n = 0;
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.getName().equals("raster-worker") && t.isAlive()) {
                n++;
            }
        }
        return n;
    }

    @Test
    void timeoutCancelsCleanlyWithoutPartialOutputOrLeaks() throws Exception {
        Mesh heavy = heavyScene(20_000);
        Framebuffer dest = new Framebuffer(256, 256);
        dest.clear(0xFF112233, 0.25f);
        RenderConfig cfg = RenderConfig.defaults().withThreads(4).withTiles(8, 8).withTimeout(1);

        int threadsBefore = workerThreadCount();
        long start = System.nanoTime();
        RenderCancelledException ex = assertThrows(RenderCancelledException.class,
                () -> new Renderer().render(heavy, Mat4f.identity(), defaultCamera(), null, dest, cfg));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(ex.getMessage().contains("timed out"));

        // Destination untouched: no half-rendered bitmap.
        for (int y = 0; y < 256; y += 37) {
            for (int x = 0; x < 256; x += 41) {
                assertEquals(0xFF112233, dest.colorAt(x, y));
                assertEquals(0.25f, dest.depthAt(x, y));
            }
        }
        // Workers exit promptly after cooperative cancellation (generous bound).
        long deadline = System.currentTimeMillis() + 10_000;
        while (workerThreadCount() > threadsBefore && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(threadsBefore, workerThreadCount(), "worker threads must not leak");
        assertTrue(elapsedMs < 30_000, "cancellation should be fast, took " + elapsedMs + " ms");
    }

    @Test
    void successfulRenderAfterCancellationWorks() {
        Mesh heavy = heavyScene(20_000);
        Framebuffer dest = new Framebuffer(64, 64);
        RenderConfig tight = RenderConfig.defaults().withThreads(4).withTiles(8, 8).withTimeout(1);
        assertThrows(RenderCancelledException.class,
                () -> new Renderer().render(heavy, Mat4f.identity(), defaultCamera(), null, dest, tight));
        // A subsequent normal render on the same destination must succeed.
        Mesh tri = mesh(List.of(v(-0.5f, -0.5f, -2f, RED), v(0.5f, -0.5f, -2f, RED), v(0f, 0.5f, -2f, RED)),
                0, 1, 2);
        new Renderer().render(tri, Mat4f.identity(), defaultCamera(), null, dest,
                RenderConfig.defaults().withThreads(2).withTiles(2, 2));
        assertTrue(countNonBackground(dest, 0xFF000000) > 0);
    }
}

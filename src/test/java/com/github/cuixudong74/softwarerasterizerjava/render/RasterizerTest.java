package com.github.cuixudong74.softwarerasterizerjava.render;

import com.github.cuixudong74.softwarerasterizerjava.render.core.Framebuffer;
import com.github.cuixudong74.softwarerasterizerjava.render.core.Mesh;
import com.github.cuixudong74.softwarerasterizerjava.render.core.Vertex;
import com.github.cuixudong74.softwarerasterizerjava.render.math.Mat4f;
import com.github.cuixudong74.softwarerasterizerjava.render.pipeline.RenderConfig;
import com.github.cuixudong74.softwarerasterizerjava.render.pipeline.Renderer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.cuixudong74.softwarerasterizerjava.render.TestScenes.*;
import static org.junit.jupiter.api.Assertions.*;

class RasterizerTest {

    private static final int W = 64, H = 64;
    private static final int BG = 0xFF000000;
    private static final int RED_ARGB = 0xFFFF0000;
    private static final int GREEN_ARGB = 0xFF00FF00;

    private static Framebuffer render(Mesh mesh, RenderConfig cfg) {
        Framebuffer fb = new Framebuffer(W, H);
        fb.clear(BG, 1f);
        new Renderer().render(mesh, Mat4f.identity(), defaultCamera(), null, fb, cfg);
        return fb;
    }

    @Test
    void sharedEdgeCoveredExactlyOnce() {
        // Two solid-colored triangles (duplicated vertices so colors do not interpolate
        // across the shared edge), depth test off: any double coverage would show up as
        // the later (green) color where the solo mask says only red was drawn.
        List<Vertex> verts = List.of(
                v(-0.8f, -0.8f, -2f, RED), v(0.8f, -0.8f, -2f, RED), v(0.8f, 0.8f, -2f, RED),
                v(-0.8f, -0.8f, -2f, GREEN), v(0.8f, 0.8f, -2f, GREEN), v(-0.8f, 0.8f, -2f, GREEN));
        Mesh colored = mesh(verts, 0, 1, 2, 3, 4, 5);
        RenderConfig cfg = new RenderConfig(1, 1, 1, false, false,
                com.github.cuixudong74.softwarerasterizerjava.render.core.AddressMode.CLAMP, 0, 1000);

        // Coverage masks from rendering each triangle alone.
        Mesh triA = mesh(verts, 0, 1, 2);
        Mesh triB = mesh(verts, 3, 4, 5);
        Framebuffer fa = render(triA, cfg);
        Framebuffer fbB = render(triB, cfg);
        Framebuffer both = render(colored, cfg);

        int covered = 0;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                boolean inA = fa.colorAt(x, y) != BG;
                boolean inB = fbB.colorAt(x, y) != BG;
                assertFalse(inA && inB,
                        "pixel (" + x + "," + y + ") covered by both triangles (double fill)");
                int expected = inA ? RED_ARGB : inB ? GREEN_ARGB : BG;
                assertEquals(expected, both.colorAt(x, y),
                        "joint render mismatch at (" + x + "," + y + ")");
                if (inA || inB) covered++;
            }
        }
        assertTrue(covered > W * H / 8, "quad should cover a substantial area");
    }

    @Test
    void noGapsAlongSharedEdge() {
        // The union of the two triangles must equal a single big triangle's coverage
        // for the same outline: render quad as 2 tris vs the same quad re-triangulated
        // along the other diagonal; the silhouettes must match exactly.
        List<Vertex> verts = List.of(
                v(-0.8f, -0.8f, -2f, WHITE), v(0.8f, -0.8f, -2f, WHITE),
                v(0.8f, 0.8f, -2f, WHITE), v(-0.8f, 0.8f, -2f, WHITE));
        RenderConfig cfg = new RenderConfig(1, 1, 1, false, false,
                com.github.cuixudong74.softwarerasterizerjava.render.core.AddressMode.CLAMP, 0, 1000);
        Framebuffer diag1 = render(mesh(verts, 0, 1, 2, 0, 2, 3), cfg);
        Framebuffer diag2 = render(mesh(verts, 0, 1, 3, 1, 2, 3), cfg);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                assertEquals(diag1.colorAt(x, y) != BG, diag2.colorAt(x, y) != BG,
                        "silhouette mismatch at (" + x + "," + y + ")");
            }
        }
    }

    @Test
    void depthTestOccludesCorrectly() {
        // Red triangle in front (z=-2), green behind (z=-3), overlapping fully.
        Vertex r0 = v(-0.5f, -0.5f, -2f, RED);
        Vertex r1 = v(0.5f, -0.5f, -2f, RED);
        Vertex r2 = v(0f, 0.5f, -2f, RED);
        Vertex g0 = v(-0.5f, -0.5f, -3f, GREEN);
        Vertex g1 = v(0.5f, -0.5f, -3f, GREEN);
        Vertex g2 = v(0f, 0.5f, -3f, GREEN);
        // Draw the far (green) triangle first, then the near (red) one.
        Mesh mesh = mesh(List.of(g0, g1, g2, r0, r1, r2), 0, 1, 2, 3, 4, 5);
        Framebuffer fb = render(mesh, RenderConfig.defaults().withThreads(1).withTiles(1, 1));
        int red = 0, green = 0;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (fb.colorAt(x, y) == RED_ARGB) red++;
                if (fb.colorAt(x, y) == GREEN_ARGB) green++;
            }
        }
        assertTrue(red > 0, "near triangle must be visible");
        assertEquals(0, green, "far triangle must be fully occluded where they overlap");
    }

    /**
     * Unproject an NDC point to view space for the default camera
     * (fov 90, aspect 1, near 1, far 100: ndcX = x/d, d = -z).
     */
    private static Vertex ndcVert(double nx, double ny, double nz,
                                  com.github.cuixudong74.softwarerasterizerjava.render.math.Vec4f color) {
        double d = 2 * 100.0 / (101.0 - nz * 99.0); // from nz = (f+n)/(f-n) - 2fn/((f-n)d)
        return v((float) (nx * d), (float) (ny * d), (float) -d, color);
    }

    @Test
    void depthInterleavingIsPerspectiveCorrect() {
        // Two full-screen triangles whose NDC depths cross at screen center:
        // A is nearer on the left (ndcZ -0.5 -> +0.5), B is nearer on the right.
        Vertex a0 = ndcVert(-2, -2, -0.5, RED);
        Vertex a1 = ndcVert(2, -2, 0.5, RED);
        Vertex a2 = ndcVert(0, 2.5, 0, RED);
        Vertex b0 = ndcVert(-2, 2, 0.5, GREEN);
        Vertex b1 = ndcVert(2, 2, -0.5, GREEN);
        Vertex b2 = ndcVert(0, -2.5, 0, GREEN);
        List<Vertex> verts = List.of(a0, a1, a2, b0, b1, b2);
        RenderConfig cfg = RenderConfig.defaults().withThreads(1).withTiles(1, 1);
        Framebuffer joint = render(mesh(verts, 0, 1, 2, 3, 5, 4), cfg); // B reordered to CCW
        Framebuffer maskA = render(mesh(verts, 0, 1, 2), cfg);
        Framebuffer maskB = render(mesh(verts, 3, 5, 4), cfg);
        int redLeft = 0, greenRight = 0;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                boolean inA = maskA.colorAt(x, y) != BG;
                boolean inB = maskB.colorAt(x, y) != BG;
                if (!inA || !inB) {
                    continue; // only the overlap region tests the depth comparison
                }
                if (x < W / 2 - 2) {
                    assertEquals(RED_ARGB, joint.colorAt(x, y),
                            "A is nearer left of center at (" + x + "," + y + ")");
                    redLeft++;
                } else if (x > W / 2 + 2) {
                    assertEquals(GREEN_ARGB, joint.colorAt(x, y),
                            "B is nearer right of center at (" + x + "," + y + ")");
                    greenRight++;
                }
            }
        }
        assertTrue(redLeft > 0, "overlap region left of center must exist");
        assertTrue(greenRight > 0, "overlap region right of center must exist");
    }

    @Test
    void backfaceCullingDropsClockwiseTriangles() {
        Vertex v0 = v(-0.5f, -0.5f, -2f, RED);
        Vertex v1 = v(0.5f, -0.5f, -2f, RED);
        Vertex v2 = v(0f, 0.5f, -2f, RED);
        Mesh ccw = mesh(List.of(v0, v1, v2), 0, 1, 2);
        Mesh cw = mesh(List.of(v0, v1, v2), 0, 2, 1);
        RenderConfig culling = RenderConfig.defaults().withThreads(1).withTiles(1, 1);
        RenderConfig noCulling = new RenderConfig(1, 1, 1, false, true,
                com.github.cuixudong74.softwarerasterizerjava.render.core.AddressMode.CLAMP, 0, 1000);

        assertTrue(countNonBackground(render(ccw, culling), BG) > 0, "CCW front face visible");
        assertEquals(0, countNonBackground(render(cw, culling), BG), "CW back face culled");
        assertTrue(countNonBackground(render(cw, noCulling), BG) > 0, "two-sided render keeps CW");
    }

    @Test
    void degenerateAndOffscreenTrianglesAreSafe() {
        Vertex p = v(0f, 0f, -2f, RED);
        // Zero-area (all vertices identical), sliver, and far-offscreen triangles.
        Mesh mesh = mesh(List.of(p, p, p,
                        v(-1000f, 0.0001f, -2f, RED), v(1000f, 0f, -2f, RED), v(0f, 0.0002f, -2f, RED),
                        v(0f, 0f, 1e9f, RED), v(1e9f, 0f, 1e9f, RED), v(0f, 1e9f, 1e9f, RED)),
                0, 1, 2, 3, 4, 5, 6, 7, 8);
        Framebuffer fb = render(mesh, RenderConfig.defaults().withThreads(2).withTiles(4, 4));
        assertNotNull(fb); // must not crash or hang
    }

    @Test
    void hugeTriangleBeyondScreenIsClippedAndFilled() {
        // A triangle vastly larger than the screen, close to the camera.
        Mesh mesh = mesh(List.of(
                v(-1000f, -1000f, -5f, RED),
                v(1000f, -1000f, -5f, RED),
                v(0f, 1000f, -5f, RED)), 0, 1, 2);
        Framebuffer fb = render(mesh, RenderConfig.defaults().withThreads(1).withTiles(1, 1));
        // Center of the screen must be covered.
        assertEquals(RED_ARGB, fb.colorAt(W / 2, H / 2));
    }
}

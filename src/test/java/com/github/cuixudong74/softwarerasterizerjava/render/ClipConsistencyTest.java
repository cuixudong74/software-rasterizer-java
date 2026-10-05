package com.github.cuixudong74.softwarerasterizerjava.render;

import com.github.cuixudong74.softwarerasterizerjava.render.core.Framebuffer;
import com.github.cuixudong74.softwarerasterizerjava.render.core.Mesh;
import com.github.cuixudong74.softwarerasterizerjava.render.core.Vertex;
import com.github.cuixudong74.softwarerasterizerjava.render.math.Mat4f;
import com.github.cuixudong74.softwarerasterizerjava.render.math.Vec3f;
import com.github.cuixudong74.softwarerasterizerjava.render.math.Vec4f;
import com.github.cuixudong74.softwarerasterizerjava.render.pipeline.RenderConfig;
import com.github.cuixudong74.softwarerasterizerjava.render.pipeline.Renderer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.cuixudong74.softwarerasterizerjava.render.TestScenes.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that frustum clipping preserves visible results: a triangle crossing the near
 * plane must produce exactly the same image as its manually clipped equivalent, and no
 * visible part may be lost by clipping.
 */
class ClipConsistencyTest {

    private static final int W = 96, H = 96;
    private static final double NEAR = 1.0;

    private static Framebuffer render(Mesh mesh) {
        Framebuffer fb = new Framebuffer(W, H);
        new Renderer().render(mesh, Mat4f.identity(), defaultCamera(), null, fb,
                RenderConfig.defaults().withThreads(1).withTiles(1, 1));
        return fb;
    }

    /** Point on the segment from->to where z = -NEAR (independent test-side clip). */
    private static Vec3f intersectNear(Vec3f from, Vec3f to) {
        double s = (-NEAR - from.z()) / (to.z() - from.z());
        return new Vec3f(
                (float) (from.x() + (to.x() - from.x()) * s),
                (float) (from.y() + (to.y() - from.y()) * s),
                (float) (from.z() + (to.z() - from.z()) * s));
    }

    /** Parameter s of the near-plane intersection on the segment from->to. */
    private static double nearParam(Vec3f from, Vec3f to) {
        return (-NEAR - from.z()) / (to.z() - from.z());
    }

    @Test
    void clippedTriangleMatchesManuallyClippedEquivalent() {
        // A, B inside (z=-2), C in front of the near plane (z=-0.5): clipped away.
        Vec3f a = new Vec3f(-0.6f, -0.5f, -2f);
        Vec3f b = new Vec3f(0.6f, -0.5f, -2f);
        Vec3f c = new Vec3f(0f, 0.7f, -0.5f);
        Mesh original = mesh(List.of(
                v(a.x(), a.y(), a.z(), RED), v(b.x(), b.y(), b.z(), RED), v(c.x(), c.y(), c.z(), RED)),
                0, 1, 2);

        // Manual clip: intersection points on edges c->a and c->b at z = -near.
        Vec3f i1 = intersectNear(c, a); // on segment c-a
        Vec3f i2 = intersectNear(c, b); // on segment c-b
        assertEquals(-NEAR, i1.z(), 1e-5);
        assertEquals(-NEAR, i2.z(), 1e-5);
        // Clipped quad (a, b, i2, i1) as two triangles, same winding as the original.
        Mesh manual = mesh(List.of(
                v(a.x(), a.y(), a.z(), RED), v(b.x(), b.y(), b.z(), RED),
                v(i2.x(), i2.y(), i2.z(), RED), v(i1.x(), i1.y(), i1.z(), RED)),
                0, 1, 2, 0, 2, 3);

        Framebuffer fromClipping = render(original);
        Framebuffer fromManual = render(manual);
        int covered = 0;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                assertEquals(fromManual.colorAt(x, y), fromClipping.colorAt(x, y),
                        "clipped render differs from manual clip at (" + x + "," + y + ")");
                // Depth may differ by a few ULPs: the clipper computes intersections in
                // clip-space floats while this test computes them in view-space doubles.
                assertEquals(fromManual.depthAt(x, y), fromClipping.depthAt(x, y), 1e-6f,
                        "depth differs at (" + x + "," + y + ")");
                if (fromClipping.colorAt(x, y) != 0xFF000000) covered++;
            }
        }
        assertTrue(covered > 0, "visible part of the crossing triangle must not be discarded");
    }

    @Test
    void attributeInterpolationAcrossClipBoundaryIsContinuous() {
        // Color gradient along the triangle; the clipped render must equal the manual one
        // including interpolated attributes at the clip boundary.
        Vec3f a = new Vec3f(-0.6f, -0.5f, -2f);
        Vec3f b = new Vec3f(0.6f, -0.5f, -2f);
        Vec3f c = new Vec3f(0f, 0.7f, -0.5f);
        Vec4f ca = new Vec4f(1, 0, 0, 1);
        Vec4f cb = new Vec4f(0, 1, 0, 1);
        Vec4f cc = new Vec4f(0, 0, 1, 1);
        Mesh original = mesh(List.of(
                v(a.x(), a.y(), a.z(), ca), v(b.x(), b.y(), b.z(), cb), v(c.x(), c.y(), c.z(), cc)),
                0, 1, 2);

        Vec3f i1 = intersectNear(c, a);
        Vec3f i2 = intersectNear(c, b);
        // Attribute at the intersection: lerp with the same parameter used for position.
        Vec4f ci1 = lerp(cc, ca, (float) nearParam(c, a));
        Vec4f ci2 = lerp(cc, cb, (float) nearParam(c, b));
        Mesh manual = mesh(List.of(
                v(a.x(), a.y(), a.z(), ca), v(b.x(), b.y(), b.z(), cb),
                v(i2.x(), i2.y(), i2.z(), ci2), v(i1.x(), i1.y(), i1.z(), ci1)),
                0, 1, 2, 0, 2, 3);

        Framebuffer fromClipping = render(original);
        Framebuffer fromManual = render(manual);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                assertEquals(fromManual.colorAt(x, y), fromClipping.colorAt(x, y),
                        "attribute mismatch at (" + x + "," + y + ")");
            }
        }
    }

    private static Vec4f lerp(Vec4f from, Vec4f to, float t) {
        return new Vec4f(
                from.x() + (to.x() - from.x()) * t,
                from.y() + (to.y() - from.y()) * t,
                from.z() + (to.z() - from.z()) * t,
                from.w() + (to.w() - from.w()) * t);
    }

    @Test
    void triangleFullyBehindNearPlaneProducesNoPixels() {
        Mesh behind = mesh(List.of(
                v(-0.5f, -0.5f, -0.2f, RED), v(0.5f, -0.5f, -0.2f, RED), v(0f, 0.5f, -0.2f, RED)),
                0, 1, 2);
        assertEquals(0, countNonBackground(render(behind), 0xFF000000));
    }

    @Test
    void triangleBehindCameraIsNotProjectedThroughOrigin() {
        // A triangle fully behind the camera (z > 0) must not appear mirrored on screen.
        Mesh behind = mesh(List.of(
                v(-0.5f, -0.5f, 2f, RED), v(0.5f, -0.5f, 2f, RED), v(0f, 0.5f, 2f, RED)),
                0, 1, 2);
        assertEquals(0, countNonBackground(render(behind), 0xFF000000));
    }
}

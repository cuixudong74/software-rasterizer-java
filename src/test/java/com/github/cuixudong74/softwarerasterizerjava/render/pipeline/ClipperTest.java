package com.github.cuixudong74.softwarerasterizerjava.render.pipeline;

import com.github.cuixudong74.softwarerasterizerjava.render.math.Vec4f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClipperTest {

    private static final float EPS = 1e-6f;

    private static Clipper.ClipVertex cv(float x, float y, float z, float w,
                                         float r, float g, float b, float u, float v) {
        return new Clipper.ClipVertex(new Vec4f(x, y, z, w), new Vec4f(r, g, b, 1), u, v);
    }

    @Test
    void fullyInsideTriangleIsUnchanged() {
        Clipper.ClipVertex a = cv(-0.5f, -0.5f, 0f, 1f, 1, 0, 0, 0, 0);
        Clipper.ClipVertex b = cv(0.5f, -0.5f, 0f, 1f, 0, 1, 0, 1, 0);
        Clipper.ClipVertex c = cv(0f, 0.5f, 0f, 1f, 0, 0, 1, 0.5f, 1);
        List<Clipper.ClipVertex> out = Clipper.clipTriangle(a, b, c);
        assertEquals(3, out.size());
    }

    @Test
    void fullyOutsideNearPlaneIsDropped() {
        // All vertices behind the near plane: z < -w.
        Clipper.ClipVertex a = cv(-0.5f, -0.5f, -2f, 1f, 1, 0, 0, 0, 0);
        Clipper.ClipVertex b = cv(0.5f, -0.5f, -2f, 1f, 0, 1, 0, 1, 0);
        Clipper.ClipVertex c = cv(0f, 0.5f, -2f, 1f, 0, 0, 1, 0.5f, 1);
        assertTrue(Clipper.clipTriangle(a, b, c).isEmpty());
    }

    @Test
    void crossingNearPlaneSplitsAndInterpolatesAttributes() {
        // Edge a->c crosses the near plane z = -w. a is inside (z=0, w=1),
        // c is outside (z=-2, w=1). Intersection at t=0.5: z=-1, w=1.
        Clipper.ClipVertex a = cv(-0.5f, -0.5f, 0f, 1f, 1, 0, 0, 0f, 0f);
        Clipper.ClipVertex b = cv(0.5f, -0.5f, 0f, 1f, 0, 1, 0, 1f, 0f);
        Clipper.ClipVertex c = cv(0f, 0.5f, -2f, 1f, 0, 0, 1, 0.5f, 1f);
        List<Clipper.ClipVertex> out = Clipper.clipTriangle(a, b, c);
        assertEquals(4, out.size(), "triangle crossing one plane becomes a quad");
        for (Clipper.ClipVertex v : out) {
            assertTrue(v.clipPos().z() >= -v.clipPos().w() - EPS,
                    "every emitted vertex must satisfy the near-plane inequality");
        }
        // Find the vertex lying exactly on the near plane that came from edge a->c.
        boolean foundInterpolated = false;
        for (Clipper.ClipVertex v : out) {
            if (Math.abs(v.clipPos().z() + v.clipPos().w()) < EPS && v.clipPos().x() < 0) {
                // Halfway between a (red, u=0) and c (blue, u=0.5): color and uv are lerped.
                assertEquals(0.5f, v.color().x(), EPS); // red channel halfway
                assertEquals(0.5f, v.color().z(), EPS); // blue channel halfway
                assertEquals(0.25f, v.u(), EPS);
                assertEquals(0.5f, v.v(), EPS);
                foundInterpolated = true;
            }
        }
        assertTrue(foundInterpolated, "expected an interpolated vertex on the near plane");
    }

    @Test
    void triangleSpanningWholeFrustumStillProducesVertices() {
        // Giant triangle enclosing the whole frustum near plane.
        Clipper.ClipVertex a = cv(-100f, -100f, 0f, 1f, 1, 0, 0, 0, 0);
        Clipper.ClipVertex b = cv(100f, -100f, 0f, 1f, 0, 1, 0, 1, 0);
        Clipper.ClipVertex c = cv(0f, 100f, 0f, 1f, 0, 0, 1, 0.5f, 1);
        List<Clipper.ClipVertex> out = Clipper.clipTriangle(a, b, c);
        assertFalse(out.isEmpty());
        for (Clipper.ClipVertex v : out) {
            Vec4f p = v.clipPos();
            assertTrue(p.x() >= -p.w() - EPS && p.x() <= p.w() + EPS);
            assertTrue(p.y() >= -p.w() - EPS && p.y() <= p.w() + EPS);
            assertTrue(p.z() >= -p.w() - EPS && p.z() <= p.w() + EPS);
        }
    }

    @Test
    void clippingIsDeterministic() {
        Clipper.ClipVertex a = cv(-2f, -0.5f, -0.5f, 1f, 1, 0, 0, 0, 0);
        Clipper.ClipVertex b = cv(0.5f, -0.5f, 0.5f, 1f, 0, 1, 0, 1, 0);
        Clipper.ClipVertex c = cv(0f, 3f, 0.2f, 1f, 0, 0, 1, 0.5f, 1);
        List<Clipper.ClipVertex> first = Clipper.clipTriangle(a, b, c);
        for (int i = 0; i < 100; i++) {
            List<Clipper.ClipVertex> again = Clipper.clipTriangle(a, b, c);
            assertEquals(first.size(), again.size());
            for (int k = 0; k < first.size(); k++) {
                assertEquals(first.get(k).clipPos(), again.get(k).clipPos());
            }
        }
    }
}

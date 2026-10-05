package com.github.cuixudong74.softwarerasterizerjava.raster.pipeline;

import com.github.cuixudong74.softwarerasterizerjava.raster.math.Vec2f;
import com.github.cuixudong74.softwarerasterizerjava.raster.math.Vec3f;
import com.github.cuixudong74.softwarerasterizerjava.raster.math.Vec4f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Direct unit tests for the Sutherland-Hodgman clipper. */
class ClipperUnitTest {

    private static ClipVertex cv(double x, double y, double z, double w) {
        return new ClipVertex(new Vec4f(x, y, z, w), new Vec3f(x, y, z), new Vec2f(x, y));
    }

    @Test
    void triangleFullyInsideIsUntouched() {
        List<ClipVertex> out = Clipper.clipTriangle(
                cv(-0.5, -0.5, 0, 1), cv(0.5, -0.5, 0, 1), cv(0, 0.5, 0, 1));
        assertEquals(3, out.size());
    }

    @Test
    void triangleFullyOutsideIsDropped() {
        List<ClipVertex> out = Clipper.clipTriangle(
                cv(2, 2, 0, 1), cv(3, 2, 0, 1), cv(2, 3, 0, 1)); // all x > w
        assertTrue(out.isEmpty());
    }

    @Test
    void crossingTriangleIsSplitAndAttributesInterpolated() {
        // One vertex beyond +x plane; expect a quad with boundary vertices on x == w.
        ClipVertex inside1 = cv(-0.5, -0.5, 0, 1);
        ClipVertex inside2 = cv(-0.5, 0.5, 0, 1);
        ClipVertex outside = cv(2.0, 0.0, 0, 1);
        List<ClipVertex> out = Clipper.clipTriangle(inside1, inside2, outside);
        assertEquals(4, out.size(), "one vertex clipped away must produce a quad");
        for (ClipVertex v : out) {
            assertTrue(v.position.x <= v.position.w + 1e-12);
        }
        // Boundary vertices: halfway between inside (x=-0.5) and outside (x=2) -> x = 1 = w.
        long boundary = out.stream().filter(v -> Math.abs(v.position.x - v.position.w) < 1e-9).count();
        assertEquals(2, boundary);
        // Attribute interpolation: color.x was set equal to position x.
        for (ClipVertex v : out) {
            assertEquals(v.position.x, v.color.x, 1e-12);
        }
    }

    @Test
    void viewSpaceNearClipKeepsOnlyFrontGeometry() {
        // near = 1: inside iff z <= -1.
        List<ClipVertex> out = Clipper.clipViewSpaceNear(
                cv(0, 0, -2, 1), cv(1, 0, -2, 1), cv(0, 1, -0.5, 1.0), 1.0);
        assertEquals(4, out.size());
        for (ClipVertex v : out) {
            assertTrue(v.position.z <= -1.0 + 1e-12);
        }
        // Fully behind the near plane -> empty.
        List<ClipVertex> gone = Clipper.clipViewSpaceNear(
                cv(0, 0, -0.5, 1), cv(1, 0, -0.5, 1), cv(0, 1, -0.5, 1.0), 1.0);
        assertTrue(gone.isEmpty());
    }
}

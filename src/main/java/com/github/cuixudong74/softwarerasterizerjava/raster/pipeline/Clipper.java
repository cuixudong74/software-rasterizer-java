package com.github.cuixudong74.softwarerasterizerjava.raster.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Sutherland-Hodgman polygon clipping.
 *
 * Two stages are used by the pipeline:
 *  - view-space near-plane clipping (before projection), which guarantees every
 *    surviving vertex has view z <= -near, hence clip w > 0 after projection.
 *    This avoids the w<0 sign-flip pitfalls of clipping purely in clip space.
 *  - clip-space clipping against the remaining 5 frustum planes
 *    (OpenGL conventions: -w <= x,y <= w, z <= w). The near plane (z >= -w) is
 *    implied by the view-space near clip, so it is skipped there to avoid
 *    double-clipping floating-point noise perturbing the geometry.
 *
 * Attributes of newly created boundary vertices are linearly interpolated,
 * which is exact because the perspective divide happens after clipping.
 */
final class Clipper {
    private Clipper() {}

    /** Plane index 0..5 -> +x, -x, +y, -y, +z(far), -z(near). */
    private static double clipDistance(ClipVertex v, int plane) {
        double x = v.position.x, y = v.position.y, z = v.position.z, w = v.position.w;
        return switch (plane) {
            case 0 -> w - x;
            case 1 -> w + x;
            case 2 -> w - y;
            case 3 -> w + y;
            case 4 -> w - z;
            case 5 -> w + z;
            default -> throw new IllegalArgumentException("plane " + plane);
        };
    }

    /**
     * Clip a triangle given in view space (position.w == 1) against the near
     * plane z <= -near. Returns a convex polygon; empty means fully outside.
     */
    static List<ClipVertex> clipViewSpaceNear(ClipVertex a, ClipVertex b, ClipVertex c, double near) {
        List<ClipVertex> poly = new ArrayList<>(List.of(a, b, c));
        return clipAgainstPlane(poly, v -> -near - v.position.z);
    }

    /**
     * Clip a triangle in clip space against the 5 frustum planes that are not
     * already enforced by the view-space near clip (i.e. all but z >= -w).
     * Returns a convex polygon; empty list means fully outside.
     */
    static List<ClipVertex> clipTriangleFrustum(ClipVertex a, ClipVertex b, ClipVertex c) {
        List<ClipVertex> poly = new ArrayList<>(List.of(a, b, c));
        for (int plane = 0; plane <= 4 && !poly.isEmpty(); plane++) {
            final int p = plane;
            poly = clipAgainstPlane(poly, v -> clipDistance(v, p));
        }
        return poly;
    }

    /** Clip against all 6 planes; used by unit tests. */
    static List<ClipVertex> clipTriangle(ClipVertex a, ClipVertex b, ClipVertex c) {
        List<ClipVertex> poly = new ArrayList<>(List.of(a, b, c));
        for (int plane = 0; plane < 6 && !poly.isEmpty(); plane++) {
            final int p = plane;
            poly = clipAgainstPlane(poly, v -> clipDistance(v, p));
        }
        return poly;
    }

    /** One Sutherland-Hodgman pass; inside iff distance >= 0. */
    private static List<ClipVertex> clipAgainstPlane(List<ClipVertex> poly,
                                                     ToDoubleFunction<ClipVertex> distance) {
        List<ClipVertex> out = new ArrayList<>(poly.size() + 1);
        int n = poly.size();
        for (int i = 0; i < n; i++) {
            ClipVertex cur = poly.get(i);
            ClipVertex nxt = poly.get((i + 1) % n);
            double dCur = distance.applyAsDouble(cur);
            double dNxt = distance.applyAsDouble(nxt);
            boolean curIn = dCur >= 0.0;
            boolean nxtIn = dNxt >= 0.0;
            if (curIn) {
                out.add(cur);
            }
            if (curIn != nxtIn) {
                double denom = dCur - dNxt;
                double t = denom != 0.0 ? dCur / denom : 0.0;
                out.add(cur.lerp(nxt, t));
            }
        }
        return out;
    }
}

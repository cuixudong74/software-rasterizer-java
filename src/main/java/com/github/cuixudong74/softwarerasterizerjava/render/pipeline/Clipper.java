package com.github.cuixudong74.softwarerasterizerjava.render.pipeline;

import com.github.cuixudong74.softwarerasterizerjava.render.math.Vec4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Sutherland-Hodgman clipping of triangles in homogeneous clip space against the
 * canonical view volume (-w <= x,y,z <= w). Attributes are interpolated linearly
 * in clip space, which is exact at clip-plane intersections, so clipping a triangle
 * and then rasterizing yields the same visible result as rasterizing the unclipped
 * triangle with per-pixel clipping.
 */
final class Clipper {

    private Clipper() {
    }

    /** A vertex in homogeneous clip space with its (not yet perspective-divided) attributes. */
    record ClipVertex(Vec4f clipPos, Vec4f color, float u, float v) {

        ClipVertex lerp(ClipVertex o, float t) {
            return new ClipVertex(
                    lerp4(clipPos, o.clipPos, t),
                    lerp4(color, o.color, t),
                    u + (o.u - u) * t,
                    v + (o.v - v) * t);
        }

        private static Vec4f lerp4(Vec4f a, Vec4f b, float t) {
            return new Vec4f(
                    a.x() + (b.x() - a.x()) * t,
                    a.y() + (b.y() - a.y()) * t,
                    a.z() + (b.z() - a.z()) * t,
                    a.w() + (b.w() - a.w()) * t);
        }
    }

    // The 6 half-spaces of the canonical view volume, as signed "distance" functions
    // (positive = inside). plane i keeps points where signedDistance >= 0.
    private static float signedDistance(int plane, Vec4f p) {
        return switch (plane) {
            case 0 -> p.w() + p.x();  // x >= -w
            case 1 -> p.w() - p.x();  // x <=  w
            case 2 -> p.w() + p.y();  // y >= -w
            case 3 -> p.w() - p.y();  // y <=  w
            case 4 -> p.w() + p.z();  // z >= -w  (near plane)
            case 5 -> p.w() - p.z();  // z <=  w  (far plane)
            default -> throw new IllegalArgumentException();
        };
    }

    /**
     * Clip one triangle against all 6 frustum planes.
     *
     * @return convex polygon of 0..N vertices (fan-triangulatable), empty if fully outside
     */
    static List<ClipVertex> clipTriangle(ClipVertex a, ClipVertex b, ClipVertex c) {
        List<ClipVertex> poly = new ArrayList<>(List.of(a, b, c));
        List<ClipVertex> out = new ArrayList<>(8);
        for (int plane = 0; plane < 6 && !poly.isEmpty(); plane++) {
            out.clear();
            int n = poly.size();
            for (int i = 0; i < n; i++) {
                ClipVertex cur = poly.get(i);
                ClipVertex prev = poly.get((i + n - 1) % n);
                float dCur = signedDistance(plane, cur.clipPos());
                float dPrev = signedDistance(plane, prev.clipPos());
                boolean inCur = dCur >= 0f;
                boolean inPrev = dPrev >= 0f;
                if (inCur != inPrev) {
                    // Edge crosses the plane: emit the intersection point.
                    float t = dPrev / (dPrev - dCur);
                    out.add(prev.lerp(cur, t));
                }
                if (inCur) {
                    out.add(cur);
                }
            }
            // Swap buffers.
            List<ClipVertex> tmp = poly;
            poly = new ArrayList<>(out);
            out = tmp;
        }
        return poly;
    }
}

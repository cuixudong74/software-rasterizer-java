package com.github.cuixudong74.softwarerasterizerjava.raster.pipeline;

import com.github.cuixudong74.softwarerasterizerjava.raster.core.Framebuffer;
import com.github.cuixudong74.softwarerasterizerjava.raster.core.Texture;

/**
 * Triangle fill.
 *
 * Fill rule (documented, deterministic): pixels are sampled at their centers
 * (x+0.5, y+0.5). A pixel is covered iff it is strictly inside every edge, or
 * lies exactly on a "top-left" edge. With the winding normalized so the
 * interior is on the positive side of each edge function, an edge A->B is
 * top-left iff (B.y - A.y > 0) || (B.y == A.y && B.x - A.x < 0) (y grows
 * downward on screen). This guarantees shared edges between adjacent
 * triangles are rasterized exactly once: no gaps, no double coverage.
 *
 * Depth: NDC z in [-1,1] is interpolated linearly in screen space (which is
 * perspective-correct for z/w). Depth test is LESS; the buffer clears to 1.0.
 *
 * Attributes: color and uv are interpolated perspective-correctly by
 * linearly interpolating attr/w and 1/w, then dividing.
 */
final class Rasterizer {
    private Rasterizer() {}

    /** Vertex after perspective divide and viewport mapping. */
    static final class ScreenVertex {
        double x, y, z, invW;
        double rOverW, gOverW, bOverW;
        double uOverW, vOverW;
    }

    /**
     * Rasterize one triangle, restricted to the pixel rectangle
     * [minX,maxX) x [minY,maxY). The caller guarantees tiles partition the
     * framebuffer, so no two threads ever write the same pixel.
     */
    static void rasterize(Framebuffer fb, ScreenVertex[] tri, Texture texture,
                          RenderConfig config, int minX, int minY, int maxX, int maxY) {
        ScreenVertex v0 = tri[0], v1 = tri[1], v2 = tri[2];

        double area2 = edge(v0.x, v0.y, v1.x, v1.y, v2.x, v2.y);
        if (area2 == 0.0 || !Double.isFinite(area2)) {
            return; // degenerate or numerically broken triangle
        }
        // Normalize winding so the interior is on the positive side.
        if (area2 < 0.0) {
            ScreenVertex t = v1;
            v1 = v2;
            v2 = t;
            area2 = -area2;
        }

        // Triangle bounding box clipped to the tile and framebuffer.
        double minFx = Math.min(v0.x, Math.min(v1.x, v2.x));
        double maxFx = Math.max(v0.x, Math.max(v1.x, v2.x));
        double minFy = Math.min(v0.y, Math.min(v1.y, v2.y));
        double maxFy = Math.max(v0.y, Math.max(v1.y, v2.y));
        int x0 = clamp((int) Math.floor(minFx), minX, maxX);
        int x1 = clamp((int) Math.ceil(maxFx), minX, maxX);
        int y0 = clamp((int) Math.floor(minFy), minY, maxY);
        int y1 = clamp((int) Math.ceil(maxFy), minY, maxY);
        if (x0 >= x1 || y0 >= y1) return;

        boolean tl0 = isTopLeft(v0, v1);
        boolean tl1 = isTopLeft(v1, v2);
        boolean tl2 = isTopLeft(v2, v0);

        int[] color = fb.colorBuffer();
        double[] depth = fb.depthBuffer();
        int width = fb.width();
        double invArea = 1.0 / area2;

        for (int py = y0; py < y1; py++) {
            double cy = py + 0.5;
            for (int px = x0; px < x1; px++) {
                double cx = px + 0.5;
                double e0 = edge(v0.x, v0.y, v1.x, v1.y, cx, cy);
                if (e0 < 0.0 || (e0 == 0.0 && !tl0)) continue;
                double e1 = edge(v1.x, v1.y, v2.x, v2.y, cx, cy);
                if (e1 < 0.0 || (e1 == 0.0 && !tl1)) continue;
                double e2 = edge(v2.x, v2.y, v0.x, v0.y, cx, cy);
                if (e2 < 0.0 || (e2 == 0.0 && !tl2)) continue;

                // Barycentric weights: w0 opposite v0 etc.
                double w0 = e1 * invArea;
                double w1 = e2 * invArea;
                double w2 = e0 * invArea;

                double z = w0 * v0.z + w1 * v1.z + w2 * v2.z;
                int idx = py * width + px;
                if (config.depthTest) {
                    if (!(z < depth[idx])) continue; // LESS test; also rejects NaN
                }

                double invW = w0 * v0.invW + w1 * v1.invW + w2 * v2.invW;
                if (invW == 0.0 || !Double.isFinite(invW)) continue;
                double w = 1.0 / invW;

                double r = (w0 * v0.rOverW + w1 * v1.rOverW + w2 * v2.rOverW) * w;
                double g = (w0 * v0.gOverW + w1 * v1.gOverW + w2 * v2.gOverW) * w;
                double b = (w0 * v0.bOverW + w1 * v1.bOverW + w2 * v2.bOverW) * w;

                int argb;
                if (texture != null) {
                    double u = (w0 * v0.uOverW + w1 * v1.uOverW + w2 * v2.uOverW) * w;
                    double vv = (w0 * v0.vOverW + w1 * v1.vOverW + w2 * v2.vOverW) * w;
                    argb = modulate(texture.sample(u, vv), r, g, b);
                } else {
                    argb = toArgb(r, g, b);
                }
                color[idx] = argb;
                depth[idx] = z;
            }
        }
    }

    private static double edge(double ax, double ay, double bx, double by, double px, double py) {
        return (px - ax) * (by - ay) - (py - ay) * (bx - ax);
    }

    /** Top-left test for edge A->B with interior on the positive side, y down. */
    private static boolean isTopLeft(ScreenVertex a, ScreenVertex b) {
        double dy = b.y - a.y;
        double dx = b.x - a.x;
        return dy > 0.0 || (dy == 0.0 && dx < 0.0);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static int toArgb(double r, double g, double b) {
        return 0xFF000000 | (toByte(r) << 16) | (toByte(g) << 8) | toByte(b);
    }

    private static int toByte(double c) {
        if (!(c > 0.0)) return 0;      // also catches NaN
        if (c >= 1.0) return 255;
        return (int) (c * 255.0 + 0.5);
    }

    /** Multiply texture texel RGB by interpolated vertex color (both in [0,1]). */
    private static int modulate(int texel, double r, double g, double b) {
        int tr = (texel >> 16) & 0xFF;
        int tg = (texel >> 8) & 0xFF;
        int tb = texel & 0xFF;
        int rr = (int) (tr * clamp01(r) + 0.5);
        int gg = (int) (tg * clamp01(g) + 0.5);
        int bb = (int) (tb * clamp01(b) + 0.5);
        return 0xFF000000 | (Math.min(rr, 255) << 16) | (Math.min(gg, 255) << 8) | Math.min(bb, 255);
    }

    private static double clamp01(double c) {
        if (!(c > 0.0)) return 0.0;
        return Math.min(c, 1.0);
    }
}

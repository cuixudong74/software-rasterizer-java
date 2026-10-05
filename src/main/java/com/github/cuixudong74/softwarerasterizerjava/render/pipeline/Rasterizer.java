package com.github.cuixudong74.softwarerasterizerjava.render.pipeline;

import com.github.cuixudong74.softwarerasterizerjava.render.core.Framebuffer;
import com.github.cuixudong74.softwarerasterizerjava.render.core.Texture;

/**
 * Tile-bounded triangle rasterizer.
 *
 * <p>Fill rule (top-left rule, evaluated at pixel centers): a pixel is covered iff its
 * center lies strictly inside the triangle, or lies exactly on an edge that is a
 * "top" edge (horizontal, interior below it) or a "left" edge (interior to its right,
 * i.e. the edge points downward in screen space). For consistently wound meshes a shared
 * edge is top-left in exactly one of the two adjacent triangles, so every pixel on the
 * shared edge is covered exactly once: no gaps, no double coverage.
 *
 * <p>Depth is interpolated as NDC z (linear in screen space, perspective-correct by
 * construction) and mapped to [0,1]; colors and UVs are interpolated with full
 * perspective correction via 1/w.
 */
final class Rasterizer {

    private Rasterizer() {
    }

    /** Screen-space vertex ready for interpolation. */
    private static final class ScreenVert {
        double x, y;     // pixel coordinates
        double depth;    // [0,1], 0 = near
        double invW;     // 1 / w_clip
        double rOverW, gOverW, bOverW, aOverW;
        double uOverW, vOverW;
    }

    /**
     * Rasterize one clipped triangle (in clip space) into the framebuffer, restricted to
     * the pixel rectangle [x0,x1) x [y0,y1). Only writes pixels inside that rectangle,
     * so disjoint tiles never touch the same pixel and results are thread-count independent.
     */
    static void rasterize(Framebuffer fb, Clipper.ClipVertex a, Clipper.ClipVertex b, Clipper.ClipVertex c,
                          Texture texture, RenderConfig config,
                          int x0, int y0, int x1, int y1) {
        ScreenVert v0 = project(a, fb.width(), fb.height());
        ScreenVert v1 = project(b, fb.width(), fb.height());
        ScreenVert v2 = project(c, fb.width(), fb.height());
        if (v0 == null || v1 == null || v2 == null) {
            return; // measure-zero degenerate (w == 0 after clipping)
        }

        double area = edge(v0, v1, v2.x, v2.y);
        if (area == 0.0) {
            return; // degenerate zero-area triangle (includes collapsed slivers)
        }
        // With this edge-function convention, interior-positive winding corresponds to
        // CCW in screen space (y down), i.e. CW in NDC = a back face by the OpenGL
        // convention (front faces are CCW in NDC and therefore have negative area here).
        if (area > 0) {
            if (config.backfaceCulling()) {
                return; // CW in NDC = back face
            }
            // Two-sided: interior is already on the positive side, rasterize as-is.
        } else {
            // Front face: flip winding so the interior is on the positive side.
            ScreenVert tmp = v1;
            v1 = v2;
            v2 = tmp;
            area = -area;
        }

        // Bounding box clamped to the tile rectangle.
        double minX = Math.min(v0.x, Math.min(v1.x, v2.x));
        double maxX = Math.max(v0.x, Math.max(v1.x, v2.x));
        double minY = Math.min(v0.y, Math.min(v1.y, v2.y));
        double maxY = Math.max(v0.y, Math.max(v1.y, v2.y));
        int ix0 = Math.max(x0, (int) Math.floor(minX));
        int iy0 = Math.max(y0, (int) Math.floor(minY));
        int ix1 = Math.min(x1, (int) Math.ceil(maxX));
        int iy1 = Math.min(y1, (int) Math.ceil(maxY));
        if (ix0 >= ix1 || iy0 >= iy1) {
            return;
        }

        // Top-left classification per edge (edge i is opposite vertex i).
        boolean tl0 = isTopLeft(v1, v2);
        boolean tl1 = isTopLeft(v2, v0);
        boolean tl2 = isTopLeft(v0, v1);

        double invArea = 1.0 / area;
        for (int py = iy0; py < iy1; py++) {
            double cy = py + 0.5;
            for (int px = ix0; px < ix1; px++) {
                double cx = px + 0.5;
                double e0 = edge(v1, v2, cx, cy);
                double e1 = edge(v2, v0, cx, cy);
                double e2 = edge(v0, v1, cx, cy);
                if (!inside(e0, tl0) || !inside(e1, tl1) || !inside(e2, tl2)) {
                    continue;
                }
                double l0 = e0 * invArea;
                double l1 = e1 * invArea;
                double l2 = e2 * invArea;

                double depth = l0 * v0.depth + l1 * v1.depth + l2 * v2.depth;
                int idx = py * fb.width() + px;
                if (config.depthTest() && !(depth < fb.depthAt(px, py))) {
                    continue;
                }

                double invW = l0 * v0.invW + l1 * v1.invW + l2 * v2.invW;
                double w = 1.0 / invW;
                double r = (l0 * v0.rOverW + l1 * v1.rOverW + l2 * v2.rOverW) * w;
                double g = (l0 * v0.gOverW + l1 * v1.gOverW + l2 * v2.gOverW) * w;
                double bl = (l0 * v0.bOverW + l1 * v1.bOverW + l2 * v2.bOverW) * w;
                double al = (l0 * v0.aOverW + l1 * v1.aOverW + l2 * v2.aOverW) * w;

                if (texture != null) {
                    double u = (l0 * v0.uOverW + l1 * v1.uOverW + l2 * v2.uOverW) * w;
                    double v = (l0 * v0.vOverW + l1 * v1.vOverW + l2 * v2.vOverW) * w;
                    int tex = texture.sample((float) u, (float) v, config.addressMode());
                    r *= ((tex >>> 16) & 0xFF) / 255.0;
                    g *= ((tex >>> 8) & 0xFF) / 255.0;
                    bl *= (tex & 0xFF) / 255.0;
                    al *= ((tex >>> 24) & 0xFF) / 255.0;
                }

                fb.writePixel(px, py, toArgb(r, g, bl, al), (float) depth);
            }
        }
    }

    private static ScreenVert project(Clipper.ClipVertex cv, int width, int height) {
        float cw = cv.clipPos().w();
        if (cw == 0f) {
            return null;
        }
        double invW = 1.0 / cw;
        double ndcX = cv.clipPos().x() * invW;
        double ndcY = cv.clipPos().y() * invW;
        double ndcZ = cv.clipPos().z() * invW;
        ScreenVert sv = new ScreenVert();
        sv.x = (ndcX * 0.5 + 0.5) * width;
        sv.y = (0.5 - ndcY * 0.5) * height; // NDC y up -> screen y down
        sv.depth = ndcZ * 0.5 + 0.5;
        sv.invW = invW;
        sv.rOverW = cv.color().x() * invW;
        sv.gOverW = cv.color().y() * invW;
        sv.bOverW = cv.color().z() * invW;
        sv.aOverW = cv.color().w() * invW;
        sv.uOverW = cv.u() * invW;
        sv.vOverW = cv.v() * invW;
        return sv;
    }

    /** Edge function: positive when p is on the interior side of directed edge a->b. */
    private static double edge(ScreenVert a, ScreenVert b, double px, double py) {
        return (b.x - a.x) * (py - a.y) - (b.y - a.y) * (px - a.x);
    }

    /** Top-left edge test (screen space, y down): downward edges and leftward horizontal edges. */
    private static boolean isTopLeft(ScreenVert a, ScreenVert b) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        return dy > 0 || (dy == 0 && dx < 0);
    }

    private static boolean inside(double e, boolean topLeft) {
        return e > 0 || (e == 0 && topLeft);
    }

    private static int toArgb(double r, double g, double b, double a) {
        return (clamp8(a) << 24) | (clamp8(r) << 16) | (clamp8(g) << 8) | clamp8(b);
    }

    private static int clamp8(double c) {
        if (!(c > 0)) {
            return 0; // also catches NaN
        }
        return c >= 1.0 ? 255 : (int) (c * 255.0 + 0.5);
    }
}

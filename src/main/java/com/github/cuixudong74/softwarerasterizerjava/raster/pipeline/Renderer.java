package com.github.cuixudong74.softwarerasterizerjava.raster.pipeline;

import com.github.cuixudong74.softwarerasterizerjava.raster.core.Camera;
import com.github.cuixudong74.softwarerasterizerjava.raster.core.Framebuffer;
import com.github.cuixudong74.softwarerasterizerjava.raster.core.Mesh;
import com.github.cuixudong74.softwarerasterizerjava.raster.core.Texture;
import com.github.cuixudong74.softwarerasterizerjava.raster.core.Vertex;
import com.github.cuixudong74.softwarerasterizerjava.raster.math.Mat4f;
import com.github.cuixudong74.softwarerasterizerjava.raster.math.Vec4f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrates the full pipeline:
 * model/view transform -> view-space near clip -> projection ->
 * clip-space frustum clip -> perspective divide -> viewport mapping ->
 * tiled parallel rasterization.
 *
 * Determinism: triangles are emitted in index order; tiles partition the
 * framebuffer so no pixel is ever written by two workers; within a tile,
 * triangles are processed in submission order. The output is therefore
 * bit-identical for any tileSize/threads combination.
 *
 * Atomicity: rendering goes into a private temporary target and is only
 * copied into the caller's framebuffer on success. Exceptions, interruption
 * or timeout leave the caller's framebuffer untouched and shut the worker
 * pool down.
 */
public final class Renderer {
    /** Clear color (ARGB) applied at the start of every render call. */
    public static final int CLEAR_COLOR = 0xFF000000;
    /** Cleared depth value; NDC z far plane. */
    public static final double CLEAR_DEPTH = 1.0;

    private final RenderConfig config;

    public Renderer(RenderConfig config) {
        if (config == null) throw new RenderException("config must not be null");
        config.validate();
        this.config = config;
    }

    /**
     * Render a mesh into the framebuffer. The framebuffer is cleared first.
     * Identical inputs and config produce a bit-identical framebuffer
     * regardless of thread count or tile size.
     */
    public void render(Framebuffer fb, Mesh mesh, Mat4f model, Camera camera, Texture texture) {
        if (fb == null || mesh == null || model == null || camera == null) {
            throw new RenderException("framebuffer, mesh, model and camera must not be null");
        }
        mesh.validate();
        camera.validate();
        checkMatrixFinite(model);

        Mat4f modelView = camera.viewMatrix().multiply(model);
        Mat4f projection = camera.projectionMatrix();

        // Phase 1 (sequential): geometry processing, emits screen-space triangles.
        List<Rasterizer.ScreenVertex[]> screenTris =
                buildScreenTriangles(mesh, modelView, projection, camera.near,
                        fb.width(), fb.height());

        // Phase 2: render into a private target, publish only on success.
        Framebuffer tmp = new Framebuffer(fb.width(), fb.height());
        tmp.clear(CLEAR_COLOR, CLEAR_DEPTH);
        rasterizeTiled(tmp, screenTris, texture);

        System.arraycopy(tmp.colorBuffer(), 0, fb.colorBuffer(), 0, fb.colorBuffer().length);
        System.arraycopy(tmp.depthBuffer(), 0, fb.depthBuffer(), 0, fb.depthBuffer().length);
    }

    private List<Rasterizer.ScreenVertex[]> buildScreenTriangles(
            Mesh mesh, Mat4f modelView, Mat4f projection, double near, int width, int height) {
        List<Rasterizer.ScreenVertex[]> out = new ArrayList<>();
        int[] indices = mesh.indices();
        List<Vertex> verts = mesh.vertices();
        for (int t = 0; t < indices.length; t += 3) {
            ClipVertex a = toViewSpace(modelView, verts.get(indices[t]), indices[t]);
            ClipVertex b = toViewSpace(modelView, verts.get(indices[t + 1]), indices[t + 1]);
            ClipVertex c = toViewSpace(modelView, verts.get(indices[t + 2]), indices[t + 2]);

            List<ClipVertex> visible = Clipper.clipViewSpaceNear(a, b, c, near);
            if (visible.size() < 3) continue;

            List<ClipVertex> projected = new ArrayList<>(visible.size());
            for (ClipVertex v : visible) {
                Vec4f clip = projection.multiply(v.position);
                checkFinite("clip-space position", clip.x, clip.y, clip.z, clip.w);
                projected.add(new ClipVertex(clip, v.color, v.uv));
            }

            // Fan-triangulate the clipped polygon and clip each sub-triangle
            // against the remaining frustum planes in clip space.
            for (int i = 1; i + 1 < projected.size(); i++) {
                List<ClipVertex> clipped = Clipper.clipTriangleFrustum(
                        projected.get(0), projected.get(i), projected.get(i + 1));
                if (clipped.size() < 3) continue;
                Rasterizer.ScreenVertex[] sv = new Rasterizer.ScreenVertex[clipped.size()];
                for (int k = 0; k < clipped.size(); k++) {
                    sv[k] = toScreen(clipped.get(k), width, height);
                }
                for (int k = 1; k + 1 < sv.length; k++) {
                    Rasterizer.ScreenVertex[] tri = {sv[0], sv[k], sv[k + 1]};
                    if (config.backfaceCulling && signedArea2(tri) <= 0.0) continue;
                    out.add(tri);
                }
            }
        }
        return out;
    }

    private void rasterizeTiled(Framebuffer fb, List<Rasterizer.ScreenVertex[]> tris,
                                Texture texture) {
        int w = fb.width();
        int h = fb.height();
        int ts = config.tileSize;
        int tilesX = (w + ts - 1) / ts;
        int tilesY = (h + ts - 1) / ts;

        // Bin triangles into tiles by bounding box; bin order = submission order.
        @SuppressWarnings("unchecked")
        List<Integer>[] bins = new List[tilesX * tilesY];
        for (int i = 0; i < tris.size(); i++) {
            Rasterizer.ScreenVertex[] tri = tris.get(i);
            double minX = min3(tri[0].x, tri[1].x, tri[2].x);
            double maxX = max3(tri[0].x, tri[1].x, tri[2].x);
            double minY = min3(tri[0].y, tri[1].y, tri[2].y);
            double maxY = max3(tri[0].y, tri[1].y, tri[2].y);
            int tx0 = tileIndex(minX, ts, tilesX);
            int tx1 = tileIndex(maxX, ts, tilesX);
            int ty0 = tileIndex(minY, ts, tilesY);
            int ty1 = tileIndex(maxY, ts, tilesY);
            for (int ty = ty0; ty <= ty1; ty++) {
                for (int tx = tx0; tx <= tx1; tx++) {
                    int b = ty * tilesX + tx;
                    if (bins[b] == null) bins[b] = new ArrayList<>();
                    bins[b].add(i);
                }
            }
        }

        ExecutorService pool = Executors.newFixedThreadPool(config.threads);
        boolean success = false;
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int ty = 0; ty < tilesY; ty++) {
                for (int tx = 0; tx < tilesX; tx++) {
                    List<Integer> bin = bins[ty * tilesX + tx];
                    if (bin == null) continue;
                    final int minX = tx * ts, minY = ty * ts;
                    final int maxX = Math.min(w, minX + ts), maxY = Math.min(h, minY + ts);
                    futures.add(pool.submit(() -> {
                        for (int idx : bin) {
                            Rasterizer.rasterize(fb, tris.get(idx), texture, config,
                                    minX, minY, maxX, maxY);
                        }
                    }));
                }
            }
            pool.shutdown();
            long timeout = config.timeoutMillis > 0 ? config.timeoutMillis : Long.MAX_VALUE;
            if (!pool.awaitTermination(timeout, TimeUnit.MILLISECONDS)) {
                throw new RenderException("render timed out after " + config.timeoutMillis + " ms");
            }
            for (Future<?> f : futures) {
                f.get(); // rethrows any worker failure
            }
            success = true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RenderException("render interrupted");
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RenderException re) throw re;
            throw new RenderException("worker failed: " + cause);
        } finally {
            if (!success) {
                pool.shutdownNow();
            }
        }
    }

    private ClipVertex toViewSpace(Mat4f modelView, Vertex v, int index) {
        Vec4f p = modelView.multiply(new Vec4f(v.position.x, v.position.y, v.position.z, 1.0));
        checkFinite("view-space position of vertex " + index, p.x, p.y, p.z, p.w);
        return new ClipVertex(p, v.color, v.uv);
    }

    private Rasterizer.ScreenVertex toScreen(ClipVertex cv, int width, int height) {
        double cw = cv.position.w;
        if (!(cw > 0.0)) {
            // View-space near clipping guarantees w > 0; this is a hard invariant.
            throw new RenderException("non-positive clip w after clipping: " + cw);
        }
        double invW = 1.0 / cw;
        double ndcX = cv.position.x * invW;
        double ndcY = cv.position.y * invW;
        double ndcZ = cv.position.z * invW;
        double sx = (ndcX + 1.0) * 0.5 * width;
        double sy = (1.0 - (ndcY + 1.0) * 0.5) * height;
        checkFinite("screen coordinate", sx, sy, ndcZ);
        Rasterizer.ScreenVertex sv = new Rasterizer.ScreenVertex();
        sv.x = sx;
        sv.y = sy;
        sv.z = ndcZ;
        sv.invW = invW;
        sv.rOverW = cv.color.x * invW;
        sv.gOverW = cv.color.y * invW;
        sv.bOverW = cv.color.z * invW;
        sv.uOverW = cv.uv.x * invW;
        sv.vOverW = cv.uv.y * invW;
        return sv;
    }

    /** Signed doubled area in screen space; front faces are positive. */
    private static double signedArea2(Rasterizer.ScreenVertex[] tri) {
        return (tri[2].x - tri[0].x) * (tri[1].y - tri[0].y)
             - (tri[2].y - tri[0].y) * (tri[1].x - tri[0].x);
    }

    private static int tileIndex(double coord, int tileSize, int tileCount) {
        int t = (int) Math.floor(coord / tileSize);
        if (t < 0) return 0;
        return Math.min(t, tileCount - 1);
    }

    private static double min3(double a, double b, double c) { return Math.min(a, Math.min(b, c)); }
    private static double max3(double a, double b, double c) { return Math.max(a, Math.max(b, c)); }

    private static void checkFinite(String what, double... values) {
        for (double d : values) {
            if (!Double.isFinite(d)) {
                throw new RenderException(what + " is non-finite: " + d);
            }
        }
    }

    private static void checkMatrixFinite(Mat4f m) {
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                if (!Double.isFinite(m.m[i][j])) {
                    throw new RenderException("model matrix has non-finite entry ["
                            + i + "][" + j + "]=" + m.m[i][j]);
                }
            }
        }
    }
}

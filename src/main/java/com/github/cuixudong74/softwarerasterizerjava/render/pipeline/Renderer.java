package com.github.cuixudong74.softwarerasterizerjava.render.pipeline;

import com.github.cuixudong74.softwarerasterizerjava.render.core.Camera;
import com.github.cuixudong74.softwarerasterizerjava.render.core.Framebuffer;
import com.github.cuixudong74.softwarerasterizerjava.render.core.Mesh;
import com.github.cuixudong74.softwarerasterizerjava.render.core.Texture;
import com.github.cuixudong74.softwarerasterizerjava.render.core.Vertex;
import com.github.cuixudong74.softwarerasterizerjava.render.math.Mat4f;
import com.github.cuixudong74.softwarerasterizerjava.render.math.Vec3f;
import com.github.cuixudong74.softwarerasterizerjava.render.math.Vec4f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Orchestrates the full pipeline: validate -> transform -> clip -> tiled rasterize.
 *
 * <p>Renders into a private buffer and commits to the destination framebuffer only on
 * success, so cancellation/timeout never leaves a half-rendered image and no executor
 * or worker thread outlives the call.
 *
 * <p>Determinism: clipping runs single-threaded in input order; each tile owns a
 * disjoint pixel rectangle and processes the clipped triangles in the same input order,
 * so the output is bit-identical for any thread/tile count.
 */
public final class Renderer {

    /**
     * Render a mesh.
     *
     * @param mesh    input triangles (model space)
     * @param model   model-to-world transform
     * @param camera  view + projection parameters
     * @param texture optional texture (null = vertex colors only)
     * @param dest    destination framebuffer; untouched if the render fails or is cancelled
     * @param config  render configuration
     * @throws InvalidInputException    on NaN/Inf geometry, bad indices, illegal camera/config
     * @throws RenderCancelledException on timeout or thread interruption
     */
    public void render(Mesh mesh, Mat4f model, Camera camera, Texture texture,
                       Framebuffer dest, RenderConfig config) {
        validate(mesh, model, camera, dest, config);

        Mat4f mvp = Transforms.projection(camera, (double) dest.width() / dest.height())
                .mul(Transforms.view(camera))
                .mul(model);

        // Vertex stage + clipping (single-threaded, input order).
        List<Clipper.ClipVertex> tris = new ArrayList<>();
        int[] indices = mesh.indices();
        List<Vertex> verts = mesh.vertices();
        for (int i = 0; i < indices.length; i += 3) {
            Clipper.ClipVertex a = toClip(verts.get(indices[i]), mvp);
            Clipper.ClipVertex b = toClip(verts.get(indices[i + 1]), mvp);
            Clipper.ClipVertex c = toClip(verts.get(indices[i + 2]), mvp);
            List<Clipper.ClipVertex> poly = Clipper.clipTriangle(a, b, c);
            // Fan-triangulate the clipped convex polygon.
            for (int k = 1; k + 1 < poly.size(); k++) {
                tris.add(poly.get(0));
                tris.add(poly.get(k));
                tris.add(poly.get(k + 1));
            }
            if (tris.size() / 3 > config.maxTriangles()) {
                throw new InvalidInputException("clipped triangle count exceeds maxTriangles="
                        + config.maxTriangles() + " (input too large or clipping explosion)");
            }
        }

        // Rasterize into a private buffer; commit only on success.
        Framebuffer work = new Framebuffer(dest.width(), dest.height());
        work.clear(0xFF000000, 1f);
        rasterizeTiled(work, tris, texture, config);
        dest.copyFrom(work);
    }

    private void rasterizeTiled(Framebuffer fb, List<Clipper.ClipVertex> tris,
                                Texture texture, RenderConfig config) {
        int tilesX = Math.min(config.tilesX(), fb.width());
        int tilesY = Math.min(config.tilesY(), fb.height());
        int tileCount = tilesX * tilesY;
        int threads = Math.min(config.threads(), tileCount);

        AtomicBoolean failed = new AtomicBoolean(false);
        AtomicBoolean cancel = new AtomicBoolean(false);
        CountDownLatch done = new CountDownLatch(tileCount);
        ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "raster-worker");
            t.setDaemon(true);
            return t;
        });
        try {
            for (int ty = 0; ty < tilesY; ty++) {
                for (int tx = 0; tx < tilesX; tx++) {
                    final int fx = tx, fy = ty;
                    final int ftx = tilesX, fty = tilesY;
                    pool.execute(() -> {
                        try {
                            int x0 = fx * fb.width() / ftx;
                            int x1 = (fx + 1) * fb.width() / ftx;
                            int y0 = fy * fb.height() / fty;
                            int y1 = (fy + 1) * fb.height() / fty;
                            for (int i = 0; i < tris.size() && !cancel.get(); i += 3) {
                                Rasterizer.rasterize(fb, tris.get(i), tris.get(i + 1), tris.get(i + 2),
                                        texture, config, x0, y0, x1, y1);
                            }
                        } catch (Throwable t) {
                            failed.set(true);
                        } finally {
                            done.countDown();
                        }
                    });
                }
            }
            boolean finished;
            if (config.timeoutMillis() > 0) {
                finished = done.await(config.timeoutMillis(), TimeUnit.MILLISECONDS);
            } else {
                done.await();
                finished = true;
            }
            if (!finished) {
                // Cooperative cancellation: workers check the flag per triangle and exit
                // promptly; shutdownNow in the finally block reclaims the pool.
                cancel.set(true);
                throw new RenderCancelledException(
                        "render timed out after " + config.timeoutMillis() + " ms");
            }
            if (failed.get()) {
                throw new RenderException("a raster worker failed");
            }
        } catch (InterruptedException e) {
            cancel.set(true);
            Thread.currentThread().interrupt();
            throw new RenderCancelledException("render interrupted");
        } finally {
            pool.shutdownNow();
        }
    }

    private static Clipper.ClipVertex toClip(Vertex v, Mat4f mvp) {
        Vec4f clip = mvp.transform(v.position());
        return new Clipper.ClipVertex(clip, v.color(), v.uv().x(), v.uv().y());
    }

    private static void validate(Mesh mesh, Mat4f model, Camera camera, Framebuffer dest, RenderConfig config) {
        if (mesh == null || model == null || camera == null || dest == null || config == null) {
            throw new InvalidInputException("mesh, model, camera, dest and config must be non-null");
        }
        List<Vertex> verts = mesh.vertices();
        int[] indices = mesh.indices();
        if (verts == null || indices == null) {
            throw new InvalidInputException("mesh vertices/indices must be non-null");
        }
        if (indices.length % 3 != 0) {
            throw new InvalidInputException("index count " + indices.length + " is not a multiple of 3");
        }
        if (indices.length / 3 > config.maxTriangles()) {
            throw new InvalidInputException("input triangle count " + indices.length / 3
                    + " exceeds maxTriangles=" + config.maxTriangles());
        }
        for (int i = 0; i < indices.length; i++) {
            if (indices[i] < 0 || indices[i] >= verts.size()) {
                throw new InvalidInputException("index[" + i + "]=" + indices[i]
                        + " out of range [0," + verts.size() + ")");
            }
        }
        for (int i = 0; i < verts.size(); i++) {
            Vertex v = verts.get(i);
            if (v == null) {
                throw new InvalidInputException("vertex[" + i + "] is null");
            }
            checkFinite("vertex[" + i + "].position", v.position().x(), v.position().y(), v.position().z());
            checkFinite("vertex[" + i + "].color", v.color().x(), v.color().y(), v.color().z(), v.color().w());
            checkFinite("vertex[" + i + "].uv", v.uv().x(), v.uv().y());
        }
        checkFinite("camera.eye", camera.eye().x(), camera.eye().y(), camera.eye().z());
        checkFinite("camera.target", camera.target().x(), camera.target().y(), camera.target().z());
        checkFinite("camera.up", camera.up().x(), camera.up().y(), camera.up().z());
        Vec3f dir = camera.target().sub(camera.eye());
        if (dir.dot(dir) == 0f) {
            throw new InvalidInputException("camera.eye equals camera.target: view direction undefined");
        }
        Vec3f fwd = dir.normalize();
        Vec3f cross = fwd.cross(camera.up());
        if (cross.dot(cross) == 0f) {
            throw new InvalidInputException("camera.up is parallel to the view direction");
        }
        if (!Double.isFinite(camera.fovYDegrees()) || camera.fovYDegrees() <= 0 || camera.fovYDegrees() >= 180) {
            throw new InvalidInputException("camera.fovYDegrees must be in (0,180), got " + camera.fovYDegrees());
        }
        if (!Double.isFinite(camera.near()) || camera.near() <= 0) {
            throw new InvalidInputException("camera.near must be positive and finite, got " + camera.near());
        }
        if (!Double.isFinite(camera.far()) || camera.far() <= camera.near()) {
            throw new InvalidInputException("camera.far must be finite and > near, got " + camera.far());
        }
    }

    private static void checkFinite(String what, float... components) {
        for (float c : components) {
            if (!Float.isFinite(c)) {
                throw new InvalidInputException(what + " contains non-finite value " + c);
            }
        }
    }
}

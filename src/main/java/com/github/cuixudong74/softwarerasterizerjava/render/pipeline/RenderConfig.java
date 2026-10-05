package com.github.cuixudong74.softwarerasterizerjava.render.pipeline;

import com.github.cuixudong74.softwarerasterizerjava.render.core.AddressMode;

/**
 * Render configuration.
 *
 * @param threads         worker threads for tiled rasterization (1 = single-threaded)
 * @param tilesX          horizontal tile count for parallel rasterization
 * @param tilesY          vertical tile count for parallel rasterization
 * @param backfaceCulling cull triangles whose screen-space winding is clockwise
 * @param depthTest       enable depth testing
 * @param addressMode     texture addressing for out-of-range UVs
 * @param timeoutMillis   max render time; 0 = no limit. On expiry the render is cancelled
 *                        and the destination framebuffer is left untouched.
 * @param maxTriangles    safety bound on clipped triangle count
 */
public record RenderConfig(int threads, int tilesX, int tilesY, boolean backfaceCulling,
                           boolean depthTest, AddressMode addressMode, long timeoutMillis,
                           int maxTriangles) {

    public RenderConfig {
        if (threads < 1) throw new InvalidInputException("threads must be >= 1, got " + threads);
        if (tilesX < 1 || tilesY < 1) throw new InvalidInputException("tile counts must be >= 1");
        if (timeoutMillis < 0) throw new InvalidInputException("timeoutMillis must be >= 0");
        if (maxTriangles < 1) throw new InvalidInputException("maxTriangles must be >= 1");
        if (addressMode == null) throw new InvalidInputException("addressMode must not be null");
    }

    public static RenderConfig defaults() {
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        return new RenderConfig(cores, 8, 8, true, true, AddressMode.CLAMP, 0, 4_000_000);
    }

    public RenderConfig withThreads(int t) {
        return new RenderConfig(t, tilesX, tilesY, backfaceCulling, depthTest, addressMode, timeoutMillis, maxTriangles);
    }

    public RenderConfig withTiles(int tx, int ty) {
        return new RenderConfig(threads, tx, ty, backfaceCulling, depthTest, addressMode, timeoutMillis, maxTriangles);
    }

    public RenderConfig withTimeout(long millis) {
        return new RenderConfig(threads, tilesX, tilesY, backfaceCulling, depthTest, addressMode, millis, maxTriangles);
    }
}

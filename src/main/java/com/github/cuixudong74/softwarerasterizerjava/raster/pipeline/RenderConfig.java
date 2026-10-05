package com.github.cuixudong74.softwarerasterizerjava.raster.pipeline;

/** Tuning knobs for the renderer. */
public final class RenderConfig {
    /** Side length of the square pixel tiles processed in parallel. */
    public final int tileSize;
    /** Worker thread count for tiled rasterization. */
    public final int threads;
    /** Cull triangles whose screen-space winding is not front-facing. */
    public final boolean backfaceCulling;
    /** Enable the LESS depth test. */
    public final boolean depthTest;
    /** Max wall-clock time for one render call; 0 means no limit. */
    public final long timeoutMillis;

    public RenderConfig(int tileSize, int threads, boolean backfaceCulling,
                        boolean depthTest, long timeoutMillis) {
        this.tileSize = tileSize;
        this.threads = threads;
        this.backfaceCulling = backfaceCulling;
        this.depthTest = depthTest;
        this.timeoutMillis = timeoutMillis;
    }

    public static RenderConfig defaults() {
        return new RenderConfig(32, Math.max(1, Runtime.getRuntime().availableProcessors()),
                true, true, 0L);
    }

    void validate() {
        if (tileSize <= 0) throw new RenderException("tileSize must be positive, got " + tileSize);
        if (threads <= 0) throw new RenderException("threads must be positive, got " + threads);
        if (timeoutMillis < 0) throw new RenderException("timeoutMillis must be >= 0, got " + timeoutMillis);
    }
}

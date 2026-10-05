package com.github.cuixudong74.softwarerasterizerjava.raster.core;

import com.github.cuixudong74.softwarerasterizerjava.raster.pipeline.RenderException;

/**
 * ARGB texture with explicit out-of-range addressing behavior.
 * Sampling uses nearest texel; u/v outside [0,1) are resolved by the wrap mode,
 * never by undefined behavior. Non-finite u/v are clamped to 0.
 */
public final class Texture {
    public enum WrapMode { CLAMP, REPEAT }

    private final int width;
    private final int height;
    private final int[] argb;
    private final WrapMode wrapMode;

    public Texture(int width, int height, int[] argb, WrapMode wrapMode) {
        if (width <= 0 || height <= 0) {
            throw new RenderException("texture dimensions must be positive, got "
                    + width + "x" + height);
        }
        if (argb == null || argb.length != width * height) {
            throw new RenderException("texture data length must equal width*height");
        }
        this.width = width;
        this.height = height;
        this.argb = argb.clone();
        this.wrapMode = wrapMode == null ? WrapMode.CLAMP : wrapMode;
    }

    public int width() { return width; }
    public int height() { return height; }
    public WrapMode wrapMode() { return wrapMode; }

    /** Sample nearest texel; out-of-range coordinates handled per wrap mode. */
    public int sample(double u, double v) {
        if (!Double.isFinite(u)) u = 0.0;
        if (!Double.isFinite(v)) v = 0.0;
        double uu = wrap(u);
        double vv = wrap(v);
        int tx = Math.min(width - 1, (int) (uu * width));
        int ty = Math.min(height - 1, (int) (vv * height));
        return argb[ty * width + tx];
    }

    private double wrap(double t) {
        if (wrapMode == WrapMode.REPEAT) {
            double f = t - Math.floor(t);
            return f; // in [0,1)
        }
        return Math.max(0.0, Math.min(1.0 - 1e-12, t));
    }
}

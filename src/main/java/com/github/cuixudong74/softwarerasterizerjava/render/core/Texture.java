package com.github.cuixudong74.softwarerasterizerjava.render.core;

import com.github.cuixudong74.softwarerasterizerjava.render.pipeline.InvalidInputException;

/**
 * A simple ARGB texture with nearest-neighbour sampling.
 * UV (0,0) is the top-left texel. Out-of-range UVs are resolved deterministically
 * by the {@link AddressMode}: CLAMP to the edge texel or REPEAT (fractional wrap).
 */
public final class Texture {

    private final int width;
    private final int height;
    private final int[] pixels;

    public Texture(int width, int height, int[] argbPixels) {
        if (width <= 0 || height <= 0) {
            throw new InvalidInputException("texture dimensions must be positive, got " + width + "x" + height);
        }
        if (argbPixels == null || argbPixels.length != width * height) {
            throw new InvalidInputException("texture pixel array must have width*height="
                    + (width * height) + " elements");
        }
        this.width = width;
        this.height = height;
        this.pixels = argbPixels.clone();
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** Sample at (u,v) with the given address mode; returns ARGB. Never undefined. */
    public int sample(float u, float v, AddressMode mode) {
        float uu = address(u, mode);
        float vv = address(v, mode);
        int tx = Math.min((int) (uu * width), width - 1);
        int ty = Math.min((int) (vv * height), height - 1);
        return pixels[ty * width + tx];
    }

    private static float address(float t, AddressMode mode) {
        if (Float.isNaN(t)) {
            return 0f; // defined fallback: NaN UV samples the (0,0) texel
        }
        return switch (mode) {
            case CLAMP -> Math.max(0f, Math.min(1f - 1e-6f, t));
            case REPEAT -> {
                float f = t - (float) Math.floor(t);
                yield f >= 1f ? 0f : f; // guard against float rounding at the upper edge
            }
        };
    }
}

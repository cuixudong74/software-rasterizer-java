package com.github.cuixudong74.softwarerasterizerjava.raster.core;

import com.github.cuixudong74.softwarerasterizerjava.raster.pipeline.RenderException;

import java.awt.image.BufferedImage;

/** Color + depth render target. Deterministic, in-process only. */
public final class Framebuffer {
    private final int width;
    private final int height;
    private final int[] color;
    private final double[] depth;

    public Framebuffer(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new RenderException("framebuffer dimensions must be positive, got "
                    + width + "x" + height);
        }
        long pixels = (long) width * height;
        if (pixels > 100_000_000L) {
            throw new RenderException("framebuffer too large: " + pixels + " pixels");
        }
        this.width = width;
        this.height = height;
        this.color = new int[width * height];
        this.depth = new double[width * height];
    }

    public int width() { return width; }
    public int height() { return height; }
    public int[] colorBuffer() { return color; }
    public double[] depthBuffer() { return depth; }

    public void clear(int argb, double depthValue) {
        java.util.Arrays.fill(color, argb);
        java.util.Arrays.fill(depth, depthValue);
    }

    public int pixel(int x, int y) { return color[y * width + x]; }
    public double depth(int x, int y) { return depth[y * width + x]; }

    public BufferedImage toImage() {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, width, height, color, 0, width);
        return img;
    }

    /** Deterministic 64-bit FNV-1a hash over color and depth buffers. */
    public long checksum() {
        long h = 0xcbf29ce484222325L;
        for (int c : color) {
            h = (h ^ (c & 0xffffffffL)) * 0x100000001b3L;
        }
        for (double d : depth) {
            h = (h ^ Double.doubleToLongBits(d)) * 0x100000001b3L;
        }
        return h;
    }
}

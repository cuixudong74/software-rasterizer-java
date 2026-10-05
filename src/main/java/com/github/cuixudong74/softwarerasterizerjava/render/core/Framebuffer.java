package com.github.cuixudong74.softwarerasterizerjava.render.core;

import com.github.cuixudong74.softwarerasterizerjava.render.pipeline.InvalidInputException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;

/**
 * Color (ARGB int per pixel) + depth (float per pixel) target.
 * Depth convention: 0 = near plane, 1 = far plane; smaller depth wins.
 * Pixel (0,0) is the top-left corner; x grows right, y grows down.
 */
public final class Framebuffer {

    /** Safety bound: at most 16384 x 16384 pixels (~1 GiB of color+depth). */
    public static final int MAX_DIMENSION = 16384;

    private final int width;
    private final int height;
    private final int[] color;
    private final float[] depth;

    public Framebuffer(int width, int height) {
        if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
            throw new InvalidInputException(
                    "framebuffer dimensions " + width + "x" + height + " out of range [1," + MAX_DIMENSION + "]");
        }
        this.width = width;
        this.height = height;
        this.color = new int[width * height];
        this.depth = new float[width * height];
        clear(0xFF000000, 1f);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public void clear(int argb, float depthValue) {
        Arrays.fill(color, argb);
        Arrays.fill(depth, depthValue);
    }

    public int colorAt(int x, int y) {
        return color[y * width + x];
    }

    public float depthAt(int x, int y) {
        return depth[y * width + x];
    }

    /** Package-private raw write used by the rasterizer. */
    public void writePixel(int x, int y, int argb, float depthValue) {
        int i = y * width + x;
        color[i] = argb;
        depth[i] = depthValue;
    }

    /** Copy the full contents of {@code src} into this buffer (dimensions must match). */
    public void copyFrom(Framebuffer src) {
        if (src.width != width || src.height != height) {
            throw new InvalidInputException("copyFrom dimension mismatch: " + src.width + "x" + src.height
                    + " into " + width + "x" + height);
        }
        System.arraycopy(src.color, 0, color, 0, color.length);
        System.arraycopy(src.depth, 0, depth, 0, depth.length);
    }

    /** Encode the color plane as a PNG image (deterministic bytes for identical pixels). */
    public byte[] toPng() {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, width, height, color, 0, width);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(img, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}

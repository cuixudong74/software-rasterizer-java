package com.github.cuixudong74.softwarerasterizerjava.render;

import com.github.cuixudong74.softwarerasterizerjava.render.core.AddressMode;
import com.github.cuixudong74.softwarerasterizerjava.render.core.Framebuffer;
import com.github.cuixudong74.softwarerasterizerjava.render.core.Mesh;
import com.github.cuixudong74.softwarerasterizerjava.render.core.Texture;
import com.github.cuixudong74.softwarerasterizerjava.render.core.Vertex;
import com.github.cuixudong74.softwarerasterizerjava.render.math.Mat4f;
import com.github.cuixudong74.softwarerasterizerjava.render.pipeline.RenderConfig;
import com.github.cuixudong74.softwarerasterizerjava.render.pipeline.Renderer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.cuixudong74.softwarerasterizerjava.render.TestScenes.*;
import static org.junit.jupiter.api.Assertions.*;

class InterpolationTest {

    private static final int W = 128, H = 128;

    private static Framebuffer render(Mesh mesh, Texture tex) {
        Framebuffer fb = new Framebuffer(W, H);
        new Renderer().render(mesh, Mat4f.identity(), defaultCamera(), tex, fb,
                RenderConfig.defaults().withThreads(1).withTiles(1, 1));
        return fb;
    }

    @Test
    void colorInterpolationIsPerspectiveCorrect() {
        // A quad receding in depth: left edge near (z=-1.5), right edge far (z=-6).
        // Red channel = u coordinate (0 on the left, 1 on the right).
        // With perspective-correct interpolation the red gradient is compressed toward
        // the far side; affine interpolation would give a different value at screen center.
        Vertex v0 = v(-1.2f, -1.2f, -1.5f, new com.github.cuixudong74.softwarerasterizerjava.render.math.Vec4f(0, 0, 0, 1));
        Vertex v1 = v(4.8f, -1.2f, -6f, new com.github.cuixudong74.softwarerasterizerjava.render.math.Vec4f(1, 0, 0, 1));
        Vertex v2 = v(4.8f, 1.2f, -6f, new com.github.cuixudong74.softwarerasterizerjava.render.math.Vec4f(1, 0, 0, 1));
        Vertex v3 = v(-1.2f, 1.2f, -1.5f, new com.github.cuixudong74.softwarerasterizerjava.render.math.Vec4f(0, 0, 0, 1));
        Mesh quad = mesh(List.of(v0, v1, v2, v3), 0, 1, 2, 0, 2, 3);
        Framebuffer fb = render(quad, null);

        // Screen-space x of the left edge (ndc -1.2/1.5 = -0.8 -> screen 12.8) and the
        // right edge (ndc 4.8/6 = 0.8 -> screen 115.2). Sample just inside both edges.
        int y = H / 2;
        int xLeft = 14, xRight = 113;
        int redLeft = (fb.colorAt(xLeft, y) >> 16) & 0xFF;
        int redRight = (fb.colorAt(xRight, y) >> 16) & 0xFF;
        assertTrue(redLeft < 20, "near the left edge red ~ 0, got " + redLeft);
        assertTrue(redRight > 235, "near the right edge red ~ 255, got " + redRight);

        // Analytic perspective-correct value at the horizontal screen center.
        // Screen center ndc x = 0. The bottom edge in view space: P(t) = lerp of the two
        // endpoints; find the view-space point projecting to ndc x = 0:
        //   x(t) = -1.2 + 6t,  z(t) = -1.5 - 4.5t,  ndc = x/(-z) = 0  ->  t = 0.2.
        // The attribute u varies linearly in t along the edge: u = t = 0.2.
        // (Perspective-correct interpolation reproduces exactly this at ndc x = 0.)
        int redCenter = (fb.colorAt(W / 2, y) >> 16) & 0xFF;
        int expected = Math.round(0.2f * 255); // 51
        assertTrue(Math.abs(redCenter - expected) <= 3,
                "perspective-correct red at screen center should be ~" + expected + ", got " + redCenter);
        // Affine interpolation would give u = 0.5 -> 128; make sure we are far from that.
        assertTrue(Math.abs(redCenter - 128) > 40, "result must not be affine-interpolated");
    }

    @Test
    void textureSamplingAndAddressModes() {
        // 2x2 texture: TL red, TR green, BL blue, BR white.
        Texture tex = new Texture(2, 2, new int[]{
                0xFFFF0000, 0xFF00FF00,
                0xFF0000FF, 0xFFFFFFFF});
        assertEquals(0xFFFF0000, tex.sample(0.1f, 0.1f, AddressMode.CLAMP));
        assertEquals(0xFF00FF00, tex.sample(0.9f, 0.1f, AddressMode.CLAMP));
        assertEquals(0xFF0000FF, tex.sample(0.1f, 0.9f, AddressMode.CLAMP));
        assertEquals(0xFFFFFFFF, tex.sample(0.9f, 0.9f, AddressMode.CLAMP));
        // CLAMP: out-of-range hits the edge texel.
        assertEquals(0xFFFF0000, tex.sample(-5f, -5f, AddressMode.CLAMP));
        assertEquals(0xFFFFFFFF, tex.sample(5f, 5f, AddressMode.CLAMP));
        // REPEAT: fractional wrap.
        assertEquals(0xFFFF0000, tex.sample(2.1f, 3.1f, AddressMode.REPEAT));
        assertEquals(0xFFFFFFFF, tex.sample(0.9f - 3f, 0.9f + 7f, AddressMode.REPEAT));
        // NaN is defined: samples the (0,0) texel.
        assertEquals(0xFFFF0000, tex.sample(Float.NaN, Float.NaN, AddressMode.CLAMP));
    }

    @Test
    void uvInterpolationIsPerspectiveCorrect() {
        // Same receding quad, but textured with a 256x1 ramp: texel x = u * 255 encodes u.
        int[] ramp = new int[256];
        for (int i = 0; i < 256; i++) {
            ramp[i] = 0xFF000000 | (i << 16);
        }
        Texture tex = new Texture(256, 1, ramp);
        Vertex v0 = v(-1.2f, -1.2f, -1.5f, WHITE, 0f, 0f);
        Vertex v1 = v(4.8f, -1.2f, -6f, WHITE, 1f, 0f);
        Vertex v2 = v(4.8f, 1.2f, -6f, WHITE, 1f, 0f);
        Vertex v3 = v(-1.2f, 1.2f, -1.5f, WHITE, 0f, 0f);
        Mesh quad = mesh(List.of(v0, v1, v2, v3), 0, 1, 2, 0, 2, 3);
        Framebuffer fb = render(quad, tex);
        int redCenter = (fb.colorAt(W / 2, H / 2) >> 16) & 0xFF;
        // u at screen center must be ~0.2 (see color test), i.e. texel ~51.
        assertTrue(Math.abs(redCenter - 51) <= 4,
                "perspective-correct u at screen center should sample texel ~51, got " + redCenter);
    }
}

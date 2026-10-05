package com.github.cuixudong74.softwarerasterizerjava.tests;

import com.github.cuixudong74.softwarerasterizerjava.raster.core.Framebuffer;
import com.github.cuixudong74.softwarerasterizerjava.raster.core.Mesh;
import com.github.cuixudong74.softwarerasterizerjava.raster.core.Texture;
import com.github.cuixudong74.softwarerasterizerjava.raster.math.Mat4f;
import com.github.cuixudong74.softwarerasterizerjava.raster.pipeline.RenderConfig;
import com.github.cuixudong74.softwarerasterizerjava.raster.pipeline.Renderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Perspective-correct interpolation of texture coordinates and colors. */
class PerspectiveInterpolationTest {

    /**
     * Triangle with u=0 at the two near vertices (z=-1) and u=1 at the far
     * vertex (z=-10). At pixel (50,51) the affine barycentric weight of the far
     * vertex is ~0.34, but the perspective-correct u is ~0.049. The texture
     * encodes u in the red channel (texel i -> red i), so the rendered pixel
     * must match the perspective value, not the affine one.
     */
    @Test
    void textureCoordinateIsPerspectiveCorrect() {
        int[] texData = new int[256];
        for (int i = 0; i < 256; i++) texData[i] = 0xFF000000 | (i << 16);
        Texture tex = new Texture(256, 1, texData, Texture.WrapMode.CLAMP);

        Mesh tri = TestScenes.singleTriangle(
                TestScenes.v(0.0, 0.9, -1, TestScenes.WHITE, 0, 0),
                TestScenes.v(-0.9, -0.9, -1, TestScenes.WHITE, 0, 0),
                TestScenes.v(9.0, -0.9, -10, TestScenes.WHITE, 1, 0));
        Framebuffer fb = new Framebuffer(100, 100);
        new Renderer(new RenderConfig(16, 1, false, true, 0))
                .render(fb, tri, Mat4f.identity(), TestScenes.camera(1.0), tex);

        // Screen verts A(50,5) B(5,95) C(95,54.5); sample pixel (50,51).
        double px = 50.5, py = 51.5;
        double denom = (95 - 54.5) * (50 - 95) + (95 - 5) * (5 - 54.5);
        double wA = ((95 - 54.5) * (px - 95) + (95 - 5) * (py - 54.5)) / denom;
        double wB = ((54.5 - 5) * (px - 95) + (50 - 95) * (py - 54.5)) / denom;
        double wC = 1.0 - wA - wB;
        double affineU = wC;
        double perspU = (wC / 10.0) / (wA + wB + wC / 10.0);
        assertTrue(Math.abs(affineU - perspU) > 0.2,
                "test point must discriminate affine vs perspective");

        int red = (fb.pixel(50, 51) >> 16) & 0xFF;
        double renderedU = red / 255.0;
        assertEquals(perspU, renderedU, 2.0 / 255.0,
                "u must be perspective-correct (expected ~" + perspU + ", affine would be "
                        + affineU + ")");
    }

    /** Vertex colors must also be interpolated perspective-correctly. */
    @Test
    void colorIsPerspectiveCorrect() {
        // Same geometry; red=1 at the far vertex only.
        Mesh tri = TestScenes.singleTriangle(
                TestScenes.v(0.0, 0.9, -1, TestScenes.GREEN),
                TestScenes.v(-0.9, -0.9, -1, TestScenes.GREEN),
                TestScenes.v(9.0, -0.9, -10, TestScenes.RED));
        Framebuffer fb = new Framebuffer(100, 100);
        new Renderer(new RenderConfig(16, 1, false, true, 0))
                .render(fb, tri, Mat4f.identity(), TestScenes.camera(1.0), null);

        double px = 50.5, py = 51.5;
        double denom = (95 - 54.5) * (50 - 95) + (95 - 5) * (5 - 54.5);
        double wA = ((95 - 54.5) * (px - 95) + (95 - 5) * (py - 54.5)) / denom;
        double wB = ((54.5 - 5) * (px - 95) + (50 - 95) * (py - 54.5)) / denom;
        double wC = 1.0 - wA - wB;
        double perspR = (wC / 10.0) / (wA + wB + wC / 10.0);
        double perspG = (wA + wB) / (wA + wB + wC / 10.0);

        int pixel = fb.pixel(50, 51);
        int r = (pixel >> 16) & 0xFF;
        int g = (pixel >> 8) & 0xFF;
        assertEquals(Math.round(perspR * 255), r, 2, "red channel must be perspective-correct");
        assertEquals(Math.round(perspG * 255), g, 2, "green channel must be perspective-correct");
    }

    /** Interpolated values must be stable: same scene, two runs, same pixels. */
    @Test
    void interpolationIsDeterministicAcrossRuns() {
        Mesh scene = TestScenes.randomScene(50, 42);
        Framebuffer fb1 = new Framebuffer(128, 128);
        Framebuffer fb2 = new Framebuffer(128, 128);
        Renderer r = new Renderer(new RenderConfig(32, 2, true, true, 0));
        r.render(fb1, scene, Mat4f.identity(), TestScenes.camera(1.0), null);
        r.render(fb2, scene, Mat4f.identity(), TestScenes.camera(1.0), null);
        assertArrayEquals(fb1.colorBuffer(), fb2.colorBuffer());
        assertArrayEquals(fb1.depthBuffer(), fb2.depthBuffer());
    }
}

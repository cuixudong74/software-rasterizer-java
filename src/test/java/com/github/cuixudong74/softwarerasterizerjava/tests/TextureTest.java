package com.github.cuixudong74.softwarerasterizerjava.tests;

import com.github.cuixudong74.softwarerasterizerjava.raster.core.Texture;
import com.github.cuixudong74.softwarerasterizerjava.raster.pipeline.RenderException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Texture addressing: out-of-range behavior must be explicit and defined. */
class TextureTest {

    private static Texture tex(Texture.WrapMode mode) {
        // 4x1 texture: red channel = texel index.
        int[] data = {0xFF000000, 0xFF010000, 0xFF020000, 0xFF030000};
        return new Texture(4, 1, data, mode);
    }

    @Test
    void clampModeSticksToEdgeTexels() {
        Texture t = tex(Texture.WrapMode.CLAMP);
        assertEquals(0xFF000000, t.sample(-0.5, 0));
        assertEquals(0xFF000000, t.sample(0.0, 0));
        assertEquals(0xFF030000, t.sample(0.999, 0));
        assertEquals(0xFF030000, t.sample(1.0, 0));
        assertEquals(0xFF030000, t.sample(7.5, 0));
    }

    @Test
    void repeatModeWrapsAround() {
        Texture t = tex(Texture.WrapMode.REPEAT);
        assertEquals(t.sample(0.1, 0), t.sample(1.1, 0));
        assertEquals(t.sample(0.1, 0), t.sample(-0.9, 0));
        assertEquals(0xFF010000, t.sample(1.3, 0)); // wraps to u=0.3 -> texel 1
    }

    @Test
    void nonFiniteCoordinatesAreDefined() {
        Texture t = tex(Texture.WrapMode.CLAMP);
        assertEquals(0xFF000000, t.sample(Double.NaN, 0));
        assertEquals(0xFF000000, t.sample(Double.POSITIVE_INFINITY, 0));
        assertEquals(0xFF000000, t.sample(0, Double.NEGATIVE_INFINITY));
    }

    @Test
    void invalidTextureDataIsRejected() {
        assertThrows(RenderException.class, () -> new Texture(0, 4, new int[0], Texture.WrapMode.CLAMP));
        assertThrows(RenderException.class, () -> new Texture(2, 2, new int[3], Texture.WrapMode.CLAMP));
    }
}

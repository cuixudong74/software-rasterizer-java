package com.github.cuixudong74.softwarerasterizerjava.render;

import com.github.cuixudong74.softwarerasterizerjava.render.core.Camera;
import com.github.cuixudong74.softwarerasterizerjava.render.core.Framebuffer;
import com.github.cuixudong74.softwarerasterizerjava.render.core.Mesh;
import com.github.cuixudong74.softwarerasterizerjava.render.core.Vertex;
import com.github.cuixudong74.softwarerasterizerjava.render.math.Mat4f;
import com.github.cuixudong74.softwarerasterizerjava.render.math.Vec2f;
import com.github.cuixudong74.softwarerasterizerjava.render.math.Vec3f;
import com.github.cuixudong74.softwarerasterizerjava.render.math.Vec4f;
import com.github.cuixudong74.softwarerasterizerjava.render.pipeline.InvalidInputException;
import com.github.cuixudong74.softwarerasterizerjava.render.pipeline.RenderConfig;
import com.github.cuixudong74.softwarerasterizerjava.render.pipeline.Renderer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.cuixudong74.softwarerasterizerjava.render.TestScenes.*;
import static org.junit.jupiter.api.Assertions.*;

class InvalidInputTest {

    private static Mesh simpleTri() {
        return mesh(List.of(v(-0.5f, -0.5f, -2f, RED), v(0.5f, -0.5f, -2f, RED), v(0f, 0.5f, -2f, RED)),
                0, 1, 2);
    }

    private static void assertInvalid(String expectedSubstring, Runnable r) {
        InvalidInputException ex = assertThrows(InvalidInputException.class, r::run);
        assertTrue(ex.getMessage().contains(expectedSubstring),
                "message should mention '" + expectedSubstring + "': " + ex.getMessage());
    }

    @Test
    void nanVertexPositionIsRejected() {
        Mesh bad = mesh(List.of(
                v(Float.NaN, 0, -2f, RED), v(0.5f, -0.5f, -2f, RED), v(0f, 0.5f, -2f, RED)), 0, 1, 2);
        assertInvalid("vertex[0].position",
                () -> new Renderer().render(bad, Mat4f.identity(), defaultCamera(), null,
                        new Framebuffer(16, 16), RenderConfig.defaults()));
    }

    @Test
    void infiniteColorIsRejected() {
        Mesh bad = mesh(List.of(
                v(-0.5f, -0.5f, -2f, new Vec4f(Float.POSITIVE_INFINITY, 0, 0, 1)),
                v(0.5f, -0.5f, -2f, RED), v(0f, 0.5f, -2f, RED)), 0, 1, 2);
        assertInvalid("vertex[0].color",
                () -> new Renderer().render(bad, Mat4f.identity(), defaultCamera(), null,
                        new Framebuffer(16, 16), RenderConfig.defaults()));
    }

    @Test
    void nanUvIsRejected() {
        Mesh bad = mesh(List.of(
                new Vertex(new Vec3f(0, 0, -2), RED, new Vec2f(Float.NaN, 0)),
                v(0.5f, -0.5f, -2f, RED), v(0f, 0.5f, -2f, RED)), 0, 1, 2);
        assertInvalid("vertex[0].uv",
                () -> new Renderer().render(bad, Mat4f.identity(), defaultCamera(), null,
                        new Framebuffer(16, 16), RenderConfig.defaults()));
    }

    @Test
    void outOfRangeIndexIsRejected() {
        Mesh bad = mesh(List.of(v(0, 0, -2f, RED)), 0, 1, 2);
        assertInvalid("index[1]=1",
                () -> new Renderer().render(bad, Mat4f.identity(), defaultCamera(), null,
                        new Framebuffer(16, 16), RenderConfig.defaults()));
    }

    @Test
    void nonMultipleOfThreeIndicesIsRejected() {
        Mesh bad = mesh(List.of(v(0, 0, -2f, RED), v(1, 0, -2f, RED)), 0, 1);
        assertInvalid("multiple of 3",
                () -> new Renderer().render(bad, Mat4f.identity(), defaultCamera(), null,
                        new Framebuffer(16, 16), RenderConfig.defaults()));
    }

    @Test
    void illegalCamerasAreRejected() {
        Framebuffer fb = new Framebuffer(16, 16);
        Mesh tri = simpleTri();
        Renderer r = new Renderer();
        assertInvalid("near", () -> r.render(tri, Mat4f.identity(),
                new Camera(new Vec3f(0, 0, 0), new Vec3f(0, 0, -1), new Vec3f(0, 1, 0), 90, 0, 100),
                null, fb, RenderConfig.defaults()));
        assertInvalid("far", () -> r.render(tri, Mat4f.identity(),
                new Camera(new Vec3f(0, 0, 0), new Vec3f(0, 0, -1), new Vec3f(0, 1, 0), 90, 10, 5),
                null, fb, RenderConfig.defaults()));
        assertInvalid("fovYDegrees", () -> r.render(tri, Mat4f.identity(),
                new Camera(new Vec3f(0, 0, 0), new Vec3f(0, 0, -1), new Vec3f(0, 1, 0), 190, 1, 100),
                null, fb, RenderConfig.defaults()));
        assertInvalid("view direction", () -> r.render(tri, Mat4f.identity(),
                new Camera(new Vec3f(1, 1, 1), new Vec3f(1, 1, 1), new Vec3f(0, 1, 0), 90, 1, 100),
                null, fb, RenderConfig.defaults()));
        assertInvalid("up is parallel", () -> r.render(tri, Mat4f.identity(),
                new Camera(new Vec3f(0, 0, 0), new Vec3f(0, 0, -1), new Vec3f(0, 0, -5), 90, 1, 100),
                null, fb, RenderConfig.defaults()));
    }

    @Test
    void illegalFramebufferDimensionsAreRejected() {
        assertThrows(InvalidInputException.class, () -> new Framebuffer(0, 10));
        assertThrows(InvalidInputException.class, () -> new Framebuffer(10, -1));
        assertThrows(InvalidInputException.class,
                () -> new Framebuffer(Framebuffer.MAX_DIMENSION + 1, 10));
    }

    @Test
    void illegalConfigsAreRejected() {
        assertThrows(InvalidInputException.class, () -> RenderConfig.defaults().withThreads(0));
        assertThrows(InvalidInputException.class, () -> RenderConfig.defaults().withTiles(0, 1));
        assertThrows(InvalidInputException.class, () -> RenderConfig.defaults().withTimeout(-5));
    }

    @Test
    void triangleCountBoundIsEnforced() {
        int[] indices = new int[3 * 101];
        Mesh big = mesh(List.of(v(0, 0, -2f, RED), v(1, 0, -2f, RED), v(0, 1, -2f, RED)), indices);
        assertInvalid("maxTriangles",
                () -> new Renderer().render(big, Mat4f.identity(), defaultCamera(), null,
                        new Framebuffer(16, 16),
                        new RenderConfig(1, 1, 1, true, true,
                                com.github.cuixudong74.softwarerasterizerjava.render.core.AddressMode.CLAMP,
                                0, 100)));
    }

    @Test
    void nullArgumentsAreRejected() {
        Renderer r = new Renderer();
        Framebuffer fb = new Framebuffer(16, 16);
        Mesh tri = simpleTri();
        assertThrows(InvalidInputException.class,
                () -> r.render(null, Mat4f.identity(), defaultCamera(), null, fb, RenderConfig.defaults()));
        assertThrows(InvalidInputException.class,
                () -> r.render(tri, null, defaultCamera(), null, fb, RenderConfig.defaults()));
        assertThrows(InvalidInputException.class,
                () -> r.render(tri, Mat4f.identity(), null, null, fb, RenderConfig.defaults()));
        assertThrows(InvalidInputException.class,
                () -> r.render(tri, Mat4f.identity(), defaultCamera(), null, null, RenderConfig.defaults()));
        assertThrows(InvalidInputException.class,
                () -> r.render(tri, Mat4f.identity(), defaultCamera(), null, fb, null));
    }

    @Test
    void failedRenderLeavesDestinationUntouched() {
        Framebuffer fb = new Framebuffer(16, 16);
        fb.clear(0xFF112233, 0.5f);
        Mesh bad = mesh(List.of(v(Float.NaN, 0, -2f, RED), v(0.5f, -0.5f, -2f, RED), v(0f, 0.5f, -2f, RED)),
                0, 1, 2);
        assertThrows(InvalidInputException.class,
                () -> new Renderer().render(bad, Mat4f.identity(), defaultCamera(), null, fb,
                        RenderConfig.defaults()));
        assertEquals(0xFF112233, fb.colorAt(8, 8));
        assertEquals(0.5f, fb.depthAt(8, 8));
    }
}

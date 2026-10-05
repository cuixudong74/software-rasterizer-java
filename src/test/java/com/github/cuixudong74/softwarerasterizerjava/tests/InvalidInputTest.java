package com.github.cuixudong74.softwarerasterizerjava.tests;

import com.github.cuixudong74.softwarerasterizerjava.raster.core.Camera;
import com.github.cuixudong74.softwarerasterizerjava.raster.core.Framebuffer;
import com.github.cuixudong74.softwarerasterizerjava.raster.core.Mesh;
import com.github.cuixudong74.softwarerasterizerjava.raster.core.Vertex;
import com.github.cuixudong74.softwarerasterizerjava.raster.math.Mat4f;
import com.github.cuixudong74.softwarerasterizerjava.raster.math.Vec2f;
import com.github.cuixudong74.softwarerasterizerjava.raster.math.Vec3f;
import com.github.cuixudong74.softwarerasterizerjava.raster.pipeline.RenderConfig;
import com.github.cuixudong74.softwarerasterizerjava.raster.pipeline.RenderException;
import com.github.cuixudong74.softwarerasterizerjava.raster.pipeline.Renderer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Illegal or non-finite input must raise distinguishable diagnostics. */
class InvalidInputTest {

    private static Renderer renderer() {
        return new Renderer(new RenderConfig(16, 1, true, true, 0));
    }

    private static Mesh goodMesh() {
        return TestScenes.singleTriangle(
                TestScenes.v(-0.5, -0.5, -2, TestScenes.RED),
                TestScenes.v(0.5, -0.5, -2, TestScenes.RED),
                TestScenes.v(0, 0.5, -2, TestScenes.RED));
    }

    @Test
    void nanVertexPositionIsRejected() {
        Mesh m = TestScenes.singleTriangle(
                TestScenes.v(Double.NaN, 0, -2, TestScenes.RED),
                TestScenes.v(0.5, -0.5, -2, TestScenes.RED),
                TestScenes.v(0, 0.5, -2, TestScenes.RED));
        RenderException e = assertThrows(RenderException.class,
                () -> renderer().render(new Framebuffer(8, 8), m, Mat4f.identity(),
                        TestScenes.camera(1.0), null));
        assertTrue(e.getMessage().contains("non-finite"), "diagnostic must mention non-finite: " + e.getMessage());
    }

    @Test
    void infiniteVertexPositionIsRejected() {
        Mesh m = TestScenes.singleTriangle(
                TestScenes.v(Double.POSITIVE_INFINITY, 0, -2, TestScenes.RED),
                TestScenes.v(0.5, -0.5, -2, TestScenes.RED),
                TestScenes.v(0, 0.5, -2, TestScenes.RED));
        assertThrows(RenderException.class,
                () -> renderer().render(new Framebuffer(8, 8), m, Mat4f.identity(),
                        TestScenes.camera(1.0), null));
    }

    @Test
    void outOfRangeIndexIsRejected() {
        Mesh m = TestScenes.mesh(List.of(
                TestScenes.v(0, 0, -2, TestScenes.RED)), 0, 1, 2);
        RenderException e = assertThrows(RenderException.class, m::validate);
        assertTrue(e.getMessage().contains("out of range"));
    }

    @Test
    void raggedIndexBufferIsRejected() {
        Mesh m = TestScenes.mesh(List.of(
                TestScenes.v(0, 0, -2, TestScenes.RED),
                TestScenes.v(1, 0, -2, TestScenes.RED),
                TestScenes.v(0, 1, -2, TestScenes.RED)), 0, 1);
        RenderException e = assertThrows(RenderException.class, m::validate);
        assertTrue(e.getMessage().contains("multiple of 3"));
    }

    @Test
    void invalidCameraParametersAreRejected() {
        Mesh m = goodMesh();
        // near >= far
        Camera bad1 = new Camera(new Vec3f(0, 0, 0), new Vec3f(0, 0, -1), new Vec3f(0, 1, 0),
                Math.PI / 2, 1.0, 10.0, 1.0);
        assertThrows(RenderException.class,
                () -> renderer().render(new Framebuffer(8, 8), m, Mat4f.identity(), bad1, null));
        // NaN fov
        Camera bad2 = new Camera(new Vec3f(0, 0, 0), new Vec3f(0, 0, -1), new Vec3f(0, 1, 0),
                Double.NaN, 1.0, 1.0, 100.0);
        assertThrows(RenderException.class,
                () -> renderer().render(new Framebuffer(8, 8), m, Mat4f.identity(), bad2, null));
        // up parallel to view direction
        Camera bad3 = new Camera(new Vec3f(0, 0, 0), new Vec3f(0, 0, -1), new Vec3f(0, 0, 5),
                Math.PI / 2, 1.0, 1.0, 100.0);
        assertThrows(RenderException.class,
                () -> renderer().render(new Framebuffer(8, 8), m, Mat4f.identity(), bad3, null));
    }

    @Test
    void nonFiniteModelMatrixIsRejected() {
        Mat4f bad = Mat4f.identity();
        bad.m[0][0] = Double.NaN;
        assertThrows(RenderException.class,
                () -> renderer().render(new Framebuffer(8, 8), goodMesh(), bad,
                        TestScenes.camera(1.0), null));
    }

    @Test
    void invalidConfigIsRejected() {
        assertThrows(RenderException.class, () -> new Renderer(new RenderConfig(0, 1, true, true, 0)));
        assertThrows(RenderException.class, () -> new Renderer(new RenderConfig(16, 0, true, true, 0)));
        assertThrows(RenderException.class, () -> new Renderer(new RenderConfig(16, 1, true, true, -5)));
    }

    @Test
    void failedRenderLeavesFramebufferUntouched() {
        Framebuffer fb = new Framebuffer(16, 16);
        fb.clear(0xFF112233, 0.5);
        Mesh bad = TestScenes.singleTriangle(
                TestScenes.v(Double.NaN, 0, -2, TestScenes.RED),
                TestScenes.v(0.5, -0.5, -2, TestScenes.RED),
                TestScenes.v(0, 0.5, -2, TestScenes.RED));
        assertThrows(RenderException.class,
                () -> renderer().render(fb, bad, Mat4f.identity(), TestScenes.camera(1.0), null));
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                assertEquals(0xFF112233, fb.pixel(x, y));
                assertEquals(0.5, fb.depth(x, y));
            }
        }
    }

    @Test
    void hugeButFiniteCoordinatesAreHandled() {
        // Extreme but legal input: must not throw, must not corrupt the output.
        Mesh m = TestScenes.singleTriangle(
                TestScenes.v(-1e12, -1e12, -2, TestScenes.RED),
                TestScenes.v(1e12, -1e12, -2, TestScenes.RED),
                TestScenes.v(0, 1e12, -2, TestScenes.RED));
        Framebuffer fb = new Framebuffer(64, 64);
        renderer().render(fb, m, Mat4f.identity(), TestScenes.camera(1.0), null);
        assertTrue(TestScenes.covered(fb.pixel(32, 32)), "giant triangle must cover the screen");
    }
}

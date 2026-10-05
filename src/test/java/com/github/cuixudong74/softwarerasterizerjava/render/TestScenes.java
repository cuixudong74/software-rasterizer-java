package com.github.cuixudong74.softwarerasterizerjava.render;

import com.github.cuixudong74.softwarerasterizerjava.render.core.Camera;
import com.github.cuixudong74.softwarerasterizerjava.render.core.Mesh;
import com.github.cuixudong74.softwarerasterizerjava.render.core.Vertex;
import com.github.cuixudong74.softwarerasterizerjava.render.math.Vec2f;
import com.github.cuixudong74.softwarerasterizerjava.render.math.Vec3f;
import com.github.cuixudong74.softwarerasterizerjava.render.math.Vec4f;

import java.util.List;

/** Shared helpers for building deterministic test scenes. */
final class TestScenes {

    private TestScenes() {
    }

    static final Vec4f RED = new Vec4f(1, 0, 0, 1);
    static final Vec4f GREEN = new Vec4f(0, 1, 0, 1);
    static final Vec4f BLUE = new Vec4f(0, 0, 1, 1);
    static final Vec4f WHITE = new Vec4f(1, 1, 1, 1);

    /** Camera at origin looking down -Z, 90 degree fov, near 1, far 100. */
    static Camera defaultCamera() {
        return new Camera(new Vec3f(0, 0, 0), new Vec3f(0, 0, -1), new Vec3f(0, 1, 0),
                90.0, 1.0, 100.0);
    }

    static Vertex v(float x, float y, float z, Vec4f color) {
        return new Vertex(new Vec3f(x, y, z), color, new Vec2f(0, 0));
    }

    static Vertex v(float x, float y, float z, Vec4f color, float u, float vv) {
        return new Vertex(new Vec3f(x, y, z), color, new Vec2f(u, vv));
    }

    static Mesh mesh(List<Vertex> verts, int... indices) {
        return new Mesh(verts, indices);
    }

    static int countNonBackground(com.github.cuixudong74.softwarerasterizerjava.render.core.Framebuffer fb, int bg) {
        int n = 0;
        for (int y = 0; y < fb.height(); y++) {
            for (int x = 0; x < fb.width(); x++) {
                if (fb.colorAt(x, y) != bg) {
                    n++;
                }
            }
        }
        return n;
    }
}

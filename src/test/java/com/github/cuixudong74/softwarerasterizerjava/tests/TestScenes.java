package com.github.cuixudong74.softwarerasterizerjava.tests;

import com.github.cuixudong74.softwarerasterizerjava.raster.core.Camera;
import com.github.cuixudong74.softwarerasterizerjava.raster.core.Mesh;
import com.github.cuixudong74.softwarerasterizerjava.raster.core.Vertex;
import com.github.cuixudong74.softwarerasterizerjava.raster.math.Mat4f;
import com.github.cuixudong74.softwarerasterizerjava.raster.math.Vec2f;
import com.github.cuixudong74.softwarerasterizerjava.raster.math.Vec3f;

import java.util.ArrayList;
import java.util.List;

/** Shared helpers: a camera at the origin looking down -Z, and mesh builders. */
final class TestScenes {
    private TestScenes() {}

    static final Vec3f WHITE = new Vec3f(1, 1, 1);
    static final Vec3f RED = new Vec3f(1, 0, 0);
    static final Vec3f GREEN = new Vec3f(0, 1, 0);
    static final Vec3f BLUE = new Vec3f(0, 0, 1);
    static final Vec2f UV0 = new Vec2f(0, 0);

    /** Camera at origin looking down -Z, fovY=90deg, near=1, far=100. */
    static Camera camera(double aspect) {
        return new Camera(new Vec3f(0, 0, 0), new Vec3f(0, 0, -1), new Vec3f(0, 1, 0),
                Math.PI / 2, aspect, 1.0, 100.0);
    }

    static Vertex v(double x, double y, double z, Vec3f color) {
        return new Vertex(new Vec3f(x, y, z), color, UV0);
    }

    static Vertex v(double x, double y, double z, Vec3f color, double u, double vv) {
        return new Vertex(new Vec3f(x, y, z), color, new Vec2f(u, vv));
    }

    static Mesh mesh(List<Vertex> verts, int... indices) {
        return new Mesh(verts, indices);
    }

    static Mesh singleTriangle(Vertex a, Vertex b, Vertex c) {
        return mesh(List.of(a, b, c), 0, 1, 2);
    }

    /** Deterministic pseudo-random scene of n triangles in front of the camera. */
    static Mesh randomScene(int n, long seed) {
        java.util.Random rnd = new java.util.Random(seed);
        List<Vertex> verts = new ArrayList<>();
        int[] indices = new int[n * 3];
        for (int i = 0; i < n; i++) {
            double cx = (rnd.nextDouble() - 0.5) * 6;
            double cy = (rnd.nextDouble() - 0.5) * 6;
            double cz = -1.5 - rnd.nextDouble() * 20;
            double s = 0.2 + rnd.nextDouble() * 1.5;
            for (int k = 0; k < 3; k++) {
                double ang = rnd.nextDouble() * Math.PI * 2;
                Vec3f color = new Vec3f(rnd.nextDouble(), rnd.nextDouble(), rnd.nextDouble());
                verts.add(new Vertex(
                        new Vec3f(cx + Math.cos(ang) * s, cy + Math.sin(ang) * s, cz + rnd.nextDouble()),
                        color, new Vec2f(rnd.nextDouble() * 2 - 0.5, rnd.nextDouble() * 2 - 0.5)));
                indices[i * 3 + k] = i * 3 + k;
            }
        }
        return new Mesh(verts, indices);
    }

    static boolean covered(int argb) {
        return argb != com.github.cuixudong74.softwarerasterizerjava.raster.pipeline.Renderer.CLEAR_COLOR;
    }

    static Mat4f identity() {
        return Mat4f.identity();
    }
}

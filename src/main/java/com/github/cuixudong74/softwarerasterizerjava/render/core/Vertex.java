package com.github.cuixudong74.softwarerasterizerjava.render.core;

import com.github.cuixudong74.softwarerasterizerjava.render.math.Vec2f;
import com.github.cuixudong74.softwarerasterizerjava.render.math.Vec3f;
import com.github.cuixudong74.softwarerasterizerjava.render.math.Vec4f;

/** A mesh vertex: model-space position plus interpolated attributes. */
public record Vertex(Vec3f position, Vec4f color, Vec2f uv) {

    public static Vertex of(float x, float y, float z, Vec4f color, Vec2f uv) {
        return new Vertex(new Vec3f(x, y, z), color, uv);
    }
}

package com.github.cuixudong74.softwarerasterizerjava.raster.core;

import com.github.cuixudong74.softwarerasterizerjava.raster.math.Vec2f;
import com.github.cuixudong74.softwarerasterizerjava.raster.math.Vec3f;

/** Model-space vertex with color and texture coordinate attributes. */
public final class Vertex {
    public final Vec3f position;
    public final Vec3f color;
    public final Vec2f uv;

    public Vertex(Vec3f position, Vec3f color, Vec2f uv) {
        this.position = position;
        this.color = color;
        this.uv = uv;
    }
}

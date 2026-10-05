package com.github.cuixudong74.softwarerasterizerjava.raster.pipeline;

import com.github.cuixudong74.softwarerasterizerjava.raster.math.Vec2f;
import com.github.cuixudong74.softwarerasterizerjava.raster.math.Vec3f;
import com.github.cuixudong74.softwarerasterizerjava.raster.math.Vec4f;

/** Vertex in clip space carrying attributes to interpolate. */
final class ClipVertex {
    Vec4f position;
    Vec3f color;
    Vec2f uv;

    ClipVertex(Vec4f position, Vec3f color, Vec2f uv) {
        this.position = position;
        this.color = color;
        this.uv = uv;
    }

    /** Linear interpolation in clip space; perspective correctness is recovered later via 1/w. */
    ClipVertex lerp(ClipVertex o, double t) {
        Vec4f p = position;
        Vec4f q = o.position;
        return new ClipVertex(
                new Vec4f(p.x + (q.x - p.x) * t, p.y + (q.y - p.y) * t,
                          p.z + (q.z - p.z) * t, p.w + (q.w - p.w) * t),
                color.lerp(o.color, t),
                uv.lerp(o.uv, t));
    }
}

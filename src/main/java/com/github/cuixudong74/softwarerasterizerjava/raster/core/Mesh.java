package com.github.cuixudong74.softwarerasterizerjava.raster.core;

import com.github.cuixudong74.softwarerasterizerjava.raster.pipeline.RenderException;

import java.util.List;

/** Indexed triangle mesh. */
public final class Mesh {
    private final List<Vertex> vertices;
    private final int[] indices;

    public Mesh(List<Vertex> vertices, int[] indices) {
        this.vertices = vertices;
        this.indices = indices;
    }

    public List<Vertex> vertices() { return vertices; }
    public int[] indices() { return indices; }
    public int triangleCount() { return indices.length / 3; }

    /**
     * Validate structure and finiteness of every attribute.
     * Throws RenderException with a distinguishable diagnostic on any violation.
     */
    public void validate() {
        if (vertices == null || indices == null) {
            throw new RenderException("mesh has null vertices or indices");
        }
        if (indices.length % 3 != 0) {
            throw new RenderException("index count " + indices.length + " is not a multiple of 3");
        }
        for (int i = 0; i < indices.length; i++) {
            int idx = indices[i];
            if (idx < 0 || idx >= vertices.size()) {
                throw new RenderException("index[" + i + "]=" + idx
                        + " out of range [0," + vertices.size() + ")");
            }
        }
        for (int i = 0; i < vertices.size(); i++) {
            Vertex v = vertices.get(i);
            if (v == null || v.position == null || v.color == null || v.uv == null) {
                throw new RenderException("vertex " + i + " has null component");
            }
            checkFinite("vertex " + i + " position", v.position.x, v.position.y, v.position.z);
            checkFinite("vertex " + i + " color", v.color.x, v.color.y, v.color.z);
            checkFinite("vertex " + i + " uv", v.uv.x, v.uv.y);
        }
    }

    private static void checkFinite(String what, double... values) {
        for (double d : values) {
            if (!Double.isFinite(d)) {
                throw new RenderException(what + " contains non-finite value " + d);
            }
        }
    }
}

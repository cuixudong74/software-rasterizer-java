package com.github.cuixudong74.softwarerasterizerjava.render.core;

import java.util.List;

/** Indexed triangle mesh. Indices must be a multiple of 3 and reference valid vertices. */
public record Mesh(List<Vertex> vertices, int[] indices) {
}

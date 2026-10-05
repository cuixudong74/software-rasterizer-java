package com.github.cuixudong74.softwarerasterizerjava.render.core;

import com.github.cuixudong74.softwarerasterizerjava.render.math.Vec3f;

/**
 * Pinhole camera. Right-handed view space: the camera looks from {@code eye} toward
 * {@code target} (view direction = -Z in view space). {@code fovYDegrees} is the vertical
 * field of view; {@code near}/{@code far} are positive distances along the view direction.
 */
public record Camera(Vec3f eye, Vec3f target, Vec3f up, double fovYDegrees, double near, double far) {
}

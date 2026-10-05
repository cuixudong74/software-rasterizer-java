package com.github.cuixudong74.softwarerasterizerjava.raster.core;

import com.github.cuixudong74.softwarerasterizerjava.raster.math.Mat4f;
import com.github.cuixudong74.softwarerasterizerjava.raster.math.Vec3f;
import com.github.cuixudong74.softwarerasterizerjava.raster.pipeline.RenderException;

/** Pinhole camera producing view and projection matrices. */
public final class Camera {
    public final Vec3f eye;
    public final Vec3f target;
    public final Vec3f up;
    public final double fovYRadians;
    public final double aspect;
    public final double near;
    public final double far;

    public Camera(Vec3f eye, Vec3f target, Vec3f up,
                  double fovYRadians, double aspect, double near, double far) {
        this.eye = eye;
        this.target = target;
        this.up = up;
        this.fovYRadians = fovYRadians;
        this.aspect = aspect;
        this.near = near;
        this.far = far;
    }

    /** Validate camera parameters; throws RenderException with diagnostics on bad input. */
    public void validate() {
        if (eye == null || target == null || up == null) {
            throw new RenderException("camera has null eye/target/up");
        }
        checkFinite("eye", eye.x, eye.y, eye.z);
        checkFinite("target", target.x, target.y, target.z);
        checkFinite("up", up.x, up.y, up.z);
        if (!Double.isFinite(fovYRadians) || fovYRadians <= 0 || fovYRadians >= Math.PI) {
            throw new RenderException("fovY must be in (0, PI), got " + fovYRadians);
        }
        if (!Double.isFinite(aspect) || aspect <= 0) {
            throw new RenderException("aspect must be positive, got " + aspect);
        }
        if (!Double.isFinite(near) || !Double.isFinite(far) || near <= 0 || far <= near) {
            throw new RenderException("require 0 < near < far, got near=" + near + " far=" + far);
        }
        if (eye.sub(target).dot(eye.sub(target)) == 0.0) {
            throw new RenderException("eye and target coincide");
        }
        Vec3f f = target.sub(eye).normalize();
        if (f.cross(up).dot(f.cross(up)) == 0.0) {
            throw new RenderException("up vector parallel to view direction");
        }
    }

    public Mat4f viewMatrix() { return Mat4f.lookAt(eye, target, up); }
    public Mat4f projectionMatrix() { return Mat4f.perspective(fovYRadians, aspect, near, far); }

    private static void checkFinite(String what, double... values) {
        for (double d : values) {
            if (!Double.isFinite(d)) {
                throw new RenderException("camera " + what + " contains non-finite value " + d);
            }
        }
    }
}

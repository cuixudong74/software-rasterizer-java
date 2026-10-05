package com.github.cuixudong74.softwarerasterizerjava.raster.pipeline;

/** Raised for invalid or non-finite render input. Never silently propagates bad numbers. */
public class RenderException extends RuntimeException {
    public RenderException(String message) {
        super(message);
    }
}

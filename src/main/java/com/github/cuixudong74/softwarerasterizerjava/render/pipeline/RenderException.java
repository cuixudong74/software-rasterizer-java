package com.github.cuixudong74.softwarerasterizerjava.render.pipeline;

/** Base runtime exception for rendering failures. */
public class RenderException extends RuntimeException {
    public RenderException(String message) {
        super(message);
    }

    public RenderException(String message, Throwable cause) {
        super(message, cause);
    }
}

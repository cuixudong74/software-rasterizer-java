package com.github.cuixudong74.softwarerasterizerjava.render.pipeline;

/** Thrown when a render is cancelled or exceeds its configured timeout. No partial output is committed. */
public class RenderCancelledException extends RenderException {
    public RenderCancelledException(String message) {
        super(message);
    }
}

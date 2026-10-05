package com.github.cuixudong74.softwarerasterizerjava.render.pipeline;

/**
 * Thrown when input geometry, camera parameters or configuration contain illegal values
 * (NaN, infinity, out-of-range indices, non-positive dimensions, ...). The message always
 * identifies which input element is invalid so the problem can be reproduced locally.
 */
public class InvalidInputException extends RenderException {
    public InvalidInputException(String message) {
        super(message);
    }
}

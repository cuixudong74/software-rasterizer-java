package com.github.cuixudong74.softwarerasterizerjava.render.core;

/** Texture coordinate wrapping behaviour for out-of-range UVs. */
public enum AddressMode {
    /** UVs are clamped to [0,1]; out-of-range samples hit the edge texel. */
    CLAMP,
    /** UVs wrap with fractional repeat: u' = u - floor(u). */
    REPEAT
}

package com.steelclash.core;

/** A pose sampled during an entity tick is stale if its combat clock advances before rendering. */
public final class PoseSampleStamp {
    private boolean valid;
    private long frame, elapsedUs, durationUs;
    private int entityTick;
    private float partialTick;

    public boolean matches(long frame, int entityTick, float partialTick, long elapsedUs, long durationUs) {
        return valid && this.frame == frame && this.entityTick == entityTick && this.partialTick == partialTick
                && this.elapsedUs == elapsedUs && this.durationUs == durationUs;
    }

    public void record(long frame, int entityTick, float partialTick, long elapsedUs, long durationUs) {
        valid = true;
        this.frame = frame;
        this.entityTick = entityTick;
        this.partialTick = partialTick;
        this.elapsedUs = elapsedUs;
        this.durationUs = durationUs;
    }

    public void clear() { valid = false; }
}

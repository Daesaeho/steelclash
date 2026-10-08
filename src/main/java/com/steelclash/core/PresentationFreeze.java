package com.steelclash.core;

import java.util.function.Supplier;

/** Holds a presentation sample, not a fractional tick. The caller's simulation can continue independently. */
public final class PresentationFreeze<T> {
    private long until;
    private boolean requested;
    private boolean captured;
    private int serial;
    private T value;

    public void request(long now, long durationNanos) {
        until = now + Math.max(0, durationNanos);
        requested = durationNanos > 0;
        captured = false;
        value = null;
    }

    public T sample(int attackSerial, long now, Supplier<T> live) {
        if (captured && serial != attackSerial) clear();
        if (!requested || now - until >= 0) {
            clear();
            return live.get();
        }
        if (!captured) {
            value = live.get();
            serial = attackSerial;
            captured = true;
        }
        return value;
    }

    /** An authoritative correction replaces the held sample without extending the requested pause. */
    public void corrected() {
        captured = false;
        value = null;
    }

    public boolean holding(long now) { return requested && captured && now - until < 0; }

    public void clear() {
        until = 0;
        requested = false;
        captured = false;
        value = null;
    }
}

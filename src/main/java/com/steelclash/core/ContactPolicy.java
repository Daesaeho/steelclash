package com.steelclash.core;

import java.util.Locale;

/**
 * What a swing does after its blade meets a body (Chivalry 2 cleave and thwack; architecture plan §18).
 * <ul>
 *     <li>{@link #CLEAVE}: carries on through, up to the attack's target limit (cut and chop).</li>
 *     <li>{@link #THWACK}: a light attack stops in the first body (hitstop) and goes into its thwack recovery.</li>
 *     <li>{@link #CLEAVE_ON_KILL}: as THWACK, but carries on if that body went down (blunt weapons since Chivalry 2's
 *     Fight Knight update).</li>
 * </ul>
 * Heavies always cleave: a heavy blunt trades the light's quick thwack for reaching a second body.
 */
public enum ContactPolicy {
    CLEAVE, THWACK, CLEAVE_ON_KILL;

    /** Blunt weapons stop in the first body unless it dies; everything else cleaves. */
    public static ContactPolicy defaultFor(DamageType type) {
        return type == DamageType.BLUNT ? CLEAVE_ON_KILL : CLEAVE;
    }

    /** Whether the swing stops at this contact. */
    public boolean stops(boolean heavy, boolean killed) {
        return switch (this) {
            case CLEAVE -> false;
            case THWACK -> !heavy;
            case CLEAVE_ON_KILL -> !heavy && !killed;
        };
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}

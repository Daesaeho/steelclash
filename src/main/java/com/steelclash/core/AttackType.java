package com.steelclash.core;

import java.util.Locale;

public enum AttackType {
    SLASH,
    OVERHEAD,
    STAB,
    /** Built in, not defined by weapon profiles: breaks guards. Becomes a shield bash with a bash-capable shield. */
    KICK,
    /** The weapon's special (lunge, slam or sweep), defined by its profile, on a cooldown. */
    SPECIAL,
    /** Built in: throw the held weapon. */
    THROW,
    /** Built in: a quick, short thrust that interrupts at close range (Chivalry 2 jab). */
    JAB;

    private static final AttackType[] VALUES = values();

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The three weapon attacks (everything but the kick). */
    public static final AttackType[] WEAPON_ATTACKS = {SLASH, OVERHEAD, STAB};

    public static AttackType byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : SLASH;
    }
}

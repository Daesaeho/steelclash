package com.steelclash.core;

import java.util.Locale;

public enum AttackType {
    SLASH,
    OVERHEAD,
    STAB,
    /** Built in, not defined by weapon profiles: breaks guards. Becomes a shield bash with a bash-capable shield. */
    KICK;

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

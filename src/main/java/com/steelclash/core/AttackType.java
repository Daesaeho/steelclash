package com.steelclash.core;

import java.util.Locale;

public enum AttackType {
    SLASH,
    OVERHEAD,
    STAB;

    private static final AttackType[] VALUES = values();

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static AttackType byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : SLASH;
    }
}

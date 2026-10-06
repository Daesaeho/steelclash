package com.steelclash.core;

public enum Phase {
    IDLE,
    /** Telegraphing the attack. Can be feinted/morphed (M3); getting hit here causes a flinch (M3). */
    WINDUP,
    /** The blade is live and traced against targets every tick. */
    RELEASE,
    /** Committed follow-through; vulnerable. Attacking here buffers the next attack; parrying is allowed. */
    RECOVERY,
    /** Weapon parry raised. Limited duration, unlike a shield guard. */
    PARRY,
    /** Lowering the guard after a parry that caught nothing. */
    GUARD_RECOVERY,
    /** Knocked off balance: after being parried, blocked by a shield, or a guard break. */
    STAGGER;

    private static final Phase[] VALUES = values();

    public static Phase byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : IDLE;
    }

    public boolean isAttack() {
        return this == WINDUP || this == RELEASE || this == RECOVERY;
    }
}

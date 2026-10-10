package com.steelclash.core;

/** When a known local attack-time item can no longer own the displayed weapon pose. */
public final class WeaponPosePolicy {
    private WeaponPosePolicy() {}
    public static boolean stale(Phase phase, AttackType type, boolean knownWeapon, boolean sameItem, boolean emptyHand) {
        if (!phase.isAttack() && phase != Phase.PARRY && phase != Phase.GUARD_RECOVERY) return false;
        // Throwing intentionally removes the item at release; keep its empty-hand follow-through.
        if (type == AttackType.THROW && phase != Phase.WINDUP && emptyHand) return false;
        return knownWeapon && !sameItem;
    }
}

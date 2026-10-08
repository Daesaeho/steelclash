package com.steelclash.core;

/**
 * Smooth hand-overs between combat poses. Most phase changes are already continuous (a windup ends where its release
 * starts, a recovery eases out to the vanilla pose), but some aren't: a combo or riposte starts a fresh windup while the
 * arm is still out at the end of the last swing, a stagger or a raised guard replaces an attack, a feint drops it. Those
 * blend from the pose last shown to the new one over {@link #BLEND_TICKS}
 * (in {@code CombatPresentation}).
 */
public final class PoseBlend {
    /** How long a hand-over takes, ticks (200 ms). */
    public static final double BLEND_TICKS = 4;

    private PoseBlend() {
    }

    /**
     * Does the pose carry on smoothly from the previous state to this one without help? Same attack and side: windup to
     * release, release to recovery. A guard lowering. And anything easing out to idle on its own.
     */
    public static boolean continuous(int prevSerial, AttackType prevType, boolean prevMirrored, Phase prevPhase,
                                     int serial, AttackType type, boolean mirrored, Phase phase) {
        if (phase == Phase.IDLE) {
            return prevPhase == Phase.IDLE || prevPhase == Phase.RECOVERY || prevPhase == Phase.GUARD_RECOVERY
                    || prevPhase == Phase.STAGGER;
        }
        if (prevPhase == Phase.PARRY && phase == Phase.GUARD_RECOVERY) {
            return true;
        }
        boolean sameAttack = prevSerial == serial && prevType == type && prevMirrored == mirrored;
        if (!sameAttack) {
            return false;
        }
        return prevPhase == phase || (prevPhase == Phase.WINDUP && phase == Phase.RELEASE)
                || (prevPhase == Phase.RELEASE && phase == Phase.RECOVERY);
    }
}

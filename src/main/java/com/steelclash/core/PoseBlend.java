package com.steelclash.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Smooth hand-overs between combat poses. Most phase changes are already continuous (a windup ends where its release
 * starts, a recovery eases out to the vanilla pose), but some aren't: a combo or riposte starts a fresh windup while the
 * arm is still out at the end of the last swing, a stagger or a raised guard replaces an attack, a feint drops it. Those
 * blend from the pose last shown to the new one over {@link #BLEND_TICKS}.
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

    /** Smoothstep of {@code t} clamped to [0, 1]. */
    public static double ease(double t) {
        double c = Math.max(0, Math.min(1, t));
        return c * c * (3 - 2 * c);
    }

    /** Angle from {@code a} toward {@code b} (degrees) by {@code t}, the short way round. */
    public static double angle(double t, double a, double b) {
        double delta = ((b - a) % 360 + 540) % 360 - 180;
        return a + delta * t;
    }

    public static double lerp(double t, double a, double b) {
        return a + (b - a) * t;
    }

    /** Part offsets from {@code a} toward {@code b} by {@code t}; a part missing on one side counts as zero. */
    public static Map<String, double[]> offsets(double t, Map<String, double[]> a, Map<String, double[]> b) {
        Map<String, double[]> out = new HashMap<>();
        double[] zero = {0, 0, 0};
        for (String part : a.keySet()) {
            out.put(part, blend(t, a.get(part), b.getOrDefault(part, zero)));
        }
        for (String part : b.keySet()) {
            out.computeIfAbsent(part, p -> blend(t, zero, b.get(p)));
        }
        return out;
    }

    private static double[] blend(double t, double[] a, double[] b) {
        return new double[]{lerp(t, a[0], b[0]), lerp(t, a[1], b[1]), lerp(t, a[2], b[2])};
    }
}

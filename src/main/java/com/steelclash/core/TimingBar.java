package com.steelclash.core;

import org.jetbrains.annotations.Nullable;

/**
 * What the timing HUD shows for a fighter's current state: which kind of bar, how full it is, and whether it fills
 * (building up) or drains (running out). Pure function of the state machine, so it's unit-tested and costs no
 * networking.
 *
 * @param kind     what the bar represents (drives colour)
 * @param fraction 0..1 fill amount
 * @param marker   optional 0..1 position of a tick mark on the bar (e.g. when a held attack becomes a heavy)
 */
public record TimingBar(Kind kind, double fraction, double marker) {
    public enum Kind {
        /** Holding an attack: building toward a heavy. */
        HEAVY_CHARGE,
        WINDUP,
        HEAVY_WINDUP,
        /** The blade is live. */
        RELEASE,
        RECOVERY,
        /** Recovery after a landed hit: attacking now combos. */
        COMBO,
        PARRY,
        GUARD_RECOVERY,
        STAGGER,
        RIPOSTE
    }

    public static final double NO_MARKER = -1;

    /**
     * @param holdingForHeavy the attack input that started this windup is still held (so it may become a heavy)
     * @param heavyHoldTicks  ticks of holding after which the windup becomes a heavy
     * @param riposteWindow   the full length of the riposte window, to show how much is left
     */
    @Nullable
    public static TimingBar of(CombatStateMachine m, float partialTick, boolean holdingForHeavy, int heavyHoldTicks,
                               int riposteWindow) {
        double progress = m.phaseProgress(partialTick);
        return switch (m.phase()) {
            case IDLE -> m.isRiposteReady() && riposteWindow > 0
                    ? new TimingBar(Kind.RIPOSTE, clamp((m.riposteTicks() - partialTick) / riposteWindow), NO_MARKER)
                    : null;
            case WINDUP -> {
                if (m.type() != AttackType.KICK && !m.isHeavy() && holdingForHeavy) {
                    double charge = clamp((m.phaseTick() + partialTick) / heavyHoldTicks);
                    yield new TimingBar(Kind.HEAVY_CHARGE, charge, 1.0);
                }
                yield new TimingBar(m.isHeavy() ? Kind.HEAVY_WINDUP : Kind.WINDUP, progress, NO_MARKER);
            }
            case RELEASE -> new TimingBar(Kind.RELEASE, progress, NO_MARKER);
            case RECOVERY -> new TimingBar(m.isComboAllowed() ? Kind.COMBO : Kind.RECOVERY, 1 - progress, NO_MARKER);
            // Once the guard has caught a hit, what matters is how long you have to riposte out of it.
            case PARRY -> m.isRiposteReady() && riposteWindow > 0
                    ? new TimingBar(Kind.RIPOSTE, clamp((m.riposteTicks() - partialTick) / riposteWindow), NO_MARKER)
                    : new TimingBar(Kind.PARRY, 1 - progress, NO_MARKER);
            case GUARD_RECOVERY -> new TimingBar(Kind.GUARD_RECOVERY, progress, NO_MARKER);
            case STAGGER -> new TimingBar(Kind.STAGGER, 1 - progress, NO_MARKER);
        };
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }
}

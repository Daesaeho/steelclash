package com.steelclash.client;

import com.steelclash.combat.Combat;
import com.steelclash.combat.CombatData;
import com.steelclash.core.ArcPath;
import com.steelclash.core.AttackType;
import com.steelclash.core.Phase;
import com.steelclash.profile.WeaponProfile;
import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;

/**
 * Where the weapon is aimed right now, relative to the wielder's view, derived from the same arc the server traces.
 *
 * @param weight how much this pose overrides the vanilla arm pose (eases in and out)
 */
public record SwingPose(Phase phase, AttackType type, double yaw, double pitch, double extension, double weight, double releaseProgress,
                        double progress, double elapsedTicks, WeaponProfile.AttackSpec spec, ArcPath path) {
    /** Guard: weapon raised across the body. */
    private static final double GUARD_YAW = -15;
    private static final double GUARD_PITCH = -35;
    private static final int GUARD_RAISE_TICKS = 3;
    /** Stagger: the weapon thrown up and out to the side. */
    private static final double STAGGER_YAW = 50;
    private static final double STAGGER_PITCH = -60;
    /** A parried blade rebounds this far back along its arc (fraction of the release) before the stagger takes over. */
    private static final double REBOUND = 0.3;

    public static Optional<SwingPose> of(LivingEntity entity, CombatData data, float partialTick) {
        var frame = data.machine.visualFrame(partialTick);
        Phase phase = frame.phase();
        if (phase == Phase.IDLE) {
            return Optional.empty();
        }
        return Combat.currentSpec(entity, data).map(spec -> {
            ArcPath path = Combat.currentPath(data, spec);
            AttackType type = data.machine.type();
            double progress = frame.progress();
            double elapsed = frame.elapsedTicks();
            // The arm rises into the windup over its first 40% (at least two ticks), so starting an attack eases in
            // rather than popping up, and the drawn-back telegraph is still in place well before the release.
            double durationTicks = frame.durationUs() / 50_000.0;
            double raiseTicks = Math.min(durationTicks, Math.max(2, 0.4 * durationTicks));
            double entryWeight = smooth(Math.min(1, elapsed / Math.max(0.02, raiseTicks)));
            return switch (phase) {
                case WINDUP -> {
                    ArcPath.Keyframe start = path.windup(progress, data.machine.isHeavy());
                    if (type == AttackType.THROW) {
                        double launch = smooth(progress);
                        start = new ArcPath.Keyframe(progress, 15 * (1 - launch), -70 * (1 - launch), 0.5 + 0.5 * launch);
                    }
                    yield new SwingPose(phase, type, start.yaw(), start.pitch(), start.extension(), entryWeight, 0,
                            progress, elapsed, spec, path);
                }
                case RELEASE -> {
                    ArcPath.Keyframe k = path.sample(progress);
                    if (type == AttackType.THROW) {
                        k = new ArcPath.Keyframe(progress, 0, 50 * smooth(progress), 1);
                    }
                    yield new SwingPose(phase, type, k.yaw(), k.pitch(), k.extension(), 1, progress, progress, elapsed, spec, path);
                }
                case PARRY -> {
                    double raise = Math.min(1, elapsed / GUARD_RAISE_TICKS);
                    yield new SwingPose(phase, type, GUARD_YAW, GUARD_PITCH, 0.8, smooth(raise), 0, progress, elapsed, spec, path);
                }
                case GUARD_RECOVERY -> new SwingPose(phase, type, GUARD_YAW, GUARD_PITCH, 0.8, 1 - smooth(progress), 0, progress, elapsed, spec, path);
                case STAGGER -> {
                    double parriedAt = HitReactions.bounce(entity, data.machine.attackSerial());
                    if (Double.isNaN(parriedAt)) {
                        yield new SwingPose(phase, type, STAGGER_YAW, STAGGER_PITCH, 0.8, 1 - smooth(progress), 0, progress,
                                elapsed, spec, path);
                    }
                    // Parried: the blade bounces back along its own path from where it met the guard, then the arm
                    // goes on into the stagger.
                    ArcPath.Keyframe back = path.sample(parriedAt - REBOUND * smooth(Math.min(1, progress / 0.35)));
                    double over = smooth(Math.max(0, Math.min(1, (progress - 0.25) / 0.45)));
                    yield new SwingPose(phase, type, back.yaw() + (STAGGER_YAW - back.yaw()) * over,
                            back.pitch() + (STAGGER_PITCH - back.pitch()) * over, back.extension() + (0.8 - back.extension()) * over,
                            1 - smooth(progress), parriedAt, progress, elapsed, spec, path);
                }
                default -> {
                    // Recovery returns from the end of the arc, or from where a thwack stopped the blade.
                    double from = data.machine.recoverFrom();
                    ArcPath.Keyframe end = path.sample(from);
                    if (type == AttackType.THROW) end = new ArcPath.Keyframe(from, 0, 50, 1);
                    yield new SwingPose(phase, type, end.yaw(), end.pitch(), end.extension(), 1 - smooth(progress), from,
                            progress, elapsed, spec, path);
                }
            };
        });
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }
}

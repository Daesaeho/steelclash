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
 * Placeholder visuals for M1/M2; M4 replaces them with authored animations.
 *
 * @param weight how much this pose overrides the vanilla arm pose (eases in and out)
 */
public record SwingPose(Phase phase, AttackType type, double yaw, double pitch, double extension, double weight, double releaseProgress,
                        WeaponProfile.AttackSpec spec) {
    /** Guard: weapon raised across the body. */
    private static final double GUARD_YAW = -15;
    private static final double GUARD_PITCH = -35;
    private static final int GUARD_RAISE_TICKS = 3;

    public static Optional<SwingPose> of(LivingEntity entity, CombatData data, float partialTick) {
        Phase phase = data.machine.phase();
        if (phase == Phase.IDLE) {
            return Optional.empty();
        }
        return Combat.currentSpec(entity, data).map(spec -> {
            ArcPath path = Combat.currentPath(data, spec);
            AttackType type = data.machine.type();
            double progress = data.machine.phaseProgress(partialTick);
            return switch (phase) {
                case WINDUP -> {
                    // Draw the weapon back past the start of the arc so the attack direction reads clearly.
                    // Heavies draw back further, so they read as heavies.
                    ArcPath.Keyframe start = path.sample(0);
                    double drawBack = data.machine.isHeavy() ? 1.6 : 1.25;
                    yield new SwingPose(phase, type, start.yaw() * drawBack, start.pitch() - (data.machine.isHeavy() ? 25 : 10),
                            start.extension() * 0.6, smooth(progress), 0, spec);
                }
                case RELEASE -> {
                    ArcPath.Keyframe k = path.sample(progress);
                    yield new SwingPose(phase, type, k.yaw(), k.pitch(), k.extension(), 1, progress, spec);
                }
                case PARRY -> {
                    double raise = Math.min(1, (data.machine.phaseTick() + partialTick) / GUARD_RAISE_TICKS);
                    yield new SwingPose(phase, type, GUARD_YAW, GUARD_PITCH, 0.8, smooth(raise), 0, spec);
                }
                case GUARD_RECOVERY -> new SwingPose(phase, type, GUARD_YAW, GUARD_PITCH, 0.8, 1 - smooth(progress), 0, spec);
                case STAGGER -> new SwingPose(phase, type, 50, -60, 0.8, 1 - smooth(progress), 0, spec);
                default -> {
                    // Recovery returns from the end of the arc, or from where a thwack stopped the blade.
                    double from = data.machine.recoverFrom();
                    ArcPath.Keyframe end = path.sample(from);
                    yield new SwingPose(phase, type, end.yaw(), end.pitch(), end.extension(), 1 - smooth(progress), from, spec);
                }
            };
        });
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }
}

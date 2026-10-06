package com.steelclash.combat;

import com.steelclash.core.ArcPath;
import com.steelclash.core.Blade;
import com.steelclash.core.CombatStateMachine;
import com.steelclash.core.Vec;
import com.steelclash.profile.WeaponProfile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Server-side hit detection: sweeps the blade through this tick's slice of the arc and collects new targets. */
public final class SwingTracer {
    /** Blade samples per tick. 6 keeps the gap between samples well under a mob's width at normal reach. */
    private static final int SUB_STEPS = 6;
    private static final double BLADE_RADIUS = 0.1;

    private SwingTracer() {
    }

    /** Returns newly hit targets in the order the blade reached them, respecting the attack's max target count. */
    public static List<LivingEntity> trace(LivingEntity attacker, CombatData data, WeaponProfile.AttackSpec spec,
                                           CombatStateMachine.Sweep sweep) {
        int remaining = spec.maxTargets() - data.hitThisSwing.size();
        List<LivingEntity> hits = new ArrayList<>();
        if (remaining <= 0) {
            return hits;
        }

        ArcPath path = spec.arc().toPath();
        double length = CombatMath.bladeLength(attacker, spec);
        Vec3 pivotNow = CombatMath.pivot(attacker, 1f);
        float yawNow = CombatMath.viewYaw(attacker);
        float pitchNow = attacker.getXRot();

        AABB searchBox = new AABB(pivotNow, pivotNow).inflate(length + 1.0).minmax(new AABB(data.prevPivot, data.prevPivot));
        List<LivingEntity> candidates = attacker.level().getEntitiesOfClass(LivingEntity.class, searchBox,
                target -> isValidTarget(attacker, target) && !data.hitThisSwing.contains(target.getId()));
        if (candidates.isEmpty()) {
            return hits;
        }

        // Include the very first blade position on the first release tick, otherwise only the swept positions.
        int firstStep = sweep.from() == 0 ? 0 : 1;
        for (int step = firstStep; step <= SUB_STEPS && hits.size() < remaining; step++) {
            float f = step / (float) SUB_STEPS;
            double t = Mth.lerp(f, sweep.from(), sweep.to());
            double yaw = Mth.rotLerp(f, data.prevYaw, yawNow);
            double pitch = Mth.lerp(f, data.prevPitch, pitchNow);
            Vec pivot = CombatMath.toVec(data.prevPivot.lerp(pivotNow, f));
            Blade.Segment blade = Blade.at(pivot, yaw, pitch, path, t, length);

            List<LivingEntity> stepHits = new ArrayList<>();
            for (LivingEntity target : candidates) {
                if (data.hitThisSwing.contains(target.getId())) {
                    continue;
                }
                AABB box = target.getBoundingBox().inflate(BLADE_RADIUS + target.getPickRadius());
                if (Blade.intersectsBox(blade.hilt(), blade.tip(),
                        new Vec(box.minX, box.minY, box.minZ), new Vec(box.maxX, box.maxY, box.maxZ))
                        && canReach(attacker, CombatMath.toVec3(pivot), target)) {
                    stepHits.add(target);
                }
            }
            Vec3 pivot3 = CombatMath.toVec3(pivot);
            stepHits.sort(Comparator.comparingDouble(e -> e.getBoundingBox().getCenter().distanceToSqr(pivot3)));
            for (LivingEntity target : stepHits) {
                if (hits.size() >= remaining) {
                    break;
                }
                data.hitThisSwing.add(target.getId());
                hits.add(target);
            }
        }
        return hits;
    }

    private static boolean isValidTarget(LivingEntity attacker, LivingEntity target) {
        if (target == attacker || !target.isAlive() || !target.isPickable() || target.isSpectator()) {
            return false;
        }
        if (attacker.isPassengerOfSameVehicle(target) || attacker.isAlliedTo(target)) {
            return false;
        }
        // Don't cut down your own pets.
        return !(target instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null
                && ownable.getOwnerUUID().equals(attacker.getUUID()));
    }

    /** No hitting through walls: the target's center or eyes must be visible from the blade pivot. */
    private static boolean canReach(Entity attacker, Vec3 pivot, LivingEntity target) {
        return isClear(attacker, pivot, target.getBoundingBox().getCenter()) || isClear(attacker, pivot, target.getEyePosition());
    }

    private static boolean isClear(Entity attacker, Vec3 from, Vec3 to) {
        return attacker.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, attacker))
                .getType() == HitResult.Type.MISS;
    }
}

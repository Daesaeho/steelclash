package com.steelclash.combat;

import com.steelclash.Config;
import com.steelclash.core.ArcPath;
import com.steelclash.core.Blade;
import com.steelclash.core.CombatStateMachine;
import com.steelclash.core.Vec;
import com.steelclash.profile.WeaponProfile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Server-side hit detection: sweeps the blade through this tick's slice of the arc and collects new targets. */
public final class SwingTracer {
    /** Blade samples per tick. 6 keeps the gap between samples well under a mob's width at normal reach. */
    private static final int SUB_STEPS = 6;
    private static final double BLADE_RADIUS = 0.1;
    /** Clank only checks the middle of the swing: the start is still the weapon being raised, the end is follow-through. */
    private static final double CLANK_FROM = 0.15;
    private static final double CLANK_UNTIL = 0.85;
    /**
     * Physical weapon length (arm + blade) used for clank checks. Reach (3+ blocks) is deliberately generous for hit
     * detection, but a 3-block steel bar would scrape every ceiling.
     */
    private static final double CLANK_LENGTH = 2.0;
    /** Generous per-tick movement bound, to widen the candidate search when targets are rewound. */
    private static final double MAX_SPEED_PER_TICK = 1.0;

    private SwingTracer() {
    }

    /**
     * @param hits  newly hit targets in the order the blade reached them (respects the attack's max target count)
     * @param clank where the blade struck a wall or obstacle this tick, if it did; the swing should stop there
     */
    public record Result(List<LivingEntity> hits, @Nullable Vec3 clank) {
    }

    /** A possible target and where the attacker saw it, as an offset from where it is now. */
    private record Candidate(LivingEntity target, Vec3 offset) {
        AABB box() {
            return target.getBoundingBox().move(offset);
        }
    }

    public static Result trace(LivingEntity attacker, CombatData data, WeaponProfile.AttackSpec spec,
                               CombatStateMachine.Sweep sweep) {
        int remaining = spec.maxTargets() - data.hitThisSwing.size();
        List<LivingEntity> hits = new ArrayList<>();

        ArcPath path = Combat.currentPath(data, spec);
        double length = CombatMath.bladeLength(attacker, spec) + (data.lunge ? Config.LUNGE_REACH_BONUS.get() : 0);
        Vec3 pivotNow = CombatMath.pivot(attacker, 1f);
        float yawNow = CombatMath.viewYaw(attacker);
        float pitchNow = attacker.getXRot();

        // A lagged attacker swings at targets as their screen showed them: trace those against where they were then.
        int rewind = LagCompensation.rewindTicks(attacker);
        AABB searchBox = new AABB(pivotNow, pivotNow).inflate(length + 1.0 + rewind * MAX_SPEED_PER_TICK)
                .minmax(new AABB(data.prevPivot, data.prevPivot));
        List<Candidate> candidates = attacker.level().getEntitiesOfClass(LivingEntity.class, searchBox,
                        target -> isValidTarget(attacker, target) && !data.hitThisSwing.contains(target.getId()))
                .stream().map(target -> new Candidate(target, LagCompensation.rewindOffset(target, rewind))).toList();

        // Include the very first blade position on the first release tick, otherwise only the swept positions.
        int firstStep = sweep.from() == 0 ? 0 : 1;
        for (int step = firstStep; step <= SUB_STEPS; step++) {
            float f = step / (float) SUB_STEPS;
            double t = Mth.lerp(f, sweep.from(), sweep.to());
            double yaw = Mth.rotLerp(f, data.prevYaw, yawNow);
            double pitch = Mth.lerp(f, data.prevPitch, pitchNow);
            Vec pivot = CombatMath.toVec(data.prevPivot.lerp(pivotNow, f));
            Blade.Segment blade = Blade.at(pivot, yaw, pitch, path, t, length);
            Vec3 pivot3 = CombatMath.toVec3(pivot);

            boolean hitThisStep = false;
            if (hits.size() < remaining) {
                List<Candidate> stepHits = new ArrayList<>();
                for (Candidate candidate : candidates) {
                    if (data.hitThisSwing.contains(candidate.target().getId())) {
                        continue;
                    }
                    AABB box = candidate.box().inflate(BLADE_RADIUS + candidate.target().getPickRadius());
                    if (Blade.intersectsBox(blade.hilt(), blade.tip(),
                            new Vec(box.minX, box.minY, box.minZ), new Vec(box.maxX, box.maxY, box.maxZ))
                            && canReach(attacker, pivot3, candidate)) {
                        stepHits.add(candidate);
                    }
                }
                stepHits.sort(Comparator.comparingDouble(c -> c.box().getCenter().distanceToSqr(pivot3)));
                for (Candidate candidate : stepHits) {
                    if (hits.size() >= remaining) {
                        break;
                    }
                    data.hitThisSwing.add(candidate.target().getId());
                    hits.add(candidate.target());
                    hitThisStep = true;
                }
            }

            // A blade that stopped in a body this step doesn't also clank on the wall behind it.
            if (t >= CLANK_FROM && t <= CLANK_UNTIL && !hitThisStep) {
                Vec dir = blade.tip().subtract(blade.hilt());
                double len = dir.length();
                Vec tip = len > CLANK_LENGTH ? blade.hilt().add(dir.scale(CLANK_LENGTH / len)) : blade.tip();
                Vec3 clank = wallHit(attacker, pivot3, CombatMath.toVec3(tip));
                if (clank != null) {
                    return new Result(hits, clank);
                }
            }
        }
        return new Result(hits, null);
    }

    /** Where the blade meets a wall or obstacle. Floors (top faces) don't count, so low swings don't snag the ground. */
    @Nullable
    private static Vec3 wallHit(Entity attacker, Vec3 from, Vec3 to) {
        BlockHitResult hit = attacker.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, attacker));
        if (hit.getType() != HitResult.Type.BLOCK || hit.getDirection() == Direction.UP) {
            return null;
        }
        return hit.getLocation();
    }

    private static boolean isValidTarget(LivingEntity attacker, LivingEntity target) {
        if (target == attacker || !target.isAlive() || !target.isPickable() || target.isSpectator()) {
            return false;
        }
        if (attacker.isPassengerOfSameVehicle(target) || attacker.isAlliedTo(target)) {
            return false;
        }
        // Monsters don't cut each other down (vanilla mobs never melee each other either): a hostile mob's arc only
        // hurts other hostiles if that's who it's actually fighting. Otherwise a horde would brawl among itself.
        if (attacker instanceof Enemy && target instanceof Enemy && attacker instanceof Mob mob && mob.getTarget() != target) {
            return false;
        }
        // Don't cut down your own pets.
        return !(target instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null
                && ownable.getOwnerUUID().equals(attacker.getUUID()));
    }

    /** No hitting through walls: the target's center or eyes must be visible from the blade pivot. */
    private static boolean canReach(Entity attacker, Vec3 pivot, Candidate candidate) {
        return isClear(attacker, pivot, candidate.box().getCenter())
                || isClear(attacker, pivot, candidate.target().getEyePosition().add(candidate.offset()));
    }

    private static boolean isClear(Entity attacker, Vec3 from, Vec3 to) {
        return attacker.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, attacker))
                .getType() == HitResult.Type.MISS;
    }
}

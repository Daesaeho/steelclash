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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Server-side hit detection: sweeps the blade through this tick's slice of the arc and collects new targets. */
public final class SwingTracer {
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
    /**
     * Swept blade: the tip may move at most this far between two samples (blocks). Less than the narrowest hitbox the
     * blade is tested against (a 0.3-wide body, inflated by the blade's radius on both sides), so nothing is skipped.
     */
    private static final double MAX_TIP_GAP = 0.5;
    /** Bound on the samples added between two regular ones. */
    private static final int MAX_SPLITS = 16;

    private SwingTracer() {
    }

    /**
     * @param contacts newly hit targets in the order the blade reached them (respects the attack's max target count)
     * @param clank    where the blade struck a wall or obstacle this tick, if it did; the swing should stop there
     */
    public record Result(List<Contact> contacts, @Nullable Vec3 clank) {
    }

    /**
     * A body the blade reached, and the release progress (0 to 1) where it did.
     *
     * @param offset where the blade first crossed into the body, on or inside its hitbox, relative to the body's
     *               position: a hit delivered later (lag compensation) still lands where the blade met the body
     * @param motion which way that point of the blade was travelling (not normalised; may be zero)
     */
    public record Contact(LivingEntity target, double progress, Vec3 offset, Vec3 motion) {
        /** The contact point, on the body where it stands now. */
        public Vec3 point() {
            return target.position().add(offset);
        }

        /** The same contact with the blade at another point of its release (where it has got to since). */
        public Contact atProgress(double newProgress) {
            return new Contact(target, newProgress, offset, motion);
        }
    }

    /** Geometry is fixed for this trace; reuse it for every blade sample and distance comparison. */
    private record Candidate(LivingEntity target, Vec3 offset, AABB hitBox, AABB body, Vec3 center) {
        static Candidate of(LivingEntity target, int rewind, double duck) {
            Vec3 offset = LagCompensation.rewindOffset(target, rewind);
            AABB box = target.getBoundingBox().move(offset);
            // Ducking (Chivalry 2): a crouching fighter is short enough for level slashes to pass over.
            if (duck > 0 && target.isCrouching()) {
                box = box.setMaxY(Math.min(box.maxY, box.minY + duck));
            }
            return new Candidate(target, offset, box.inflate(BLADE_RADIUS + target.getPickRadius()), box, box.getCenter());
        }
    }

    public static Result trace(LivingEntity attacker, CombatData data, WeaponProfile.AttackSpec spec,
                               CombatStateMachine.Sweep sweep) {
        CombatProfiler.begin(CombatProfiler.Section.NARROW);
        try {
            return traceSwept(attacker, data, spec, sweep);
        } finally {
            CombatProfiler.end(CombatProfiler.Section.NARROW);
        }
    }

    private static Result traceSwept(LivingEntity attacker, CombatData data, WeaponProfile.AttackSpec spec,
                                     CombatStateMachine.Sweep sweep) {
        int remaining = spec.maxTargets() - data.hitThisSwing.size();
        boolean checkClanks = Config.ENVIRONMENT_CLANK.get();
        if (remaining <= 0 && !checkClanks) {
            return new Result(List.of(), null);
        }
        List<Contact> hits = new ArrayList<>();

        ArcPath path = Combat.currentPath(data, spec);
        double length = CombatMath.bladeLength(attacker, spec) + (data.lunge ? Config.LUNGE_REACH_BONUS.get() : 0);
        Vec3 pivotNow = CombatMath.pivot(attacker, 1f);
        float[] view = CombatMath.swingView(attacker, data, true); // turn-capped during the swing
        float yawNow = view[0];
        float pitchNow = view[1];

        // A lagged attacker swings at targets as their screen showed them: trace those against where they were then.
        int rewind = LagCompensation.rewindTicks(attacker);
        AABB searchBox = new AABB(pivotNow, pivotNow).inflate(length + 1.0 + rewind * MAX_SPEED_PER_TICK)
                .minmax(new AABB(data.prevPivot, data.prevPivot));
        CombatProfiler.begin(CombatProfiler.Section.BROAD);
        List<Candidate> candidates = List.of();
        try {
            if (remaining > 0) {
                List<LivingEntity> targets = attacker.level().getEntitiesOfClass(LivingEntity.class, searchBox,
                        target -> isValidTarget(attacker, target) && !data.hitThisSwing.contains(target.getId()));
                candidates = new ArrayList<>(targets.size());
                double duck = Config.DUCK_HEIGHT.get();
                for (LivingEntity target : targets) {
                    candidates.add(Candidate.of(target, rewind, duck));
                }
            }
        } finally {
            CombatProfiler.end(CombatProfiler.Section.BROAD);
        }
        CombatProfiler.count(CombatProfiler.Counter.CANDIDATES, candidates.size());
        if (candidates.isEmpty() && !checkClanks) {
            return new Result(hits, null);
        }

        List<Candidate> stepHits = new ArrayList<>();
        int steps = Config.BLADE_SAMPLES_PER_TICK.get();
        boolean swept = Config.SWEPT_BLADE.get();
        // Include the very first blade position on the first release tick, otherwise only the swept positions.
        int firstStep = sweep.from() == 0 ? 0 : 1;
        Blade.Segment previous = firstStep == 0 ? null : bladeAt(data, path, sweep, pivotNow, yawNow, pitchNow, length, 0);
        for (int step = firstStep; step <= steps; step++) {
            float f = step / (float) steps;
            double t = Mth.lerp(f, sweep.from(), sweep.to());
            Blade.Segment blade = bladeAt(data, path, sweep, pivotNow, yawNow, pitchNow, length, f);
            // Swept blade: where the tip would jump further than a thin body is wide, more samples go in along its path.
            int splits = swept && previous != null
                    ? Math.max(1, Math.min(MAX_SPLITS, (int) Math.ceil(tipGap(previous, blade) / MAX_TIP_GAP))) : 1;
            float stepFrom = (step - 1) / (float) steps;
            boolean hitThisStep = false;
            for (int split = 1; split <= splits; split++) {
                float sampleAt = split == splits ? f : Mth.lerp(split / (float) splits, stepFrom, f);
                Blade.Segment sample = split == splits ? blade
                        : bladeAt(data, path, sweep, pivotNow, yawNow, pitchNow, length, sampleAt);
                if (hits.size() < remaining) {
                    Vec3 hilt = CombatMath.toVec3(sample.hilt());
                    stepHits.clear();
                    for (Candidate candidate : candidates) {
                        if (data.hitThisSwing.contains(candidate.target().getId())) {
                            continue;
                        }
                        AABB box = candidate.hitBox();
                        if (Blade.intersectsBox(sample.hilt(), sample.tip(),
                                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)
                                && canReach(attacker, hilt, candidate)) {
                            stepHits.add(candidate);
                        }
                    }
                    stepHits.sort(Comparator.comparingDouble(c -> c.center().distanceToSqr(hilt)));
                    for (Candidate candidate : stepHits) {
                        if (hits.size() >= remaining) {
                            break;
                        }
                        data.hitThisSwing.add(candidate.target().getId());
                        // The swing's very first sample measures the blade's travel against the next one.
                        Blade.Segment other = previous != null ? previous
                                : bladeAt(data, path, sweep, pivotNow, yawNow, pitchNow, length, 1f / steps);
                        hits.add(contact(candidate, sample, other, previous == null,
                                Mth.lerp(sampleAt, sweep.from(), sweep.to())));
                        hitThisStep = true;
                    }
                }
                previous = sample;
            }

            // A blade that stopped in a body this step doesn't also clank on the wall behind it.
            if (checkClanks && t >= CLANK_FROM && t <= CLANK_UNTIL && !hitThisStep) {
                Vec dir = blade.tip().subtract(blade.hilt());
                double len = dir.length();
                Vec tip = len > CLANK_LENGTH ? blade.hilt().add(dir.scale(CLANK_LENGTH / len)) : blade.tip();
                CombatProfiler.begin(CombatProfiler.Section.WORLD);
                Vec3 clank;
                try {
                    clank = wallHit(attacker, CombatMath.toVec3(blade.hilt()), CombatMath.toVec3(tip));
                } finally {
                    CombatProfiler.end(CombatProfiler.Section.WORLD);
                }
                if (clank != null) {
                    return new Result(hits, clank);
                }
            }
        }
        return new Result(hits, null);
    }

    /** The blade a fraction {@code f} of the way through this tick's sweep, from last tick's view and pivot to this one's. */
    private static Blade.Segment bladeAt(CombatData data, ArcPath path, CombatStateMachine.Sweep sweep, Vec3 pivotNow,
                                         float yawNow, float pitchNow, double length, float f) {
        double t = Mth.lerp(f, sweep.from(), sweep.to());
        double yaw = Mth.rotLerp(f, data.prevYaw, yawNow);
        double pitch = Mth.lerp(f, data.prevPitch, pitchNow);
        return Blade.at(CombatMath.toVec(data.prevPivot.lerp(pivotNow, f)), yaw, pitch, path, t, length);
    }

    /** How far the tip moved from one sample to the next, blocks. */
    private static double tipGap(Blade.Segment from, Blade.Segment to) {
        double dx = to.tip().x() - from.tip().x();
        double dy = to.tip().y() - from.tip().y();
        double dz = to.tip().z() - from.tip().z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Where the blade crossed into the body (the first point of it inside the hitbox, kept on the body) and which way
     * that point of the blade was moving, measured against the neighbouring sample ({@code other}, which comes after
     * {@code blade} when {@code otherIsLater}).
     */
    private static Contact contact(Candidate candidate, Blade.Segment blade, Blade.Segment other, boolean otherIsLater,
                                   double progress) {
        AABB box = candidate.hitBox();
        double entry = Blade.entry(blade.hilt(), blade.tip(), box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
        double along = Double.isNaN(entry) ? 1 : entry;
        Vec here = blade.along(along);
        AABB body = candidate.body();
        LivingEntity target = candidate.target();
        // The body here may be rewound (lag compensation); relative to it, the point is the same as on the body now.
        Vec3 offset = new Vec3(Mth.clamp(here.x(), body.minX, body.maxX), Mth.clamp(here.y(), body.minY, body.maxY),
                Mth.clamp(here.z(), body.minZ, body.maxZ)).subtract(candidate.offset()).subtract(target.position());
        Vec there = other.along(along);
        Vec travel = otherIsLater ? there.subtract(here) : here.subtract(there);
        if (travel.dot(travel) < 1e-8) {
            travel = blade.tip().subtract(blade.hilt()); // a still blade pushes along itself
        }
        return new Contact(target, progress, offset, CombatMath.toVec3(travel));
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
        if (attacker.isPassengerOfSameVehicle(target)) {
            return false;
        }
        // Monsters don't cut each other down (vanilla mobs never melee each other either): a hostile mob's arc only
        // hurts other hostiles if that's who it's actually fighting. Otherwise a horde would brawl among itself.
        if (attacker instanceof Enemy && target instanceof Enemy && attacker instanceof Mob mob && mob.getTarget() != target) {
            return false;
        }
        // Allies' bodies are in the way (Chivalry 2) unless friendly collision is off; pets are never cut down.
        if (Allies.areAllies(attacker, target)) {
            return Config.FRIENDLY_COLLISION.get();
        }
        return !Allies.isFriendlyPet(attacker, target);
    }

    /** No hitting through walls: the target's center or eyes must be visible from the blade pivot. */
    private static boolean canReach(Entity attacker, Vec3 pivot, Candidate candidate) {
        return isClear(attacker, pivot, candidate.center())
                || isClear(attacker, pivot, candidate.target().getEyePosition().add(candidate.offset()));
    }

    private static boolean isClear(Entity attacker, Vec3 from, Vec3 to) {
        return attacker.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, attacker))
                .getType() == HitResult.Type.MISS;
    }
}

package com.steelclash.ai;

import com.steelclash.combat.CombatData;
import com.steelclash.combat.ModAttachments;
import com.steelclash.core.BotStyle;
import com.steelclash.profile.WeaponProfiles;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Takes over movement while the bot brain wants to hold back: varied footwork just outside the opponent's reach
 * (circle, hold, backpedal, a baiting step in), spreading around the target instead of bunching, and backing off to
 * evade or catch its breath. Style per mob type ({@link BotStyles}) plus a small per-mob jitter keep a group from
 * moving in lockstep. Runs at a high priority so it interrupts the vanilla melee goal (both use MOVE and LOOK); when
 * the brain engages again, this goal stops and vanilla pathing resumes.
 */
public class ClashSpacingGoal extends Goal {
    private static final double MAX_RANGE = 10;
    private static final double NEIGHBOUR_RANGE = 6;
    /** Waiting bots closer than this angle around the target spread apart. */
    private static final double SPREAD_DEGREES = 60;

    private final PathfinderMob mob;

    public ClashSpacingGoal(PathfinderMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive() || !ClashBrain.manages(mob) || mob.distanceTo(target) > MAX_RANGE) {
            return false;
        }
        CombatData data = mob.getData(ModAttachments.COMBAT);
        return data.brain != null && data.brain.wantsSpace;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        mob.getNavigation().stop();
    }

    @Override
    public void stop() {
        mob.getMoveControl().strafe(0, 0);
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        BrainState brain = mob.getData(ModAttachments.COMBAT).brain;
        if (target == null || brain == null) {
            return;
        }
        mob.getLookControl().setLookAt(target, 30, 30);
        mob.lookAt(target, 30, 30);

        BotStyle style = BotStyles.of(mob);
        double jitter = BotStyle.jitter(mob.getUUID().getLeastSignificantBits());
        double reach = WeaponProfiles.resolveFor(mob).map(r -> ClashBrain.reach(mob, r.profile())).orElse(2.0);
        double hold = reach + style.holdExtra() + jitter * 0.3;
        double distance = ClashBrain.edgeDistance(mob, target);
        float keepDistance = distance > hold + 0.4 ? 0.5f : distance < hold - 0.4 ? -0.6f : 0f;
        float side = (float) (style.strafeSpeed() * (1 + jitter * 0.2)) * brain.strafeDirection;

        // Evading or out of breath: back away, drifting sideways.
        if (mob.tickCount < brain.evadeUntil || brain.lowStamina) {
            mob.getMoveControl().strafe(distance < hold + 1.5 ? -1.0f : 0f, side * 0.5f);
            return;
        }

        if (mob.tickCount >= brain.footworkUntil) {
            brain.footwork = style.chooseFootwork(mob.getRandom().nextDouble());
            brain.footworkUntil = mob.tickCount + 20 + mob.getRandom().nextInt(30);
            brain.strafeDirection = spreadDirection(target, brain.strafeDirection);
        }
        if (mob.horizontalCollision) {
            brain.strafeDirection = -brain.strafeDirection;
        }

        switch (brain.footwork) {
            case CIRCLE -> mob.getMoveControl().strafe(keepDistance, side);
            case HOLD -> mob.getMoveControl().strafe(keepDistance, 0);
            case BACKPEDAL -> mob.getMoveControl().strafe(distance < hold + 1.0 ? -0.6f : 0f, side * 0.3f);
            case FEINT_STEP -> {
                // Step in for the first half, then back out: baits a whiff or an early parry.
                boolean stepIn = brain.footworkUntil - mob.tickCount > 12;
                mob.getMoveControl().strafe(stepIn ? 0.6f : -0.6f, 0);
            }
        }
    }

    /**
     * Circle away from the nearest other bot fighting the same target, so waiting bots fan out around it.
     * Minecraft's sideways input is positive toward the mob's left.
     */
    private int spreadDirection(LivingEntity target, int current) {
        double fx = target.getX() - mob.getX();
        double fz = target.getZ() - mob.getZ();
        Mob closest = null;
        double closestAngle = Double.MAX_VALUE;
        double myAngle = Math.atan2(mob.getZ() - target.getZ(), mob.getX() - target.getX());
        for (Mob other : mob.level().getEntitiesOfClass(Mob.class, target.getBoundingBox().inflate(NEIGHBOUR_RANGE),
                o -> o != mob && o.getTarget() == target && o.isAlive())) {
            double angle = Math.atan2(other.getZ() - target.getZ(), other.getX() - target.getX());
            double delta = Math.abs(Math.toDegrees(wrap(angle - myAngle)));
            if (delta < closestAngle) {
                closestAngle = delta;
                closest = other;
            }
        }
        if (closest == null || closestAngle > SPREAD_DEGREES) {
            return mob.getRandom().nextInt(4) == 0 ? -current : current;
        }
        // cross > 0: the neighbour is on our right (facing the target), so move left (positive), and vice versa.
        double cross = fx * (closest.getZ() - mob.getZ()) - fz * (closest.getX() - mob.getX());
        return cross > 0 ? 1 : -1;
    }

    private static double wrap(double radians) {
        while (radians > Math.PI) {
            radians -= 2 * Math.PI;
        }
        while (radians < -Math.PI) {
            radians += 2 * Math.PI;
        }
        return radians;
    }
}

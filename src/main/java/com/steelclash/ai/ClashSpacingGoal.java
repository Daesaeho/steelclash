package com.steelclash.ai;

import com.steelclash.combat.CombatData;
import com.steelclash.combat.ModAttachments;
import com.steelclash.profile.WeaponProfiles;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Takes over movement while the bot brain wants to hold back: keep just outside the opponent's reach and circle,
 * like Chivalry 2 bots waiting for their turn. Runs at a high priority so it interrupts the vanilla melee goal (both
 * use the MOVE and LOOK flags); when the brain wants to engage again, this goal stops and vanilla pathing resumes.
 */
public class ClashSpacingGoal extends Goal {
    private static final double HOLD_DISTANCE_EXTRA = 1.2;
    private static final double MAX_RANGE = 10;

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
        if (target == null) {
            return;
        }
        CombatData data = mob.getData(ModAttachments.COMBAT);
        BrainState brain = data.brain;
        mob.getLookControl().setLookAt(target, 30, 30);
        mob.lookAt(target, 30, 30);

        double reach = WeaponProfiles.resolveFor(mob).map(r -> ClashBrain.reach(mob, r.profile())).orElse(2.0);
        double hold = reach + HOLD_DISTANCE_EXTRA;
        double distance = ClashBrain.edgeDistance(mob, target);
        float forward = distance > hold + 0.4 ? 0.5f : distance < hold - 0.4 ? -0.6f : 0f;

        if (brain != null) {
            if (mob.tickCount >= brain.strafeFlipAt || mob.horizontalCollision) {
                brain.strafeDirection = -brain.strafeDirection;
                brain.strafeFlipAt = mob.tickCount + 40 + mob.getRandom().nextInt(50);
            }
            mob.getMoveControl().strafe(forward, 0.45f * brain.strafeDirection);
        } else {
            mob.getMoveControl().strafe(forward, 0);
        }
    }
}

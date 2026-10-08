package com.steelclash.ai;

import com.steelclash.Config;
import com.steelclash.combat.CombatData;
import com.steelclash.combat.ModAttachments;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import org.jetbrains.annotations.Nullable;

/**
 * A disarmed mob runs back to the weapon it dropped and picks it up (Chivalry 2: a disarmed fighter goes for their
 * weapon). Only its own weapon: {@link com.steelclash.combat.Disarm} remembers which drop that is. It gives up when the
 * weapon is gone (a player took it), out of reach, or after {@link #GIVE_UP_TICKS}. Runs above the spacing goal and the
 * vanilla melee goal (both use MOVE and LOOK), so the run isn't fought over.
 */
public class RetrieveWeaponGoal extends Goal {
    public static final int GIVE_UP_TICKS = 200;
    private static final double MAX_DISTANCE = 24;
    private static final double PICKUP_DISTANCE = 1.5;
    private static final double SPEED = 1.25;

    private final PathfinderMob mob;

    public RetrieveWeaponGoal(PathfinderMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return weapon() != null;
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
        ItemEntity drop = weapon();
        if (drop != null) {
            mob.getNavigation().moveTo(drop, SPEED);
        }
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        ItemEntity drop = weapon();
        if (drop == null) {
            return;
        }
        mob.getLookControl().setLookAt(drop);
        if (mob.distanceToSqr(drop) <= PICKUP_DISTANCE * PICKUP_DISTANCE) {
            mob.setItemSlot(EquipmentSlot.MAINHAND, drop.getItem().copy());
            mob.take(drop, drop.getItem().getCount());
            drop.discard();
            mob.getData(ModAttachments.COMBAT).lostWeapon = null;
            mob.getNavigation().stop();
        } else if (mob.getNavigation().isDone() || mob.tickCount % 10 == 0) {
            mob.getNavigation().moveTo(drop, SPEED);
        }
    }

    /** The mob's own dropped weapon, while it's worth going for; clears the memory once it isn't. */
    @Nullable
    private ItemEntity weapon() {
        CombatData data = mob.getData(ModAttachments.COMBAT);
        if (data.lostWeapon == null) {
            return null;
        }
        boolean stillWanted = Config.MOBS_RETRIEVE_WEAPONS.get() && mob.getMainHandItem().isEmpty()
                && mob.level().getGameTime() - data.lostWeaponAt <= GIVE_UP_TICKS;
        if (stillWanted && mob.level() instanceof ServerLevel level && level.getEntity(data.lostWeapon) instanceof ItemEntity drop
                && drop.isAlive() && mob.distanceToSqr(drop) <= MAX_DISTANCE * MAX_DISTANCE) {
            return drop;
        }
        data.lostWeapon = null;
        return null;
    }
}

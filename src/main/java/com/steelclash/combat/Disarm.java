package com.steelclash.combat;

import com.steelclash.Config;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Chivalry 2 disarm: parrying with no stamina knocks the weapon out of your hands.
 * <p>
 * The dropped weapon can never be picked up by mobs (so a zombie can't steal your sword); players pick it up by
 * walking over it, handled in {@link #tryPickUp}.
 */
public final class Disarm {
    public static final String DISARMED_TAG = "steelclash_disarmed";
    private static final int PICKUP_GRACE_TICKS = 15;

    private Disarm() {
    }

    public static void disarm(LivingEntity victim) {
        ItemStack weapon = victim.getMainHandItem();
        if (weapon.isEmpty()) {
            return;
        }
        if (Config.DISARM_MODE.get() == Config.DisarmMode.HOLSTER) {
            int ticks = Config.HOLSTER_TICKS.get();
            if (victim instanceof Player player) {
                player.getCooldowns().addCooldown(weapon.getItem(), ticks);
            } else {
                victim.getData(ModAttachments.COMBAT).holsteredUntil = victim.level().getGameTime() + ticks;
            }
            return;
        }

        victim.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        Vec3 look = victim.getLookAngle();
        ItemEntity drop = new ItemEntity(victim.level(), victim.getX(), victim.getEyeY() - 0.3, victim.getZ(), weapon);
        drop.setDeltaMovement(look.x * 0.25 + (victim.getRandom().nextDouble() - 0.5) * 0.1, 0.3,
                look.z * 0.25 + (victim.getRandom().nextDouble() - 0.5) * 0.1);
        drop.setNeverPickUp(); // blocks vanilla pickup by everyone, including mobs; players use tryPickUp
        drop.setThrower(victim);
        drop.getPersistentData().putBoolean(DISARMED_TAG, true);
        victim.level().addFreshEntity(drop);
        victim.level().playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                SoundEvents.ITEM_BREAK, victim.getSoundSource(), 0.8f, 1.2f);
    }

    /** Lets players (never mobs) pick up disarmed weapons, straight into an empty main hand when possible. */
    public static void tryPickUp(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()) {
            return;
        }
        List<ItemEntity> drops = player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(1.0, 0.5, 1.0),
                e -> e.isAlive() && e.getAge() > PICKUP_GRACE_TICKS && e.getPersistentData().getBoolean(DISARMED_TAG));
        for (ItemEntity drop : drops) {
            ItemStack stack = drop.getItem();
            int count = stack.getCount();
            if (player.getMainHandItem().isEmpty()) {
                player.setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
                stack.setCount(0);
            } else {
                player.getInventory().add(stack);
            }
            int taken = count - stack.getCount();
            if (taken > 0) {
                player.take(drop, taken);
            }
            if (stack.isEmpty()) {
                drop.discard();
            }
        }
    }
}

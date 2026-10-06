package com.steelclash.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Any weapon, thrown (Chivalry 2 lets you throw whatever you're holding). Hits for the weapon's own melee damage times
 * {@link #THROW_DAMAGE_MULT}, then drops as an item where it lands so it can be picked back up.
 */
public class ThrownWeapon extends ThrowableItemProjectile {
    public static final float THROW_DAMAGE_MULT = 1.2f;
    public static final float SPEED = 1.6f;

    public ThrownWeapon(EntityType<? extends ThrownWeapon> type, Level level) {
        super(type, level);
    }

    public ThrownWeapon(Level level, LivingEntity thrower, ItemStack weapon) {
        super(ModEntities.THROWN_WEAPON.get(), thrower, level);
        setItem(weapon.copyWithCount(1));
    }

    @Override
    protected Item getDefaultItem() {
        return Items.IRON_SWORD;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.05;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity target = result.getEntity();
        if (target == getOwner()) {
            return;
        }
        target.hurt(damageSources().thrown(this, getOwner()), damage());
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_HIT, getSoundSource(), 1f, 1.1f);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CRIT, getX(), getY(), getZ(), 6, 0.1, 0.1, 0.1, 0.2);
            if (result.getType() != HitResult.Type.ENTITY) {
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_HIT_GROUND, getSoundSource(), 1f, 1f);
            }
            // Drop the weapon where it landed so it can be picked up again.
            ItemEntity drop = new ItemEntity(level, getX(), getY(), getZ(), getItem());
            drop.setDeltaMovement(getDeltaMovement().scale(-0.1));
            drop.setDefaultPickUpDelay();
            level.addFreshEntity(drop);
            discard();
        }
    }

    /** The weapon's melee damage (its attack damage modifiers on top of a bare hand), boosted for the throw. */
    private float damage() {
        double[] damage = {1.0};
        getItem().forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.is(Attributes.ATTACK_DAMAGE)) {
                damage[0] += modifier.amount();
            }
        });
        return (float) (damage[0] * THROW_DAMAGE_MULT);
    }
}

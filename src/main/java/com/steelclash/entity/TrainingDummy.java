package com.steelclash.entity;

import com.steelclash.combat.Combat;
import com.steelclash.combat.CombatData;
import com.steelclash.combat.MobCombat;
import com.steelclash.combat.ModAttachments;
import com.steelclash.core.AttackType;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.ItemAbilities;

/**
 * A sparring partner that can't die. Sneak + right-click with an item to arm it (shields go in the offhand),
 * sneak + right-click with an empty hand to cycle modes:
 * <ul>
 *     <li>PASSIVE: just takes hits</li>
 *     <li>PARRY: parries everything aimed at it (holds its shield up if it has one) — use it to test disarms</li>
 *     <li>ATTACK: slashes at the nearest player every few seconds — practice parrying</li>
 *     <li>SPAR: random attacks, parries half the time</li>
 * </ul>
 * Implements {@link Enemy} so right-click (without sneaking) parries at it instead of interacting.
 */
public class TrainingDummy extends PathfinderMob implements Enemy {
    public enum Mode {
        PASSIVE, PARRY, ATTACK, SPAR;

        Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private static final int HEAL_DELAY_TICKS = 60;
    private static final double FACE_RANGE = 8;
    private static final double ATTACK_RANGE = 3.5;

    private Mode mode = Mode.PASSIVE;
    private int nextAttackTick = 40;

    public TrainingDummy(EntityType<? extends TrainingDummy> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        setDropChance(EquipmentSlot.MAINHAND, 0f);
        setDropChance(EquipmentSlot.OFFHAND, 0f);
        updateName();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40)
                .add(Attributes.ATTACK_DAMAGE, 2)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1)
                .add(Attributes.MOVEMENT_SPEED, 0);
    }

    public Mode mode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        if (mode != Mode.PARRY && mode != Mode.SPAR) {
            stopUsingItem();
        }
        updateName();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        // Spar with the nearest player; with no player around, with the nearest hostile mob (handy for arenas).
        LivingEntity nearest = level().getNearestPlayer(this, FACE_RANGE);
        if (nearest == null) {
            nearest = level().getNearestEntity(Monster.class, TargetingConditions.forCombat().range(ATTACK_RANGE + 1),
                    this, getX(), getY(), getZ(), getBoundingBox().inflate(ATTACK_RANGE + 1));
        }
        if (nearest != null && mode != Mode.PASSIVE) {
            double dx = nearest.getX() - getX();
            double dz = nearest.getZ() - getZ();
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
            setYRot(yaw);
            setYHeadRot(yaw);
            yBodyRot = yaw;
            setXRot(0);
        }

        CombatData data = getData(ModAttachments.COMBAT);
        boolean hasShield = getOffhandItem().canPerformAction(ItemAbilities.SHIELD_BLOCK);
        if ((mode == Mode.PARRY || mode == Mode.SPAR) && hasShield) {
            if (!isUsingItem()) {
                startUsingItem(InteractionHand.OFF_HAND);
            }
        } else {
            double chance = mode == Mode.PARRY ? 1.0 : mode == Mode.SPAR ? 0.5 : 0.0;
            MobCombat.tickDefense(this, data, chance);
        }

        if ((mode == Mode.ATTACK || mode == Mode.SPAR) && nearest != null && distanceTo(nearest) <= ATTACK_RANGE
                && tickCount >= nextAttackTick && !data.machine.isBusy()) {
            AttackType type = mode == Mode.ATTACK ? AttackType.SLASH : AttackType.values()[getRandom().nextInt(3)];
            if (isUsingItem()) {
                stopUsingItem();
            }
            Combat.requestAttack(this, type);
            nextAttackTick = tickCount + (mode == Mode.ATTACK ? 50 : 30 + getRandom().nextInt(40));
        }

        if (getLastHurtByMobTimestamp() + HEAL_DELAY_TICKS < tickCount && getHealth() < getMaxHealth()) {
            setHealth(getMaxHealth());
        }
    }

    /** Never dies from combat: damage stops at half a heart. /kill and the void still work. */
    @Override
    protected void actuallyHurt(DamageSource source, float amount) {
        if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            amount = Math.min(amount, getHealth() - 1f);
        }
        super.actuallyHurt(source, Math.max(0, amount));
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown() || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.isEmpty()) {
                setMode(mode.next());
            } else {
                EquipmentSlot slot = held.canPerformAction(ItemAbilities.SHIELD_BLOCK) ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
                stopUsingItem();
                setItemSlot(slot, held.copyWithCount(1));
            }
        }
        return InteractionResult.sidedSuccess(level().isClientSide());
    }

    private void updateName() {
        setCustomName(Component.translatable("entity.steelclash.training_dummy.mode." + mode.name().toLowerCase(Locale.ROOT)));
        setCustomNameVisible(true);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("DummyMode", mode.name());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        try {
            mode = Mode.valueOf(tag.getString("DummyMode"));
        } catch (IllegalArgumentException ignored) {
            mode = Mode.PASSIVE;
        }
        updateName();
    }
}

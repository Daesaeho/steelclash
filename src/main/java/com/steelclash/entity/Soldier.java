package com.steelclash.entity;

import com.steelclash.SteelClash;
import com.steelclash.profile.WeaponProfiles;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.neoforged.neoforge.common.ItemAbilities;
import org.jetbrains.annotations.Nullable;

/**
 * A brigand soldier: the Chivalry 2-style opponents of Steel Clash's PvE. Footmen and knights fight in melee through the
 * bot brain (they're in {@code #steelclash:fighters}); archers shoot. Knights lead patrols (vanilla patrol AI).
 * Gear comes from the item tags {@code #steelclash:soldier_weapons/footman|knight} (vanilla plus Spartan Weaponry).
 */
public class Soldier extends PatrollingMonster implements RangedAttackMob {
    public enum Rank {
        FOOTMAN, KNIGHT, ARCHER
    }

    private static final TagKey<Item> FOOTMAN_WEAPONS = TagKey.create(Registries.ITEM, SteelClash.id("soldier_weapons/footman"));
    private static final TagKey<Item> KNIGHT_WEAPONS = TagKey.create(Registries.ITEM, SteelClash.id("soldier_weapons/knight"));
    private static final float GEAR_DROP_CHANCE = 0.06f;

    public Soldier(EntityType<? extends Soldier> type, Level level, Rank rank) {
        super(type, level);
        this.xpReward = rank == Rank.KNIGHT ? 12 : 7;
    }

    /**
     * The rank comes from the entity type, not a field: {@code Mob}'s constructor calls {@link #registerGoals()} before
     * this class's constructor body runs, so a field would still be unset while the goals are chosen.
     */
    public Rank rank() {
        EntityType<?> type = getType();
        if (type == ModEntities.KNIGHT.get()) {
            return Rank.KNIGHT;
        }
        return type == ModEntities.ARCHER.get() ? Rank.ARCHER : Rank.FOOTMAN;
    }

    public static AttributeSupplier.Builder createAttributes(Rank rank) {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, switch (rank) {
                    case FOOTMAN -> 24;
                    case KNIGHT -> 34;
                    case ARCHER -> 20;
                })
                .add(Attributes.MOVEMENT_SPEED, rank == Rank.KNIGHT ? 0.27 : 0.3)
                .add(Attributes.ATTACK_DAMAGE, rank == Rank.KNIGHT ? 4 : 3)
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.KNOCKBACK_RESISTANCE, rank == Rank.KNIGHT ? 0.3 : 0.0);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals(); // vanilla patrol goal
        goalSelector.addGoal(0, new FloatGoal(this));
        if (rank() == Rank.ARCHER) {
            goalSelector.addGoal(2, new RangedBowAttackGoal<>(this, 1.0, 20, 15f));
        } else {
            goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
        }
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, Soldier.class).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, false));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
    }

    /** Knights lead patrols. */
    @Override
    public boolean canBeLeader() {
        return rank() == Rank.KNIGHT;
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData spawnGroupData) {
        RandomSource random = level.getRandom();
        populateDefaultEquipmentSlots(random, difficulty);
        ItemStack helmet = getItemBySlot(EquipmentSlot.HEAD).copy();
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
        // Vanilla patrol leaders wear the illager banner; brigand knights keep their helmet.
        if (getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof BannerItem) {
            setItemSlot(EquipmentSlot.HEAD, helmet);
        }
        return data;
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        boolean hard = difficulty.getEffectiveDifficulty() > 2.5;
        switch (rank()) {
            case ARCHER -> {
                equip(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                equip(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
                equip(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
            }
            case FOOTMAN -> {
                equip(EquipmentSlot.MAINHAND, pick(FOOTMAN_WEAPONS, random, Items.IRON_SWORD));
                equip(EquipmentSlot.HEAD, new ItemStack(random.nextBoolean() ? Items.CHAINMAIL_HELMET : Items.IRON_HELMET));
                equip(EquipmentSlot.CHEST, new ItemStack(random.nextBoolean() ? Items.CHAINMAIL_CHESTPLATE : Items.LEATHER_CHESTPLATE));
                if (random.nextFloat() < 0.35f && isOneHanded(getMainHandItem())) {
                    equip(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                }
            }
            case KNIGHT -> {
                equip(EquipmentSlot.MAINHAND, pick(KNIGHT_WEAPONS, random, Items.IRON_AXE));
                boolean diamond = hard && random.nextFloat() < 0.4f;
                equip(EquipmentSlot.HEAD, new ItemStack(diamond ? Items.DIAMOND_HELMET : Items.IRON_HELMET));
                equip(EquipmentSlot.CHEST, new ItemStack(diamond ? Items.DIAMOND_CHESTPLATE : Items.IRON_CHESTPLATE));
                equip(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
                equip(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
                if (random.nextFloat() < 0.5f && isOneHanded(getMainHandItem())) {
                    equip(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                }
            }
        }
    }

    private void equip(EquipmentSlot slot, ItemStack stack) {
        setItemSlot(slot, stack);
        setDropChance(slot, GEAR_DROP_CHANCE);
    }

    private static boolean isOneHanded(ItemStack weapon) {
        // Long weapons need both hands; checked by item tag so Spartan polearms and greatswords count.
        return !weapon.is(TagKey.create(Registries.ITEM, SteelClash.id("two_handed_weapons")));
    }

    /** A random melee weapon from the tag (Spartan material tags also contain bows and quivers: skipped). */
    private ItemStack pick(TagKey<Item> tag, RandomSource random, Item fallback) {
        List<Holder<Item>> items = BuiltInRegistries.ITEM.getTag(tag).stream().flatMap(set -> set.stream())
                .filter(h -> WeaponProfiles.resolve(new ItemStack(h), level().registryAccess()).isPresent())
                .toList();
        return items.isEmpty() ? new ItemStack(fallback) : new ItemStack(items.get(random.nextInt(items.size())));
    }

    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        ItemStack bow = getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack arrowStack = getProjectile(bow);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, arrowStack.isEmpty() ? new ItemStack(Items.ARROW) : arrowStack, velocity, bow);
        double dx = target.getX() - getX();
        double dy = target.getY(0.3333) - arrow.getY();
        double dz = target.getZ() - getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + horizontal * 0.2, dz, 1.6f, 14 - level().getDifficulty().getId() * 4);
        playSound(SoundEvents.SKELETON_SHOOT, 1.0f, 1.0f / (getRandom().nextFloat() * 0.4f + 0.8f));
        level().addFreshEntity(arrow);
    }

    @Override
    public boolean canFireProjectileWeapon(net.minecraft.world.item.ProjectileWeaponItem weapon) {
        return rank() == Rank.ARCHER;
    }

    /** Does this soldier carry a shield (used by the bot brain to block)? */
    public boolean hasShield() {
        return getOffhandItem().canPerformAction(ItemAbilities.SHIELD_BLOCK);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }
}

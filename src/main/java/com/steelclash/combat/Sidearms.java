package com.steelclash.combat;

import com.steelclash.Config;
import com.steelclash.SteelClash;
import com.steelclash.profile.WeaponProfiles;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;

/**
 * Archers' melee sidearms (Chivalry 2 archers carry a dagger or short sword): mobs in {@code #steelclash:sidearm_users}
 * (skeletons, strays, bogged) spawn with a melee weapon from {@code #steelclash:mob_sidearms/tier_1..3} next to their
 * bow. When their target closes in they put the bow away and draw it (the bot brain then fights with it); once the
 * target is far enough again, they go back to the bow. The stowed item is kept in the {@link ModAttachments#SIDEARM}
 * attachment, which is saved with the mob.
 */
public final class Sidearms {
    public static final TagKey<EntityType<?>> USERS = TagKey.create(Registries.ENTITY_TYPE, SteelClash.id("sidearm_users"));
    private static final List<TagKey<Item>> TIERS = List.of(tier(1), tier(2), tier(3));
    private static final String PENDING_TAG = "steelclash_sidearm_pending";
    /** Draw the sidearm when the target is this close (blocks). */
    public static final double DRAW_DISTANCE = 4.0;
    /** Go back to the bow when the target is this far, or gone (blocks). */
    public static final double STOW_DISTANCE = 9.0;
    /** A switch takes this long before the next one (ticks), so a target at the edge doesn't make the mob juggle. */
    public static final int SWITCH_COOLDOWN = 30;

    private Sidearms() {
    }

    private static TagKey<Item> tier(int n) {
        return TagKey.create(Registries.ITEM, SteelClash.id("mob_sidearms/tier_" + n));
    }

    /** Spawn is being finalized: give it a sidearm once it has joined (after its bow was handed out). */
    public static void markForArming(Mob mob) {
        if (mob.getType().is(USERS)) {
            mob.getPersistentData().putBoolean(PENDING_TAG, true);
        }
    }

    public static void armIfPending(Mob mob) {
        if (!mob.getPersistentData().getBoolean(PENDING_TAG)) {
            return;
        }
        mob.getPersistentData().remove(PENDING_TAG);
        RandomSource random = mob.getRandom();
        if (random.nextDouble() >= Config.SIDEARM_CHANCE.get()) {
            return;
        }
        arm(mob, MobGear.rollTier(random, mob.level().getDifficulty().getId()));
    }

    /** Gives a ranged mob a sidearm from the tier's tag; false when it has none to give or isn't holding a ranged weapon. */
    public static boolean arm(Mob mob, int tier) {
        if (!(mob.getMainHandItem().getItem() instanceof ProjectileWeaponItem) || !stowed(mob).isEmpty()) {
            return false;
        }
        Optional<ItemStack> sidearm = pick(mob, TIERS.get(Math.max(0, Math.min(TIERS.size() - 1, tier))));
        sidearm.ifPresent(stack -> mob.setData(ModAttachments.SIDEARM, stack));
        return sidearm.isPresent();
    }

    /** The weapon the mob isn't holding right now (the sidearm, or the bow while the sidearm is out). */
    public static ItemStack stowed(LivingEntity entity) {
        return entity.hasData(ModAttachments.SIDEARM) ? entity.getData(ModAttachments.SIDEARM) : ItemStack.EMPTY;
    }

    /** Each server tick: draw the sidearm against a close target, go back to the bow once it's far or gone. */
    public static void tick(Mob mob, CombatData data) {
        ItemStack stowed = stowed(mob);
        if (stowed.isEmpty() || mob.level().getGameTime() < data.sidearmReadyAt || data.machine.isBusy()) {
            return;
        }
        boolean holdingBow = mob.getMainHandItem().getItem() instanceof ProjectileWeaponItem;
        LivingEntity target = mob.getTarget();
        boolean present = target != null && target.isAlive();
        double distanceSqr = present ? mob.distanceToSqr(target) : Double.MAX_VALUE;
        boolean draw = holdingBow && present && distanceSqr <= DRAW_DISTANCE * DRAW_DISTANCE;
        boolean stow = !holdingBow && stowed.getItem() instanceof ProjectileWeaponItem
                && distanceSqr >= STOW_DISTANCE * STOW_DISTANCE;
        if (draw || stow) {
            swap(mob, data);
        }
    }

    private static void swap(Mob mob, CombatData data) {
        if (mob.isUsingItem()) {
            mob.stopUsingItem(); // a half-drawn bow is let down, not fired
        }
        ItemStack held = mob.getMainHandItem();
        // A skeleton re-picks its bow or melee goal whenever its main hand changes (AbstractSkeleton.setItemSlot).
        mob.setItemSlot(EquipmentSlot.MAINHAND, stowed(mob));
        mob.setData(ModAttachments.SIDEARM, held);
        data.sidearmReadyAt = mob.level().getGameTime() + SWITCH_COOLDOWN;
    }

    private static Optional<ItemStack> pick(Mob mob, TagKey<Item> tag) {
        List<Holder<Item>> candidates = BuiltInRegistries.ITEM.getTag(tag).stream()
                .flatMap(set -> set.stream())
                .filter(holder -> WeaponProfiles.resolve(new ItemStack(holder), mob.level().registryAccess()).isPresent())
                .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ItemStack(candidates.get(mob.getRandom().nextInt(candidates.size()))));
    }
}

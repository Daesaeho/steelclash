package com.steelclash.combat;

import com.steelclash.Config;
import com.steelclash.SteelClash;
import com.steelclash.profile.WeaponProfiles;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Arms mobs far more often than vanilla, so the bot brain has weapons to fight with (PLAN §7.4). Weapons come from the
 * item tags {@code #steelclash:mob_weapons/tier_1..3} (vanilla plus Spartan Weaponry when installed); harder
 * difficulties roll better tiers, shields and helmets more often.
 * <p>
 * Marked during {@code FinalizeSpawnEvent} and applied when the mob joins the level, i.e. after vanilla's own
 * equipment roll; a mob vanilla (or Spartan Weaponry) already armed is left alone.
 */
public final class MobGear {
    public static final TagKey<EntityType<?>> ARMABLE = TagKey.create(Registries.ENTITY_TYPE, SteelClash.id("armable"));
    public static final TagKey<Item> SHIELDS = TagKey.create(Registries.ITEM, SteelClash.id("mob_shields"));
    private static final List<TagKey<Item>> TIERS = List.of(tier(1), tier(2), tier(3));
    private static final String PENDING_TAG = "steelclash_arm_pending";
    private static final float DROP_CHANCE = 0.05f;
    private static final Set<String> TWO_HANDED = Set.of("two_handed", "polearm", "spear", "staff");

    private MobGear() {
    }

    private static TagKey<Item> tier(int n) {
        return TagKey.create(Registries.ITEM, SteelClash.id("mob_weapons/tier_" + n));
    }

    /** Spawn is being finalized (natural, spawner, egg, command without NBT...): arm it once it has joined. */
    public static void markForArming(Mob mob) {
        if (mob.getType().is(ARMABLE)) {
            mob.getPersistentData().putBoolean(PENDING_TAG, true);
        }
    }

    public static void armIfPending(Mob mob) {
        if (!mob.getPersistentData().getBoolean(PENDING_TAG)) {
            return;
        }
        mob.getPersistentData().remove(PENDING_TAG);
        if (!mob.getMainHandItem().isEmpty()) {
            return; // vanilla or another mod already armed it
        }
        RandomSource random = mob.getRandom();
        int difficulty = mob.level().getDifficulty().getId();
        double armedChance = switch (difficulty) {
            case 0, 1 -> Config.ARMED_CHANCE_EASY.get();
            case 2 -> Config.ARMED_CHANCE_NORMAL.get();
            default -> Config.ARMED_CHANCE_HARD.get();
        };
        if (random.nextDouble() >= armedChance) {
            return;
        }
        int tier = rollTier(random, difficulty);
        Optional<ItemStack> weapon = pick(mob, TIERS.get(tier), random);
        if (weapon.isEmpty()) {
            return;
        }
        mob.setItemSlot(EquipmentSlot.MAINHAND, weapon.get());
        mob.setDropChance(EquipmentSlot.MAINHAND, DROP_CHANCE);

        double scale = difficulty <= 1 ? 0.7 : difficulty == 2 ? 1.0 : 1.4;
        boolean oneHanded = WeaponProfiles.resolve(weapon.get(), mob.level().registryAccess())
                .map(r -> !TWO_HANDED.contains(r.profile().archetype())).orElse(true);
        if (oneHanded && mob.getOffhandItem().isEmpty() && random.nextDouble() < Config.ARMED_SHIELD_CHANCE.get() * scale) {
            pick(mob, SHIELDS, random).ifPresent(shield -> {
                mob.setItemSlot(EquipmentSlot.OFFHAND, shield);
                mob.setDropChance(EquipmentSlot.OFFHAND, DROP_CHANCE);
            });
        }
        if (mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && random.nextDouble() < Config.ARMED_HELMET_CHANCE.get() * scale) {
            mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(switch (tier) {
                case 0 -> Items.LEATHER_HELMET;
                case 1 -> random.nextBoolean() ? Items.CHAINMAIL_HELMET : Items.IRON_HELMET;
                default -> random.nextInt(3) == 0 ? Items.DIAMOND_HELMET : Items.IRON_HELMET;
            }));
            mob.setDropChance(EquipmentSlot.HEAD, DROP_CHANCE);
        }
    }

    /** Easy: mostly wood/stone; Normal: mostly iron; Hard: iron with a good chance of diamond. Returns 0..2. */
    static int rollTier(RandomSource random, int difficulty) {
        double r = random.nextDouble();
        return switch (difficulty) {
            case 0, 1 -> r < 0.7 ? 0 : 1;
            case 2 -> r < 0.4 ? 0 : r < 0.9 ? 1 : 2;
            default -> r < 0.2 ? 0 : r < 0.75 ? 1 : 2;
        };
    }

    /** A random item from the tag that Steel Clash knows how to fight with (weapons) or that is a shield. */
    private static Optional<ItemStack> pick(Mob mob, TagKey<Item> tag, RandomSource random) {
        List<Holder<Item>> candidates = BuiltInRegistries.ITEM.getTag(tag).stream()
                .flatMap(set -> set.stream())
                .filter(holder -> tag == SHIELDS || WeaponProfiles.resolve(new ItemStack(holder), mob.level().registryAccess()).isPresent())
                .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ItemStack(candidates.get(random.nextInt(candidates.size()))));
    }
}

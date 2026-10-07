package com.steelclash.gametest;

import static com.steelclash.gametest.TestSupport.FACING_POSITIVE_X;
import static com.steelclash.gametest.TestSupport.check;
import static com.steelclash.gametest.TestSupport.data;
import static com.steelclash.gametest.TestSupport.finish;
import static com.steelclash.gametest.TestSupport.isHurt;
import static com.steelclash.gametest.TestSupport.swing;
import static com.steelclash.gametest.TestSupport.swordsman;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.steelclash.Config;
import com.steelclash.SteelClash;
import com.steelclash.core.ContactPolicy;
import com.steelclash.combat.Combat;
import com.steelclash.combat.CombatData;
import com.steelclash.core.AttackTimings;
import com.steelclash.core.AttackType;
import com.steelclash.core.Phase;
import com.steelclash.profile.WeaponProfile;
import com.steelclash.profile.WeaponProfiles;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** M6c-1: weapon specials, throwing, damage types versus armour. */
@GameTestHolder(SteelClash.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MechanicsGameTests {
    private static final String ARENA = "arena";

    private MechanicsGameTests() {
    }

    @GameTest(template = ARENA)
    public static void swordLungeOutreachesAStab(GameTestHelper helper) {
        Player stabber = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X, 2);
        Player lunger = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X, 6);
        Zombie farA = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 5, 2, 2);
        Zombie farB = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 5, 2, 6);
        swing(stabber, AttackType.STAB);
        swing(lunger, AttackType.SPECIAL);
        check(helper, !isHurt(farA), "a plain stab should fall short");
        check(helper, isHurt(farB), "the sword's lunge special should reach");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void specialsHaveACooldown(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        swing(player, AttackType.SPECIAL);
        check(helper, data(player).machine.phase() == Phase.IDLE, "the first special finishes");
        Combat.requestAttack(player, AttackType.SPECIAL);
        check(helper, data(player).machine.phase() == Phase.IDLE, "a second special right away is refused (cooldown)");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void maceSlamStaggersEveryoneAround(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.MACE), FACING_POSITIVE_X);
        // The slam lands 2 blocks ahead; these two stand either side of the impact.
        Zombie left = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 3);
        Zombie right = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 5);
        swing(player, AttackType.SPECIAL);
        check(helper, data(left).machine.phase() == Phase.STAGGER && data(right).machine.phase() == Phase.STAGGER,
                "the slam should stagger both zombies near the impact");
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void thrownSwordHitsAndDrops(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        Zombie target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 6, 2, 4);
        target.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // don't burn in daylight
        swing(player, AttackType.THROW);
        check(helper, player.getMainHandItem().isEmpty(), "the sword should leave the hand");
        helper.succeedWhen(() -> {
            check(helper, isHurt(target), "the thrown sword should hit the zombie 4.5 blocks away");
            // Make sure it was the sword, not something stray from a neighbouring test (e.g. an arrow).
            var source = target.getLastDamageSource();
            check(helper, source != null && source.getDirectEntity() instanceof com.steelclash.entity.ThrownWeapon,
                    "the damage should come from the thrown weapon, was " + (source == null ? "none" : source.getMsgId()));
            boolean dropped = !helper.getLevel().getEntitiesOfClass(ItemEntity.class, target.getBoundingBox().inflate(3),
                    e -> e.getItem().is(Items.IRON_SWORD)).isEmpty();
            check(helper, dropped, "the sword should drop where it landed");
        });
    }

    @GameTest(template = ARENA)
    public static void cutsGlanceOffPlate(GameTestHelper helper) {
        Zombie plainTarget = armouredZombie(helper, 2);
        Zombie typedTarget = armouredZombie(helper, 6);
        // Armour attributes only apply once the mob has ticked with the gear on.
        helper.runAfterDelay(3, () -> {
            check(helper, typedTarget.getArmorValue() >= 16, "full diamond should count as heavy armour, was " + typedTarget.getArmorValue());
            boolean original = Config.DAMAGE_TYPES.get();
            try {
                Config.DAMAGE_TYPES.set(false);
                float plain = overheadDamage(helper, plainTarget, 2);
                Config.DAMAGE_TYPES.set(true);
                float typed = overheadDamage(helper, typedTarget, 6);
                check(helper, plain > 0 && typed < plain * 0.85,
                        "a sword (cut) should do clearly less to full diamond with damage types on: " + typed + " vs " + plain);
            } finally {
                Config.DAMAGE_TYPES.set(original);
            }
            helper.succeed();
        });
    }

    private static Zombie armouredZombie(GameTestHelper helper, int row) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, row);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        zombie.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
        zombie.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        return zombie;
    }

    private static float overheadDamage(GameTestHelper helper, Zombie zombie, int row) {
        Player player = swordsman(helper, new ItemStack(Items.DIAMOND_SWORD), FACING_POSITIVE_X, row);
        Combat.requestAttack(player, AttackType.OVERHEAD, 0, false);
        finish(player);
        return zombie.getMaxHealth() - zombie.getHealth();
    }

    @GameTest(template = "arena")
    public static void trainingDummyIsCraftable(GameTestHelper helper) {
        var recipe = helper.getLevel().getRecipeManager().byKey(com.steelclash.SteelClash.id("training_dummy"));
        check(helper, recipe.isPresent(), "the training dummy recipe should load");
        check(helper, recipe.get().value().getResultItem(helper.getLevel().registryAccess())
                .is(com.steelclash.entity.ModEntities.TRAINING_DUMMY_SPAWN_EGG.get()), "it should make a training dummy");
        helper.succeed();
    }

    /** Weapon profiles can time attacks in milliseconds (sub-tick); ticks still work, and both can be mixed. */
    @GameTest(template = ARENA)
    public static void profilesTakeMillisecondTimings(GameTestHelper helper) {
        String arc = "\"arc\": {\"shape\": \"thrust\", \"width\": 1}";
        WeaponProfile.AttackSpec ms = decode("{\"windup_ms\": 370, \"release_ms\": 120, \"recovery_ms\": 333, " + arc + "}");
        check(helper, ms.timings().equals(AttackTimings.ofMillis(370, 120, 333)), "milliseconds: " + ms.timings());
        WeaponProfile.AttackSpec mixed = decode("{\"windup\": 7, \"release_ms\": 75, \"recovery\": 9, " + arc + "}");
        check(helper, mixed.timings().equals(new AttackTimings(350_000, 75_000, 450_000)), "mixed: " + mixed.timings());
        boolean rejected = WeaponProfile.AttackSpec.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"windup_ms\": 300, \"recovery\": 5, " + arc + "}")).error().isPresent();
        check(helper, rejected, "an attack without a release time is rejected");

        // A live attack: 370 ms of windup releases 20 ms into the 8th tick.
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        CombatData d = data(player);
        d.machine.startAttack(AttackType.STAB, AttackTimings.ofMillis(370, 120, 333));
        for (int tick = 0; tick < 8; tick++) {
            d.machine.tick();
        }
        check(helper, d.machine.phase() == Phase.RELEASE && d.machine.phaseElapsedUs() == 30_000,
                "30 ms into the release after 8 ticks, phase " + d.machine.phase() + " at " + d.machine.phaseElapsedUs() + " us");
        helper.succeed();
    }

    /** Attacks can choose cleave or thwack, and a thwack recovery that scales with attack speed like the rest. */
    @GameTest(template = ARENA)
    public static void profilesTakeContactAndThwackTimings(GameTestHelper helper) {
        String timed = "\"windup_ms\": 300, \"release_ms\": 200, \"recovery_ms\": 300, \"arc\": {\"shape\": \"horizontal\"}";
        WeaponProfile.AttackSpec thwack = decode("{" + timed + ", \"contact\": \"thwack\", \"thwack_ms\": 450}");
        check(helper, thwack.contact().equals(java.util.Optional.of(ContactPolicy.THWACK)), "contact " + thwack.contact());
        check(helper, thwack.thwackUs(300_000) == 450_000, "thwack at reference speed: " + thwack.thwackUs(300_000));
        check(helper, thwack.thwackUs(150_000) == 225_000, "twice as fast, half the thwack: " + thwack.thwackUs(150_000));
        WeaponProfile.AttackSpec plain = decode("{" + timed + "}");
        check(helper, plain.contact().isEmpty() && plain.thwackUs(280_000) == 280_000, "unset: the normal recovery");
        boolean rejected = WeaponProfile.AttackSpec.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{" + timed + ", \"contact\": \"bounce\"}")).error().isPresent();
        check(helper, rejected, "an unknown contact is rejected");
        helper.succeed();
    }

    /** Combo and riposte timings replace the windup, scaled with attack speed; heavies add fixed times. */
    @GameTest(template = ARENA)
    public static void profilesTakeComboRiposteAndHeavyTimings(GameTestHelper helper) {
        String timed = "\"windup_ms\": 500, \"release_ms\": 400, \"recovery_ms\": 750, \"arc\": {\"shape\": \"horizontal\"}";
        WeaponProfile.AttackSpec spec = decode("{" + timed + ", \"combo_ms\": 725, \"riposte_ms\": 400}");
        check(helper, spec.comboUs(500_000) == 725_000, "combo at reference speed: " + spec.comboUs(500_000));
        check(helper, spec.comboUs(250_000) == 362_500, "twice as fast, half the combo: " + spec.comboUs(250_000));
        check(helper, spec.riposteUs(1_000_000).equals(java.util.Optional.of(800_000)), "riposte " + spec.riposteUs(1_000_000));
        WeaponProfile.AttackSpec plain = decode("{" + timed + "}");
        check(helper, plain.comboUs(480_000) == 480_000 && plain.riposteUs(480_000).isEmpty(), "unset: the normal windup");

        WeaponProfile.HeavySpec heavy = WeaponProfile.HeavySpec.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"windup_extra_ms\": 250, \"recovery_extra_ms\": 100}")).getOrThrow();
        check(helper, heavy.windupUs(500_000, 1) == 750_000, "heavy windup " + heavy.windupUs(500_000, 1));
        check(helper, heavy.windupUs(500_000, 0.5) == 625_000, "the extra scales too: " + heavy.windupUs(500_000, 0.5));
        check(helper, heavy.recoveryUs(750_000, 1) == 850_000, "heavy recovery " + heavy.recoveryUs(750_000, 1));
        WeaponProfile.HeavySpec scaled = WeaponProfile.HeavySpec.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"windup_mult\": 1.5}")).getOrThrow();
        check(helper, scaled.windupUs(500_000, 0.5) == 750_000 && scaled.recoveryUs(750_000, 1) == 750_000,
                "no extras: the multiplier, recovery unchanged");
        helper.succeed();
    }

    /** Chivalry 2: a whiff can be comboed, and the combo uses the attack's combo timing as its windup. */
    @GameTest(template = ARENA)
    public static void whiffComboUsesComboTiming(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        CombatData d = data(player);
        WeaponProfile profile = WeaponProfiles.resolveFor(player).orElseThrow().profile();
        WeaponProfile.AttackSpec stab = profile.spec(AttackType.STAB).orElseThrow();
        check(helper, stab.comboMs().isPresent(), "the sword profile should set combo_ms");
        int windupUs = com.steelclash.combat.CombatMath.timings(player, profile, stab).windupUs();

        Combat.requestAttack(player, AttackType.SLASH);
        for (int tick = 0; tick < 100 && d.machine.phase() != Phase.RECOVERY; tick++) {
            Combat.tickServer(player, d);
        }
        check(helper, d.machine.phase() == Phase.RECOVERY && d.machine.isComboAllowed(), "a whiff should allow a combo");
        Combat.requestAttack(player, AttackType.STAB);
        check(helper, d.machine.phase() == Phase.WINDUP, "comboed out of the recovery");
        check(helper, d.machine.timings().windupUs() == stab.comboUs(windupUs),
                "combo windup " + d.machine.timings().windupUs() + ", expected " + stab.comboUs(windupUs));
        helper.succeed();
    }

    /** A riposte uses the attack's riposte timing; a heavy riposte adds the heavy's fixed extra to it. */
    @GameTest(template = ARENA)
    public static void riposteAndHeavyUseChivalryTimings(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        // A faster wielder than the profile's reference speed, so every replaced and added time must scale.
        player.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(5.6);
        CombatData d = data(player);
        WeaponProfile profile = WeaponProfiles.resolveFor(player).orElseThrow().profile();
        WeaponProfile.AttackSpec slash = profile.spec(AttackType.SLASH).orElseThrow();
        AttackTimings base = com.steelclash.combat.CombatMath.timings(player, profile, slash);
        double speedScale = base.releaseUs() / (double) slash.timings().releaseUs();
        check(helper, speedScale < 0.9, "the wielder should be faster than the reference: " + speedScale);
        int riposteUs = slash.riposteUs(base.windupUs()).orElseThrow();

        check(helper, d.machine.startParry(20, 5), "parry");
        d.machine.parrySucceeded(10);
        check(helper, d.machine.isRiposteReady(), "riposte ready");
        Combat.requestAttack(player, AttackType.SLASH);
        check(helper, d.machine.timings().windupUs() == riposteUs,
                "riposte windup " + d.machine.timings().windupUs() + ", expected " + riposteUs);
        check(helper, Combat.makeHeavy(player, d), "heavy riposte");
        WeaponProfile.HeavySpec heavy = profile.heavy();
        check(helper, heavy.windupExtraMs().isPresent(), "the sword profile should set windup_extra_ms");
        check(helper, d.machine.timings().windupUs() == heavy.windupUs(riposteUs, speedScale),
                "heavy riposte windup " + d.machine.timings().windupUs());
        check(helper, d.machine.timings().recoveryUs() == heavy.recoveryUs(base.recoveryUs(), speedScale)
                        && d.machine.timings().recoveryUs() > base.recoveryUs(),
                "heavy recovery " + d.machine.timings().recoveryUs() + " vs light " + base.recoveryUs());
        helper.succeed();
    }

    private static WeaponProfile.AttackSpec decode(String json) {
        return WeaponProfile.AttackSpec.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }
}

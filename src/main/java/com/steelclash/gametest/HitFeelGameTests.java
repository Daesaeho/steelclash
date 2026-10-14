package com.steelclash.gametest;

import static com.steelclash.gametest.TestSupport.FACING_NEGATIVE_X;
import static com.steelclash.gametest.TestSupport.FACING_POSITIVE_X;
import static com.steelclash.gametest.TestSupport.check;
import static com.steelclash.gametest.TestSupport.data;
import static com.steelclash.gametest.TestSupport.dummy;
import static com.steelclash.gametest.TestSupport.face;
import static com.steelclash.gametest.TestSupport.isHurt;
import static com.steelclash.gametest.TestSupport.swordsman;

import com.steelclash.Config;
import com.steelclash.SteelClash;
import com.steelclash.combat.Combat;
import com.steelclash.combat.CombatData;
import com.steelclash.combat.HitFeel;
import com.steelclash.combat.LagCompensation;
import com.steelclash.core.AttackType;
import com.steelclash.core.HitLocation;
import com.steelclash.core.Phase;
import com.steelclash.entity.TrainingDummy;
import java.util.function.Supplier;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Traced contacts: where the blade met the body, which part, the armour there, and what the blow then does. */
@GameTestHolder(SteelClash.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HitFeelGameTests {
    private static final String ARENA = "arena";

    private HitFeelGameTests() {
    }

    /** A bare zombie at block (3, 2, z), facing the swordsman at x = 1.5. */
    private static Zombie zombie(GameTestHelper helper, int z) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, z);
        face(zombie, FACING_NEGATIVE_X);
        return zombie;
    }

    /**
     * Swings the attack's first arc from its usual side (a slash comes from the attacker's right) and runs it to the end.
     * Where a blade lands depends on its arc, and {@code TestSupport.swing} picks a random variant and side.
     */
    private static void strike(Player attacker, AttackType type) {
        Combat.requestAttack(attacker, type, 0, false);
        TestSupport.finish(attacker);
    }

    private static HitFeel.Impact impact(GameTestHelper helper, Zombie zombie, String what) {
        HitFeel.Impact impact = HitFeel.lastImpact(zombie);
        if (impact == null) {
            helper.fail(what + ": no traced impact");
        }
        return impact;
    }

    /**
     * Runs {@code body} with a server setting changed, putting it back afterwards. Settings are global: a test that
     * changes one does so within one tick, so no other test sees it.
     */
    private static <T> void with(ModConfigSpec.ConfigValue<T> setting, T value, Runnable body) {
        T before = setting.get();
        try {
            setting.set(value);
            body.run();
        } finally {
            setting.set(before);
        }
    }

    @GameTest(template = ARENA)
    public static void tracedHitsLandWhereTheBladeCrossed(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);

        Zombie stabbed = zombie(helper, 4);
        strike(player, AttackType.STAB);
        HitFeel.Impact stab = impact(helper, stabbed, "stab");
        check(helper, stab.region() == HitLocation.Region.TORSO, "a level stab lands in the chest, was " + stab.region());
        check(helper, Math.abs(stab.point().x - (stabbed.getX() - stabbed.getBbWidth() / 2)) < 0.05,
                "it lands on the front of the body facing the attacker: " + stab.point() + " vs body at " + stabbed.position());
        check(helper, stab.motion().x > 0.8, "a stab drives straight into the body: " + stab.motion());
        stabbed.discard();

        Zombie split = zombie(helper, 4);
        strike(player, AttackType.OVERHEAD);
        HitFeel.Impact overhead = impact(helper, split, "overhead");
        check(helper, overhead.region() == HitLocation.Region.HEAD, "an overhead comes down on the head, was " + overhead.region());
        check(helper, overhead.motion().y < -0.5, "travelling down: " + overhead.motion());
        split.discard();

        Zombie cut = zombie(helper, 4);
        strike(player, AttackType.SLASH);
        HitFeel.Impact slash = impact(helper, cut, "slash");
        // A player's level slash rises a little as it comes in, so it meets a taller zombie at the neck or shoulder.
        check(helper, !slash.region().isLeg() && slash.point().y > cut.getY() + cut.getBbHeight() * 0.6,
                "a level slash lands high on the body, was " + slash.region() + " at " + (slash.point().y - cut.getY()));
        check(helper, Math.abs(slash.motion().z) > Math.abs(slash.motion().x), "a slash travels across the body: " + slash.motion());
        check(helper, slash.point().x < cut.getX(), "and meets the near side of the body, where the blade came in, not the "
                + "far side its tip reaches: " + slash.point() + " vs body at " + cut.position());
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void armourOnTheStruckPartIsWhatTheHitRings(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        Object[][] cases = {
                {Items.IRON_CHESTPLATE, HitFeel.Armour.PLATE},
                {Items.DIAMOND_CHESTPLATE, HitFeel.Armour.PLATE},
                {Items.CHAINMAIL_CHESTPLATE, HitFeel.Armour.MAIL},
                {Items.LEATHER_CHESTPLATE, HitFeel.Armour.NONE},
                {Items.AIR, HitFeel.Armour.NONE},
        };
        for (Object[] c : cases) {
            Zombie zombie = zombie(helper, 4);
            zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack((net.minecraft.world.item.Item) c[0]));
            zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET)); // not the part that's struck
            strike(player, AttackType.STAB);
            HitFeel.Impact stab = impact(helper, zombie, c[0].toString());
            check(helper, stab.region() == HitLocation.Region.TORSO, "premise: the stab lands in the chest");
            check(helper, stab.armour() == c[1], c[0] + " sounds like " + c[1] + ", was " + stab.armour());
            zombie.discard();
        }
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void aKillingCutToTheHeadTakesItOff(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        Supplier<HitFeel.Impact> kill = () -> {
            Zombie zombie = zombie(helper, 4);
            zombie.setHealth(1);
            strike(player, AttackType.OVERHEAD);
            HitFeel.Impact overhead = impact(helper, zombie, "overhead");
            check(helper, overhead.killed() && overhead.region() == HitLocation.Region.HEAD, "premise: killed by a blow to the head");
            zombie.discard();
            return overhead;
        };
        check(helper, kill.get().severed(), "a killing cut to a bare head takes it off");
        with(Config.DECAPITATION, false, () -> check(helper, !kill.get().severed(), "not with decapitation off"));
        with(Config.HIT_LOCATIONS, false, () -> {
            Zombie zombie = zombie(helper, 4);
            zombie.setHealth(1);
            strike(player, AttackType.OVERHEAD);
            check(helper, !impact(helper, zombie, "unlocated").severed(), "nor without hit locations");
            zombie.discard();
        });

        Zombie helmeted = zombie(helper, 4);
        helmeted.setHealth(1);
        helmeted.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        // It drops the helmet as it dies (as a player always does): the blade still met a helmeted head.
        helmeted.setDropChance(EquipmentSlot.HEAD, 2f);
        strike(player, AttackType.OVERHEAD);
        HitFeel.Impact kept = impact(helper, helmeted, "helmeted");
        check(helper, kept.killed() && !kept.severed(), "a helmet keeps the head on: killed " + kept.killed()
                + ", severed " + kept.severed() + ", region " + kept.region() + ", health " + helmeted.getHealth());
        check(helper, kept.armour() == HitFeel.Armour.PLATE, "and rings like plate");
        helmeted.discard();

        Zombie survivor = zombie(helper, 4);
        strike(player, AttackType.OVERHEAD);
        check(helper, !impact(helper, survivor, "survivor").severed(), "a blow that doesn't kill takes nothing off");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void aKillingBlowThrowsTheBodyAlongTheBlade(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        // With the vanilla shove off, all the body's movement is the death push.
        with(Config.MELEE_KNOCKBACK, 0.0, () -> {
            Zombie zombie = zombie(helper, 4);
            zombie.setHealth(1);
            zombie.setDeltaMovement(Vec3.ZERO);
            strike(player, AttackType.SLASH);
            HitFeel.Impact slash = impact(helper, zombie, "slash");
            Vec3 thrown = zombie.getDeltaMovement().multiply(1, 0, 1);
            Vec3 along = slash.motion().multiply(1, 0, 1).normalize();
            check(helper, slash.killed(), "premise: killed");
            check(helper, thrown.dot(along) > 0.3 && thrown.subtract(along.scale(thrown.dot(along))).length() < 0.05,
                    "thrown the way the blade went: " + thrown + " along " + along);
            zombie.discard();

            with(Config.DEATH_PUSH, 0.0, () -> {
                Zombie still = zombie(helper, 4);
                still.setHealth(1);
                still.setDeltaMovement(Vec3.ZERO);
                strike(player, AttackType.SLASH);
                check(helper, still.isDeadOrDying() && still.getDeltaMovement().horizontalDistance() < 1e-6,
                        "with deathPush 0 it drops where it stood: " + still.getDeltaMovement());
                still.discard();
            });
        });
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void cuttingThroughABodySlowsTheRestOfTheSwing(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        CombatData d = data(player);
        // The slash comes from the attacker's right (+Z when facing +X), so this zombie is met early in the release.
        Zombie first = zombie(helper, 5);
        long dragged = releaseAfterFirstContact(helper, player, d);
        check(helper, isHurt(first), "premise: the blade cut it");
        long plain = d.machine.timings().releaseUs();
        check(helper, dragged > plain, "the release grows once the blade cuts through: " + dragged + " vs " + plain);
        check(helper, dragged <= 2 * plain, "never past twice its length");
        TestSupport.finish(player);
        first.discard();

        Zombie second = zombie(helper, 5);
        with(Config.CLEAVE_DRAG_MS, 0, () -> check(helper, releaseAfterFirstContact(helper, player, d) == d.machine.timings().releaseUs(),
                "with cleaveDragMs 0 the blade doesn't slow"));
        TestSupport.finish(player);
        check(helper, isHurt(second), "premise: cut");
        helper.succeed();
    }

    /** Starts a slash and ticks until it has met someone; the release's length then. */
    private static long releaseAfterFirstContact(GameTestHelper helper, Player player, CombatData d) {
        Combat.requestAttack(player, AttackType.SLASH, 0, false);
        for (int tick = 0; tick < 100 && d.hitThisSwing.isEmpty(); tick++) {
            Combat.tickServer(player, d);
        }
        check(helper, d.machine.phase() == Phase.RELEASE, "still swinging after the first body, was " + d.machine.phase());
        return d.machine.phaseDurationUs();
    }

    @GameTest(template = ARENA)
    public static void aParriedBladeSparksWhereItMetTheGuard(GameTestHelper helper) {
        Player attacker = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        TrainingDummy defender = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        Combat.requestParry(defender);
        strike(attacker, AttackType.STAB);
        check(helper, !isHurt(defender) && data(attacker).machine.phase() == Phase.STAGGER, "premise: parried");
        Vec3 spark = HitFeel.lastParry(defender);
        check(helper, spark != null, "the parry was shown from the traced contact");
        check(helper, spark.x < defender.getX() - defender.getBbWidth() / 2 && spark.x > attacker.getX(),
                "in front of the defender, toward the blade: " + spark + " defender at " + defender.position());
        check(helper, spark.y > defender.getY() + 0.5 && spark.y < defender.getY() + defender.getBbHeight(),
                "at the height the blade came in: " + spark);
        Integer bounced = HitFeel.lastBounce(attacker);
        check(helper, bounced != null && bounced == data(attacker).machine.attackSerial(),
                "the blade bounces back, tagged with the parried attack: " + bounced + " vs " + data(attacker).machine.attackSerial());
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void aHeldHitLandsWhereTheBodyNowStands(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        Zombie zombie = zombie(helper, 4);
        LagCompensation.forceLatency(zombie, 200); // a lagged defender: the hit waits for a parry that may be coming
        zombie.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1); // only the test moves it
        strike(player, AttackType.STAB);
        check(helper, !isHurt(zombie) && HitFeel.lastImpact(zombie) == null, "premise: the hit is held");
        zombie.setPos(zombie.getX(), zombie.getY(), zombie.getZ() + 1); // it steps aside before the hit lands
        helper.succeedWhen(() -> {
            HitFeel.Impact impact = HitFeel.lastImpact(zombie);
            helper.assertTrue(impact != null, "the held hit lands");
            helper.assertTrue(zombie.getBoundingBox().inflate(0.05).contains(impact.point()),
                    "on the body where it now stands: " + impact.point() + " vs " + zombie.getBoundingBox());
        });
    }

    @GameTest(template = ARENA)
    public static void aBlockedSpecialSparksButDoesNotBounce(GameTestHelper helper) {
        Player attacker = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        TrainingDummy defender = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        Combat.requestParry(defender);
        strike(attacker, AttackType.SPECIAL);
        check(helper, !isHurt(defender) && data(attacker).machine.phase() != Phase.STAGGER,
                "premise: the guard stops the special and its attacker keeps the initiative");
        check(helper, HitFeel.lastParry(defender) != null, "sparks fly where the blade met the guard");
        check(helper, HitFeel.lastBounce(attacker) == null, "but there is no stagger for a bounce to play in");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void aFlickedBladeIsSweptThroughTheBodyItPassed(GameTestHelper helper) {
        // Two samples a tick and no turn cap: a 90° flick moves the blade 45° between samples, over a whole zombie.
        with(Config.TURN_CAP, 0.0, () -> with(Config.BLADE_SAMPLES_PER_TICK, 2, () -> {
            check(helper, flick(helper, true), "swept, the blade meets the zombie it passed between two samples");
            check(helper, !flick(helper, false), "premise: unswept, the samples step over it");
        }));
        helper.succeed();
    }

    /**
     * A stab whose blade is fully out, flicked 90° left in one tick across a zombie standing between where the blade
     * points at the two samples of that tick. Whether the zombie was hit.
     */
    private static boolean flick(GameTestHelper helper, boolean swept) {
        boolean[] hit = {false};
        with(Config.SWEPT_BLADE, swept, () -> {
            Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
            CombatData d = data(player);
            // About 63° left of the stab, 2.2 blocks out: off the blade facing +X and off the blade at the halfway sample.
            Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 3);
            Combat.requestAttack(player, AttackType.STAB, 0, false);
            for (int tick = 0; tick < 100 && !(d.machine.phase() == Phase.RELEASE && d.machine.phaseProgress(0) >= 0.6); tick++) {
                Combat.tickServer(player, d);
            }
            check(helper, d.machine.phase() == Phase.RELEASE && !isHurt(zombie), "premise: the stab is out and has missed");
            face(player, 180); // turn left to face -Z: the blade swings across the zombie this tick
            Combat.tickServer(player, d);
            hit[0] = isHurt(zombie);
            TestSupport.finish(player);
            zombie.discard();
        });
        return hit[0];
    }

    @GameTest(template = ARENA)
    public static void everyBladeSampleRateStillFindsItsTarget(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        for (int samples : new int[]{2, 20}) {
            with(Config.BLADE_SAMPLES_PER_TICK, samples, () -> {
                Zombie zombie = zombie(helper, 4);
                strike(player, AttackType.SLASH);
                check(helper, isHurt(zombie), "a slash sampled " + samples + " times a tick still hits");
                zombie.discard();
            });
        }
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void untracedHitsKeepThePlainFeedback(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        with(Config.TRACED_CONTACTS, false, () -> {
            Zombie zombie = zombie(helper, 4);
            zombie.setHealth(1);
            strike(player, AttackType.OVERHEAD);
            check(helper, zombie.isDeadOrDying(), "premise: the overhead still kills");
            check(helper, HitFeel.lastImpact(zombie) == null, "with tracedContacts off no impact is worked out");
            zombie.discard();
        });
        helper.succeed();
    }
}

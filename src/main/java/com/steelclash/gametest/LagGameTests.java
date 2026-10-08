package com.steelclash.gametest;

import static com.steelclash.gametest.TestSupport.FACING_NEGATIVE_X;
import static com.steelclash.gametest.TestSupport.FACING_POSITIVE_X;
import static com.steelclash.gametest.TestSupport.check;
import static com.steelclash.gametest.TestSupport.data;
import static com.steelclash.gametest.TestSupport.dummy;
import static com.steelclash.gametest.TestSupport.isHurt;
import static com.steelclash.gametest.TestSupport.swing;
import static com.steelclash.gametest.TestSupport.swordsman;

import com.steelclash.SteelClash;
import com.steelclash.combat.Combat;
import com.steelclash.combat.LagCompensation;
import com.steelclash.core.AttackType;
import com.steelclash.core.AttackTimings;
import com.steelclash.core.Phase;
import com.steelclash.entity.TrainingDummy;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** M7: lag compensation. Latency is forced, since mock players and mobs have no connection. */
@GameTestHolder(SteelClash.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LagGameTests {
    private static final String ARENA = "arena";
    /** 150 ms: the milestone's target. Rewinds 5 ticks, holds hits for 4. */
    private static final int PING = 150;

    private LagGameTests() {
    }

    @GameTest(template = ARENA)
    public static void laggedAttackerHitsWhereTheTargetWas(GameTestHelper helper) {
        TrainingDummy target = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        helper.runAfterDelay(10, () -> {
            // The target steps out of reach; a lagged attacker's screen still shows it in front of them.
            Vec3 away = helper.absoluteVec(new Vec3(7.5, 2, 4.5));
            target.teleportTo(away.x, away.y, away.z);

            Player local = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
            swing(local, AttackType.SLASH);
            check(helper, !isHurt(target), "without latency the slash must miss a target that moved away");

            Player lagged = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
            LagCompensation.forceLatency(lagged, PING);
            swing(lagged, AttackType.SLASH);
            check(helper, isHurt(target), "a " + PING + " ms attacker should hit the target where their screen showed it");
            helper.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void rewindNeverReachesFurtherBackThanTheCap(GameTestHelper helper) {
        TrainingDummy target = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        helper.runAfterDelay(20, () -> {
            Vec3 away = helper.absoluteVec(new Vec3(7.5, 2, 4.5));
            target.teleportTo(away.x, away.y, away.z);
            helper.runAfterDelay(8, () -> {
                // Moved 8 ticks ago: past the 300 ms (6 tick) cap even for a terrible connection.
                Player lagged = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
                LagCompensation.forceLatency(lagged, 2000);
                swing(lagged, AttackType.SLASH);
                check(helper, !isHurt(target), "rewind must be capped");
                helper.succeed();
            });
        });
    }

    @GameTest(template = ARENA)
    public static void laggedDefenderCanStillParry(GameTestHelper helper) {
        Player attacker = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        TrainingDummy defender = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        LagCompensation.forceLatency(defender, PING);
        swing(attacker, AttackType.SLASH);
        check(helper, !isHurt(defender), "the hit should be held while the lagged defender's input is in flight");
        // The parry arrives a tick later, as it would from a client 150 ms away.
        helper.runAfterDelay(1, () -> Combat.requestParry(defender));
        helper.runAfterDelay(2, () -> {
            check(helper, !isHurt(defender), "the late parry should stop the held hit");
            check(helper, data(defender).machine.parriedHits() >= 1, "the parry should count the hit");
            check(helper, data(attacker).machine.phase() == Phase.STAGGER, "the parried attacker should be staggered");
            helper.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void heldHitLandsWhenTheGraceRunsOut(GameTestHelper helper) {
        Player attacker = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        TrainingDummy defender = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        LagCompensation.forceLatency(defender, PING);
        swing(attacker, AttackType.SLASH);
        helper.runAfterDelay(2, () -> check(helper, !isHurt(defender), "still held 2 ticks later"));
        helper.runAfterDelay(8, () -> {
            check(helper, isHurt(defender), "without a parry the held hit lands after the grace");
            helper.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void defenderWithoutLatencyIsHitAtOnce(GameTestHelper helper) {
        Player attacker = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        TrainingDummy defender = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        swing(attacker, AttackType.SLASH);
        check(helper, isHurt(defender), "no latency, no delay");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void expiredMatchingCounterDoesNotSkipParryGrace(GameTestHelper helper) {
        Player attacker = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        TrainingDummy defender = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        Combat.start(attacker, data(attacker), AttackType.SLASH);
        Combat.start(defender, data(defender), AttackType.SLASH);
        var m = data(defender).machine;
        // The attack still winds up, but its counter window has expired.
        m.apply(Phase.WINDUP, AttackType.SLASH, 50L * AttackTimings.TICK_US, 100L * AttackTimings.TICK_US,
                AttackTimings.ofTicks(100, 5, 5), 0, false, false, false, 0, false);
        LagCompensation.forceLatency(defender, PING);
        var spec = Combat.currentSpec(attacker, data(attacker)).orElseThrow();
        var hit = new Combat.Hit(AttackType.SLASH, spec, 1f, spec.staminaDamage(), false);
        check(helper, LagCompensation.hold(attacker, defender, hit), "an expired counter cannot end the grace early");
        LagCompensation.tick();
        check(helper, !isHurt(defender), "keep waiting for a real defense or the deadline");
        Combat.requestParry(defender);
        LagCompensation.tick();
        check(helper, !isHurt(defender) && m.parriedHits() > 0, "the actual parry can still arrive during the grace");
        helper.succeed();
    }
}

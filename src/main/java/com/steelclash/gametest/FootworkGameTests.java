package com.steelclash.gametest;

import static com.steelclash.gametest.TestSupport.FACING_NEGATIVE_X;
import static com.steelclash.gametest.TestSupport.FACING_POSITIVE_X;
import static com.steelclash.gametest.TestSupport.advance;
import static com.steelclash.gametest.TestSupport.check;
import static com.steelclash.gametest.TestSupport.data;
import static com.steelclash.gametest.TestSupport.dummy;
import static com.steelclash.gametest.TestSupport.finish;
import static com.steelclash.gametest.TestSupport.isHurt;
import static com.steelclash.gametest.TestSupport.swordsman;

import com.steelclash.SteelClash;
import com.steelclash.combat.Combat;
import com.steelclash.combat.CombatData;
import com.steelclash.combat.Disarm;
import com.steelclash.combat.Dodge;
import com.steelclash.combat.HealthRegen;
import com.steelclash.core.AttackType;
import com.steelclash.core.Phase;
import com.steelclash.entity.TrainingDummy;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Chivalry 2 footwork and sustain: slower while fighting, ducking under slashes, health regeneration. */
@GameTestHolder(SteelClash.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FootworkGameTests {
    private static final String ARENA = "arena";

    private FootworkGameTests() {
    }

    @GameTest(template = ARENA)
    public static void attackingSlowsYouDown(GameTestHelper helper) {
        Player attacker = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        double normal = attacker.getAttributeValue(Attributes.MOVEMENT_SPEED);
        Combat.requestAttack(attacker, AttackType.SLASH);
        advance(attacker, 2);
        double windup = attacker.getAttributeValue(Attributes.MOVEMENT_SPEED);
        check(helper, Math.abs(windup - normal * 0.65) < 1e-6, "windup should move at 65%: " + windup + " vs " + normal);
        finish(attacker);
        advance(attacker, 1);
        check(helper, Math.abs(attacker.getAttributeValue(Attributes.MOVEMENT_SPEED) - normal) < 1e-6,
                "full speed again after the attack");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void duckingUnderALevelSlash(GameTestHelper helper) {
        TrainingDummy target = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        target.setPose(Pose.CROUCHING);

        Player level = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        swingSlash(level, AttackType.SLASH);
        check(helper, !isHurt(target), "a level slash should pass over a ducking target");

        Player low = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        low.setXRot(25);
        swingSlash(low, AttackType.SLASH);
        check(helper, isHurt(target), "a slash aimed down should still hit a ducking target");

        target.setHealth(target.getMaxHealth());
        Player overhead = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        swingSlash(overhead, AttackType.OVERHEAD);
        check(helper, isHurt(target), "an overhead should hit a ducking target");

        target.setHealth(target.getMaxHealth());
        target.setPose(Pose.STANDING);
        Player standing = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        swingSlash(standing, AttackType.SLASH);
        check(helper, isHurt(target), "the same level slash hits a standing target");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void healthComesBackAfterAQuietSpell(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        CombatData data = data(player);
        long now = helper.getLevel().getGameTime();
        player.setHealth(5);
        data.lastHurtAt = now - 20; // hurt a second ago: still too soon
        for (int i = 0; i < 20; i++) {
            HealthRegen.tick(player, data);
        }
        check(helper, player.getHealth() == 5, "no regeneration right after taking damage, health " + player.getHealth());
        data.lastHurtAt = now - 200; // ten seconds ago
        for (int i = 0; i < 20; i++) {
            HealthRegen.tick(player, data);
        }
        check(helper, Math.abs(player.getHealth() - 6) < 0.01, "one health per second once regenerating, health " + player.getHealth());
        for (int i = 0; i < 200; i++) {
            HealthRegen.tick(player, data);
        }
        check(helper, Math.abs(player.getHealth() - 8) < 0.01, "regeneration stops at 40% (8 of 20), health " + player.getHealth());
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void fightingPausesHealthRegeneration(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        CombatData data = data(player);
        player.setHealth(5);
        data.lastHurtAt = helper.getLevel().getGameTime() - 200;
        Combat.requestParry(player);
        for (int i = 0; i < 20; i++) {
            HealthRegen.tick(player, data);
        }
        check(helper, player.getHealth() == 5, "no regeneration while guarding, health " + player.getHealth());
        Combat.releaseParry(player);
        advance(player, 30); // the guard comes down
        for (int i = 0; i < 20; i++) {
            HealthRegen.tick(player, data);
        }
        check(helper, player.getHealth() == 5, "still none just after the fight, health " + player.getHealth());
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void dodgeCostsStaminaCancelsAWindupAndHasACooldown(GameTestHelper helper) {
        TrainingDummy dummy = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        helper.runAfterDelay(5, () -> { // let it settle onto the floor
            CombatData d = data(dummy);
            check(helper, dummy.onGround(), "the dummy should be standing on the floor");
            Combat.start(dummy, d, AttackType.SLASH);
            float before = d.stamina.current();
            check(helper, Dodge.perform(dummy, d), "a dodge during a windup is allowed");
            check(helper, d.machine.phase() == Phase.IDLE, "the dodge abandons the windup, phase " + d.machine.phase());
            check(helper, Math.abs(before - d.stamina.current() - 12) < 0.01, "a dodge costs 12 stamina");
            check(helper, !Dodge.canDodge(dummy, d), "no second dodge straight away");
            d.dodgeReadyAt = 0;
            Combat.start(dummy, d, AttackType.SLASH);
            while (d.machine.phase() == Phase.WINDUP) {
                d.machine.tick();
            }
            check(helper, !Dodge.canDodge(dummy, d), "no dodging out of a swing in its release");
            d.machine.cancel();
            d.stamina.set(5);
            check(helper, !Dodge.canDodge(dummy, d), "no dodging without the stamina for it");
            Vec3 back = Dodge.velocity(0, 0, 0);
            check(helper, back.z < 0 && Math.abs(back.x) < 1e-6, "standing still dodges backwards (yaw 0 faces +Z), got " + back);
            Vec3 left = Dodge.velocity(0, 1, 0);
            check(helper, left.x > 0 && Math.abs(left.z) < 1e-6, "strafing left at yaw 0 goes toward +X, got " + left);
            helper.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void dodgeRulesAndDisarmCooldowns(GameTestHelper helper) {
        TrainingDummy dummy = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        helper.runAfterDelay(5, () -> {
            CombatData d = data(dummy);
            long now = helper.getLevel().getGameTime();
            Combat.start(dummy, d, AttackType.JAB);
            int plainJab = d.machine.timings().windup();
            d.machine.cancel();
            check(helper, Dodge.perform(dummy, d), "dodge");
            check(helper, !Combat.startParry(dummy, d), "no guard in the first half of the dash");
            Combat.start(dummy, d, AttackType.JAB);
            check(helper, d.machine.timings().windup() > plainJab, "a jab right after a dodge is slower");
            d.machine.cancel();
            d.dodgedAt = now - Dodge.DASH_TICKS;
            check(helper, Combat.startParry(dummy, d), "the guard comes up once the dash is half done");
            d.machine.cancel();
            d.dodgeReadyAt = 0;
            d.stamina.set(d.stamina.max());
            Disarm.disarm(dummy);
            check(helper, !Dodge.canDodge(dummy, d), "no dodge straight after being disarmed");
            check(helper, !Combat.start(dummy, d, AttackType.JAB), "no jab either");
            helper.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void crouchingPausesStaminaRegeneration(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        CombatData d = data(player);
        d.stamina.set(50);
        player.setPose(Pose.CROUCHING);
        advance(player, 60);
        check(helper, d.stamina.current() == 50, "no regeneration while crouched, stamina " + d.stamina.current());
        player.setPose(Pose.STANDING);
        advance(player, 60);
        check(helper, d.stamina.current() > 50, "regenerates again standing up");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void jumpingCostsStaminaOnlyWhileFighting(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        CombatData d = data(player);
        long now = helper.getLevel().getGameTime();
        d.lastCombatAt = now - 400; // a while since the last fight
        NeoForge.EVENT_BUS.post(new LivingEvent.LivingJumpEvent(player));
        check(helper, d.stamina.current() == d.stamina.max(), "jumping outside a fight is free");
        d.lastCombatAt = now;
        NeoForge.EVENT_BUS.post(new LivingEvent.LivingJumpEvent(player));
        float spent = d.stamina.max() - d.stamina.current();
        check(helper, Math.abs(spent - 12) < 0.01, "a jump mid-fight costs 12 stamina, cost " + spent);
        helper.succeed();
    }

    /** A fixed variant so the arc height is known: variant 0 passes level through the middle of the swing. */
    private static void swingSlash(Player attacker, AttackType type) {
        CombatData data = data(attacker);
        Combat.requestAttack(attacker, type, 0, false);
        for (int tick = 0; tick < 200 && data.machine.isAttacking(); tick++) {
            Combat.tickServer(attacker, data);
        }
    }
}

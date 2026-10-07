package com.steelclash.gametest;

import static com.steelclash.gametest.TestSupport.FACING_NEGATIVE_X;
import static com.steelclash.gametest.TestSupport.FACING_POSITIVE_X;
import static com.steelclash.gametest.TestSupport.advance;
import static com.steelclash.gametest.TestSupport.check;
import static com.steelclash.gametest.TestSupport.data;
import static com.steelclash.gametest.TestSupport.dummy;
import static com.steelclash.gametest.TestSupport.finish;
import static com.steelclash.gametest.TestSupport.isHurt;
import static com.steelclash.gametest.TestSupport.swing;
import static com.steelclash.gametest.TestSupport.swordsman;

import com.steelclash.SteelClash;
import com.steelclash.combat.Combat;
import com.steelclash.combat.CombatData;
import com.steelclash.core.AttackType;
import com.steelclash.core.Phase;
import com.steelclash.entity.TrainingDummy;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** M3: heavies, feints, morphs, combos, flinches, kicks, counters, clanks and lunges. */
@GameTestHolder(SteelClash.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OffenseGameTests {
    private static final String ARENA = "arena";

    private OffenseGameTests() {
    }

    @GameTest(template = ARENA)
    public static void heavyWindsUpLongerAndHitsHarder(GameTestHelper helper) {
        Player light = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X, 2);
        Player heavy = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X, 6);
        Zombie lightTarget = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 2);
        Zombie heavyTarget = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 6);

        Combat.requestAttack(light, AttackType.STAB);
        int lightWindup = data(light).machine.phaseDuration();
        finish(light);

        Combat.requestAttack(heavy, AttackType.STAB);
        Combat.requestHeavy(heavy);
        int heavyWindup = data(heavy).machine.phaseDuration();
        check(helper, data(heavy).machine.isHeavy(), "should be a heavy");
        finish(heavy);

        float lightDamage = lightTarget.getMaxHealth() - lightTarget.getHealth();
        float heavyDamage = heavyTarget.getMaxHealth() - heavyTarget.getHealth();
        check(helper, heavyWindup > lightWindup, "heavy windup " + heavyWindup + " should exceed light " + lightWindup);
        check(helper, lightDamage > 0 && heavyDamage > lightDamage,
                "heavy damage " + heavyDamage + " should exceed light damage " + lightDamage);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void feintCancelsTheAttack(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 4);
        Combat.requestAttack(player, AttackType.SLASH);
        advance(player, 2);
        Combat.requestFeint(player);
        CombatData d = data(player);
        check(helper, d.machine.phase() == Phase.IDLE, "feint should return to idle, was " + d.machine.phase());
        advance(player, 30);
        check(helper, !isHurt(zombie), "a feinted attack must not hit");
        check(helper, d.stamina.current() < d.stamina.max(), "feinting costs stamina");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void morphSwitchesTheAttack(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        Zombie front = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 4);
        Zombie behind = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 2, 4);
        Combat.requestAttack(player, AttackType.SLASH);
        advance(player, 2);
        Combat.requestAttack(player, AttackType.STAB);
        check(helper, data(player).machine.type() == AttackType.STAB && data(player).machine.isMorphed(), "should morph into a stab");
        finish(player);
        check(helper, isHurt(front) && !isHurt(behind), "the morphed stab should hit only the front zombie");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void landedHitAllowsCombo(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 4);
        Combat.requestAttack(player, AttackType.SLASH);
        CombatData d = data(player);
        for (int i = 0; i < 100 && d.machine.phase() != Phase.RECOVERY; i++) {
            Combat.tickServer(player, d);
        }
        check(helper, d.machine.isComboAllowed(), "a landed slash should allow a combo");
        Combat.requestAttack(player, AttackType.OVERHEAD);
        check(helper, d.machine.phase() == Phase.WINDUP && d.machine.type() == AttackType.OVERHEAD,
                "the combo should start immediately, was " + d.machine.phase());
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void whiffMustSitOutRecovery(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        Combat.requestAttack(player, AttackType.SLASH);
        CombatData d = data(player);
        for (int i = 0; i < 100 && d.machine.phase() != Phase.RECOVERY; i++) {
            Combat.tickServer(player, d);
        }
        Combat.requestAttack(player, AttackType.OVERHEAD);
        check(helper, d.machine.phase() == Phase.RECOVERY, "a whiff can't combo; the next attack waits");
        check(helper, d.queuedAttack == AttackType.OVERHEAD, "but it is buffered");
        check(helper, d.stamina.current() < d.stamina.max(), "whiffing costs stamina");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void hitDuringWindupFlinches(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        TrainingDummy dummy = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        Combat.requestAttack(dummy, AttackType.STAB);
        check(helper, data(dummy).machine.phase() == Phase.WINDUP, "dummy should be winding up");
        swing(player, AttackType.OVERHEAD);
        check(helper, isHurt(dummy), "the overhead should land");
        check(helper, data(dummy).machine.phase() == Phase.STAGGER, "the dummy's windup should be interrupted");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void jabInterruptsAWindup(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        TrainingDummy dummy = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        Combat.requestAttack(dummy, AttackType.OVERHEAD);
        swing(player, AttackType.JAB);
        check(helper, isHurt(dummy), "the jab should land");
        check(helper, data(dummy).machine.phase() == Phase.STAGGER, "the jab interrupts the windup");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void jabIsQuickAndLight(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        TrainingDummy jabbed = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        swing(player, AttackType.JAB);
        float jabDamage = jabbed.getMaxHealth() - jabbed.getHealth();
        jabbed.discard();
        TrainingDummy slashed = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        swing(player, AttackType.SLASH);
        float slashDamage = slashed.getMaxHealth() - slashed.getHealth();
        check(helper, jabDamage > 0 && jabDamage < slashDamage * 0.5f, "jab " + jabDamage + " vs slash " + slashDamage);
        CombatData d = data(player);
        Combat.start(player, d, AttackType.JAB);
        int jabWindup = d.machine.timings().windup();
        check(helper, !Combat.feint(player, d), "a jab can't be feinted");
        check(helper, !Combat.makeHeavy(player, d), "or made heavy");
        check(helper, !Combat.startParry(player, d), "or cancelled into a parry");
        d.machine.cancel();
        Combat.start(player, d, AttackType.SLASH);
        check(helper, jabWindup < d.machine.timings().windup(), "a jab winds up faster than a slash");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void jabCanBeParried(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        TrainingDummy dummy = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        Combat.requestParry(dummy);
        swing(player, AttackType.JAB);
        check(helper, !isHurt(dummy), "a parried jab doesn't hurt");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void kickDoesNotInterruptAnAttacker(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        TrainingDummy dummy = dummy(helper, 2, 4, FACING_NEGATIVE_X);
        Combat.requestAttack(dummy, AttackType.OVERHEAD);
        swing(player, AttackType.KICK);
        check(helper, data(dummy).machine.phase() == Phase.WINDUP, "the dummy swings straight through the kick, phase "
                + data(dummy).machine.phase());
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void kicksBlockKicks(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        TrainingDummy dummy = dummy(helper, 2, 4, FACING_NEGATIVE_X);
        Combat.requestAttack(dummy, AttackType.KICK);
        swing(player, AttackType.KICK);
        check(helper, !isHurt(dummy) && data(dummy).machine.phase() == Phase.WINDUP, "two kicks cancel out");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void jabsBlockJabs(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        TrainingDummy dummy = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        Combat.requestAttack(dummy, AttackType.JAB);
        swing(player, AttackType.JAB);
        check(helper, !isHurt(dummy), "the dummy's jab blocks the incoming jab");
        check(helper, data(player).machine.phase() == Phase.STAGGER, "the blocked jabber reels back");
        check(helper, data(dummy).machine.type() == AttackType.JAB && data(dummy).machine.isAttacking(), "the dummy's jab carries on");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void feintIntoAKickOrJab(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        CombatData d = data(player);
        Combat.requestAttack(player, AttackType.SLASH);
        Combat.requestAttack(player, AttackType.KICK);
        check(helper, d.machine.phase() == Phase.WINDUP && d.machine.type() == AttackType.KICK, "the slash became a kick");
        d.machine.cancel();
        Combat.requestAttack(player, AttackType.OVERHEAD);
        Combat.requestAttack(player, AttackType.JAB);
        check(helper, d.machine.type() == AttackType.JAB, "the overhead became a jab");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void attackPressedWhileStaggeredStartsWhenItEnds(GameTestHelper helper) {
        TrainingDummy dummy = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        CombatData d = data(dummy);
        Combat.stagger(dummy, d, 5, true);
        Combat.requestAttack(dummy, AttackType.SLASH);
        check(helper, d.queuedAttack == AttackType.SLASH, "the attack is buffered during the stagger");
        advance(dummy, 6);
        check(helper, d.machine.phase() == Phase.WINDUP, "and starts as soon as it ends, phase " + d.machine.phase());
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void heavyHyperArmorIgnoresFlinch(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        TrainingDummy dummy = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        dummy.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.MACE)); // blunt: hyper armor on heavies
        Combat.requestAttack(dummy, AttackType.STAB);
        Combat.requestHeavy(dummy);
        swing(player, AttackType.OVERHEAD);
        check(helper, isHurt(dummy), "the overhead should land");
        check(helper, data(dummy).machine.phase() == Phase.WINDUP, "a hyper-armored heavy keeps winding up, was "
                + data(dummy).machine.phase());
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void kickBreaksAShieldGuard(GameTestHelper helper) {
        TrainingDummy dummy = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        dummy.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        dummy.startUsingItem(InteractionHand.OFF_HAND);
        helper.runAfterDelay(8, () -> {
            check(helper, dummy.isBlocking(), "dummy should be blocking");
            Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
            swing(player, AttackType.KICK);
            check(helper, !dummy.isUsingItem(), "the kick should knock the shield down");
            check(helper, data(dummy).machine.phase() == Phase.STAGGER, "the kicked guard should stagger");
            check(helper, data(dummy).stamina.current() < data(dummy).stamina.max(), "kicking a guard drains stamina");
            check(helper, !isHurt(dummy), "kicking a guard does no damage");
            helper.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void kickBreaksAParry(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        TrainingDummy dummy = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        Combat.requestParry(dummy);
        swing(player, AttackType.KICK);
        check(helper, data(dummy).machine.phase() == Phase.STAGGER, "the kick should break the parry");
        check(helper, data(player).machine.phase() != Phase.STAGGER, "a kick is never parried");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void kickStaggersAnUnguardedTarget(GameTestHelper helper) {
        Player player = swordsman(helper, ItemStack.EMPTY, FACING_POSITIVE_X); // kicks need no weapon
        TrainingDummy dummy = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        swing(player, AttackType.KICK);
        check(helper, data(dummy).machine.phase() == Phase.STAGGER, "an unguarded kick target should stagger");
        check(helper, isHurt(dummy), "and take a little damage");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void sameAttackCounters(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        TrainingDummy dummy = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        Combat.requestAttack(dummy, AttackType.SLASH);
        int before = data(dummy).machine.phaseDuration();
        swing(player, AttackType.SLASH);
        check(helper, !isHurt(dummy), "a counter parries the incoming slash");
        check(helper, data(player).machine.phase() == Phase.STAGGER, "the countered attacker is staggered");
        check(helper, data(dummy).machine.phase() == Phase.WINDUP && data(dummy).machine.phaseDuration() < before,
                "the counter keeps winding up, faster");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void differentAttackDoesNotCounter(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        TrainingDummy dummy = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        Combat.requestAttack(dummy, AttackType.STAB);
        swing(player, AttackType.SLASH);
        check(helper, isHurt(dummy), "a stab doesn't counter a slash");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void bladeClanksOffAWall(GameTestHelper helper) {
        for (int y = 2; y <= 4; y++) {
            for (int z = 2; z <= 6; z++) {
                helper.setBlock(2, y, z, Blocks.STONE);
            }
        }
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        swing(player, AttackType.SLASH);
        check(helper, data(player).machine.phase() == Phase.STAGGER, "slashing into a wall should clank and stagger");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void overheadIntoTheFloorDoesNotClank(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        swing(player, AttackType.OVERHEAD);
        check(helper, data(player).machine.phase() != Phase.STAGGER, "the floor must not clank");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void sprintLungeReachesFarther(GameTestHelper helper) {
        Player sprinter = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X, 2);
        Player walker = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X, 6);
        Zombie far1 = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 5, 2, 2);
        Zombie far2 = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 5, 2, 6);
        sprinter.setSprinting(true);
        swing(sprinter, AttackType.STAB);
        swing(walker, AttackType.STAB);
        check(helper, isHurt(far1), "a sprinting stab should reach the far zombie");
        check(helper, !isHurt(far2), "a standing stab should fall short");
        helper.succeed();
    }
}

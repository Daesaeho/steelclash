package com.steelclash.gametest;

import static com.steelclash.gametest.TestSupport.FACING_NEGATIVE_X;
import static com.steelclash.gametest.TestSupport.check;
import static com.steelclash.gametest.TestSupport.data;
import static com.steelclash.gametest.TestSupport.dummy;
import static com.steelclash.gametest.TestSupport.face;

import com.steelclash.SteelClash;
import net.minecraft.world.entity.Mob;
import com.steelclash.combat.MobCombat;
import com.steelclash.combat.Combat;
import com.steelclash.ai.ClashSpacingGoal;
import com.steelclash.combat.ModAttachments;
import com.steelclash.core.AttackType;
import com.steelclash.core.Phase;
import com.steelclash.entity.TrainingDummy;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** M5: the bot brain (attack turns, kicking turtles, spacing). GameTest worlds run on Normal difficulty. */
@GameTestHolder(SteelClash.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BotGameTests {
    private static final String ARENA = "arena";

    private BotGameTests() {
    }

    @GameTest(template = ARENA, timeoutTicks = 260)
    public static void attackersTakeTurns(GameTestHelper helper) {
        TrainingDummy target = dummy(helper, 4, 4, FACING_NEGATIVE_X);
        List<Zombie> zombies = List.of(
                zombie(helper, 2, 4, target), zombie(helper, 6, 4, target),
                zombie(helper, 4, 2, target), zombie(helper, 4, 6, target));
        AtomicInteger mostAtOnce = new AtomicInteger();
        Set<String> attacks = new HashSet<>();
        helper.onEachTick(() -> {
            int swinging = 0;
            for (Zombie zombie : zombies) {
                if (!zombie.hasData(ModAttachments.COMBAT)) {
                    continue;
                }
                var machine = data(zombie).machine;
                if (machine.phase() == Phase.WINDUP || machine.phase() == Phase.RELEASE) {
                    swinging++;
                    attacks.add(zombie.getId() + ":" + machine.attackSerial());
                }
            }
            mostAtOnce.set(Math.max(mostAtOnce.get(), swinging));
        });
        helper.runAfterDelay(240, () -> {
            check(helper, attacks.size() >= 3, "the zombies should keep attacking (saw " + attacks.size() + " attacks)");
            check(helper, mostAtOnce.get() <= 2, "Normal allows 2 attackers at once, saw " + mostAtOnce.get());
            helper.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void botKicksATurtle(GameTestHelper helper) {
        TrainingDummy turtle = dummy(helper, 5, 4, FACING_NEGATIVE_X);
        turtle.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        turtle.setMode(TrainingDummy.Mode.PARRY); // holds its shield up
        Zombie zombie = zombie(helper, 3, 4, turtle);
        AtomicBoolean kicked = new AtomicBoolean();
        helper.onEachTick(() -> {
            face(turtle, FACING_NEGATIVE_X);
            if (zombie.hasData(ModAttachments.COMBAT)) {
                var machine = data(zombie).machine;
                if (machine.type() == AttackType.KICK && machine.isAttacking()) {
                    kicked.set(true);
                }
            }
        });
        helper.succeedWhen(() -> check(helper, kicked.get(), "the zombie should kick a target that hides behind its shield"));
    }

    @GameTest(template = ARENA)
    public static void fightersGetTheSpacingGoal(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 3, 2, 4); // not NoFreeWill: that strips every goal
        boolean installed = zombie.goalSelector.getAvailableGoals().stream().anyMatch(g -> g.getGoal() instanceof ClashSpacingGoal);
        check(helper, installed, "zombies should get the bot spacing goal");
        helper.succeed();
    }

    private static Zombie zombie(GameTestHelper helper, int x, int z, TrainingDummy target) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, x, 2, z);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // no burning in daylight
        zombie.setTarget(target);
        return zombie;
    }

    /** Vindicators cross their arms (hiding them and the axe) unless flagged aggressive; fighters stay flagged while fighting. */
    @GameTest(template = ARENA)
    public static void fightingMobsStayInTheirFightingStance(GameTestHelper helper) {
        Mob vindicator = helper.spawnWithNoFreeWill(EntityType.VINDICATOR, 2, 2, 4);
        TrainingDummy target = dummy(helper, 5, 4, FACING_NEGATIVE_X);
        vindicator.setAggressive(false);
        vindicator.setTarget(target);
        MobCombat.keepAggressive(vindicator, data(vindicator));
        check(helper, vindicator.isAggressive(), "a vindicator with a target close by is aggressive (arms uncrossed)");
        vindicator.setTarget(null);
        MobCombat.keepAggressive(vindicator, data(vindicator));
        check(helper, !vindicator.isAggressive(), "and calms down again once the fight is over");
        Combat.requestAttack(vindicator, AttackType.SLASH);
        MobCombat.keepAggressive(vindicator, data(vindicator));
        check(helper, vindicator.isAggressive(), "swinging always counts as fighting");
        helper.succeed();
    }
}

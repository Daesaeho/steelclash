package com.steelclash.gametest;

import static com.steelclash.gametest.TestSupport.FACING_NEGATIVE_X;
import static com.steelclash.gametest.TestSupport.FACING_POSITIVE_X;
import static com.steelclash.gametest.TestSupport.check;
import static com.steelclash.gametest.TestSupport.data;
import static com.steelclash.gametest.TestSupport.dummy;
import static com.steelclash.gametest.TestSupport.swordsman;

import com.steelclash.SteelClash;
import com.steelclash.ai.BrainState;
import com.steelclash.ai.ClashBrain;
import com.steelclash.combat.Combat;
import com.steelclash.combat.CombatData;
import com.steelclash.combat.CombatMath;
import com.steelclash.combat.LiveAttacks;
import com.steelclash.combat.MobCombat;
import com.steelclash.combat.ModAttachments;
import com.steelclash.core.AttackType;
import com.steelclash.core.Guard;
import com.steelclash.core.Phase;
import com.steelclash.entity.TrainingDummy;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Defenders find incoming attacks in {@link LiveAttacks} instead of asking the level every tick. They must answer
 * exactly what the level query answered: the same reach, only fighters that are in the level, the level's order when
 * two attacks come at once. And the list must not keep attacks that are over.
 */
@GameTestHolder(SteelClash.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LiveAttackGameTests {
    private static final String ARENA = "arena";

    private LiveAttackGameTests() {
    }

    /** A sword-armed zombie bot at block (x, 2, z) that has answered nothing yet and won't attack by itself. */
    private static Zombie bot(GameTestHelper helper, int x, int z, LivingEntity target) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, x, 2, z);
        zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // no burning in daylight
        zombie.setTarget(target);
        CombatData d = data(zombie);
        d.brain = new BrainState();
        d.brain.cooldown = 10;
        return zombie;
    }

    /** What the level query the bots used returns: the windups around {@code defender} that face it, in the level's order. */
    private static List<LivingEntity> levelThreats(GameTestHelper helper, LivingEntity defender) {
        return helper.getLevel().getEntitiesOfClass(LivingEntity.class, defender.getBoundingBox().inflate(5),
                e -> e != defender && e.hasData(ModAttachments.COMBAT) && data(e).machine.phase() == Phase.WINDUP
                        && Guard.inCone(CombatMath.viewYaw(e), e.getX(), e.getZ(), defender.getX(), defender.getZ(), 120));
    }

    @GameTest(template = ARENA)
    public static void botIgnoresAWindupOutOfReach(GameTestHelper helper) {
        TrainingDummy far = dummy(helper, 1, 4, FACING_POSITIVE_X);
        Zombie zombie = bot(helper, 7, 4, far); // six blocks away: the hitboxes are more than five apart
        Combat.start(far, data(far), AttackType.SLASH);
        check(helper, levelThreats(helper, zombie).isEmpty(), "premise: the level finds no windup within five blocks");
        long asked = LiveAttacks.levelQueries();
        ClashBrain.tick(zombie, data(zombie));
        check(helper, data(zombie).brain.answeredAttacker == -1, "a windup out of reach is not answered");

        TrainingDummy near = dummy(helper, 5, 4, FACING_POSITIVE_X);
        Combat.start(near, data(near), AttackType.SLASH);
        ClashBrain.tick(zombie, data(zombie));
        check(helper, data(zombie).brain.answeredAttacker == near.getId(), "the one within reach is");
        check(helper, LiveAttacks.levelQueries() == asked, "none or one attack in reach: the list answers, the level is not asked");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void botIgnoresALoneWindupFacingAway(GameTestHelper helper) {
        TrainingDummy attacker = dummy(helper, 1, 4, FACING_NEGATIVE_X); // its back to the bot
        Zombie zombie = bot(helper, 3, 4, attacker);
        Combat.start(attacker, data(attacker), AttackType.SLASH);
        check(helper, levelThreats(helper, zombie).isEmpty(), "premise: the level finds no windup that faces the bot");
        ClashBrain.tick(zombie, data(zombie));
        check(helper, data(zombie).brain.answeredAttacker == -1, "a windup aimed elsewhere is not a threat");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void botDoesNotAnswerItsOwnWindup(GameTestHelper helper) {
        TrainingDummy target = dummy(helper, 5, 4, FACING_NEGATIVE_X);
        Zombie zombie = bot(helper, 3, 4, target);
        check(helper, Combat.start(zombie, data(zombie), AttackType.SLASH), "premise: the bot itself is winding up");
        ClashBrain.tick(zombie, data(zombie));
        check(helper, data(zombie).brain.answeredAttacker == -1, "its own attack is on the list, and is no threat to it");
        helper.succeed();
    }

    /** An attack stays on the list until the end of the tick it ended in; it is no threat any more. */
    @GameTest(template = ARENA)
    public static void botIgnoresAnAttackThatJustEnded(GameTestHelper helper) {
        TrainingDummy attacker = dummy(helper, 1, 4, FACING_POSITIVE_X);
        Zombie zombie = bot(helper, 3, 4, attacker);
        CombatData a = data(attacker);
        Combat.start(attacker, a, AttackType.SLASH);
        Combat.cancel(attacker, a);
        check(helper, LiveAttacks.lists(attacker), "premise: still listed, the sweep comes at the end of the tick");
        ClashBrain.tick(zombie, data(zombie));
        check(helper, data(zombie).brain.answeredAttacker == -1, "an attack that is over is not answered");

        // Nor is a release the bot made no plan for: it parries late only what it decided to parry late.
        Combat.start(attacker, a, AttackType.SLASH);
        while (a.machine.phase() == Phase.WINDUP) {
            a.machine.tick(); // advance the telegraph without delivering the blade
        }
        check(helper, a.machine.phase() == Phase.RELEASE, "premise: the blade is live");
        ClashBrain.tick(zombie, data(zombie));
        check(helper, data(zombie).brain.answeredAttacker == -1, "a release it had no plan for is not answered");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void botDoesNotSeeAFighterOutsideTheLevel(GameTestHelper helper) {
        Player outsider = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X); // a mock player: in no level
        TrainingDummy target = dummy(helper, 5, 4, FACING_NEGATIVE_X);
        Zombie zombie = bot(helper, 3, 4, target);
        Combat.start(outsider, data(outsider), AttackType.SLASH);
        check(helper, LiveAttacks.lists(outsider), "premise: the attack is listed; only the level tells it isn't there");
        ClashBrain.tick(zombie, data(zombie));
        check(helper, data(zombie).brain.answeredAttacker == -1,
                "bots answer what a level query finds, and a fighter that isn't in the level was never found");
        helper.succeed();
    }

    /** Two windups at once: the level's order picks, whichever of the two attacks started first. */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void theLevelsOrderDecidesBetweenTwoWindups(GameTestHelper helper) {
        TrainingDummy one = dummy(helper, 1, 3, FACING_POSITIVE_X);
        TrainingDummy two = dummy(helper, 1, 5, FACING_POSITIVE_X);
        Zombie zombie = bot(helper, 3, 4, one);
        answersTheLevelsFirst(helper, zombie, one, two);
        // After the list has forgotten both, the same pair again in the other starting order.
        helper.runAfterDelay(2, () -> {
            check(helper, !LiveAttacks.lists(one) && !LiveAttacks.lists(two), "premise: both attacks were forgotten");
            answersTheLevelsFirst(helper, zombie, two, one);
            helper.succeed();
        });
    }

    private static void answersTheLevelsFirst(GameTestHelper helper, Zombie zombie, TrainingDummy first, TrainingDummy second) {
        Combat.start(first, data(first), AttackType.SLASH);
        Combat.start(second, data(second), AttackType.SLASH);
        List<LivingEntity> expected = levelThreats(helper, zombie);
        check(helper, expected.size() == 2, "premise: both windups are threats, the level finds " + expected.size());
        BrainState brain = data(zombie).brain;
        brain.answeredAttacker = -1;
        long asked = LiveAttacks.levelQueries();
        ClashBrain.tick(zombie, data(zombie));
        check(helper, LiveAttacks.levelQueries() == asked + 1, "two at once: the level is asked, once");
        check(helper, brain.answeredAttacker == expected.get(0).getId(),
                "with " + first.getId() + " starting before " + second.getId() + " the bot answered " + brain.answeredAttacker
                        + ", the level's first is " + expected.get(0).getId());
        Combat.cancel(first, data(first));
        Combat.cancel(second, data(second));
    }

    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void anAttackThatIsOverLeavesTheList(GameTestHelper helper) {
        TrainingDummy attacker = dummy(helper, 1, 4, FACING_POSITIVE_X);
        Zombie zombie = bot(helper, 3, 4, attacker);
        Combat.start(attacker, data(attacker), AttackType.SLASH);
        check(helper, LiveAttacks.lists(attacker), "an attack is listed the moment it starts");
        Combat.cancel(attacker, data(attacker));
        helper.runAfterDelay(2, () -> {
            check(helper, !LiveAttacks.lists(attacker), "an attack that is over is forgotten at the end of the tick");
            Combat.start(attacker, data(attacker), AttackType.SLASH);
            ClashBrain.tick(zombie, data(zombie));
            BrainState brain = data(zombie).brain;
            check(helper, brain.answeredAttacker == attacker.getId() && brain.answeredSerial == data(attacker).machine.attackSerial(),
                    "the fighter's next attack is listed and answered again");
            helper.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void aFighterWhoLeavesIsForgotten(GameTestHelper helper) {
        TrainingDummy attacker = dummy(helper, 1, 4, FACING_POSITIVE_X);
        Combat.start(attacker, data(attacker), AttackType.SLASH);
        check(helper, LiveAttacks.lists(attacker), "premise: its attack is listed");
        attacker.discard(); // mid-windup: its state will say WINDUP for ever
        helper.runAfterDelay(2, () -> {
            check(helper, !LiveAttacks.lists(attacker), "a fighter that left the world must not stay listed");
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ the simple reactive parry (dummies, brainless mobs)

    @GameTest(template = ARENA)
    public static void aGuardingDummyParriesTheWindupInReach(GameTestHelper helper) {
        TrainingDummy attacker = dummy(helper, 1, 4, FACING_POSITIVE_X);
        TrainingDummy defender = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        TrainingDummy outOfReach = dummy(helper, 7, 4, FACING_NEGATIVE_X);
        CombatData a = data(attacker);
        Combat.start(attacker, a, AttackType.SLASH);
        while (a.machine.ticksLeftInPhase() > 2) {
            a.machine.tick(); // the guard goes up just before the blade goes live
        }
        check(helper, a.machine.phase() == Phase.WINDUP, "premise: still winding up");
        long asked = LiveAttacks.levelQueries();
        MobCombat.tickDefense(outOfReach, data(outOfReach), 1.0);
        check(helper, data(outOfReach).machine.phase() == Phase.IDLE && data(outOfReach).reactedAttackerId == -1,
                "six blocks away is out of reach: no reaction");
        MobCombat.tickDefense(defender, data(defender), 1.0);
        check(helper, data(defender).machine.phase() == Phase.PARRY, "the dummy in reach raises its guard");
        check(helper, LiveAttacks.levelQueries() == asked, "none or one attack in reach: the level is not asked");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void aGuardingDummyDoesNotReactToARelease(GameTestHelper helper) {
        TrainingDummy attacker = dummy(helper, 1, 4, FACING_POSITIVE_X);
        TrainingDummy defender = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        CombatData a = data(attacker);
        Combat.start(attacker, a, AttackType.SLASH);
        while (a.machine.phase() == Phase.WINDUP) {
            a.machine.tick(); // advance the telegraph without delivering the blade
        }
        check(helper, a.machine.phase() == Phase.RELEASE, "premise: the blade is live");
        MobCombat.tickDefense(defender, data(defender), 1.0);
        check(helper, data(defender).machine.phase() == Phase.IDLE && data(defender).reactedAttackerId == -1,
                "the reactive parry reads windups only; a release is too late");
        helper.succeed();
    }

    /** Two windups in reach: every one is considered, so the urgent one is parried whichever started first. */
    @GameTest(template = ARENA)
    public static void aGuardingDummyParriesTheUrgentOneOfTwo(GameTestHelper helper) {
        TrainingDummy urgent = dummy(helper, 1, 3, FACING_POSITIVE_X);
        TrainingDummy fresh = dummy(helper, 1, 5, FACING_POSITIVE_X);
        TrainingDummy defender = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        CombatData u = data(urgent);
        Combat.start(urgent, u, AttackType.SLASH);
        Combat.start(fresh, data(fresh), AttackType.SLASH);
        while (u.machine.ticksLeftInPhase() > 2) {
            u.machine.tick();
        }
        long asked = LiveAttacks.levelQueries();
        MobCombat.tickDefense(defender, data(defender), 1.0);
        check(helper, data(defender).machine.phase() == Phase.PARRY, "the windup about to land is parried, not just the latest one");
        check(helper, LiveAttacks.levelQueries() == asked + 1, "several at once: the level is asked, once");
        helper.succeed();
    }
}

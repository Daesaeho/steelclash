package com.steelclash.gametest;

import static com.steelclash.gametest.TestSupport.FACING_POSITIVE_X;
import static com.steelclash.gametest.TestSupport.FACING_NEGATIVE_X;
import static com.steelclash.gametest.TestSupport.check;
import static com.steelclash.gametest.TestSupport.data;
import static com.steelclash.gametest.TestSupport.dummy;
import static com.steelclash.gametest.TestSupport.face;

import com.steelclash.SteelClash;
import com.steelclash.ai.BrainState;
import com.steelclash.ai.ClashBrain;
import com.steelclash.ai.ClashSpacingGoal;
import com.steelclash.combat.Combat;
import com.steelclash.combat.CombatData;
import com.steelclash.combat.CombatMath;
import com.steelclash.combat.MobCombat;
import com.steelclash.combat.ModAttachments;
import com.steelclash.core.AttackType;
import com.steelclash.core.BotSkill;
import com.steelclash.core.Guard;
import com.steelclash.core.Phase;
import com.steelclash.entity.TrainingDummy;
import com.steelclash.profile.WeaponProfile;
import com.steelclash.profile.WeaponProfiles;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
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

    @GameTest(template = ARENA)
    public static void botCarriesItsLateParryPlanIntoRelease(GameTestHelper helper) {
        // A dummy, not a mock player: the bot finds threats with a level query, and mock players aren't in the level.
        TrainingDummy attacker = dummy(helper, 1, 4, FACING_POSITIVE_X);
        Zombie defender = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 4);
        defender.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        defender.setTarget(attacker);
        face(defender, FACING_NEGATIVE_X);
        Combat.start(attacker, data(attacker), AttackType.SLASH);
        while (data(attacker).machine.phase() == Phase.WINDUP) {
            data(attacker).machine.tick(); // advance the telegraph without delivering the blade
        }
        CombatData d = data(defender);
        d.brain = new BrainState();
        d.brain.answeredAttacker = attacker.getId();
        d.brain.answeredSerial = data(attacker).machine.attackSerial();
        d.brain.answer = BrainState.Answer.LATE_PARRY;
        d.brain.cooldown = 10;
        ClashBrain.tick(defender, d);
        check(helper, d.machine.phase() == Phase.PARRY, "the planned late parry must be raised during release");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void anUnplannedReleaseDoesNotHideAWindup(GameTestHelper helper) {
        TrainingDummy swinging = dummy(helper, 1, 3, FACING_POSITIVE_X);
        TrainingDummy windingUp = dummy(helper, 1, 5, FACING_POSITIVE_X);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 4);
        zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        zombie.setTarget(windingUp);
        Combat.start(swinging, data(swinging), AttackType.SLASH);
        while (data(swinging).machine.phase() == Phase.WINDUP) {
            data(swinging).machine.tick();
        }
        Combat.start(windingUp, data(windingUp), AttackType.SLASH);
        ClashBrain.tick(zombie, data(zombie));
        check(helper, data(zombie).brain != null && data(zombie).brain.answeredAttacker == windingUp.getId(),
                "a release the bot had no plan for must not take the place of the windup it can still answer");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void profilesWithoutSelectableAttacksDoNotCrash(GameTestHelper helper) {
        Zombie mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 4);
        mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        WeaponProfile base = WeaponProfiles.resolveFor(mob).orElseThrow().profile();
        WeaponProfile empty = withAttacks(base, Map.of());
        WeaponProfile kickOnly = withAttacks(base, Map.of(AttackType.KICK, base.attack(AttackType.SLASH).orElseThrow()));
        try {
            var reach = ClashBrain.class.getDeclaredMethod("reach", Mob.class, WeaponProfile.class);
            reach.setAccessible(true);
            check(helper, ((Double) reach.invoke(null, mob, empty)) == 2.0, "guard-only spacing uses the fallback reach");
            var pick = MobCombat.class.getDeclaredMethod("pickAttack", Mob.class, WeaponProfile.class);
            pick.setAccessible(true);
            check(helper, ((Optional<?>) pick.invoke(null, mob, kickOnly)).isEmpty(), "a kick-only map has no selectable weapon attack");
            check(helper, ((Optional<?>) pick.invoke(null, mob, empty)).isEmpty(), "an empty map has no selectable attack");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        helper.succeed();
    }

    private static WeaponProfile withAttacks(WeaponProfile base, Map<AttackType, WeaponProfile.AttackSpec> attacks) {
        return new WeaponProfile(base.archetype(), base.referenceAttackSpeed(), base.speedScaling(), attacks, base.guard(),
                base.riposteWindupMult(), base.heavy(), base.hyperArmorOnHeavy(), base.damageType(), base.special());
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

    /** A bot countering a slash follows the attacker's feint into an overhead once it has seen it for its reaction time. */
    @GameTest(template = ARENA)
    public static void botCounterFeints(GameTestHelper helper) {
        TrainingDummy attacker = dummy(helper, 1, 4, FACING_POSITIVE_X); // in the level, so the bot sees it coming
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 4);
        zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        face(zombie, FACING_NEGATIVE_X);
        zombie.setTarget(attacker);
        CombatData a = data(attacker);
        CombatData z = data(zombie);
        Combat.start(attacker, a, AttackType.SLASH);
        while (a.machine.phase() == Phase.WINDUP && a.machine.ticksLeftInPhase() > 5) {
            Combat.tickServer(attacker, a);
        }
        // The bot has decided to counter this attack (normally a dice roll on its read of the attacker's habits).
        BrainState brain = z.brain != null ? z.brain : (z.brain = new BrainState());
        brain.answeredAttacker = attacker.getId();
        brain.answeredSerial = a.machine.attackSerial();
        brain.answer = BrainState.Answer.COUNTER;
        ClashBrain.tick(zombie, z);
        check(helper, z.machine.phase() == Phase.WINDUP && z.machine.type() == AttackType.SLASH, "the bot counters the slash");
        check(helper, z.machine.isFromGuard(), "bots must enter guard before starting a counter");
        Combat.morph(attacker, a, AttackType.OVERHEAD);
        BotSkill skill = ClashBrain.skill(zombie);
        ClashBrain.tick(zombie, z);
        check(helper, z.machine.type() == AttackType.SLASH, "no reaction before the bot has seen the feint");
        for (int tick = 0; tick < skill.reactionTicks() && a.machine.phase() == Phase.WINDUP; tick++) {
            Combat.tickServer(attacker, a);
            ClashBrain.tick(zombie, z);
        }
        check(helper, z.machine.type() == AttackType.OVERHEAD,
                "after " + skill.reactionTicks() + " ticks the bot's counter follows into the overhead, it has " + z.machine.type());
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void threatLimitKeepsTheFirstWindupFacingTheBot(GameTestHelper helper) {
        TrainingDummy away = dummy(helper, 1, 4, FACING_NEGATIVE_X);
        TrainingDummy first = dummy(helper, 1, 3, FACING_POSITIVE_X);
        TrainingDummy second = dummy(helper, 1, 5, FACING_POSITIVE_X);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 4);
        zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        zombie.setTarget(first);
        for (TrainingDummy attacker : List.of(away, first, second)) {
            Combat.start(attacker, data(attacker), AttackType.SLASH);
        }
        // The original query's first in-cone result is the bot's established priority, regardless of entity order.
        LivingEntity expected = helper.getLevel().getEntitiesOfClass(LivingEntity.class, zombie.getBoundingBox().inflate(5),
                        e -> e != zombie && e.hasData(ModAttachments.COMBAT) && data(e).machine.phase() == Phase.WINDUP)
                .stream().filter(e -> Guard.inCone(CombatMath.viewYaw(e), e.getX(), e.getZ(), zombie.getX(), zombie.getZ(), 120))
                .findFirst().orElseThrow();
        check(helper, expected != away, "a windup facing away is outside the threat cone");
        ClashBrain.tick(zombie, data(zombie));
        check(helper, data(zombie).brain != null && data(zombie).brain.answeredAttacker == expected.getId(),
                "limiting the query must keep the first valid threat, not the first windup outside the cone");
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 30)
    public static void losingTargetReleasesAttackTokensThroughTheDispatcher(GameTestHelper helper) {
        TrainingDummy target = dummy(helper, 5, 4, FACING_NEGATIVE_X);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 2, 4);
        zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        zombie.setTarget(target);
        CombatData z = data(zombie);
        z.brain = new BrainState();
        z.brain.targetId = target.getId();
        z.brain.wantsSpace = true;
        ClashBrain.TOKENS.acquire(target.getId(), zombie.getId(), 2, id -> true);
        zombie.setTarget(null);
        helper.runAfterDelay(2, () -> {
            check(helper, !ClashBrain.TOKENS.holds(target.getId(), zombie.getId()),
                    "the normal entity tick must release tokens when manages() becomes false");
            check(helper, !z.brain.wantsSpace && z.brain.targetId == -1, "spacing stops when the target is gone");
            helper.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void switchingTargetReleasesTheOldAttackToken(GameTestHelper helper) {
        TrainingDummy first = dummy(helper, 5, 3, FACING_NEGATIVE_X);
        TrainingDummy second = dummy(helper, 5, 5, FACING_NEGATIVE_X);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 2, 4);
        zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        zombie.setTarget(first);
        CombatData z = data(zombie);
        z.brain = new BrainState();
        z.brain.targetId = first.getId();
        z.brain.cooldown = 10; // prevent an unrelated new attack from obscuring the token check
        ClashBrain.TOKENS.acquire(first.getId(), zombie.getId(), 2, id -> true);
        zombie.setTarget(second);
        ClashBrain.tick(zombie, z);
        check(helper, !ClashBrain.TOKENS.holds(first.getId(), zombie.getId()), "switching frees the old opponent's slot");
        check(helper, z.brain.targetId == second.getId(), "the brain tracks its new opponent");
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 30)
    public static void leavingTargetRemovesItsTokenBucket(GameTestHelper helper) {
        TrainingDummy target = dummy(helper, 5, 4, FACING_NEGATIVE_X);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 2, 4);
        int targetId = target.getId();
        ClashBrain.TOKENS.acquire(targetId, zombie.getId(), 2, id -> true);
        target.discard();
        helper.runAfterDelay(2, () -> {
            check(helper, ClashBrain.TOKENS.holderCount(targetId) == 0, "EntityLeaveLevelEvent cleans the target bucket");
            helper.succeed();
        });
    }
}

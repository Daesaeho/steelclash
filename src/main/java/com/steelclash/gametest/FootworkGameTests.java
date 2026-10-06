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
import com.steelclash.combat.HealthRegen;
import com.steelclash.core.AttackType;
import com.steelclash.entity.TrainingDummy;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
        player.setHealth(10);
        data.lastHurtAt = now - 20; // hurt a second ago: still too soon
        for (int i = 0; i < 20; i++) {
            HealthRegen.tick(player, data);
        }
        check(helper, player.getHealth() == 10, "no regeneration right after taking damage, health " + player.getHealth());
        data.lastHurtAt = now - 200; // ten seconds ago
        for (int i = 0; i < 20; i++) {
            HealthRegen.tick(player, data);
        }
        check(helper, Math.abs(player.getHealth() - 11) < 0.01, "one health per second once regenerating, health " + player.getHealth());
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

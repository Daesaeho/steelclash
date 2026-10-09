package com.steelclash.gametest;

import static com.steelclash.gametest.TestSupport.check;
import static com.steelclash.gametest.TestSupport.data;
import static com.steelclash.gametest.TestSupport.dummy;
import static com.steelclash.gametest.TestSupport.swing;
import static com.steelclash.gametest.TestSupport.swordsman;

import com.steelclash.Config;
import com.steelclash.SteelClash;
import com.steelclash.combat.Combat;
import com.steelclash.combat.LagCompensation;
import com.steelclash.core.AttackTimings;
import com.steelclash.core.AttackType;
import com.steelclash.core.Phase;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(SteelClash.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpecialGraceGameTests {
    private static final int PING = 150;

    private SpecialGraceGameTests() {}

    @GameTest(template = "arena")
    public static void delayedSpecialRetainsItsHitStaggerAfterTheAttackerChangesAction(GameTestHelper helper) {
        var immediate = swordsman(helper, new ItemStack(Items.IRON_SWORD), TestSupport.FACING_POSITIVE_X, 1);
        var control = dummy(helper, 3, 1, TestSupport.FACING_NEGATIVE_X);
        float health = control.getHealth();
        swing(immediate, AttackType.SPECIAL);
        float damage = health - control.getHealth();
        check(helper, damage > 0 && data(control).machine.phase() == Phase.STAGGER,
                "an immediate special damages and staggers an idle defender");

        var delayed = swordsman(helper, new ItemStack(Items.IRON_SWORD), TestSupport.FACING_POSITIVE_X, 5);
        var defender = dummy(helper, 3, 5, TestSupport.FACING_NEGATIVE_X);
        float before = defender.getHealth();
        LagCompensation.forceLatency(defender, PING);
        swing(delayed, AttackType.SPECIAL);
        check(helper, defender.getHealth() == before && data(defender).machine.phase() == Phase.IDLE,
                "hold both damage and reaction during the parry grace");
        check(helper, Combat.start(delayed, data(delayed), AttackType.JAB), "the attacker starts a different action");
        helper.runAfterDelay(6, () -> {
            check(helper, Math.abs((before - defender.getHealth()) - damage) < .001,
                    "the captured special delivers the same damage after grace");
            check(helper, data(defender).machine.phase() == Phase.STAGGER,
                    "the delayed special still applies its hit stagger, independent of the attacker's current jab");
            helper.succeed();
        });
    }

    @GameTest(template = "arena")
    public static void delayedOrdinaryHitDoesNotInheritTheAttackersNewSpecial(GameTestHelper helper) {
        var attacker = swordsman(helper, new ItemStack(Items.IRON_SWORD), TestSupport.FACING_POSITIVE_X);
        var defender = dummy(helper, 3, 4, TestSupport.FACING_NEGATIVE_X);
        float before = defender.getHealth();
        LagCompensation.forceLatency(defender, PING);
        swing(attacker, AttackType.SLASH);
        check(helper, defender.getHealth() == before, "the ordinary hit is held");
        check(helper, Combat.start(attacker, data(attacker), AttackType.SPECIAL), "the attacker now winds up a special");
        helper.runAfterDelay(6, () -> {
            check(helper, defender.getHealth() < before, "the ordinary hit lands after grace");
            check(helper, data(defender).machine.phase() == Phase.IDLE,
                    "an ordinary hit on an idle defender does not gain a special-hit stagger");
            helper.succeed();
        });
    }

    @GameTest(template = "arena")
    public static void blockedDelayedSpecialKeepsOnlyItsGuardPenalty(GameTestHelper helper) {
        var attacker = swordsman(helper, new ItemStack(Items.IRON_SWORD), TestSupport.FACING_POSITIVE_X);
        var defender = dummy(helper, 3, 4, TestSupport.FACING_NEGATIVE_X);
        float before = defender.getHealth();
        LagCompensation.forceLatency(defender, PING);
        swing(attacker, AttackType.SPECIAL);
        check(helper, defender.getHealth() == before, "the special is initially held");
        Combat.requestParry(defender);
        check(helper, data(defender).machine.phase() == Phase.PARRY, "the defender raises guard during grace");
        LagCompensation.tick();
        check(helper, defender.getHealth() == before, "the arriving guard blocks the held special's damage");
        check(helper, data(defender).machine.phase() == Phase.STAGGER
                && data(defender).machine.phaseDurationUs() == Config.SPECIAL_BLOCK_STAGGER_TICKS.get() * AttackTimings.TICK_US,
                "the block penalty is not replaced by the damaging-hit penalty");
        helper.succeed();
    }
}

package com.steelclash.gametest;

import static com.steelclash.gametest.TestSupport.*;

import com.steelclash.SteelClash;
import com.steelclash.combat.Combat;
import com.steelclash.core.AttackType;
import com.steelclash.core.AttackTimings;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real full-swing regressions: solid cover blocks secondary slams; a special cannot relax a guard break. */
@GameTestHolder(SteelClash.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CoverAndStaggerGameTests {
    private CoverAndStaggerGameTests() {}

    @GameTest(template = "arena")
    public static void slamDoesNotAffectTargetsBehindSolidCover(GameTestHelper helper) {
        var player = swordsman(helper, new ItemStack(Items.MACE), FACING_POSITIVE_X);
        var protectedTarget = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 5, 2, 4);
        var exposed = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 3);
        for (int y = 2; y <= 4; y++) helper.setBlock(4, y, 4, Blocks.STONE);
        float protectedStamina = data(protectedTarget).stamina.current();
        var velocity = protectedTarget.getDeltaMovement();
        float exposedStamina = data(exposed).stamina.current();
        swing(player, AttackType.SPECIAL);
        check(helper, data(exposed).stamina.current() < exposedStamina, "the unobstructed control is in the slam radius");
        check(helper, data(protectedTarget).stamina.current() == protectedStamina,
                "solid cover must stop the secondary stamina drain");
        check(helper, !data(protectedTarget).machine.isBusy() && protectedTarget.getDeltaMovement().equals(velocity),
                "solid cover must prevent secondary stagger and knockback too");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void slamDoesNotPassThroughTheFloor(GameTestHelper helper) {
        var player = swordsman(helper, new ItemStack(Items.MACE), FACING_POSITIVE_X);
        // A chicken fits entirely below the arena's y=1..2 floor while remaining within the vertical query/radius.
        var below = helper.spawnWithNoFreeWill(EntityType.CHICKEN, 3, 0, 4);
        var exposed = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 3);
        float stamina = data(below).stamina.current();
        var velocity = below.getDeltaMovement();
        float exposedStamina = data(exposed).stamina.current();
        swing(player, AttackType.SPECIAL);
        check(helper, data(exposed).stamina.current() < exposedStamina, "the slam actually executed");
        check(helper, data(below).stamina.current() == stamina && !data(below).machine.isBusy()
                && below.getDeltaMovement().equals(velocity), "the floor must block all secondary slam consequences");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void specialHitCannotRelaxAnExistingGuardBreak(GameTestHelper helper) {
        var attacker = swordsman(helper, new ItemStack(Items.MACE), FACING_POSITIVE_X);
        var target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 4);
        var targetData = data(target);
        Combat.stagger(target, targetData, 24, false);
        targetData.machine.tick();
        check(helper, Combat.start(attacker, data(attacker), AttackType.SPECIAL), "special starts normally");
        for (int tick = 0; tick < 80 && !isHurt(target); tick++) Combat.tickServer(attacker, data(attacker));
        check(helper, isHurt(target), "a real special reached the target through vanilla damage dispatch");
        check(helper, !targetData.machine.canParry(), "a special cannot turn the hard guard break into a soft stagger");
        check(helper, targetData.machine.phaseDurationUs() - targetData.machine.phaseElapsedUs()
                >= 23L * AttackTimings.TICK_US, "a special cannot shorten the existing guard-break lockout");
        helper.succeed();
    }
}

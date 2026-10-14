package com.steelclash.gametest;

import static com.steelclash.gametest.TestSupport.check;
import static com.steelclash.gametest.TestSupport.data;

import com.steelclash.Config;
import com.steelclash.SteelClash;
import com.steelclash.combat.Combat;
import com.steelclash.core.AttackTimings;
import com.steelclash.core.AttackType;
import com.steelclash.core.Phase;
import com.steelclash.profile.Kicks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real combat release dispatch must not overlay kicks with a vanilla weapon-hand attack. */
@GameTestHolder(SteelClash.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MobReleaseAnimationGameTests {
    private MobReleaseAnimationGameTests() {}

    @GameTest(template = "arena")
    public static void mobKickAndShieldBashDoNotStartVanillaHandSwings(GameTestHelper helper) {
        boolean clanks = Config.ENVIRONMENT_CLANK.get();
        Config.ENVIRONMENT_CLANK.set(false);
        try {
            for (boolean shield : new boolean[]{false, true}) {
                Husk actor = actor(helper, shield);
                try {
                    check(helper, Kicks.canBash(actor) == shield, "the fixture selects kick versus shield bash");
                    throughFirstReleaseSweep(helper, actor, AttackType.KICK);
                    check(helper, !actor.swinging && actor.swingTime == 0,
                            "an admitted " + (shield ? "shield bash" : "kick") + " does not start a vanilla hand swing");
                } finally {
                    actor.discard();
                }
            }
        } finally {
            Config.ENVIRONMENT_CLANK.set(clanks);
        }
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void mobHandAttacksRetainVanillaSwingDispatch(GameTestHelper helper) {
        boolean clanks = Config.ENVIRONMENT_CLANK.get();
        Config.ENVIRONMENT_CLANK.set(false);
        try {
            // A fresh actor per action prevents an earlier hand swing from satisfying a later control.
            for (AttackType type : new AttackType[]{AttackType.SLASH, AttackType.OVERHEAD, AttackType.STAB,
                    AttackType.JAB, AttackType.SPECIAL, AttackType.THROW}) {
                Husk actor = actor(helper, false);
                try {
                    throughFirstReleaseSweep(helper, actor, type);
                    check(helper, actor.swinging && actor.swingingArm == InteractionHand.MAIN_HAND,
                            "an admitted " + type + " retains the vanilla main-hand animation dispatch");
                    if (type == AttackType.THROW) {
                        check(helper, actor.getMainHandItem().isEmpty(), "the throw also reaches its ordinary item-release path");
                    }
                } finally {
                    actor.discard();
                }
            }
        } finally {
            Config.ENVIRONMENT_CLANK.set(clanks);
        }
        helper.succeed();
    }

    private static Husk actor(GameTestHelper helper, boolean shield) {
        Husk actor = helper.spawnWithNoFreeWill(EntityType.HUSK, 1, 2, 6);
        TestSupport.face(actor, TestSupport.FACING_POSITIVE_X);
        actor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        actor.setItemInHand(InteractionHand.OFF_HAND, shield ? new ItemStack(Items.SHIELD) : ItemStack.EMPTY);
        check(helper, !actor.swinging && actor.swingTime == 0, "a fresh actor has no prior vanilla hand animation");
        return actor;
    }

    private static void throughFirstReleaseSweep(GameTestHelper helper, Husk actor, AttackType type) {
        var combat = data(actor);
        Combat.requestAttack(actor, type, 0, false);
        check(helper, combat.machine.phase() == Phase.WINDUP && combat.machine.type() == type,
                "the ordinary server request admits " + type);
        long windup = combat.machine.timings().windupUs();
        int ticks = Math.toIntExact((windup + AttackTimings.TICK_US - 1) / AttackTimings.TICK_US) + 1;
        boolean releaseSwept = false;
        for (int tick = 0; tick < ticks; tick++) {
            Phase before = combat.machine.phase();
            Combat.tickServer(actor, combat);
            releaseSwept |= before == Phase.RELEASE
                    || combat.machine.phase() == Phase.RELEASE && combat.machine.phaseElapsedUs() > 0;
        }
        check(helper, releaseSwept && combat.machine.type() == type
                        && (combat.machine.phase() == Phase.RELEASE || combat.machine.phase() == Phase.RECOVERY),
                "production ticking consumes an admitted release slice for " + type);
    }
}

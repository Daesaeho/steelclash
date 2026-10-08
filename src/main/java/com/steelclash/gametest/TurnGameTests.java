package com.steelclash.gametest;

import static com.steelclash.gametest.TestSupport.FACING_NEGATIVE_X;
import static com.steelclash.gametest.TestSupport.FACING_POSITIVE_X;
import static com.steelclash.gametest.TestSupport.check;
import static com.steelclash.gametest.TestSupport.data;
import static com.steelclash.gametest.TestSupport.dummy;
import static com.steelclash.gametest.TestSupport.isHurt;
import static com.steelclash.gametest.TestSupport.swordsman;

import com.steelclash.SteelClash;
import com.steelclash.combat.Combat;
import com.steelclash.combat.CombatData;
import com.steelclash.core.AttackType;
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

/** Accels, drags and the turn cap: turning during the release moves where in it the blade connects. */
@GameTestHolder(SteelClash.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TurnGameTests {
    private static final String ARENA = "arena";
    /** Degrees per tick the attacker turns during the release: inside the default 360°/s (18°/tick) cap. */
    private static final float TURN = 15f;

    private TurnGameTests() {
    }

    @GameTest(template = ARENA)
    public static void accelLandsEarlierAndDragLater(GameTestHelper helper) {
        TrainingDummy target = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        // A right-to-left slash sweeps toward falling yaw: turning the same way is an accel, the other way a drag.
        int plain = contactTick(helper, target, 0f);
        int accel = contactTick(helper, target, -TURN);
        int drag = contactTick(helper, target, TURN);
        String ticks = " [release tick of contact: accel " + accel + ", plain " + plain + ", drag " + drag + " (-1 = whiff)]";
        check(helper, plain >= 0, "the plain slash should hit" + ticks);
        check(helper, accel >= 0 && accel < plain, "an accel should connect earlier in the release" + ticks);
        check(helper, drag == -1 || drag > plain, "a drag should connect later (or carry past)" + ticks);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void turnCapStopsAFlickBehind(GameTestHelper helper) {
        TrainingDummy behind = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        Player attacker = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_NEGATIVE_X); // back to the dummy
        CombatData data = data(attacker);
        Combat.requestAttack(attacker, AttackType.SLASH, 0, false);
        boolean flicked = false;
        float before = 0;
        float firstStep = Float.NaN;
        for (int i = 0; i < 60 && data.machine.isAttacking(); i++) {
            if (!flicked && entersOrInRelease(data)) {
                flicked = true;
                before = data.prevYaw;
                attacker.setYRot(FACING_POSITIVE_X); // 180° flick onto the dummy in one tick
                attacker.setYHeadRot(FACING_POSITIVE_X);
            }
            Combat.tickServer(attacker, data);
            if (flicked && Float.isNaN(firstStep)) {
                firstStep = Math.abs(net.minecraft.util.Mth.wrapDegrees(data.prevYaw - before));
            }
        }
        check(helper, firstStep <= 18.01f, "the traced view may turn at most 18° per tick, turned " + firstStep);
        check(helper, !isHurt(behind), "a 180° flick mid-release must not cut someone behind you");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void attackAfterIdleMovementUsesFreshSnapshots(GameTestHelper helper) {
        TrainingDummy target = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        Player attacker = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_NEGATIVE_X);
        Vec3 idlePosition = helper.absoluteVec(new Vec3(6.5, 2, 4.5));
        attacker.moveTo(idlePosition.x, idlePosition.y, idlePosition.z, FACING_NEGATIVE_X, 0);
        CombatData d = data(attacker);
        d.prevPivot = new Vec3(1000, 1000, 1000);
        d.prevYaw = 45;
        d.prevPitch = 70;
        Combat.tickServer(attacker, d); // idle snapshots are irrelevant to the next swing
        Vec3 position = helper.absoluteVec(new Vec3(1.5, 2, 4.5));
        attacker.moveTo(position.x, position.y, position.z, FACING_POSITIVE_X, 0);
        Combat.requestAttack(attacker, AttackType.SLASH, 0, false);
        check(helper, d.prevPivot.distanceToSqr(com.steelclash.combat.CombatMath.pivot(attacker, 1f)) < 1e-9,
                "attack startup must replace any stale idle position");
        check(helper, d.prevYaw == FACING_POSITIVE_X && d.prevPitch == 0,
                "attack startup must use the current view, not an idle snapshot");
        TestSupport.finish(attacker);
        check(helper, isHurt(target), "the slash still hits after moving and turning while idle");
        helper.succeed();
    }

    /** Swings a fresh right-to-left slash at the (healed) target, turning {@code turnPerTick} each release tick. */
    private static int contactTick(GameTestHelper helper, TrainingDummy target, float turnPerTick) {
        target.setHealth(target.getMaxHealth());
        Player attacker = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        CombatData data = data(attacker);
        Combat.requestAttack(attacker, AttackType.SLASH, 0, false);
        int releaseTick = 0;
        for (int i = 0; i < 60 && data.machine.isAttacking(); i++) {
            boolean release = entersOrInRelease(data);
            if (release) {
                attacker.setYRot(attacker.getYRot() + turnPerTick);
                attacker.setYHeadRot(attacker.getYRot());
            }
            Combat.tickServer(attacker, data);
            if (release) {
                if (isHurt(target)) {
                    return releaseTick;
                }
                releaseTick++;
            }
        }
        return -1;
    }

    /** This coming tick sweeps the blade: the release, or the windup's last tick turning into it. */
    private static boolean entersOrInRelease(CombatData data) {
        return data.machine.phase() == Phase.RELEASE
                || data.machine.phase() == Phase.WINDUP && data.machine.phaseTick() >= data.machine.timings().windup() - 1;
    }
}

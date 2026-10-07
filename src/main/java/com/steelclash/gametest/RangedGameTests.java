package com.steelclash.gametest;

import static com.steelclash.gametest.TestSupport.FACING_NEGATIVE_X;
import static com.steelclash.gametest.TestSupport.check;
import static com.steelclash.gametest.TestSupport.data;
import static com.steelclash.gametest.TestSupport.face;

import com.steelclash.SteelClash;
import com.steelclash.combat.Combat;
import com.steelclash.combat.CombatData;
import com.steelclash.combat.RangedDefense;
import com.steelclash.core.AttackType;
import com.steelclash.core.Phase;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Chivalry 2 projectile rules: counter, weapon block, headshots, interrupted draws ({@link RangedDefense}). */
@GameTestHolder(SteelClash.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RangedGameTests {
    private static final String ARENA = "arena";
    private static final float ARROW_DAMAGE = 8f;

    private RangedGameTests() {
    }

    /** A swordsman zombie at (3, 2, z) facing -x, standing still. */
    private static Zombie swordsman(GameTestHelper helper, int z) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, z);
        zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        face(zombie, FACING_NEGATIVE_X);
        return zombie;
    }

    /**
     * Hurts {@code target} with an arrow flying along -x... toward it from {@code fromFront} (else from behind), at
     * {@code height} above its feet; returns the health it lost.
     */
    private static float shoot(GameTestHelper helper, LivingEntity target, boolean fromFront, double height) {
        double side = fromFront ? -1.5 : 1.5; // the zombies face -x, so the front is at lower x
        Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
        arrow.setPos(target.getX() + side, target.getY() + height, target.getZ());
        arrow.setDeltaMovement(new Vec3(-side, 0, 0));
        float before = target.getHealth();
        target.invulnerableTime = 0;
        target.hurt(helper.getLevel().damageSources().arrow(arrow, null), ARROW_DAMAGE);
        return before - target.getHealth();
    }

    @GameTest(template = ARENA)
    public static void attackStartedJustBeforeDeflectsAnArrow(GameTestHelper helper) {
        Zombie zombie = swordsman(helper, 2);
        CombatData d = data(zombie);
        Combat.requestAttack(zombie, AttackType.SLASH);
        check(helper, d.machine.phase() == Phase.WINDUP, "winding up");
        check(helper, shoot(helper, zombie, true, 1.0) == 0, "an arrow met at the start of a windup is deflected");
        check(helper, d.machine.phase() == Phase.WINDUP, "the attack carries on");

        Zombie behind = swordsman(helper, 4);
        Combat.requestAttack(behind, AttackType.SLASH);
        check(helper, shoot(helper, behind, false, 1.0) > 0, "an arrow from behind isn't countered");

        Zombie late = swordsman(helper, 6);
        CombatData l = data(late);
        Combat.requestAttack(late, AttackType.SLASH);
        for (int tick = 0; tick < 6; tick++) {
            Combat.tickServer(late, l); // 300 ms in: past the 250 ms window
        }
        check(helper, l.machine.phase() == Phase.WINDUP, "still winding up");
        check(helper, shoot(helper, late, true, 1.0) > 0, "too late into the windup: hit");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void weaponGuardBluntsAnArrowButNeverBreaks(GameTestHelper helper) {
        Zombie open = swordsman(helper, 2);
        float unguarded = shoot(helper, open, true, 1.0);

        Zombie guarded = swordsman(helper, 4);
        CombatData d = data(guarded);
        check(helper, Combat.startParry(guarded, d), "guard up");
        d.stamina.spend(d.stamina.current() - 1); // one stamina left: a melee block would break this guard
        float blocked = shoot(helper, guarded, true, 1.0);
        check(helper, blocked > 0 && blocked < unguarded * 0.85,
                "the guard takes about 30% off: " + blocked + " vs " + unguarded + " unguarded");
        check(helper, guarded.getMainHandItem().is(Items.IRON_SWORD) && d.machine.phase() == Phase.PARRY,
                "arrows never disarm or break the guard: " + d.machine.phase());

        Zombie turned = swordsman(helper, 6);
        CombatData t = data(turned);
        Combat.startParry(turned, t);
        check(helper, shoot(helper, turned, false, 1.0) == unguarded, "a guard doesn't cover the back");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void headshotsHitHarder(GameTestHelper helper) {
        Villager body = helper.spawnWithNoFreeWill(EntityType.VILLAGER, 3, 2, 2);
        Villager head = helper.spawnWithNoFreeWill(EntityType.VILLAGER, 3, 2, 5);
        float toBody = shoot(helper, body, true, body.getBbHeight() * 0.5);
        float toHead = shoot(helper, head, true, head.getEyeHeight());
        check(helper, Math.abs(toBody - ARROW_DAMAGE) < 0.01, "a body shot does its damage: " + toBody);
        check(helper, Math.abs(toHead - ARROW_DAMAGE * 1.25f) < 0.01, "a headshot does 25% more: " + toHead);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void gettingHurtDropsABowDraw(GameTestHelper helper) {
        Skeleton skeleton = helper.spawnWithNoFreeWill(EntityType.SKELETON, 3, 2, 3);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        skeleton.startUsingItem(InteractionHand.MAIN_HAND);
        check(helper, skeleton.isUsingItem(), "drawing");
        skeleton.hurt(helper.getLevel().damageSources().generic(), 1f);
        check(helper, !skeleton.isUsingItem(), "the hit should drop the draw");
        helper.succeed();
    }
}

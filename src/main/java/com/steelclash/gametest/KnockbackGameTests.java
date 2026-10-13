package com.steelclash.gametest;

import static com.steelclash.gametest.TestSupport.FACING_POSITIVE_X;
import static com.steelclash.gametest.TestSupport.check;
import static com.steelclash.gametest.TestSupport.swing;
import static com.steelclash.gametest.TestSupport.swordsman;

import com.steelclash.Config;
import com.steelclash.SteelClash;
import com.steelclash.core.AttackType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The {@code meleeKnockback} setting: weapon swings and punches shove less or not at all; kicks and other mobs still do. */
@GameTestHolder(SteelClash.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KnockbackGameTests {
    private static final String ARENA = "arena";

    private KnockbackGameTests() {
    }

    /** How fast {@code hit} sends a fresh, still target sideways. */
    private static double shove(Zombie target, Runnable hit) {
        target.setHealth(target.getMaxHealth());
        target.invulnerableTime = 0;
        target.setDeltaMovement(Vec3.ZERO);
        hit.run();
        return target.getDeltaMovement().horizontalDistance();
    }

    @GameTest(template = ARENA)
    public static void meleeKnockbackSettingScalesSwingsAndPunchesOnly(GameTestHelper helper) {
        Zombie target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 2, 4);
        Wolf wolf = helper.spawnWithNoFreeWill(EntityType.WOLF, 3, 2, 4);
        // The setting is global: change it, hit and put it back within this one tick, so no other test sees it.
        double configured = Config.MELEE_KNOCKBACK.get();
        try {
            Config.MELEE_KNOCKBACK.set(1.0);
            double slash = shove(target, () -> swing(swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X), AttackType.SLASH));
            double punch = shove(target, () -> swordsman(helper, ItemStack.EMPTY, FACING_POSITIVE_X).attack(target));
            double kick = shove(target, () -> swing(swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X), AttackType.KICK));
            double bite = shove(target, () -> wolf.doHurtTarget(target));
            check(helper, slash > 0.1 && punch > 0.1 && kick > 0.1 && bite > 0.1,
                    "premise: at 1 everything shoves (slash " + slash + ", punch " + punch + ", kick " + kick + ", bite " + bite + ")");

            Config.MELEE_KNOCKBACK.set(0.0);
            double noSlash = shove(target, () -> swing(swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X), AttackType.SLASH));
            check(helper, target.getHealth() < target.getMaxHealth(), "premise: the slash still lands and hurts");
            check(helper, noSlash == 0, "at 0 a weapon swing doesn't move its target, it moved at " + noSlash);
            double noPunch = shove(target, () -> swordsman(helper, ItemStack.EMPTY, FACING_POSITIVE_X).attack(target));
            check(helper, target.getHealth() < target.getMaxHealth(), "premise: the punch still lands and hurts");
            check(helper, noPunch == 0, "nor does a punch, it moved at " + noPunch);
            double stillKick = shove(target, () -> swing(swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X), AttackType.KICK));
            check(helper, Math.abs(stillKick - kick) < 1e-6, "a kick shoves as before: " + stillKick + " vs " + kick);
            double stillBite = shove(target, () -> wolf.doHurtTarget(target));
            check(helper, Math.abs(stillBite - bite) < 1e-6, "an animal's bite is not ours to change: " + stillBite + " vs " + bite);

            Config.MELEE_KNOCKBACK.set(0.5);
            double half = shove(target, () -> swing(swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X), AttackType.SLASH));
            check(helper, Math.abs(half - slash / 2) < 1e-6, "at 0.5 a swing shoves half as hard: " + half + " vs " + slash);
        } finally {
            Config.MELEE_KNOCKBACK.set(configured);
        }
        helper.succeed();
    }
}

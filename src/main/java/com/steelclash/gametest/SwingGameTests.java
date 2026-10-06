package com.steelclash.gametest;

import com.steelclash.SteelClash;
import com.steelclash.combat.Combat;
import com.steelclash.combat.CombatData;
import com.steelclash.combat.ModAttachments;
import com.steelclash.compat.Compat;
import com.steelclash.core.AttackType;
import com.steelclash.profile.WeaponProfiles;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Server-side swing behavior against real mobs. Run with {@code ./gradlew runGameTestServer}.
 * <p>
 * Layout (relative to the 9×5×9 arena): the swordsman stands at (1.5, 1, 4.5) facing +x; targets stand two blocks
 * ahead at x = 3.
 */
@GameTestHolder(SteelClash.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SwingGameTests {
    private static final String ARENA = "arena";
    private static final float FACING_POSITIVE_X = -90f;
    private static final float FACING_NEGATIVE_X = 90f;

    private SwingGameTests() {
    }

    @GameTest(template = ARENA)
    public static void slashHitsTargetInFront(GameTestHelper helper) {
        Player player = TestSupport.swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 4);
        TestSupport.swing(player, AttackType.SLASH);
        TestSupport.check(helper, TestSupport.isHurt(zombie), "slash should hurt the zombie in front");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void overheadHitsTargetInFront(GameTestHelper helper) {
        Player player = TestSupport.swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 4);
        TestSupport.swing(player, AttackType.OVERHEAD);
        TestSupport.check(helper, TestSupport.isHurt(zombie), "overhead should hurt the zombie in front");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void slashCutsThroughTwoTargetsSideBySide(GameTestHelper helper) {
        Player player = TestSupport.swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        Zombie left = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 3);
        Zombie right = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 5);
        TestSupport.swing(player, AttackType.SLASH);
        TestSupport.check(helper, TestSupport.isHurt(left) && TestSupport.isHurt(right), "a sword slash (max 3 targets) should hit both zombies");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void stabHitsOnlyTheFirstTarget(GameTestHelper helper) {
        Player player = TestSupport.swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        Zombie front = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 4);
        Zombie behind = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 2, 4);
        TestSupport.swing(player, AttackType.STAB);
        TestSupport.check(helper, TestSupport.isHurt(front), "stab should hurt the front zombie");
        TestSupport.check(helper, !TestSupport.isHurt(behind), "stab (max 1 target) must not also hit the zombie behind");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void wallBlocksTheSwing(GameTestHelper helper) {
        for (int y = 2; y <= 4; y++) {
            for (int z = 2; z <= 6; z++) {
                helper.setBlock(2, y, z, Blocks.STONE);
            }
        }
        Player player = TestSupport.swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 4);
        TestSupport.swing(player, AttackType.SLASH);
        TestSupport.check(helper, !TestSupport.isHurt(zombie), "swings must not hit through walls");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void targetBehindIsMissed(GameTestHelper helper) {
        Player player = TestSupport.swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_NEGATIVE_X);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 4);
        TestSupport.swing(player, AttackType.SLASH);
        TestSupport.check(helper, !TestSupport.isHurt(zombie), "a slash facing away must miss");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void emptyHandDoesNotAttack(GameTestHelper helper) {
        Player player = TestSupport.swordsman(helper, ItemStack.EMPTY, FACING_POSITIVE_X);
        Combat.requestAttack(player, AttackType.SLASH);
        TestSupport.check(helper, !player.getData(ModAttachments.COMBAT).machine.isAttacking(), "no weapon, no attack");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void profilesResolve(GameTestHelper helper) {
        TestSupport.expectProfile(helper, new ItemStack(Items.IRON_SWORD), "sword");
        TestSupport.expectProfile(helper, new ItemStack(Items.DIAMOND_AXE), "axe");
        TestSupport.expectProfile(helper, new ItemStack(Items.TRIDENT), "spear");
        TestSupport.expectProfile(helper, new ItemStack(Items.MACE), "blunt");
        TestSupport.expectProfile(helper, new ItemStack(Items.STICK), null);
        if (Compat.SPARTAN_WEAPONRY.isLoaded()) {
            TestSupport.expectProfile(helper, TestSupport.spartan("iron_longsword"), "sword");
            TestSupport.expectProfile(helper, TestSupport.spartan("iron_halberd"), "polearm");
            TestSupport.expectProfile(helper, TestSupport.spartan("iron_dagger"), "dagger");
            TestSupport.expectProfile(helper, TestSupport.spartan("iron_greatsword"), "two_handed");
            TestSupport.expectProfile(helper, TestSupport.spartan("iron_spear"), "spear");
            TestSupport.expectProfile(helper, TestSupport.spartan("iron_warhammer"), "blunt");
        }
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void spartanLongswordHits(GameTestHelper helper) {
        if (!Compat.SPARTAN_WEAPONRY.isLoaded()) {
            helper.succeed();
            return;
        }
        Player player = TestSupport.swordsman(helper, TestSupport.spartan("iron_longsword"), FACING_POSITIVE_X);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 4);
        TestSupport.swing(player, AttackType.SLASH);
        TestSupport.check(helper, TestSupport.isHurt(zombie), "a Spartan longsword slash should hurt the zombie");
        helper.succeed();
    }
}

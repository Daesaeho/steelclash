package com.steelclash.gametest;

import static com.steelclash.gametest.TestSupport.FACING_POSITIVE_X;
import static com.steelclash.gametest.TestSupport.check;
import static com.steelclash.gametest.TestSupport.data;
import static com.steelclash.gametest.TestSupport.finish;
import static com.steelclash.gametest.TestSupport.isHurt;
import static com.steelclash.gametest.TestSupport.swordsman;

import com.steelclash.SteelClash;
import com.steelclash.combat.Combat;
import com.steelclash.combat.CombatData;
import com.steelclash.core.ArcPath;
import com.steelclash.core.AttackTimings;
import com.steelclash.core.AttackType;
import com.steelclash.core.Phase;
import com.steelclash.profile.WeaponProfile;
import com.steelclash.profile.WeaponProfiles;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** M6b: arc variants and mirrored (left-to-right) swings. */
@GameTestHolder(SteelClash.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VariantGameTests {
    private static final String ARENA = "arena";

    private VariantGameTests() {
    }

    @GameTest(template = ARENA)
    public static void everyVariantFromEitherSideHitsATargetInFront(GameTestHelper helper) {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        WeaponProfile profile = WeaponProfiles.resolve(sword, helper.getLevel().registryAccess()).orElseThrow().profile();
        int swings = 0;
        for (AttackType type : AttackType.WEAPON_ATTACKS) {
            WeaponProfile.AttackSpec spec = profile.attack(type).orElseThrow();
            for (int variant = 0; variant < spec.variantCount(); variant++) {
                for (boolean mirrored : new boolean[]{false, true}) {
                    Player player = swordsman(helper, sword.copy(), FACING_POSITIVE_X);
                    Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 4);
                    Combat.requestAttack(player, type, variant, mirrored);
                    CombatData d = data(player);
                    check(helper, d.machine.variant() == variant && d.machine.isMirrored() == mirrored,
                            "the requested variant and side should be used");
                    finish(player);
                    check(helper, isHurt(zombie), type + " variant " + variant + (mirrored ? " (mirrored)" : "") + " missed");
                    zombie.discard();
                    swings++;
                }
            }
        }
        check(helper, swings >= 12, "the sword should have several variants per attack, swung " + swings);
        helper.succeed();
    }

    /**
     * Proves the side really changes the traced arc: a target 45° to the right is reached early by a right-to-left
     * slash and late by a left-to-right one.
     */
    @GameTest(template = ARENA)
    public static void mirroredSlashReachesTheRightSideLater(GameTestHelper helper) {
        check(helper, firstReleaseTickHits(helper, false, 2), "a right-to-left slash should hit the target on its right first");
        check(helper, !firstReleaseTickHits(helper, true, 6), "a left-to-right slash must not reach its right side yet");
        helper.succeed();
    }

    /** Swings a slash at a zombie 45° to the right and reports whether the first release tick already hit it. */
    private static boolean firstReleaseTickHits(GameTestHelper helper, boolean mirrored, int row) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X, row);
        // Facing +x, the right-hand side is +z: 2 blocks ahead and 2 to the right = 45°.
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, row + 2);
        Combat.requestAttack(player, AttackType.SLASH, 0, mirrored);
        CombatData d = data(player);
        for (int i = 0; i < 100 && d.machine.phase() != Phase.RELEASE; i++) {
            Combat.tickServer(player, d);
        }
        Combat.tickServer(player, d); // first release tick
        boolean hit = isHurt(zombie);
        zombie.discard();
        return hit;
    }

    @GameTest(template = ARENA)
    public static void combosAlternateSides(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 4);
        Combat.requestAttack(player, AttackType.SLASH, 0, false);
        CombatData d = data(player);
        for (int i = 0; i < 100 && d.machine.phase() != Phase.RECOVERY; i++) {
            Combat.tickServer(player, d);
        }
        check(helper, d.machine.isComboAllowed(), "the slash should land and allow a combo");
        Combat.requestAttack(player, AttackType.SLASH); // no explicit side: the combo picks it
        check(helper, d.machine.phase() == Phase.WINDUP && d.machine.isMirrored(), "the combo should swing from the other side");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void cachedPathsFollowVariantsSidesAndReplacementSpecs(GameTestHelper helper) {
        WeaponProfile profile = WeaponProfiles.resolve(new ItemStack(Items.IRON_SWORD), helper.getLevel().registryAccess())
                .orElseThrow().profile();
        WeaponProfile.AttackSpec spec = profile.attack(AttackType.SLASH).orElseThrow();
        CombatData d = new CombatData();
        for (int variant = 0; variant <= spec.variantCount(); variant++) { // includes the out-of-range fallback
            for (boolean mirrored : new boolean[]{false, true}) {
                d.machine.startAttack(AttackType.SLASH, AttackTimings.ofTicks(1, 1, 1), variant, mirrored);
                ArcPath expected = spec.arc(variant).toPath();
                if (mirrored) {
                    expected = expected.mirrored();
                }
                ArcPath actual = Combat.currentPath(d, spec);
                check(helper, actual.equals(expected), "cached path must follow variant " + variant + " and side " + mirrored);
                check(helper, actual == Combat.currentPath(d, spec), "repeated calls reuse the immutable path");
                d.machine.feint();
            }
        }
        d.machine.startAttack(AttackType.SLASH, AttackTimings.ofTicks(1, 1, 1), 0, false);
        ArcPath before = Combat.currentPath(d, spec);
        // A reload supplies a new ArcSpec, even while the attack's type/variant/side remain unchanged.
        WeaponProfile.AttackSpec replacement = new WeaponProfile.AttackSpec(1, 1, 1, 1f,
                new WeaponProfile.ArcSpec(WeaponProfile.ArcSpec.Shape.HORIZONTAL, 37f), 1, 0f, 1f, 1f);
        ArcPath after = Combat.currentPath(d, replacement);
        check(helper, after != before && after.equals(replacement.arc().toPath()), "a replacement spec invalidates the cached arc");
        helper.succeed();
    }
}

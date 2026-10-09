package com.steelclash.gametest;

import static com.steelclash.gametest.TestSupport.check;
import static com.steelclash.gametest.TestSupport.data;
import static com.steelclash.gametest.TestSupport.swing;
import static com.steelclash.gametest.TestSupport.swordsman;

import com.steelclash.Config;
import com.steelclash.SteelClash;
import com.steelclash.combat.Allies;
import com.steelclash.core.AttackType;
import com.steelclash.core.Phase;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(SteelClash.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PetProtectionGameTests {
    private PetProtectionGameTests() {}

    @GameTest(template = "arena")
    public static void maceSlamSparesTheAttackersPetWithoutATeam(GameTestHelper helper) {
        verifySlam(helper, false, true);
    }

    @GameTest(template = "arena")
    public static void maceSlamSparesACoopAlliesPetWithoutATeam(GameTestHelper helper) {
        verifySlam(helper, true, true);
    }

    @GameTest(template = "arena")
    public static void maceSlamStillAffectsAnEnemyPetWhenCoopIsOff(GameTestHelper helper) {
        verifySlam(helper, true, false);
    }

    private static void verifySlam(GameTestHelper helper, boolean differentOwner, boolean protectedPet) {
        var attacker = swordsman(helper, new ItemStack(Items.MACE), TestSupport.FACING_POSITIVE_X);
        var owner = differentOwner ? DownedGameTests.player(helper, 8.5, 8.5) : attacker;
        check(helper, !differentOwner || !owner.getUUID().equals(attacker.getUUID()), "the ally/enemy is a different owner");
        var scoreboard = helper.getLevel().getScoreboard();
        String name = attacker.getScoreboardName();
        var previousTeam = scoreboard.getPlayersTeam(name);
        boolean previousCoop = Config.PLAYERS_ARE_ALLIES.get();
        try {
            // Mock players share a scoreboard name. Remove/restore it synchronously so team inheritance cannot
            // accidentally protect the pet and hide the missing explicit pet exclusion.
            if (previousTeam != null) scoreboard.removePlayerFromTeam(name, previousTeam);
            Config.PLAYERS_ARE_ALLIES.set(protectedPet);
            Wolf pet = wolf(helper, owner.getUUID(), 3);
            Wolf enemy = wolf(helper, null, 5);
            check(helper, !Allies.areAllies(attacker, pet), "scoreboard alliance does not hide the pet-protection path");
            check(helper, Allies.isFriendlyPet(attacker, pet) == protectedPet, "the shared rule identifies this pet correctly");
            check(helper, !differentOwner || pet.getOwner() == owner, "resolve the in-level owner for the allied/enemy pet");
            float health = pet.getHealth();
            float stamina = data(pet).stamina.current();
            var velocity = pet.getDeltaMovement();
            float enemyStamina = data(enemy).stamina.current();

            swing(attacker, AttackType.SPECIAL); // Full mace release, including its secondary area effect.
            check(helper, data(enemy).stamina.current() < enemyStamina && data(enemy).machine.phase() == Phase.STAGGER
                    && enemy.getDeltaMovement().lengthSqr() > 0, "the slam really affects the hostile control in its radius");
            if (protectedPet) {
                check(helper, pet.getHealth() == health && data(pet).stamina.current() == stamina,
                        "the owner's/allied pet loses neither health nor stamina");
                check(helper, data(pet).machine.phase() == Phase.IDLE && pet.getDeltaMovement().equals(velocity),
                        "the owner's/allied pet is neither staggered nor knocked back");
            } else {
                check(helper, data(pet).stamina.current() < stamina && data(pet).machine.phase() == Phase.STAGGER
                        && !pet.getDeltaMovement().equals(velocity), "an enemy-owned pet remains a valid slam target");
            }
        } finally {
            Config.PLAYERS_ARE_ALLIES.set(previousCoop);
            if (previousTeam != null) scoreboard.addPlayerToTeam(name, previousTeam);
        }
        helper.succeed();
    }

    private static Wolf wolf(GameTestHelper helper, UUID owner, int z) {
        Wolf wolf = helper.spawnWithNoFreeWill(EntityType.WOLF, 3, 2, z);
        TestSupport.face(wolf, TestSupport.FACING_NEGATIVE_X);
        if (owner != null) {
            wolf.setOwnerUUID(owner);
            wolf.setTame(true, true);
        }
        return wolf;
    }
}

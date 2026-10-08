package com.steelclash.gametest;

import static com.steelclash.gametest.TestSupport.FACING_NEGATIVE_X;
import static com.steelclash.gametest.TestSupport.FACING_POSITIVE_X;
import static com.steelclash.gametest.TestSupport.check;
import static com.steelclash.gametest.TestSupport.data;
import static com.steelclash.gametest.TestSupport.dummy;
import static com.steelclash.gametest.TestSupport.swing;
import static com.steelclash.gametest.TestSupport.swordsman;

import com.steelclash.Config;
import com.steelclash.SteelClash;
import com.steelclash.combat.Allies;
import com.steelclash.combat.Combat;
import com.steelclash.combat.CombatData;
import com.steelclash.core.AttackType;
import com.steelclash.core.Phase;
import com.steelclash.entity.TrainingDummy;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Chivalry 2 team rules ({@link Allies}): allies' bodies are in the way, stabs stop in them, friendly fire is scaled. */
@GameTestHolder(SteelClash.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TeamGameTests {
    private static final String ARENA = "arena";
    private static final AtomicInteger TEAMS = new AtomicInteger();

    private TeamGameTests() {
    }

    /**
     * Puts the entities on a fresh scoreboard team. Mock players all share one scoreboard name, so every test removes
     * its team again ({@link #done}) before the next one runs.
     */
    private static void team(GameTestHelper helper, Entity... members) {
        Scoreboard scoreboard = helper.getLevel().getScoreboard();
        PlayerTeam team = scoreboard.addPlayerTeam("sc_team_" + TEAMS.incrementAndGet());
        for (Entity member : members) {
            scoreboard.addPlayerToTeam(member.getScoreboardName(), team);
        }
    }

    /** Removes this class's teams and passes the test. */
    private static void done(GameTestHelper helper) {
        clearTeams(helper);
        helper.succeed();
    }

    private static void clearTeams(GameTestHelper helper) {
        Scoreboard scoreboard = helper.getLevel().getScoreboard();
        scoreboard.getPlayerTeams().stream().filter(t -> t.getName().startsWith("sc_team_")).toList()
                .forEach(scoreboard::removePlayerTeam);
    }

    @GameTest(template = ARENA)
    public static void stabStopsInATeammate(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        Villager ally = helper.spawnWithNoFreeWill(EntityType.VILLAGER, 2, 2, 4);
        Zombie enemy = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 4);
        team(helper, player, ally);
        swing(player, AttackType.STAB);
        check(helper, TestSupport.isHurt(ally), "the teammate in the way takes the stab");
        check(helper, !TestSupport.isHurt(enemy), "the enemy behind is spared");
        check(helper, data(player).machine.isThwacked(), "the stab stops in the teammate (Chivalry 2 update 2.9)");
        done(helper);
    }

    @GameTest(template = ARENA)
    public static void slashCarriesThroughATeammateForReducedDamage(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        Villager ally = helper.spawnWithNoFreeWill(EntityType.VILLAGER, 3, 2, 4);
        Villager stranger = helper.spawnWithNoFreeWill(EntityType.VILLAGER, 3, 2, 5);
        team(helper, player, ally);
        swing(player, AttackType.SLASH);
        float toAlly = ally.getMaxHealth() - ally.getHealth();
        float toStranger = stranger.getMaxHealth() - stranger.getHealth();
        check(helper, toStranger > 0, "the slash should reach both");
        check(helper, Math.abs(toAlly - toStranger * Allies.damageScale()) < 0.05,
                "the teammate takes " + Allies.damageScale() + " of it: " + toAlly + " vs " + toStranger);
        check(helper, !data(player).machine.isThwacked(), "a slash carries on through a teammate");
        done(helper);
    }

    @GameTest(template = ARENA)
    public static void noFriendlyFireStillBlocksTheStab(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        Villager ally = helper.spawnWithNoFreeWill(EntityType.VILLAGER, 2, 2, 4);
        Zombie enemy = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 4);
        team(helper, player, ally);
        double scale = Config.FRIENDLY_DAMAGE_SCALE.get();
        Config.FRIENDLY_DAMAGE_SCALE.set(0.0);
        try {
            swing(player, AttackType.STAB);
        } finally {
            Config.FRIENDLY_DAMAGE_SCALE.set(scale);
        }
        check(helper, !TestSupport.isHurt(ally), "no friendly fire");
        check(helper, !TestSupport.isHurt(enemy) && data(player).machine.isThwacked(), "the teammate's body still stops the stab");
        done(helper);
    }

    @GameTest(template = ARENA)
    public static void aTeammatesGuardNeverParries(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        TrainingDummy ally = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        team(helper, player, ally);
        CombatData guard = data(ally);
        check(helper, Combat.startParry(ally, guard), "guard up");
        float stamina = guard.stamina.current();
        swing(player, AttackType.SLASH);
        CombatData attacker = data(player);
        check(helper, attacker.machine.phase() != Phase.STAGGER, "the attacker isn't parried by a teammate");
        check(helper, guard.stamina.current() == stamina, "the teammate's guard doesn't pay for it");
        done(helper);
    }

    @GameTest(template = ARENA)
    public static void aTeammatesShieldNeverBlocksOrBouncesTheSwing(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        TrainingDummy ally = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        ally.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        ally.startUsingItem(InteractionHand.OFF_HAND);
        helper.runAfterDelay(8, () -> {
            // Teamed only now: every mock player shares one scoreboard name, so another test's cleanup during the
            // wait for the shield would take the player off the team.
            team(helper, player, ally);
            check(helper, ally.isBlocking(), "the teammate's shield is raised");
            float stamina = data(ally).stamina.current();
            swing(player, AttackType.SLASH);
            check(helper, TestSupport.isHurt(ally), "a friendly swing still does reduced damage through a shield");
            check(helper, data(ally).stamina.current() == stamina, "a teammate's shield loses no stamina");
            check(helper, data(player).machine.phase() != Phase.STAGGER, "the teammate never bounces the attacker");
            done(helper);
        });
    }

    @GameTest(template = ARENA)
    public static void kicksAndSlamsSpareTeammates(GameTestHelper helper) {
        Player kicker = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        TrainingDummy guarding = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        team(helper, kicker, guarding);
        Combat.requestParry(guarding);
        swing(kicker, AttackType.KICK);
        check(helper, data(guarding).machine.phase() == Phase.PARRY, "a kick passes a teammate by: " + data(guarding).machine.phase());

        Player slammer = swordsman(helper, new ItemStack(Items.MACE), FACING_POSITIVE_X, 1);
        Zombie ally = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 0);
        Zombie enemy = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 2);
        team(helper, slammer, ally);
        swing(slammer, AttackType.SPECIAL);
        check(helper, data(enemy).machine.phase() == Phase.STAGGER, "the slam staggers the enemy");
        check(helper, data(ally).machine.phase() != Phase.STAGGER, "but not the teammate");
        done(helper);
    }

    @GameTest(template = ARENA)
    public static void playersAreAlliesInCoop(GameTestHelper helper) {
        Player a = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X, 2);
        Player b = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X, 5);
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, 3, 2, 4);
        clearTeams(helper); // no team left over from another test
        check(helper, Allies.areAllies(a, b), "co-op: players fight on one side");
        check(helper, !Allies.areAllies(a, villager), "a villager isn't on anyone's side");
        Config.PLAYERS_ARE_ALLIES.set(false);
        try {
            check(helper, !Allies.areAllies(a, b), "off: players aren't allies by default");
            team(helper, a, b);
            check(helper, Allies.areAllies(a, b), "a scoreboard team still makes them allies");
        } finally {
            Config.PLAYERS_ARE_ALLIES.set(true);
        }
        done(helper);
    }
}

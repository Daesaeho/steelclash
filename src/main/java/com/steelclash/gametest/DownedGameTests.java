package com.steelclash.gametest;

import static com.steelclash.gametest.TestSupport.check;
import static com.steelclash.gametest.TestSupport.data;

import com.steelclash.Config;
import com.steelclash.SteelClash;
import com.steelclash.combat.Combat;
import com.steelclash.combat.CombatData;
import com.steelclash.combat.Dodge;
import com.steelclash.combat.Downed;
import com.steelclash.core.AttackType;
import com.steelclash.core.Phase;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Chivalry 2's downed state and revives ({@link Downed}). Everything runs synchronously inside each test. */
@GameTestHolder(SteelClash.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DownedGameTests {
    private static final String ARENA = "arena";

    private DownedGameTests() {
    }

    /** A survival mock player in the level at (x, 2, z) of the test structure, holding a sword. */
    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper helper, double x, double z) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 pos = helper.absoluteVec(new Vec3(x, 2, z));
        player.moveTo(pos.x, pos.y, pos.z, 0, 0);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        player.setGameMode(GameType.SURVIVAL); // the GameTest world is creative: invulnerable
        try { // new players are untouchable for 3 s, counted down only as they tick; the field is private
            java.lang.reflect.Field spawnInvulnerable = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            spawnInvulnerable.setAccessible(true);
            spawnInvulnerable.setInt(player, 0);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return player;
    }

    /** A lethal blow. Not a mob's: Steel Clash turns a mob's instant vanilla hit into a telegraphed swing. */
    private static void lethal(GameTestHelper helper, ServerPlayer player) {
        player.invulnerableTime = 0;
        player.hurt(helper.getLevel().damageSources().generic(), 1000f);
    }

    @GameTest(template = ARENA)
    public static void lethalBlowDownsAPlayerWithAnAllyAround(GameTestHelper helper) {
        ServerPlayer downed = player(helper, 2.5, 2.5);
        player(helper, 4.5, 2.5); // an ally to come for them
        lethal(helper, downed);
        CombatData d = data(downed);
        check(helper, downed.isAlive() && d.isDowned(), "downed, not dead");
        check(helper, downed.getHealth() == Config.DOWNED_HEALTH.get().floatValue(), "a little health left: " + downed.getHealth());
        check(helper, downed.getForcedPose() == Pose.SWIMMING, "crawling");
        check(helper, !Combat.start(downed, d, AttackType.SLASH) && d.machine.phase() == Phase.IDLE, "can't attack");
        check(helper, !Combat.startParry(downed, d), "can't parry");
        downed.setOnGround(true); // mock players never tick, so they're never "on the ground" by themselves
        check(helper, !Dodge.perform(downed, d), "can't dodge");
        PlayerInteractEvent.RightClickItem use = new PlayerInteractEvent.RightClickItem(downed, InteractionHand.MAIN_HAND);
        NeoForge.EVENT_BUS.post(use);
        check(helper, use.isCanceled(), "can't use items");
        var stand = helper.spawn(EntityType.ARMOR_STAND, 3, 2, 2);
        var interactAt = new PlayerInteractEvent.EntityInteractSpecific(downed, InteractionHand.MAIN_HAND, stand,
                new Vec3(0, 1, 0));
        NeoForge.EVENT_BUS.post(interactAt);
        check(helper, interactAt.isCanceled(), "can't take equipment through an armor stand's precise interaction");
        float health = downed.getHealth();
        downed.heal(5);
        check(helper, downed.getHealth() == health, "no healing while down");
        lethal(helper, downed);
        check(helper, !downed.isAlive(), "a second lethal blow finishes them");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void aloneOrKilledOutrightYouDie(GameTestHelper helper) {
        Config.PLAYERS_ARE_ALLIES.set(false); // the other tests' mock players don't count
        try {
            ServerPlayer alone = player(helper, 2.5, 2.5);
            lethal(helper, alone);
            check(helper, !alone.isAlive(), "with nobody to revive them, a player dies");
        } finally {
            Config.PLAYERS_ARE_ALLIES.set(true);
        }
        ServerPlayer killed = player(helper, 2.5, 4.5);
        player(helper, 4.5, 4.5);
        killed.hurt(helper.getLevel().damageSources().fellOutOfWorld(), 1000f);
        check(helper, !killed.isAlive(), "the void (or /kill) still kills outright");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void downedPlayersBleedOut(GameTestHelper helper) {
        ServerPlayer downed = player(helper, 2.5, 2.5);
        player(helper, 4.5, 2.5);
        lethal(helper, downed);
        CombatData d = data(downed);
        check(helper, d.downedTicksLeft == Config.BLEED_OUT_SECONDS.get() * 20, "the bleed-out clock starts: " + d.downedTicksLeft);
        d.downedTicksLeft = 2;
        Downed.tick(downed, d);
        check(helper, downed.isAlive(), "one tick left");
        Downed.tick(downed, d);
        check(helper, !downed.isAlive(), "bled out");

        ServerPlayer leaver = player(helper, 2.5, 4.5);
        lethal(helper, leaver);
        check(helper, data(leaver).isDowned(), "down");
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(leaver));
        check(helper, !leaver.isAlive(), "logging out while down is bleeding out");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void aCrouchingAllyRevives(GameTestHelper helper) {
        ServerPlayer downed = player(helper, 2.5, 2.5);
        ServerPlayer ally = player(helper, 3.5, 2.5);
        lethal(helper, downed);
        CombatData d = data(downed);
        int needed = Downed.reviveTicksNeeded();

        Downed.tick(downed, d);
        check(helper, d.reviveTicks == 0, "standing next to them isn't enough");
        ally.setShiftKeyDown(true);
        for (int tick = 0; tick < needed / 2; tick++) {
            Downed.tick(downed, d);
        }
        check(helper, d.reviveTicks == needed / 2 && d.reviverId == ally.getId(), "reviving: " + d.reviveTicks);
        data(ally).lastHurtAt = helper.getLevel().getGameTime(); // the reviver takes a hit
        Downed.tick(downed, d);
        check(helper, d.reviveTicks == 0, "a hit on the reviver restarts the revive");
        data(ally).lastHurtAt = -1;
        downed.setHealth(2); // hurt a little more while down: the revive sets health, it doesn't keep it

        for (int tick = 0; tick < needed; tick++) {
            Downed.tick(downed, d);
        }
        check(helper, !d.isDowned(), "back up");
        check(helper, Math.abs(downed.getHealth() - downed.getMaxHealth() * Config.REVIVE_HEALTH.get()) < 0.01,
                "with part of their health: " + downed.getHealth());
        check(helper, downed.getForcedPose() == null, "standing again");
        check(helper, Combat.start(downed, d, AttackType.SLASH), "and fighting");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void mobsLeaveTheDownedAlone(GameTestHelper helper) {
        ServerPlayer downed = player(helper, 2.5, 2.5);
        player(helper, 4.5, 2.5);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 2, 5);
        zombie.setTarget(downed);
        check(helper, zombie.getTarget() == downed, "a standing player is a target");
        lethal(helper, downed);
        zombie.setTarget(null);
        zombie.setTarget(downed);
        check(helper, zombie.getTarget() == null, "a downed one isn't");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void reviverDamageAfterThePatientsTickStillResetsProgress(GameTestHelper helper) {
        ServerPlayer downed = player(helper, 2.5, 2.5);
        ServerPlayer ally = player(helper, 3.5, 2.5);
        lethal(helper, downed);
        ally.setShiftKeyDown(true);
        CombatData d = data(downed);
        for (int tick = 0; tick < 10; tick++) {
            Downed.tick(downed, d);
        }
        check(helper, d.reviveTicks == 10, "the revive is in progress");
        // This hit is later than the patient's tick, as entity ordering or lag-compensated delivery can make it.
        ally.invulnerableTime = 0;
        ally.hurt(helper.getLevel().damageSources().generic(), 0.5f);
        check(helper, d.reviveTicks == 0 && d.reviverId == -1, "damage resets the revive immediately, independent of tick order");
        helper.succeed();
    }
}

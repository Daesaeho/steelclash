package com.steelclash.gametest;

import static com.steelclash.gametest.TestSupport.check;
import static com.steelclash.gametest.TestSupport.dummy;

import com.steelclash.SteelClash;
import com.steelclash.ai.BotStyles;
import com.steelclash.combat.Combat;
import com.steelclash.combat.CombatData;
import com.steelclash.combat.ModAttachments;
import com.steelclash.combat.Sidearms;
import com.steelclash.compat.Compat;
import com.steelclash.core.AttackType;
import com.steelclash.core.BotStyle;
import com.steelclash.entity.TrainingDummy;
import com.steelclash.profile.WeaponProfiles;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** M6a: armed mob spawns, shield-carrying bots, bot styles. GameTest worlds run on Normal difficulty. */
@GameTestHolder(SteelClash.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GearGameTests {
    private static final String ARENA = "arena";

    private GearGameTests() {
    }

    @GameTest(template = ARENA)
    public static void spawnedZombiesAreOftenArmed(GameTestHelper helper) {
        List<Zombie> zombies = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            BlockPos pos = helper.absolutePos(new BlockPos(1 + i % 7, 2, 1 + (i / 7) % 7));
            // A real spawn (fires FinalizeSpawnEvent), unlike GameTestHelper#spawn.
            Zombie zombie = EntityType.ZOMBIE.spawn(helper.getLevel(), pos, MobSpawnType.SPAWN_EGG);
            if (zombie != null) {
                zombie.setNoAi(true);
                zombies.add(zombie);
            }
        }
        long armed = zombies.stream()
                .filter(z -> WeaponProfiles.resolve(z.getMainHandItem(), helper.getLevel().registryAccess()).isPresent())
                .count();
        zombies.forEach(z -> z.discard());
        // Normal: 50% chance each, so 40 spawns land well inside [10, 32] unless arming is broken.
        check(helper, armed >= 10 && armed <= 32, "expected about half of 40 zombies armed on Normal, got " + armed);
        helper.succeed();
    }

    /** Skeletons carry a dagger sidearm next to their bow (Spartan Weaponry daggers; vanilla has none). */
    @GameTest(template = ARENA)
    public static void spawnedSkeletonsCarryASidearm(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 4));
        Skeleton skeleton = EntityType.SKELETON.spawn(helper.getLevel(), pos, MobSpawnType.SPAWN_EGG);
        check(helper, skeleton != null, "spawn");
        skeleton.setNoAi(true);
        ItemStack sidearm = Sidearms.stowed(skeleton);
        check(helper, skeleton.getMainHandItem().getItem() instanceof ProjectileWeaponItem,
                "the bow (or Spartan Weaponry's longbow) stays in hand: " + skeleton.getMainHandItem());
        if (Compat.SPARTAN_WEAPONRY.isLoaded()) {
            String profile = WeaponProfiles.resolve(sidearm, helper.getLevel().registryAccess())
                    .map(r -> r.profile().archetype()).orElse("none");
            check(helper, profile.equals("dagger"), "expected a dagger sidearm, got " + sidearm + " (" + profile + ")");
        } else {
            check(helper, sidearm.isEmpty(), "no daggers without Spartan Weaponry, got " + sidearm);
        }
        skeleton.discard();
        helper.succeed();
    }

    /** Chivalry 2 archers draw their sidearm when someone closes in, and go back to the bow once they're clear. */
    @GameTest(template = ARENA)
    public static void skeletonDrawsItsSidearmUpClose(GameTestHelper helper) {
        Skeleton skeleton = helper.spawn(EntityType.SKELETON, 1, 2, 4);
        skeleton.setNoAi(true);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        skeleton.setData(ModAttachments.SIDEARM, new ItemStack(Items.IRON_SWORD));
        CombatData data = TestSupport.data(skeleton);
        TrainingDummy far = dummy(helper, 7, 4, TestSupport.FACING_NEGATIVE_X);
        TrainingDummy near = dummy(helper, 3, 4, TestSupport.FACING_NEGATIVE_X);

        skeleton.setTarget(far);
        Sidearms.tick(skeleton, data);
        check(helper, skeleton.getMainHandItem().is(Items.BOW), "6 blocks away: keep shooting");

        skeleton.setTarget(near);
        Sidearms.tick(skeleton, data);
        check(helper, skeleton.getMainHandItem().is(Items.IRON_SWORD), "2 blocks away: draw the sidearm");
        check(helper, Sidearms.stowed(skeleton).is(Items.BOW), "the bow is stowed");

        skeleton.setTarget(null);
        Sidearms.tick(skeleton, data);
        check(helper, skeleton.getMainHandItem().is(Items.IRON_SWORD), "a switch has a cooldown");
        data.sidearmReadyAt = 0;
        Combat.requestAttack(skeleton, AttackType.SLASH);
        check(helper, data.machine.isBusy(), "the skeleton swings its sidearm");
        Sidearms.tick(skeleton, data);
        check(helper, skeleton.getMainHandItem().is(Items.IRON_SWORD), "no switching mid-swing");
        data.machine.feint();
        skeleton.setTarget(far);
        Sidearms.tick(skeleton, data);
        check(helper, skeleton.getMainHandItem().is(Items.IRON_SWORD), "6 blocks: close enough to keep the sidearm out");
        skeleton.setTarget(null);
        Sidearms.tick(skeleton, data);
        check(helper, skeleton.getMainHandItem().is(Items.BOW), "target gone: back to the bow");
        check(helper, Sidearms.stowed(skeleton).is(Items.IRON_SWORD), "the sidearm is stowed again");
        helper.succeed();
    }

    /** The switch happens on its own as the mob ticks. */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void skeletonSwitchesOnItsOwn(GameTestHelper helper) {
        // No free will: a skeleton with a bow backs off to shooting range, and could be out of drawing range before its
        // switch is due. The switch runs in the entity tick either way.
        Skeleton skeleton = helper.spawnWithNoFreeWill(EntityType.SKELETON, 1, 2, 4);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        skeleton.setData(ModAttachments.SIDEARM, new ItemStack(Items.IRON_SWORD));
        skeleton.setTarget(dummy(helper, 3, 4, TestSupport.FACING_NEGATIVE_X));
        helper.succeedWhen(() -> check(helper, skeleton.getMainHandItem().is(Items.IRON_SWORD), "should draw the sidearm"));
    }

    @GameTest(template = ARENA)
    public static void testSpawnsStayUnarmed(GameTestHelper helper) {
        // GameTestHelper#spawn skips FinalizeSpawnEvent, so test mobs never get random gear.
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 3, 2, 4);
        check(helper, zombie.getMainHandItem().isEmpty(), "test-spawned zombies must not be randomly armed");
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void shieldBearingBotBlocks(GameTestHelper helper) {
        TrainingDummy attacker = dummy(helper, 5, 4, TestSupport.FACING_NEGATIVE_X);
        attacker.setMode(TrainingDummy.Mode.ATTACK); // with no player around it swings at the nearest monster
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 3, 2, 4);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        zombie.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        zombie.setTarget(attacker);
        AtomicBoolean blocked = new AtomicBoolean();
        java.util.Set<Integer> dummyAttacks = new java.util.HashSet<>();
        java.util.Map<String, Integer> zombieDuringWindups = new java.util.TreeMap<>();
        int[] usingTicks = {0};
        double[] closest = {99};
        helper.onEachTick(() -> {
            // Isolate defense: keep the zombie from attacking (a relentless shambler would flinch the dummy out of
            // every attack and never need to block). It holds back like a bot waiting for its turn.
            if (zombie.hasData(com.steelclash.combat.ModAttachments.COMBAT) && TestSupport.data(zombie).brain != null) {
                TestSupport.data(zombie).brain.cooldown = 1000;
            }
            if (zombie.isBlocking()) {
                blocked.set(true);
            }
            if (zombie.isUsingItem()) {
                usingTicks[0]++;
            }
            closest[0] = Math.min(closest[0], zombie.distanceTo(attacker));
            if (attacker.hasData(com.steelclash.combat.ModAttachments.COMBAT)) {
                var m = TestSupport.data(attacker).machine;
                if (m.phase() == com.steelclash.core.Phase.WINDUP) {
                    dummyAttacks.add(m.attackSerial());
                    String zPhase = zombie.hasData(com.steelclash.combat.ModAttachments.COMBAT)
                            ? TestSupport.data(zombie).machine.phase().name() : "NODATA";
                    zombieDuringWindups.merge(zPhase, 1, Integer::sum);
                }
            }
        });
        helper.succeedWhen(() -> check(helper, blocked.get(), "a zombie with a shield should raise it against attacks"
                + " [dummy attacks " + dummyAttacks.size() + ", zombie phases during them " + zombieDuringWindups
                + ", shield-in-use ticks " + usingTicks[0] + ", closest " + String.format("%.1f", closest[0]) + "]"));
    }

    @GameTest(template = ARENA)
    public static void mobTypesHaveStyles(GameTestHelper helper) {
        check(helper, BotStyles.of(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 2, 2)) == BotStyle.SHAMBLER, "zombie: shambler");
        check(helper, BotStyles.of(helper.spawnWithNoFreeWill(EntityType.VINDICATOR, 4, 2, 2)) == BotStyle.RUSHER, "vindicator: rusher");
        check(helper, BotStyles.of(helper.spawnWithNoFreeWill(EntityType.SPIDER, 6, 2, 2)) == BotStyle.SKIRMISHER, "spider: skirmisher");
        check(helper, BotStyles.of(helper.spawnWithNoFreeWill(EntityType.COW, 2, 2, 6)) == BotStyle.DUELIST, "unlisted: duelist");
        helper.succeed();
    }
}

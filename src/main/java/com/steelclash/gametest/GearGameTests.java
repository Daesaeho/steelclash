package com.steelclash.gametest;

import static com.steelclash.gametest.TestSupport.check;
import static com.steelclash.gametest.TestSupport.dummy;

import com.steelclash.SteelClash;
import com.steelclash.ai.BotStyles;
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
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

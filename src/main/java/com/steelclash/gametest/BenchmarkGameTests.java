package com.steelclash.gametest;

import static com.steelclash.gametest.TestSupport.FACING_NEGATIVE_X;
import static com.steelclash.gametest.TestSupport.FACING_POSITIVE_X;
import static com.steelclash.gametest.TestSupport.data;
import static com.steelclash.gametest.TestSupport.face;

import com.steelclash.SteelClash;
import com.steelclash.combat.Combat;
import com.steelclash.combat.CombatData;
import com.steelclash.combat.CombatProfiler;
import com.steelclash.core.AttackType;
import com.steelclash.entity.ModEntities;
import com.steelclash.entity.TrainingDummy;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Combat benchmark (architecture plan §41): fixed scenes run for a fixed number of ticks with {@link CombatProfiler}
 * on, writing a report per scene to {@code run-gametest/steelclash-bench/}. Only runs with {@code -Pbench}
 * ({@code steelclash.bench}); otherwise each test passes at once, so ordinary test runs stay fast.
 * <ul>
 *     <li>Scripted scenes (A–C): training dummies in facing pairs attack and parry nonstop: the combat pipeline with no
 *     AI. Health and stamina are topped up every tick so nobody dies or is disarmed.</li>
 *     <li>Horde scenes (D–F): armed husks (they don't burn in daylight) with the bot brain, around one invulnerable
 *     player: AI plus combat.</li>
 * </ul>
 * The profiler is global, so scenes take turns: each waits until the ordinary tests are done and every earlier scene
 * has finished (GameTest batches turned out to overlap). There are no real clients, so packets are built and counted
 * but not encoded or sent.
 */
@GameTestHolder(SteelClash.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BenchmarkGameTests {
    private static final String ARENA = "arena";
    /** All scenes in one batch, so they start together and take turns (separate batches could deadlock the turns). */
    private static final String BATCH = "steelclash_bench";
    private static final boolean ENABLED = System.getProperty("steelclash.bench") != null;
    private static final int WARMUP_TICKS = 200;
    private static final int MEASURE_TICKS = 600;
    private static final int SCENE_TICKS = WARMUP_TICKS + MEASURE_TICKS;
    /** Ordinary GameTests finish well within this; their activity must not leak into a scene. */
    private static final int START_DELAY_TICKS = 300;
    private static final int SCENES = 6;
    /** The first scene also warms up the JVM (JIT): it gets a much longer warm-up so its numbers aren't cold-start noise. */
    private static final int FIRST_WARMUP_TICKS = 1200;
    private static final int TIMEOUT = START_DELAY_TICKS + FIRST_WARMUP_TICKS + SCENES * (SCENE_TICKS + 40) + 1000;
    /** Whose turn it is. */
    private static int turn;

    private BenchmarkGameTests() {
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT, batch = BATCH)
    public static void benchADuel(GameTestHelper helper) {
        scripted(helper, 0, "A_duel_2", 2);
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT, batch = BATCH)
    public static void benchBMelee20(GameTestHelper helper) {
        scripted(helper, 1, "B_melee_20", 20);
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT, batch = BATCH)
    public static void benchCMelee50(GameTestHelper helper) {
        scripted(helper, 2, "C_melee_50", 50);
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT, batch = BATCH)
    public static void benchD1v8(GameTestHelper helper) {
        horde(helper, 3, "D_1v8_bots", 8);
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT, batch = BATCH)
    public static void benchEHorde40(GameTestHelper helper) {
        horde(helper, 4, "E_horde_40_bots", 40);
    }

    /** Stress, beyond the plan's scenes: where does the headroom end? */
    @GameTest(template = ARENA, timeoutTicks = TIMEOUT, batch = BATCH)
    public static void benchFHorde150(GameTestHelper helper) {
        horde(helper, 5, "F_horde_150_bots", 150);
    }

    // ------------------------------------------------------------------ scenes

    private static void scripted(GameTestHelper helper, int order, String name, int fighters) {
        if (!ENABLED) {
            helper.succeed();
            return;
        }
        scene(helper, order, name, fighters, () -> scriptedScene(helper, fighters));
    }

    /** A scene's fighters and what to do with them every tick. */
    private record Scene(List<LivingEntity> fighters, Runnable eachTick) {
    }

    /** Pairs of dummies a sword's length apart, facing each other, attacking and parrying nonstop. */
    private static Scene scriptedScene(GameTestHelper helper, int fighters) {
        ServerLevel level = helper.getLevel();
        Vec3 origin = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
        List<LivingEntity> all = new ArrayList<>();
        for (int i = 0; i < fighters / 2; i++) {
            double x = origin.x + (i % 5) * 4;
            double z = origin.z + (i / 5) * 4;
            TrainingDummy a = dummy(level, x, origin.y, z, FACING_POSITIVE_X);
            TrainingDummy b = dummy(level, x + 2, origin.y, z, FACING_NEGATIVE_X);
            // Dummies count as monsters, and monsters only cut the one they're fighting (SwingTracer.isValidTarget).
            a.setTarget(b);
            b.setTarget(a);
            all.add(a);
            all.add(b);
        }
        RandomSource random = RandomSource.create(42);
        return new Scene(all, () -> {
            for (LivingEntity fighter : all) {
                topUp(fighter);
                CombatData d = data(fighter);
                if (!d.machine.isBusy()) {
                    if (random.nextFloat() < 0.25f) {
                        Combat.requestParry(fighter);
                    } else {
                        Combat.requestAttack(fighter, AttackType.WEAPON_ATTACKS[random.nextInt(3)]);
                    }
                }
            }
        });
    }

    private static void horde(GameTestHelper helper, int order, String name, int bots) {
        if (!ENABLED) {
            helper.succeed();
            return;
        }
        scene(helper, order, name, bots + 1, () -> hordeScene(helper, bots));
    }

    /** Armed husks with the bot brain in a ring around one invulnerable player. */
    @SuppressWarnings("removal")
    private static Scene hordeScene(GameTestHelper helper, int bots) {
        ServerLevel level = helper.getLevel();
        ServerPlayer target = helper.makeMockServerPlayerInLevel();
        Vec3 center = helper.absoluteVec(new Vec3(3.5, 2, 3.5));
        target.moveTo(center.x, center.y, center.z, 0, 0);
        target.getAbilities().invulnerable = true;
        target.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        List<LivingEntity> all = new ArrayList<>();
        all.add(target);
        for (int i = 0; i < bots; i++) {
            double angle = i * (Math.PI * 2 / bots);
            double radius = 3 + (i % Math.max(3, bots / 15)) * 1.5; // more rings for bigger hordes
            Husk husk = EntityType.HUSK.create(level);
            husk.moveTo(center.x + Math.cos(angle) * radius, center.y, center.z + Math.sin(angle) * radius, 0, 0);
            husk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            husk.setPersistenceRequired();
            level.addFreshEntity(husk);
            husk.setTarget(target);
            all.add(husk);
        }
        return new Scene(all, () -> {
            for (LivingEntity fighter : all) {
                topUp(fighter);
                if (fighter instanceof Mob mob && mob.getTarget() == null) {
                    mob.setTarget(target);
                }
            }
        });
    }

    // ------------------------------------------------------------------ driver

    /**
     * One per-tick callback for the whole test (GameTest can't take new callbacks while it runs them): wait for this
     * scene's turn, set it up, warm up, measure, report.
     */
    private static void scene(GameTestHelper helper, int order, String name, int fighterCount,
                              java.util.function.Supplier<Scene> setUp) {
        Scene[] scene = new Scene[1];
        long[] startTick = new long[1];
        long[] gcBefore = new long[1];
        boolean[] done = new boolean[1];
        helper.onEachTick(() -> {
            if (done[0]) {
                return;
            }
            if (scene[0] == null) {
                if (turn != order || helper.getTick() < START_DELAY_TICKS) {
                    return;
                }
                scene[0] = setUp.get();
                startTick[0] = helper.getTick();
            }
            scene[0].eachTick().run();
            long t = helper.getTick() - startTick[0];
            long warmup = order == 0 ? FIRST_WARMUP_TICKS : WARMUP_TICKS;
            if (t == warmup) {
                gcBefore[0] = gcMillis();
                CombatProfiler.start();
            } else if (t >= warmup + MEASURE_TICKS) {
                done[0] = true;
                CombatProfiler.stop();
                String text = format(name, fighterCount, CombatProfiler.report(), gcMillis() - gcBefore[0]);
                SteelClash.LOGGER.info("\n{}", text);
                write(helper, name, text);
                scene[0].fighters().forEach(LivingEntity::discard);
                turn = order + 1;
                helper.succeed();
            }
        });
    }

    private static TrainingDummy dummy(ServerLevel level, double x, double y, double z, float yaw) {
        TrainingDummy dummy = ModEntities.TRAINING_DUMMY.get().create(level);
        dummy.moveTo(x, y, z, yaw, 0);
        level.addFreshEntity(dummy);
        face(dummy, yaw);
        return dummy;
    }

    /** Nobody dies, and nobody runs out of stamina (that would disarm them and end the fight). */
    private static void topUp(LivingEntity fighter) {
        fighter.setHealth(fighter.getMaxHealth());
        CombatData d = data(fighter);
        d.stamina.set(d.stamina.max());
    }

    private static long gcMillis() {
        long total = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            total += Math.max(0, gc.getCollectionTime());
        }
        return total;
    }

    // ------------------------------------------------------------------ report

    private static String format(String name, int fighters, CombatProfiler.Report r, long gcMs) {
        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.ROOT, "Steel Clash combat benchmark: %s (%d fighters, %d ticks measured)%n",
                name, fighters, r.ticks()));
        out.append(String.format(Locale.ROOT, "%-8s %10s %10s%n", "section", "ms/tick", "KB/tick"));
        double kb = 0;
        for (CombatProfiler.Section s : CombatProfiler.Section.values()) {
            out.append(String.format(Locale.ROOT, "%-8s %10.4f %10.2f%n", s.name(), r.msPerTick(s), r.kbPerTick(s)));
            kb += r.kbPerTick(s);
        }
        out.append(String.format(Locale.ROOT, "%-8s %10.4f %10.2f%n", "TOTAL", r.totalMsPerTick(), kb));
        out.append(String.format(Locale.ROOT, "per tick: p50 %.4f ms, p99 %.4f ms, max %.4f ms (budget 2.5 ms)%n",
                r.percentileMs(0.5), r.percentileMs(0.99), r.maxMs()));
        out.append(String.format(Locale.ROOT, "live blades/tick %.2f, candidates/tick %.2f, contacts/tick %.2f, packets/tick %.2f%n",
                r.perTick(CombatProfiler.Counter.RELEASES), r.perTick(CombatProfiler.Counter.CANDIDATES),
                r.perTick(CombatProfiler.Counter.CONTACTS), r.perTick(CombatProfiler.Counter.PACKETS)));
        out.append(String.format(Locale.ROOT, "GC during the run: %d ms (whole JVM)%s%n", gcMs,
                r.allocationsMeasured() ? "" : "; allocation sampling not supported on this JVM"));
        return out.toString();
    }

    private static void write(GameTestHelper helper, String name, String text) {
        try {
            Path dir = helper.getLevel().getServer().getServerDirectory().resolve("steelclash-bench");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(name + ".txt"), text);
        } catch (IOException e) {
            SteelClash.LOGGER.warn("Benchmark: couldn't write the report", e);
        }
    }
}

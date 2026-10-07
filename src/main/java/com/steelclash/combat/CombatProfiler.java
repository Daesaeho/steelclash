package com.steelclash.combat;

import java.lang.management.ManagementFactory;
import java.util.Arrays;

/**
 * Measures where server-side combat spends its time, for the benchmark (architecture plan §31 and §41). Off by default:
 * then every hook is one field read. While on, it times named sections on the profiled thread (the server thread)
 * as <em>exclusive</em> time (a section nested in another isn't counted twice), counts events, and samples the bytes
 * the thread allocates in each section. {@link #endTick} closes one server tick so per-tick percentiles can be
 * reported.
 */
public final class CombatProfiler {
    public enum Section {
        /** Bot brains, mob defense, fighting-stance upkeep. */
        AI,
        /** State machines, stamina, movement slowdown: everything in a fighter's combat tick not listed below. */
        STATE,
        /** Candidate search for a live blade: the entity query, lag-rewind offsets and prepared hitboxes. */
        BROAD,
        /** Swept blade against each candidate's box. */
        NARROW,
        /** Blade against the world (clanks). */
        WORLD,
        /** Hits: parries, counters, blocks, damage, flinch, feedback. */
        RESOLVE,
        /** Lag compensation's position history. */
        LAG,
        /** Building and sending combat packets. */
        SYNC
    }

    public enum Counter {
        /** Ticks of live blade (one per fighter per tick in a release). */
        RELEASES,
        CANDIDATES,
        CONTACTS,
        PACKETS
    }

    private static final Section[] SECTIONS = Section.values();
    private static final int MAX_DEPTH = 16;
    private static final com.sun.management.ThreadMXBean THREADS = threadBean();

    private static volatile boolean enabled;
    private static Thread profiledThread;

    private static final long[] sectionNanos = new long[SECTIONS.length];
    private static final long[] sectionBytes = new long[SECTIONS.length];
    private static final long[] counters = new long[Counter.values().length];
    private static final long[] tickSectionNanos = new long[SECTIONS.length];
    private static long[] tickTotals = new long[1024];
    private static int ticks;

    private static final Section[] stackSection = new Section[MAX_DEPTH];
    private static final long[] stackStart = new long[MAX_DEPTH];
    private static final long[] stackChild = new long[MAX_DEPTH];
    private static final long[] stackBytes = new long[MAX_DEPTH];
    private static final long[] stackChildBytes = new long[MAX_DEPTH];
    private static int depth;

    private CombatProfiler() {
    }

    private static com.sun.management.ThreadMXBean threadBean() {
        java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        if (bean instanceof com.sun.management.ThreadMXBean sun && sun.isThreadAllocatedMemorySupported()) {
            sun.setThreadAllocatedMemoryEnabled(true);
            return sun;
        }
        return null;
    }

    /** Starts profiling the calling thread (the server thread), clearing earlier results. */
    public static void start() {
        reset();
        profiledThread = Thread.currentThread();
        enabled = true;
    }

    public static void stop() {
        enabled = false;
    }

    public static void reset() {
        Arrays.fill(sectionNanos, 0);
        Arrays.fill(sectionBytes, 0);
        Arrays.fill(counters, 0);
        Arrays.fill(tickSectionNanos, 0);
        ticks = 0;
        depth = 0;
    }

    private static boolean active() {
        return enabled && Thread.currentThread() == profiledThread;
    }

    public static void begin(Section section) {
        if (!active() || depth >= MAX_DEPTH) {
            return;
        }
        stackSection[depth] = section;
        stackStart[depth] = System.nanoTime();
        stackChild[depth] = 0;
        stackBytes[depth] = allocated();
        stackChildBytes[depth] = 0;
        depth++;
    }

    public static void end(Section section) {
        if (!active() || depth == 0 || stackSection[depth - 1] != section) {
            return;
        }
        depth--;
        long elapsed = System.nanoTime() - stackStart[depth];
        long bytes = allocated() - stackBytes[depth];
        int i = section.ordinal();
        sectionNanos[i] += elapsed - stackChild[depth];
        tickSectionNanos[i] += elapsed - stackChild[depth];
        sectionBytes[i] += bytes - stackChildBytes[depth];
        if (depth > 0) {
            stackChild[depth - 1] += elapsed;
            stackChildBytes[depth - 1] += bytes;
        }
    }

    public static void count(Counter counter, long amount) {
        if (active()) {
            counters[counter.ordinal()] += amount;
        }
    }

    /** Closes one server tick (its combat time goes into the per-tick distribution). */
    public static void endTick() {
        if (!enabled) {
            return;
        }
        long total = 0;
        for (long n : tickSectionNanos) {
            total += n;
        }
        Arrays.fill(tickSectionNanos, 0);
        if (ticks == tickTotals.length) {
            tickTotals = Arrays.copyOf(tickTotals, ticks * 2);
        }
        tickTotals[ticks++] = total;
    }

    private static long allocated() {
        return THREADS == null ? 0 : THREADS.getCurrentThreadAllocatedBytes();
    }

    /** Results so far. */
    public static Report report() {
        long[] sorted = Arrays.copyOf(tickTotals, ticks);
        Arrays.sort(sorted);
        return new Report(ticks, sectionNanos.clone(), sectionBytes.clone(), counters.clone(), sorted, THREADS != null);
    }

    /**
     * @param tickTotalsSorted combat nanoseconds of each tick, ascending
     */
    public record Report(int ticks, long[] sectionNanos, long[] sectionBytes, long[] counters, long[] tickTotalsSorted,
                         boolean allocationsMeasured) {
        public double msPerTick(Section section) {
            return ticks == 0 ? 0 : sectionNanos[section.ordinal()] / 1e6 / ticks;
        }

        public double kbPerTick(Section section) {
            return ticks == 0 ? 0 : sectionBytes[section.ordinal()] / 1024.0 / ticks;
        }

        public double perTick(Counter counter) {
            return ticks == 0 ? 0 : counters[counter.ordinal()] / (double) ticks;
        }

        public double totalMsPerTick() {
            long sum = 0;
            for (long n : sectionNanos) {
                sum += n;
            }
            return ticks == 0 ? 0 : sum / 1e6 / ticks;
        }

        public double percentileMs(double p) {
            if (tickTotalsSorted.length == 0) {
                return 0;
            }
            int i = (int) Math.min(tickTotalsSorted.length - 1, Math.floor(p * tickTotalsSorted.length));
            return tickTotalsSorted[i] / 1e6;
        }

        public double maxMs() {
            return tickTotalsSorted.length == 0 ? 0 : tickTotalsSorted[tickTotalsSorted.length - 1] / 1e6;
        }
    }
}

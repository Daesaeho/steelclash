package com.steelclash.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class LagMathTest {
    @Test
    void noLatencyNoCompensation() {
        assertEquals(0, LagMath.rewindTicks(0, 2, 300));
        assertEquals(0, LagMath.graceTicks(0, 250));
    }

    @Test
    void rewindIsRoundTripPlusInterpolationCapped() {
        // 150 ms ping + 100 ms interpolation = 250 ms = 5 ticks
        assertEquals(5, LagMath.rewindTicks(150, 2, 300));
        // 600 ms ping: capped at 300 ms = 6 ticks
        assertEquals(6, LagMath.rewindTicks(600, 2, 300));
    }

    @Test
    void graceCoversARoundTripAndIsCapped() {
        assertEquals(4, LagMath.graceTicks(150, 250));  // 150 + 25 = 175 ms, rounds to 4 ticks
        assertEquals(1, LagMath.graceTicks(20, 250));   // 45 ms
        assertEquals(5, LagMath.graceTicks(1000, 250)); // capped at 250 ms
    }

    @Test
    void oneWayIsHalfTheRoundTrip() {
        assertEquals(0, LagMath.oneWayTicks(0));
        assertEquals(2, LagMath.oneWayTicks(150)); // 75 ms
        assertEquals(1, LagMath.oneWayTicks(60));  // 30 ms
    }

    @Test
    void historyReturnsThePositionAtOrBeforeATick() {
        PositionHistory h = new PositionHistory(4);
        assertNull(h.at(5));
        for (int t = 1; t <= 6; t++) {
            h.record(t, t * 10, 0, 0);
        }
        assertEquals(60, h.at(6).x(), 1e-9);
        assertEquals(40, h.at(4).x(), 1e-9);
        assertEquals(30, h.at(1).x(), 1e-9, "older than the buffer: the oldest kept (capacity 4 keeps ticks 3..6)");
        assertEquals(60, h.at(100).x(), 1e-9, "future: the newest");
    }

    @Test
    void recordingTheSameTickTwiceUpdatesIt() {
        PositionHistory h = new PositionHistory(4);
        h.record(5, 1, 0, 0);
        h.record(5, 2, 0, 0);
        assertEquals(2, h.at(5).x(), 1e-9);
    }

    @Test
    void historyUsesTimestampsEvenWhenRecordsArriveOutOfOrder() {
        PositionHistory h = new PositionHistory(3);
        h.record(4, 40, 4, -4);
        h.record(12, 120, 12, -12);
        h.record(8, 80, 8, -8);
        assertEquals(new Vec(80, 8, -8), h.at(10));
        assertEquals(new Vec(120, 12, -12), h.at(100));
        assertEquals(new Vec(40, 4, -4), h.at(0));
        h.record(20, 200, 20, -20); // evicts tick 4, so tick 8 is now the oldest timestamp
        assertEquals(new Vec(80, 8, -8), h.at(0));
        assertEquals(new Vec(120, 12, -12), h.at(15));
    }

    @Test
    void oneSlotHistoryWrapsAndUpdatesAllCoordinates() {
        PositionHistory h = new PositionHistory(1);
        h.record(1, 10, 20, 30);
        h.record(2, 40, 50, 60);
        h.record(2, 70, 80, 90);
        assertEquals(new Vec(70, 80, 90), h.at(1));
        assertEquals(new Vec(70, 80, 90), h.at(2));
        assertEquals(new Vec(70, 80, 90), h.at(3));
    }
}

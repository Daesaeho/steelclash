package com.steelclash.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BladeTwistTest {
    @Test
    void slashesTurnTheEdgeSideways() {
        double rightToLeft = ArcPath.horizontal(140).edgeAngle(0.5);
        assertTrue(rightToLeft < -70 && rightToLeft > -100, "right-to-left slash: edge turned to lead leftward, got " + rightToLeft);
        assertEquals(-rightToLeft, ArcPath.horizontal(140).mirrored().edgeAngle(0.5), 1e-9, "mirrored slash: the other way");
    }

    @Test
    void overheadsKeepTheEdgeDown() {
        double overhead = ArcPath.vertical().edgeAngle(0.5);
        assertTrue(Math.abs(overhead) < 15, "overhead: edge down, got " + overhead);
    }

    @Test
    void thrustsAreHeldFlat() {
        assertEquals(90, ArcPath.thrust().edgeAngle(0.3), 1e-9);
    }

    @Test
    void twistIsSmoothAcrossKeyframes() {
        ArcPath slash = ArcPath.horizontal(140);
        double previous = slash.edgeAngle(0);
        for (double t = 0.02; t <= 1.0; t += 0.02) {
            double now = slash.edgeAngle(t);
            assertTrue(Math.abs(now - previous) < 10, "no snapping at t=" + t + ": " + previous + " -> " + now);
            previous = now;
        }
    }
}

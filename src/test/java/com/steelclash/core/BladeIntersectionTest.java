package com.steelclash.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class BladeIntersectionTest {
    @Test
    void boundaryAndParallelCasesOnEveryAxis() {
        Vec max = new Vec(1, 1, 1);
        assertTrue(Blade.intersectsBox(Vec.ZERO, Vec.ZERO, Vec.ZERO, max));
        assertTrue(Blade.intersectsBox(max, max, Vec.ZERO, max));
        assertFalse(Blade.intersectsBox(new Vec(2, 2, 2), new Vec(2, 2, 2), Vec.ZERO, max));
        assertTrue(Blade.intersectsBox(new Vec(-1, -1, -1), Vec.ZERO, Vec.ZERO, max));
        assertTrue(Blade.intersectsBox(new Vec(2, 2, 2), max, Vec.ZERO, max));
        for (int axis = 0; axis < 3; axis++) {
            double[] a = {0.5, 0.5, 0.5};
            double[] b = {0.5, 0.5, 0.5};
            a[axis] = -1;
            b[axis] = 2;
            assertTrue(hit(a, b));
            assertTrue(hit(b, a));
            a[(axis + 1) % 3] = b[(axis + 1) % 3] = 1;
            assertTrue(hit(a, b), "a segment on a face is included");
            a[(axis + 1) % 3] = b[(axis + 1) % 3] = 1.01;
            assertFalse(hit(a, b), "parallel segment outside the box");
        }
    }

    @Test
    void scalarSlabsMatchOriginalAcrossSeededCases() {
        Random random = new Random(0x51AB);
        for (int i = 0; i < 100_000; i++) {
            // Local fights and fights near the world border, in both swing directions.
            double origin = i % 2 == 0 ? 0 : 29_000_000;
            Vec a = point(random, origin);
            Vec b = point(random, origin);
            if (i % 5 == 0) {
                b = a; // zero-length segments
            } else if (i % 5 == 1) {
                b = new Vec(a.x(), b.y(), a.z()); // parallel axes
            } else if (i % 5 == 2) {
                b = new Vec(a.x() + 0.5e-9, b.y(), a.z() + 1e-9); // slab epsilon boundary
            }
            Vec min = point(random, origin);
            Vec max = min.add(new Vec(random.nextDouble() * 4, random.nextDouble() * 4, random.nextDouble() * 4));
            boolean expected = originalIntersects(a, b, min, max);
            assertEquals(expected, Blade.intersectsBox(a, b, min, max), "vector case " + i);
            assertEquals(expected, Blade.intersectsBox(a, b, min.x(), min.y(), min.z(), max.x(), max.y(), max.z()),
                    "scalar case " + i);
        }
    }

    private static Vec point(Random random, double origin) {
        return new Vec(origin + random.nextDouble() * 10 - 5, random.nextDouble() * 10 - 5,
                -origin + random.nextDouble() * 10 - 5);
    }

    private static boolean hit(double[] a, double[] b) {
        return Blade.intersectsBox(new Vec(a[0], a[1], a[2]), new Vec(b[0], b[1], b[2]), Vec.ZERO, new Vec(1, 1, 1));
    }

    /** Pre-optimization implementation retained as a differential oracle for the numerical tolerance. */
    private static boolean originalIntersects(Vec a, Vec b, Vec min, Vec max) {
        double tMin = 0;
        double tMax = 1;
        double[] start = {a.x(), a.y(), a.z()};
        double[] delta = {b.x() - a.x(), b.y() - a.y(), b.z() - a.z()};
        double[] lo = {min.x(), min.y(), min.z()};
        double[] hi = {max.x(), max.y(), max.z()};
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(delta[axis]) < 1e-9) {
                if (start[axis] < lo[axis] || start[axis] > hi[axis]) {
                    return false;
                }
            } else {
                double inv = 1.0 / delta[axis];
                double t1 = (lo[axis] - start[axis]) * inv;
                double t2 = (hi[axis] - start[axis]) * inv;
                if (t1 > t2) {
                    double tmp = t1;
                    t1 = t2;
                    t2 = tmp;
                }
                tMin = Math.max(tMin, t1);
                tMax = Math.min(tMax, t2);
                if (tMin > tMax) {
                    return false;
                }
            }
        }
        return true;
    }
}

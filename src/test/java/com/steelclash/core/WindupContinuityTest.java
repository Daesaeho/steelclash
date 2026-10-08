package com.steelclash.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WindupContinuityTest {
    @Test
    void everyLightAndHeavyWindupEndsAtItsLiveArcStartOnEitherSide() {
        for (AttackType type : AttackType.values()) {
            for (boolean mirrored : new boolean[]{false, true}) {
                ArcPath path = ArcPath.defaultFor(type);
                if (mirrored) path = path.mirrored();
                ArcPath.Keyframe start = path.sample(0);
                for (boolean heavy : new boolean[]{false, true}) {
                    ArcPath.Keyframe end = path.windup(1, heavy);
                    assertEquals(start.yaw(), end.yaw(), 1e-9, type.toString());
                    assertEquals(start.pitch(), end.pitch(), 1e-9, type.toString());
                    assertEquals(start.extension(), end.extension(), 1e-9, type.toString());
                    ArcPath.Keyframe near = path.windup(1 - 1e-6, heavy);
                    assertEquals(end.yaw(), near.yaw(), 1e-8);
                    assertEquals(end.pitch(), near.pitch(), 1e-8);
                    assertEquals(end.extension(), near.extension(), 1e-8);
                }
            }
        }
    }

    @Test
    void heavyPreparationStillHasAReadableDrawback() {
        ArcPath path = ArcPath.horizontal(140);
        assertTrue(path.windup(0.5, true).yaw() > path.windup(0.5, false).yaw());
        assertTrue(path.windup(0.5, true).pitch() < path.windup(0.5, false).pitch());
    }

    @Test
    void cutsDrawBackTheWayTheyWillComeFromAndThrustsDoNot() {
        ArcPath slash = ArcPath.horizontal(140);
        assertTrue(slash.windup(0.5, false).yaw() > 70 + 30, "a slash cocks well past where its arc starts");
        assertTrue(slash.mirrored().windup(0.5, false).yaw() < -70 - 30, "the other way round when mirrored");
        assertTrue(ArcPath.vertical().windup(0.5, false).pitch() < -100, "an overhead leans back over the head");
        assertTrue(ArcPath.vertical().windup(0.5, true).pitch() < ArcPath.vertical().windup(0.5, false).pitch());
        ArcPath thrust = ArcPath.thrust();
        assertEquals(thrust.sample(0).yaw(), thrust.windup(0.5, true).yaw(), 1, "a thrust isn't swung sideways");
        assertTrue(thrust.windup(0.5, true).pitch() < thrust.sample(0).pitch() - 20, "but it is raised");
    }
}

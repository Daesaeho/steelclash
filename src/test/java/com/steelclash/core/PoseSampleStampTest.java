package com.steelclash.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PoseSampleStampTest {
    /** Simulates PAL's tick-time setup and the renderer's shared-pose reuse. */
    private static final class Renderer {
        final PoseSampleStamp stamp = new PoseSampleStamp();
        CombatStateMachine.VisualFrame displayed;
        int samples;
        CombatStateMachine.VisualFrame render(CombatStateMachine machine, long frame, int tick, float partial) {
            if (!stamp.matches(frame,tick,partial,machine.phaseElapsedUs(),machine.phaseDurationUs())) {
                displayed=machine.visualFrame(partial);
                stamp.record(frame,tick,partial,machine.phaseElapsedUs(),machine.phaseDurationUs());
                samples++;
            }
            return displayed;
        }
    }

    @Test void zeroPartialTickSetupBeforeCombatAdvanceCannotRewindTheRenderedRelease() {
        CombatStateMachine machine=new CombatStateMachine();
        assertTrue(machine.startAttack(AttackType.SLASH,AttackTimings.ofMillis(500,425,750)));
        for(int i=0;i<12;i++)machine.tick(); // release at 100 ms
        Renderer renderer=new Renderer();
        double previous=renderer.render(machine,7,136,.82f).progress();
        renderer.render(machine,7,137,0); // PAL setup inside entity.tick, before combat's Post event
        machine.tick();
        // A new frame at exactly zero partial may be skipped by PAL's tickCount+partial deduplication.
        var corrected=renderer.render(machine,8,137,0);
        assertEquals(150_000,corrected.elapsedUs());
        assertTrue(corrected.progress()>previous);
        assertEquals(3,renderer.samples);
        assertSame(corrected,renderer.render(machine,8,137,0)); // other bones reuse this render sample
        assertEquals(3,renderer.samples);
    }

    @Test void sameFrameAndEntityTickStillInvalidateWhenCombatAdvances() {
        CombatStateMachine machine=new CombatStateMachine();
        machine.startAttack(AttackType.SLASH,AttackTimings.ofMillis(500,425,750));
        Renderer renderer=new Renderer();renderer.render(machine,9,14,0);
        machine.tick();
        assertEquals(50_000,renderer.render(machine,9,14,0).elapsedUs());
        assertEquals(2,renderer.samples);
    }

    @Test void durationExtensionCannotReuseAnOldGuardProgress() {
        CombatStateMachine machine=new CombatStateMachine();assertTrue(machine.startParry(3,2,1));
        machine.tick();machine.tick();
        Renderer renderer=new Renderer();var before=renderer.render(machine,10,30,0);
        machine.parrySucceeded(3);
        var after=renderer.render(machine,10,30,0);
        assertEquals(before.elapsedUs(),after.elapsedUs());
        assertTrue(after.durationUs()>before.durationUs());
        assertTrue(after.progress()<before.progress());
        assertEquals(2,renderer.samples);
    }

    @Test void newFramePartialTickAndExplicitInvalidationAllRefresh() {
        CombatStateMachine machine=new CombatStateMachine();machine.startAttack(AttackType.SLASH,AttackTimings.ofMillis(500,425,750));
        Renderer renderer=new Renderer();renderer.render(machine,1,0,0);
        assertEquals(25_000,renderer.render(machine,1,0,.5f).elapsedUs());
        renderer.render(machine,2,0,.5f);assertEquals(3,renderer.samples);
        renderer.stamp.clear();renderer.render(machine,2,0,.5f);assertEquals(4,renderer.samples);
    }
}

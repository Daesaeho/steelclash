package com.steelclash.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnimationSetValidationTest {
    @Test
    void missingClipsInheritWhileExplicitEmptyOverridesRemainEmpty() {
        PoseClip clip = new PoseClip(List.of(new PoseClip.Keyframe(0, Map.of("body", new double[]{1, 2, 3}))));
        AnimationSet fallback = new AnimationSet(false, 1.35, 2.5, Map.of("slash.release", clip, "stab.release", clip));
        AnimationSet override = new AnimationSet(true, 1.4, 7, Map.of("slash.release", PoseClip.EMPTY)).withFallback(fallback);
        assertTrue(override.clip("slash.release").isEmpty());
        assertSame(clip, override.clip("stab.release"));
        assertSame(clip, override.clip("special.lunge.release", "stab.release"));
        assertEquals(7, override.gripGap());
        assertTrue(override.twoHanded());
    }

    @Test
    void parsesOptionalFirstPersonCompositionWithBackwardsCompatibleDefaults() {
        assertEquals(AnimationSet.FirstPerson.DEFAULT, parse("{}").firstPerson());
        var settings = parse("{\"first_person\":{\"forward\":6,\"down\":2,\"retraction\":0}}").firstPerson();
        assertEquals(new AnimationSet.FirstPerson(6, 2, 0), settings);
    }

    @Test
    void rejectsMalformedChannelAndTimeDataWithClipContext() {
        for (String frame : List.of("{\"t\":1.01}", "{\"t\":-0.1}", "{\"t\":\"NaN\"}",
                "{\"t\":0,\"rightArn\":[1]}", "{\"t\":0,\"head\":4}", "{\"t\":0,\"head\":[]}",
                "{\"t\":0,\"head\":[1,2,3,4]}", "{\"t\":0,\"body\":[\"Infinity\"]}")) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> parse("{\"clips\":{\"slash.windup\":[" + frame + "]}}"));
            assertTrue(error.getMessage().contains("slash.windup"), error.getMessage());
        }
        assertThrows(IllegalArgumentException.class,
                () -> parse("{\"clips\":{\"slash.windup\":[{\"t\":0},{\"t\":-0.0}]}}"));
    }

    @Test
    void rejectsInvalidMetadata() {
        for (String json : List.of("{\"format_version\":2}", "{\"format_version\":1.5}",
                "{\"grip\":\"two_handed_typo\"}", "{\"grip_gap\":0}", "{\"grip_gap\":11}",
                "{\"heavy_windup_scale\":\"NaN\"}", "{\"first_person\":{\"retraction\":-1}}")) {
            assertThrows(IllegalArgumentException.class, () -> parse(json));
        }
    }

    private static AnimationSet parse(String json) { return AnimationSet.parse(JsonParser.parseString(json).getAsJsonObject()); }
}

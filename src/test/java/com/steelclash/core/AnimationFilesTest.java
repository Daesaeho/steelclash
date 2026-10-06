package com.steelclash.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Every shipped animation file parses and covers every phase it can be asked for. */
class AnimationFilesTest {
    private static final Path DIR = Path.of("src/main/resources/assets/steelclash/steelclash_animations");

    @Test
    void allShippedAnimationsParseAndAreComplete() throws IOException {
        List<Path> files;
        try (Stream<Path> stream = Files.list(DIR)) {
            files = stream.filter(p -> p.toString().endsWith(".json")).toList();
        }
        assertFalse(files.isEmpty());
        for (Path file : files) {
            AnimationSet animation;
            try (Reader reader = Files.newBufferedReader(file)) {
                animation = AnimationSet.parse(JsonParser.parseReader(reader).getAsJsonObject());
            }
            for (AttackType type : AttackType.values()) {
                for (Phase phase : new Phase[]{Phase.WINDUP, Phase.RELEASE, Phase.RECOVERY}) {
                    String key = AnimationSet.clipKey(phase, type);
                    assertFalse(animation.clip(key).isEmpty(), file.getFileName() + " is missing " + key);
                }
            }
            for (Phase phase : new Phase[]{Phase.PARRY, Phase.GUARD_RECOVERY, Phase.STAGGER}) {
                assertFalse(animation.clip(AnimationSet.clipKey(phase, AttackType.SLASH)).isEmpty(),
                        file.getFileName() + " is missing " + phase);
            }
            for (AttackType type : AttackType.WEAPON_ATTACKS) {
                assertFalse(animation.clip(type.serializedName() + ".heavy_windup").isEmpty(),
                        file.getFileName() + " is missing " + type.serializedName() + ".heavy_windup");
            }
            if (file.getFileName().toString().equals("two_handed.json")) {
                assertTrue(animation.twoHanded(), "two_handed grips with both hands");
            }
        }
    }
}

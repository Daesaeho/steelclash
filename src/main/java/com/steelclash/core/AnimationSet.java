package com.steelclash.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One archetype's pose clips (see {@code assets/steelclash/steelclash_animations/*.json}).
 *
 * @param twoHanded        the off hand grips the weapon too
 * @param heavyWindupScale how much more exaggerated a heavy's windup pose is
 */
public record AnimationSet(boolean twoHanded, double heavyWindupScale, Map<String, PoseClip> clips) {
    public static final AnimationSet NONE = new AnimationSet(false, 1, Map.of());

    public PoseClip clip(String key) {
        return clips.getOrDefault(key, PoseClip.EMPTY);
    }

    /** Parses the JSON format: {@code {"grip": "two_handed", "heavy_windup_scale": 1.35, "clips": {"slash.windup": [{"t": 0, "body": [x, y, z]}, ...]}}}. */
    public static AnimationSet parse(JsonObject json) {
        boolean twoHanded = json.has("grip") && json.get("grip").getAsString().equals("two_handed");
        double heavyScale = json.has("heavy_windup_scale") ? json.get("heavy_windup_scale").getAsDouble() : 1.35;
        Map<String, PoseClip> clips = new HashMap<>();
        JsonObject clipsJson = json.getAsJsonObject("clips");
        if (clipsJson != null) {
            for (Map.Entry<String, JsonElement> clip : clipsJson.entrySet()) {
                List<PoseClip.Keyframe> keyframes = new ArrayList<>();
                for (JsonElement kfElement : clip.getValue().getAsJsonArray()) {
                    JsonObject kf = kfElement.getAsJsonObject();
                    double t = kf.has("t") ? kf.get("t").getAsDouble() : 0;
                    Map<String, double[]> parts = new HashMap<>();
                    for (Map.Entry<String, JsonElement> part : kf.entrySet()) {
                        if (part.getValue().isJsonArray()) {
                            JsonArray a = part.getValue().getAsJsonArray();
                            parts.put(part.getKey(), new double[]{at(a, 0), at(a, 1), at(a, 2)});
                        }
                    }
                    keyframes.add(new PoseClip.Keyframe(t, parts));
                }
                clips.put(clip.getKey(), new PoseClip(keyframes));
            }
        }
        return new AnimationSet(twoHanded, heavyScale, Map.copyOf(clips));
    }

    /** Clip key for a phase: "slash.windup", "kick.release", "parry", "stagger", ... */
    public static String clipKey(Phase phase, AttackType type) {
        return switch (phase) {
            case WINDUP, RELEASE, RECOVERY -> type.serializedName() + "." + phase.name().toLowerCase(java.util.Locale.ROOT);
            case PARRY -> "parry";
            case GUARD_RECOVERY -> "guard_recovery";
            case STAGGER -> "stagger";
            case IDLE -> "";
        };
    }

    private static double at(JsonArray array, int i) {
        return i < array.size() ? array.get(i).getAsDouble() : 0;
    }
}

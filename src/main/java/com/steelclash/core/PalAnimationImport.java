package com.steelclash.core;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;

/** Offline import entry point. Never overwrites an existing file or changes runtime resources automatically. */
public final class PalAnimationImport {
    private static final long MAX_INPUT_BYTES = 8 * 1024 * 1024;

    private PalAnimationImport() {}

    /** Merges only the selected attack's three phases into a validated copy of the existing family asset. */
    public static JsonObject merge(JsonObject source, JsonObject base, String animationName, AttackType attack,
                                   boolean leftMain, double releaseStart, double recoveryStart, double toleranceDegrees) {
        return merge(source, null, base, animationName, attack, leftMain, releaseStart, recoveryStart, toleranceDegrees);
    }

    public static JsonObject merge(JsonObject source, JsonObject sourceProject, JsonObject base, String animationName, AttackType attack,
                                   boolean leftMain, double releaseStart, double recoveryStart, double toleranceDegrees) {
        return merge(source, sourceProject, base, animationName, null, attack, leftMain, releaseStart, recoveryStart, toleranceDegrees);
    }

    public static JsonObject merge(JsonObject source, JsonObject sourceProject, JsonObject base, String animationName,
                                   String heavyAnimationName, AttackType attack, boolean leftMain,
                                   double releaseStart, double recoveryStart, double toleranceDegrees) {
        AnimationSet settings = AnimationSet.parse(base);
        boolean nativeSource = sourceProject != null;
        JsonObject nativeExport = source;
        if (heavyAnimationName != null && !nativeSource) throw new IllegalArgumentException("paired heavy import requires its native source project");
        if (nativeSource && heavyAnimationName == null && !settings.clip(AnimationSet.heavyWindupKey(attack)).isEmpty())
            throw new IllegalArgumentException("source-aware replacement of an existing authored heavy windup requires palImportHeavyAnimation to preserve phase seams");
        if (nativeSource) source = BlockbenchAnimationRetarget.recover(sourceProject, nativeExport, animationName, leftMain, settings.twoHanded());
        JsonObject converted = PalAnimationConverter.convert(source, animationName, attack, leftMain,
                settings.twoHanded(), releaseStart, recoveryStart, toleranceDegrees,
                nativeSource ? PoseClip.Interpolation.LINEAR : PoseClip.Interpolation.MONOTONE_CUBIC);
        if (nativeSource) addAuthoredHipPolicy(converted);
        if (heavyAnimationName != null) {
            JsonObject heavySource = BlockbenchAnimationRetarget.recover(sourceProject,nativeExport,heavyAnimationName,leftMain,settings.twoHanded());
            double lightLength = source.getAsJsonObject("animations").getAsJsonObject(animationName).get("animation_length").getAsDouble();
            if (heavySource.getAsJsonObject("animations").getAsJsonObject(heavyAnimationName).get("animation_length").getAsDouble() != lightLength)
                throw new IllegalArgumentException("paired source takes must use the same authoring length and phase boundaries");
            JsonObject heavy = PalAnimationConverter.convert(heavySource,heavyAnimationName,attack,leftMain,settings.twoHanded(),
                    releaseStart,recoveryStart,toleranceDegrees,PoseClip.Interpolation.LINEAR);
            addAuthoredHipPolicy(heavy);
            heavy.getAsJsonObject("clips").entrySet().forEach(e->{
                String key=e.getKey().replace(".",".heavy_");
                converted.getAsJsonObject("clips").add(key,e.getValue());
                converted.getAsJsonObject("clip_interpolation").addProperty(key,"linear");
            });
        }
        JsonObject result = base.deepCopy();
        JsonObject clips = result.has("clips") ? result.getAsJsonObject("clips") : new JsonObject();
        converted.getAsJsonObject("clips").entrySet().forEach(e -> clips.add(e.getKey(), e.getValue()));
        result.add("clips", clips);
        if (converted.has("clip_interpolation")) {
            JsonObject curves = result.has("clip_interpolation") ? result.getAsJsonObject("clip_interpolation") : new JsonObject();
            converted.getAsJsonObject("clip_interpolation").entrySet().forEach(e -> curves.add(e.getKey(),e.getValue()));
            result.add("clip_interpolation",curves);
        } else if (result.has("clip_interpolation")) {
            // A later strict PAL import replaces these clips with cubic bakes; stale linear metadata must not survive.
            converted.getAsJsonObject("clips").keySet().forEach(result.getAsJsonObject("clip_interpolation")::remove);
        }
        AnimationSet.parse(result);
        return result;
    }

    public static void write(Path source, Path base, Path output, String animationName, AttackType attack,
                             boolean leftMain, double releaseStart, double recoveryStart, double toleranceDegrees)
            throws IOException {
        write(source, null, base, output, animationName, attack, leftMain, releaseStart, recoveryStart, toleranceDegrees);
    }

    public static void write(Path source, Path sourceProject, Path base, Path output, String animationName, AttackType attack,
                             boolean leftMain, double releaseStart, double recoveryStart, double toleranceDegrees) throws IOException {
        write(source,sourceProject,base,output,animationName,null,attack,leftMain,releaseStart,recoveryStart,toleranceDegrees);
    }

    public static void write(Path source, Path sourceProject, Path base, Path output, String animationName, String heavyAnimationName,
                             AttackType attack, boolean leftMain, double releaseStart, double recoveryStart, double toleranceDegrees) throws IOException {
        JsonObject result = merge(read(source), sourceProject == null ? null : read(sourceProject), read(base), animationName, heavyAnimationName, attack, leftMain,
                releaseStart, recoveryStart, toleranceDegrees);
        // CREATE_NEW checks at the filesystem boundary, including aliases of either input path.
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private static void addAuthoredHipPolicy(JsonObject converted) {
        for (var clip : converted.getAsJsonObject("clips").entrySet()) for (var frame : clip.getValue().getAsJsonArray()) {
            com.google.gson.JsonArray zero=new com.google.gson.JsonArray(); zero.add(0);zero.add(0);zero.add(0);
            frame.getAsJsonObject().add("hipLag",zero);
        }
    }

    private static JsonObject read(Path path) throws IOException {
        if (Files.size(path) > MAX_INPUT_BYTES) throw new IllegalArgumentException(path + ": input exceeds 8 MiB");
        String text = Files.readString(path, StandardCharsets.UTF_8);
        // Gson's tree parser retains only the final duplicate key. Validate before building the tree.
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            validateJson(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException(path + ": trailing JSON data");
        }
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static void validateJson(JsonReader reader, int depth) throws IOException {
        if (depth > 64) throw new IllegalArgumentException(reader.getPath() + ": JSON nesting exceeds 64");
        switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                Set<String> names = new HashSet<>();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (!names.add(name)) throw new IllegalArgumentException(reader.getPath() + ": duplicate JSON key " + name);
                    validateJson(reader, depth + 1);
                }
                reader.endObject();
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                while (reader.hasNext()) validateJson(reader, depth + 1);
                reader.endArray();
            }
            case STRING, NUMBER -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> reader.nextNull();
            default -> throw new IllegalArgumentException(reader.getPath() + ": expected a JSON value");
        }
    }

    /** source.json base.json NEW-output.json animation slash|overhead|stab left|right release recovery tolerance */
    public static void main(String[] args) throws IOException {
        if (args.length < 9 || args.length > 11) throw new IllegalArgumentException(
                "Expected: source base NEW-output animation attack left|right release-seconds recovery-seconds tolerance-degrees [source.bbmodel [heavy-animation]]");
        if (!args[5].equals("left") && !args[5].equals("right")) throw new IllegalArgumentException("main arm must be left or right");
        write(Path.of(args[0]), args.length >= 10 ? Path.of(args[9]) : null, Path.of(args[1]), Path.of(args[2]), args[3], args.length == 11 ? args[10] : null,
                AttackType.valueOf(args[4].toUpperCase(Locale.ROOT)), args[5].equals("left"),
                Double.parseDouble(args[6]), Double.parseDouble(args[7]), Double.parseDouble(args[8]));
        System.out.println("Wrote validated animation candidate: " + Path.of(args[2]).toAbsolutePath());
    }
}

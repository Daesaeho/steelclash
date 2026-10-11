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
        AnimationSet settings = AnimationSet.parse(base);
        JsonObject converted = PalAnimationConverter.convert(source, animationName, attack, leftMain,
                settings.twoHanded(), releaseStart, recoveryStart, toleranceDegrees);
        JsonObject result = base.deepCopy();
        JsonObject clips = result.has("clips") ? result.getAsJsonObject("clips") : new JsonObject();
        converted.getAsJsonObject("clips").entrySet().forEach(e -> clips.add(e.getKey(), e.getValue()));
        result.add("clips", clips);
        AnimationSet.parse(result);
        return result;
    }

    public static void write(Path source, Path base, Path output, String animationName, AttackType attack,
                             boolean leftMain, double releaseStart, double recoveryStart, double toleranceDegrees)
            throws IOException {
        JsonObject result = merge(read(source), read(base), animationName, attack, leftMain,
                releaseStart, recoveryStart, toleranceDegrees);
        // CREATE_NEW checks at the filesystem boundary, including aliases of either input path.
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
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
        if (args.length != 9) throw new IllegalArgumentException(
                "Expected: source base NEW-output animation attack left|right release-seconds recovery-seconds tolerance-degrees");
        if (!args[5].equals("left") && !args[5].equals("right")) throw new IllegalArgumentException("main arm must be left or right");
        write(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]), args[3],
                AttackType.valueOf(args[4].toUpperCase(Locale.ROOT)), args[5].equals("left"),
                Double.parseDouble(args[6]), Double.parseDouble(args[7]), Double.parseDouble(args[8]));
        System.out.println("Wrote validated animation candidate: " + Path.of(args[2]).toAbsolutePath());
    }
}

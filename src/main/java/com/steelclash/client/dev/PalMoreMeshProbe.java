package com.steelclash.client.dev;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.steelclash.SteelClash;
import com.steelclash.compat.PalMoreRotationCompat;
import com.steelclash.client.anim.CombatPresentation;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

/** Read-only rendered-mesh evidence for the optional PAL More Rotation prototype. */
@EventBusSubscriber(modid = SteelClash.MOD_ID, value = Dist.CLIENT)
public final class PalMoreMeshProbe {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String OUTPUT = System.getProperty("steelclash.palMoreMeshOut");
    private static final int MAX_SAMPLES = 64;
    private static int samples;
    private static Path output;
    private static boolean disabled, warned;
    private static Method cuboidAt;
    private static Field positionsField;
    private static Method bendMethod, positionMethod, originalPositionMethod;

    private PalMoreMeshProbe() {}

    @SubscribeEvent
    static void afterPlayerRender(RenderLivingEvent.Post<?, ?> event) {
        if (OUTPUT == null || disabled || samples >= MAX_SAMPLES || !PalMoreRotationCompat.isSupported()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getEntity() != minecraft.player || !(event.getRenderer().getModel() instanceof PlayerModel<?> model)) return;
        var pose = CombatPresentation.get(minecraft.player, 0).orElse(null);
        if (pose == null || !pose.phase().isAttack()) return; // Reserve bounded samples for actual posed frames.

        JsonObject sample = new JsonObject();
        sample.addProperty("sample", ++samples);
        sample.addProperty("nanoTime", System.nanoTime());
        sample.addProperty("gameTick", minecraft.level == null ? -1 : minecraft.level.getGameTime());
        sample.addProperty("firstPerson", FirstPersonMode.isFirstPersonPass());
        sample.addProperty("phase", pose.phase().name());
        JsonArray parts = new JsonArray();
        sample.add("parts", parts);
        recordPart(parts, "body", model.body);
        recordPart(parts, "rightArm", model.rightArm);
        recordPart(parts, "leftArm", model.leftArm);
        write(sample);
    }

    private static void recordPart(JsonArray parts, String name, net.minecraft.client.model.geom.ModelPart part) {
        JsonObject result = new JsonObject();
        result.addProperty("part", name);
        result.addProperty("visible", part.visible);
        try {
            ensureReflection(part);
            Object cuboid = cuboidAt.invoke(part, 0);
            if (cuboid == null) {
                result.addProperty("diagnostic", "no bendable cuboid at index 0");
            } else {
                result.addProperty("cubeClass", cuboid.getClass().getName());
                result.addProperty("bend", ((Number) bendMethod.invoke(cuboid)).doubleValue());
                Object positions = positionsField.get(cuboid);
                int count = positions == null ? 0 : Array.getLength(positions);
                result.addProperty("vertexCount", count);
                double maxDistance = 0;
                for (int i = 0; i < count; i++) {
                    Object rememberingPos = Array.get(positions, i);
                    Vector3f current = (Vector3f) positionMethod.invoke(rememberingPos);
                    Vector3f original = (Vector3f) originalPositionMethod.invoke(rememberingPos);
                    maxDistance = Math.max(maxDistance, current.distance(original));
                }
                result.addProperty("maxVertexDelta", maxDistance);
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            result.addProperty("diagnostic", error.getClass().getSimpleName() + ": " + error.getMessage());
            warnOnce("Could not inspect PAL More rendered bendable cuboids; recording diagnostics only", error);
        }
        parts.add(result);
    }

    private static void ensureReflection(net.minecraft.client.model.geom.ModelPart part) throws ReflectiveOperationException {
        if (cuboidAt == null) {
            ClassLoader loader = PalMoreMeshProbe.class.getClassLoader();
            Class<?> bendablePart = Class.forName("com.zigythebird.bendable_cuboids.api.BendableModelPart", false, loader);
            cuboidAt = bendablePart.getMethod("bc$getCuboid", int.class);
        }
        if (bendMethod != null && positionsField != null && positionMethod != null && originalPositionMethod != null) return;
        Object first = cuboidAt.invoke(part, 0);
        if (first == null) return;
        Class<?> cubeClass = first.getClass();
        bendMethod = cubeClass.getMethod("getBend");
        Class<?> declaring = cubeClass;
        while (declaring != null && positionsField == null) {
            try {
                positionsField = declaring.getDeclaredField("positions");
                positionsField.setAccessible(true);
            } catch (NoSuchFieldException ignored) {
                declaring = declaring.getSuperclass();
            }
        }
        if (positionsField == null) throw new NoSuchFieldException("positions");
        Object positions = positionsField.get(first);
        if (positions == null || Array.getLength(positions) == 0) return;
        Class<?> posClass = Array.get(positions, 0).getClass();
        positionMethod = posClass.getMethod("getPos");
        originalPositionMethod = posClass.getMethod("getOriginalPos");
    }

    private static void write(JsonObject sample) {
        try {
            boolean first = output == null;
            if (output == null) {
                output = Path.of(OUTPUT).toAbsolutePath();
                Path parent = output.getParent();
                if (parent != null) Files.createDirectories(parent);
            }
            Files.writeString(output, new com.google.gson.Gson().toJson(sample) + System.lineSeparator(),
                    first ? java.nio.file.StandardOpenOption.CREATE_NEW : java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception error) {
            disabled = true;
            warnOnce("Could not write PAL More mesh probe output", error);
        }
    }

    private static void warnOnce(String message, Throwable error) {
        if (warned) return;
        warned = true;
        LOGGER.warn(message, error);
    }
}

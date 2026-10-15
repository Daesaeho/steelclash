package com.steelclash.client.anim;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.steelclash.SteelClash;
import com.steelclash.core.Mat3;
import com.steelclash.core.Vec;
import com.steelclash.core.GripContact;
import com.steelclash.core.WeaponRig;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * Physical grip geometry for explicitly calibrated, unchanged generated handheld assets on the vanilla player rig.
 * Unknown items, replaced resources, model wrappers and custom renderers retain the existing rig. Resource and mesh
 * validation runs once per reload/model identity, never once per pose sample. PAL's first-person model pass uses these
 * same third-person hand contexts. Exact renderer/model classes cannot identify transforms injected into those
 * classes or applied later by another mod; those rig ownership paths still need their own runtime verification.
 */
public final class VerifiedHandheldGrip {
    private static final String FOLDER = "steelclash_item_grips/";
    private static final int MAX_RESOURCE_BYTES = 8 * 1024 * 1024;
    private static final double EPSILON = 1e-6;
    private static final Vec VANILLA_SOCKET = new Vec(-1, 10, -2);
    private static final Map<ResourceLocation, Optional<Calibration>> DESCRIPTORS = new HashMap<>();
    private static final Map<ResourceLocation, Optional<ResourceAsset>> RESOURCES = new HashMap<>();
    private static final IdentityHashMap<BakedModel, Map<ResourceLocation, Optional<Calibration>>> MODELS = new IdentityHashMap<>();
    private static long generation = Long.MIN_VALUE;

    private record ResourceAsset(byte[] bytes, String hash) {}
    private record ResourceCheck(ResourceLocation id, String hash) {}
    private record Calibration(ResourceLocation sprite, Vec rightRotation, Vec leftRotation, Vec translation, Vec scale,
                               int quadCount, String geometryHash,
                               double minimumGap, double maximumGap,
                               GripContact mainContact, Vec wideRootShift, Vec slimRootShift,
                               WeaponRig.GripAttachment wide, WeaponRig.GripAttachment slim) {}

    private record Pixel(int x, int y) {}

    private VerifiedHandheldGrip() {}

    /** Canonical right-hand geometry within its measured gap bounds; the caller mirrors the complete left-main solve. */
    public static WeaponRig.GripAttachment forPlayer(AbstractClientPlayer player, double gripGap) {
        if (!Double.isFinite(gripGap) || gripGap <= 0) return null;
        Minecraft mc = Minecraft.getInstance();
        var renderer = mc.getEntityRenderDispatcher().getRenderer(player);
        if (renderer.getClass() != PlayerRenderer.class
                || ((PlayerRenderer) renderer).getModel().getClass() != PlayerModel.class) return null;
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return null;
        long current = AnimationLibrary.INSTANCE.generation();
        if (current != generation) {
            DESCRIPTORS.clear();
            RESOURCES.clear();
            MODELS.clear();
            generation = current;
        }
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(stack.getItem());
        Optional<Calibration> descriptor = DESCRIPTORS.computeIfAbsent(item, id -> load(mc.getResourceManager(), id));
        if (descriptor.isEmpty()) return null;
        Calibration calibration = descriptor.get();
        // Bounds describe measured contacts with this mesh, not the wider mathematical reach of the socket solver.
        // No tolerance expands that scope: a single-value calibration only supports that exact gap.
        if (gripGap < calibration.minimumGap() || gripGap > calibration.maximumGap()) return null;
        BakedModel model = mc.getItemRenderer().getModel(stack, player.level(), player, player.getId());
        Optional<Calibration> verified = MODELS.computeIfAbsent(model, ignored -> new HashMap<>())
                .computeIfAbsent(item, ignored -> verify(model, stack, calibration));
        if (verified.isEmpty()) return null;
        Calibration value = verified.get();
        return player.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM ? value.slim() : value.wide();
    }

    private static Optional<Calibration> load(ResourceManager manager, ResourceLocation item) {
        // Keep vanilla descriptors short; namespace other items to avoid collisions between mods.
        String name = item.getNamespace().equals("minecraft") ? item.getPath() : item.getNamespace() + "/" + item.getPath();
        ResourceLocation location = SteelClash.id(FOLDER + name + ".json");
        if (manager.getResource(location).isEmpty()) return Optional.empty();
        try {
            JsonObject json = JsonParser.parseString(new String(resource(manager, location), StandardCharsets.UTF_8)).getAsJsonObject();
            if (json.get("schema").getAsInt() != 2 || !id(json.get("item")).equals(item)) throw new IllegalArgumentException("item/schema");
            List<ResourceCheck> models = new ArrayList<>();
            for (JsonElement element : json.getAsJsonArray("models")) models.add(check(element.getAsJsonObject()));
            if (models.isEmpty() || models.size() > 16) throw new IllegalArgumentException("model chain");
            ResourceLocation first = ResourceLocation.fromNamespaceAndPath(item.getNamespace(), "models/item/" + item.getPath() + ".json");
            if (!models.getFirst().id().equals(first)) throw new IllegalArgumentException("item model resource");
            ResourceCheck texture = check(json.getAsJsonObject("texture"));
            ResourceLocation sprite = id(json.getAsJsonObject("texture").get("sprite"));
            if (!texture.id().equals(ResourceLocation.fromNamespaceAndPath(sprite.getNamespace(), "textures/" + sprite.getPath() + ".png")))
                throw new IllegalArgumentException("sprite texture resource");
            for (ResourceCheck check : models) requireHash(manager, check);
            requireHash(manager, texture);
            List<Pixel> gripPixels = pixels(json.getAsJsonArray("grip_texels"));
            if (!gripPixels.equals(verifiedGripPixels(asset(manager, texture.id()).bytes()))
                    || gripPixels.size() > 256) throw new IllegalArgumentException("grip texels do not match opaque brown/outline/pommel pixels");
            requireGeneratedChain(manager, models);
            JsonObject display = json.getAsJsonObject("handheld_display");
            Vec right = vector(display.getAsJsonArray("right_rotation"));
            Vec left = vector(display.getAsJsonArray("left_rotation"));
            Vec translation = vector(display.getAsJsonArray("translation_pixels"));
            Vec scale = vector(display.getAsJsonArray("scale"));
            if (scale.x() <= 0 || scale.y() <= 0 || scale.z() <= 0 || Math.max(scale.x(), Math.max(scale.y(), scale.z())) > 4)
                throw new IllegalArgumentException("display scale");
            Mat3 rotation = displayRotation(right, false);
            if (rotation.distance(displayRotation(left, true)) > EPSILON) throw new IllegalArgumentException("handed display rotations");
            Vec anchor = vector(json.getAsJsonArray("grip_anchor_pixels"));
            if (anchor.x() < 0 || anchor.x() > 16 || anchor.y() < 0 || anchor.y() > 16 || anchor.z() != 8)
                throw new IllegalArgumentException("flat sprite anchor");
            Vec shaft = vector(json.getAsJsonArray("shaft_axis"));
            if (shaft.length() < EPSILON) throw new IllegalArgumentException("shaft axis");
            JsonObject gap = json.getAsJsonObject("grip_gap_pixels");
            double minimumGap = gap.get("min").getAsDouble();
            double maximumGap = gap.get("max").getAsDouble();
            if (!Double.isFinite(minimumGap) || !Double.isFinite(maximumGap)
                    || minimumGap <= 0 || maximumGap < minimumGap)
                throw new IllegalArgumentException("grip gap bounds");
            Vec local = new Vec((anchor.x() - 8) * scale.x(), (anchor.y() - 8) * scale.y(), (anchor.z() - 8) * scale.z());
            Vec itemGrip = translation.add(rotation.apply(local));
            Vec itemAxis = rotation.apply(new Vec(shaft.x() * scale.x(), shaft.y() * scale.y(), shaft.z() * scale.z()));
            itemAxis = itemAxis.scale(1 / itemAxis.length());
            Vec leftGrip = new Vec(-translation.x(), translation.y(), translation.z()).add(rotation.apply(local));
            if (leftGrip.subtract(new Vec(-itemGrip.x(), itemGrip.y(), itemGrip.z())).length() > EPSILON
                    || Math.abs(itemAxis.x()) > EPSILON)
                throw new IllegalArgumentException("attachment does not mirror across the vanilla hand plane");
            JsonObject mesh = json.getAsJsonObject("mesh");
            int count = mesh.get("quad_count").getAsInt();
            if (count < 6 || count > 256) throw new IllegalArgumentException("quad count");
            String geometryHash = hash(mesh.get("position_sha256"));
            JsonObject rootShift = json.getAsJsonObject("physical_root_shift_pixels");
            Vec wideRootShift = vector(rootShift.getAsJsonArray("wide"));
            Vec slimRootShift = vector(rootShift.getAsJsonArray("slim"));
            if (Math.abs(wideRootShift.x()) > 2 || Math.abs(wideRootShift.y()) > EPSILON || Math.abs(wideRootShift.z()) > EPSILON
                    || Math.abs(slimRootShift.x()) > 2 || Math.abs(slimRootShift.y()) > EPSILON || Math.abs(slimRootShift.z()) > EPSILON)
                throw new IllegalArgumentException("physical root shift must be a bounded X-only pivot offset");
            GripContact contact = gripContact(gripPixels, rotation, translation, scale);
            return Optional.of(new Calibration(sprite, right, left, translation, scale, count, geometryHash, minimumGap, maximumGap,
                    contact, wideRootShift, slimRootShift,
                    new WeaponRig.GripAttachment(VANILLA_SOCKET, itemGrip, itemAxis, Vec.ZERO, wideRootShift, false, contact),
                    new WeaponRig.GripAttachment(VANILLA_SOCKET, itemGrip, itemAxis, slimRootShift, slimRootShift, true, contact)));
        } catch (Exception exception) {
            SteelClash.LOGGER.warn("Skipping unverified Steel Clash grip descriptor {}: {}", location, exception.toString());
            return Optional.empty();
        }
    }

    /** Every parent in the actual model chain must be calibrated; only builtin/generated terminates it. */
    private static void requireGeneratedChain(ResourceManager manager, List<ResourceCheck> checks) throws IOException {
        Set<ResourceLocation> calibrated = new HashSet<>();
        for (ResourceCheck check : checks) if (!calibrated.add(check.id())) throw new IllegalArgumentException("duplicate model resource");
        Set<ResourceLocation> visited = new HashSet<>();
        ResourceLocation cursor = checks.getFirst().id();
        while (visited.add(cursor)) {
            if (!calibrated.contains(cursor)) throw new IllegalArgumentException("uncalibrated model parent");
            JsonObject json = JsonParser.parseString(new String(resource(manager, cursor), StandardCharsets.UTF_8)).getAsJsonObject();
            if (json.has("loader") || json.has("elements") || (json.has("overrides") && !json.getAsJsonArray("overrides").isEmpty()))
                throw new IllegalArgumentException("custom model structure");
            ResourceLocation parent = id(json.get("parent"));
            if (parent.equals(ResourceLocation.withDefaultNamespace("builtin/generated"))) {
                if (!visited.equals(calibrated)) throw new IllegalArgumentException("unused model hashes");
                return;
            }
            cursor = ResourceLocation.fromNamespaceAndPath(parent.getNamespace(), "models/" + parent.getPath() + ".json");
        }
        throw new IllegalArgumentException("cyclic model chain");
    }

    private static Optional<Calibration> verify(BakedModel model, ItemStack stack, Calibration value) {
        try {
            if (model.getClass() != SimpleBakedModel.class || model.isCustomRenderer() || model.isGui3d()
                    || !model.getOverrides().getOverrides().isEmpty()) return Optional.empty();
            for (boolean fabulous : new boolean[]{false, true}) {
                List<BakedModel> passes = model.getRenderPasses(stack, fabulous);
                if (passes.size() != 1 || passes.getFirst() != model) return Optional.empty();
            }
            if (!displayMatches(model, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, value.rightRotation(), value)
                    || !displayMatches(model, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, true, value.leftRotation(), value))
                return Optional.empty();
            if (!model.getParticleIcon().contents().name().equals(value.sprite())
                    || model.getParticleIcon().contents().width() != 16 || model.getParticleIcon().contents().height() != 16)
                return Optional.empty();
            RandomSource random = RandomSource.create(0);
            for (Direction side : Direction.values()) if (!model.getQuads(null, side, random).isEmpty()) return Optional.empty();
            List<BakedQuad> quads = model.getQuads(null, null, random);
            if (quads.size() != value.quadCount()) return Optional.empty();
            List<String> signatures = new ArrayList<>();
            for (BakedQuad quad : quads) {
                if (!quad.getSprite().contents().name().equals(value.sprite())) return Optional.empty();
                int[] vertices = quad.getVertices();
                if (vertices.length != 32) return Optional.empty();
                StringBuilder signature = new StringBuilder(quad.getDirection().name()).append(':');
                for (int vertex = 0; vertex < 4; vertex++) {
                    if (vertex > 0) signature.append(';');
                    for (int axis = 0; axis < 3; axis++) {
                        int bits = vertices[vertex * 8 + axis];
                        if (!Float.isFinite(Float.intBitsToFloat(bits))) return Optional.empty();
                        if (axis > 0) signature.append(',');
                        signature.append(HexFormat.of().toHexDigits(bits));
                    }
                }
                signatures.add(signature.toString());
            }
            signatures.sort(String::compareTo);
            String actual = sha256((String.join("\n", signatures) + "\n").getBytes(StandardCharsets.UTF_8));
            if (!actual.equals(value.geometryHash())) return Optional.empty();
            // This path runs only on a cache miss: native captures can prove the calibrated geometry was active.
            SteelClash.LOGGER.info("Verified Steel Clash physical grip for {} at animation generation {}: {} quads, mesh {}",
                    value.sprite(), generation, quads.size(), actual);
            return Optional.of(value);
        } catch (RuntimeException exception) {
            SteelClash.LOGGER.debug("Skipping unverified baked handheld grip model: {}", exception.toString());
            return Optional.empty();
        }
    }

    private static boolean displayMatches(BakedModel model, ItemDisplayContext context, boolean left, Vec expectedRotation, Calibration value) {
        ItemTransform transform = model.getTransforms().getTransform(context);
        if (!near(transform.rotation.x(), expectedRotation.x()) || !near(transform.rotation.y(), expectedRotation.y())
                || !near(transform.rotation.z(), expectedRotation.z()) || transform.rightRotation.lengthSquared() != 0
                || !near(transform.translation.x() * 16, value.translation().x())
                || !near(transform.translation.y() * 16, value.translation().y())
                || !near(transform.translation.z() * 16, value.translation().z())
                || !near(transform.scale.x(), value.scale().x()) || !near(transform.scale.y(), value.scale().y())
                || !near(transform.scale.z(), value.scale().z())) return false;
        PoseStack actual = new PoseStack();
        if (model.applyTransform(context, actual, left) != model) return false;
        Mat3 rotation = displayRotation(expectedRotation, left);
        Matrix4f expected = new Matrix4f().translation((float)((left ? -1 : 1) * value.translation().x() / 16),
                (float)(value.translation().y() / 16), (float)(value.translation().z() / 16));
        Matrix4f basis = new Matrix4f().m00((float)rotation.m00()).m10((float)rotation.m01()).m20((float)rotation.m02())
                .m01((float)rotation.m10()).m11((float)rotation.m11()).m21((float)rotation.m12())
                .m02((float)rotation.m20()).m12((float)rotation.m21()).m22((float)rotation.m22());
        expected.mul(basis).scale((float)value.scale().x(), (float)value.scale().y(), (float)value.scale().z());
        return actual.last().pose().equals(expected, (float) EPSILON);
    }

    private static Mat3 displayRotation(Vec degrees, boolean left) {
        return Mat3.rotX(Math.toRadians(degrees.x())).mul(Mat3.rotY(Math.toRadians(degrees.y() * (left ? -1 : 1))))
                .mul(Mat3.rotZ(Math.toRadians(degrees.z() * (left ? -1 : 1))));
    }

    private static boolean near(double a, double b) { return Double.isFinite(a) && Math.abs(a - b) <= EPSILON; }

    private static Vec vector(JsonArray values) {
        if (values.size() != 3) throw new IllegalArgumentException("expected three vector components");
        Vec result = new Vec(values.get(0).getAsDouble(), values.get(1).getAsDouble(), values.get(2).getAsDouble());
        if (!Double.isFinite(result.x()) || !Double.isFinite(result.y()) || !Double.isFinite(result.z()))
            throw new IllegalArgumentException("non-finite vector");
        return result;
    }

    private static List<Pixel> pixels(JsonArray values) {
        if (values == null || values.isEmpty() || values.size() > 256) throw new IllegalArgumentException("grip texel count");
        List<Pixel> result = new ArrayList<>(values.size());
        Set<Pixel> unique = new HashSet<>();
        for (JsonElement element : values) {
            JsonArray pair = element.getAsJsonArray();
            if (pair.size() != 2) throw new IllegalArgumentException("grip texel coordinate");
            int x = pair.get(0).getAsInt(), y = pair.get(1).getAsInt();
            Pixel pixel = new Pixel(x, y);
            if (x < 0 || x >= 16 || y < 0 || y >= 16 || !unique.add(pixel))
                throw new IllegalArgumentException("grip texels must be unique in-bounds pixels");
            result.add(pixel);
        }
        result.sort(java.util.Comparator.comparingInt(Pixel::y).thenComparingInt(Pixel::x));
        return List.copyOf(result);
    }

    /** Reproduce the captured analyzer's brown + opaque outline + pommel contact set from the hash-checked PNG. */
    private static List<Pixel> verifiedGripPixels(byte[] png) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        if (image == null || image.getWidth() != 16 || image.getHeight() != 16)
            throw new IllegalArgumentException("grip texture must be a decoded 16x16 PNG");
        Set<Pixel> brown = new HashSet<>();
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int color = image.getRGB(x, y), alpha = color >>> 24, red = color >>> 16 & 255;
            int green = color >>> 8 & 255, blue = color & 255;
            if (alpha > 0 && red > 2 * blue && green > blue) brown.add(new Pixel(x, y));
        }
        Set<Pixel> result = new HashSet<>(brown);
        for (Pixel pixel : brown) for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
            int x = pixel.x() + dx, y = pixel.y() + dy;
            if (x >= 0 && x < 16 && y >= 0 && y < 16 && (image.getRGB(x, y) >>> 24) > 0) result.add(new Pixel(x, y));
        }
        for (int y = 11; y < 16; y++) for (int x = 0; x < 5; x++)
            if ((image.getRGB(x, y) >>> 24) > 0) result.add(new Pixel(x, y));
        return result.stream().sorted(java.util.Comparator.comparingInt(Pixel::y).thenComparingInt(Pixel::x)).toList();
    }

    private static GripContact gripContact(List<Pixel> pixels, Mat3 rotation, Vec translation, Vec scale) {
        List<GripContact.Prism> prisms = new ArrayList<>(pixels.size());
        Vec halfX = rotation.apply(new Vec(.5 * scale.x(), 0, 0));
        Vec halfY = rotation.apply(new Vec(0, .5 * scale.y(), 0));
        Vec halfZ = rotation.apply(new Vec(0, 0, .5 * scale.z()));
        for (Pixel pixel : pixels) {
            Vec local = new Vec(pixel.x() - 7.5, 7.5 - pixel.y(), 0);
            Vec scaled = new Vec(local.x() * scale.x(), local.y() * scale.y(), local.z() * scale.z());
            prisms.add(new GripContact.Prism(translation.add(rotation.apply(scaled)), halfX, halfY, halfZ));
        }
        return new GripContact(prisms);
    }

    private static ResourceCheck check(JsonObject json) { return new ResourceCheck(id(json.get("resource")), hash(json.get("sha256"))); }
    private static ResourceLocation id(JsonElement json) { return ResourceLocation.parse(json.getAsString()); }
    private static String hash(JsonElement json) {
        String result = json.getAsString();
        if (!result.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("SHA256");
        return result;
    }

    private static void requireHash(ResourceManager manager, ResourceCheck check) throws IOException {
        if (!asset(manager, check.id()).hash().equals(check.hash())) throw new IllegalArgumentException("resource changed: " + check.id());
    }

    private static byte[] resource(ResourceManager manager, ResourceLocation id) throws IOException { return asset(manager, id).bytes(); }

    private static ResourceAsset asset(ResourceManager manager, ResourceLocation id) throws IOException {
        Optional<ResourceAsset> result = RESOURCES.get(id);
        if (result == null) {
            var resource = manager.getResource(id);
            if (resource.isEmpty()) result = Optional.empty();
            else try (var stream = resource.get().open()) {
                byte[] read = stream.readNBytes(MAX_RESOURCE_BYTES + 1);
                if (read.length > MAX_RESOURCE_BYTES) throw new IOException("Grip resource too large: " + id);
                result = Optional.of(new ResourceAsset(read, sha256(read)));
            }
            RESOURCES.put(id, result);
        }
        return result.orElseThrow(() -> new IOException("Missing grip resource: " + id));
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}

package com.steelclash.compat;

import com.steelclash.SteelClash;
import com.steelclash.client.ProceduralSwingAnimation;
import com.steelclash.core.ItemRotationConvention;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import com.zigythebird.playeranim.accessors.IAnimatedPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Client-call-only capability selector for the reviewed optional item-axis backend. */
public final class PalMoreRotationCompat {
    private static final String MOD_ID = "playeranimationlibrarymorerotation";
    private static final boolean SUPPORTED = Boolean.getBoolean("steelclash.palMoreRotationPrototype") && resolve();
    private static final boolean BEND_PROBE = Boolean.getBoolean("steelclash.palMoreRotationProbe");
    private record ItemPose(LivingEntity entity, HumanoidArm arm, PoseStack stack, Matrix4f pose, Matrix3f normal) {}
    private static final ThreadLocal<ItemPose> ITEM_POSE = new ThreadLocal<>();
    private record ItemBones(PlayerAnimBone left, PlayerAnimBone right) {}
    private static final ThreadLocal<ItemBones> ITEM_BONES = ThreadLocal.withInitial(() ->
            new ItemBones(new PlayerAnimBone("left_item"), new PlayerAnimBone("right_item")));
    private static int debugItems;

    private PalMoreRotationCompat() {}

    public static ItemRotationConvention itemConvention() {
        return SUPPORTED ? ItemRotationConvention.STANDARD : ItemRotationConvention.PAL_SWAPPED;
    }

    public static boolean hasBendingBackend() { return SUPPORTED && ModList.get().isLoaded("bendable_cuboids"); }
    public static boolean bendProbeEnabled() { return BEND_PROBE && hasBendingBackend(); }
    public static boolean isSupported() { return SUPPORTED; }

    public static void beginItemFix(LivingEntity entity, HumanoidArm arm, PoseStack stack) {
        ITEM_POSE.remove();
        boolean owned = SUPPORTED && ProceduralSwingAnimation.ownsItemPose(entity);
        if (Boolean.getBoolean("mixin.debug.export") && entity instanceof net.minecraft.client.player.AbstractClientPlayer && debugItems++ < 8)
            SteelClash.LOGGER.info("PAL prototype item capture arm={} owned={} {}", arm, owned,
                    ProceduralSwingAnimation.prototypeLayerState(entity));
        if (!owned) return;
        ITEM_POSE.set(new ItemPose(entity, arm, stack, new Matrix4f(stack.last().pose()), new Matrix3f(stack.last().normal())));
    }

    public static void finishItemFix(LivingEntity entity, HumanoidArm arm, PoseStack stack) {
        ItemPose saved = ITEM_POSE.get();
        ITEM_POSE.remove();
        if (saved == null || saved.entity != entity || saved.arm != arm || saved.stack != stack) return;
        if (!(entity instanceof IAnimatedPlayer animated) || animated.playerAnimLib$getAnimManager() == null) return;
        ItemBones bones = ITEM_BONES.get();
        PlayerAnimBone bone = arm == HumanoidArm.LEFT ? bones.left : bones.right;
        bone.setToInitialPose();
        animated.playerAnimLib$getAnimManager().get3DTransform(bone);
        if (Boolean.getBoolean("mixin.debug.export") && debugItems <= 8)
            SteelClash.LOGGER.info("PAL prototype item apply arm={} channels=({},{},{})", arm, bone.getRotX(), bone.getRotY(), bone.getRotZ());
        // The reviewed runtime calls the addon's undo/reapply before PAL's original item rotation.
        // Keep the pre-callback hand/bend transform and apply the same-frame PAL sample once in standard axes.
        stack.last().pose().set(saved.pose);
        stack.last().normal().set(saved.normal);
        stack.mulPose(Axis.ZP.rotation(-bone.getRotZ()));
        stack.mulPose(Axis.YP.rotation(-bone.getRotY()));
        stack.mulPose(Axis.XP.rotation(-bone.getRotX()));
        stack.scale(bone.getScaleX(), bone.getScaleY(), bone.getScaleZ());
    }

    private static boolean resolve() {
        var container = ModList.get().getModContainerById(MOD_ID);
        if (container.isEmpty()) return false;
        String version = container.get().getModInfo().getVersion().toString();
        if (!version.equals("1.1.0") && !version.startsWith("1.1.0+")) {
            SteelClash.LOGGER.warn("PAL More Rotation {} is outside the reviewed 1.1.0 prototype; compatibility is unverified", version);
            return false;
        }
        try {
            Class<?> holder = Class.forName("com.kltyton.playeranimationlibrarymorerotation.compat.PalMoreBendHolder", false,
                    PalMoreRotationCompat.class.getClassLoader());
            holder.getMethod("palMore$setBend", float.class, float.class, float.class);
            SteelClash.LOGGER.info("Reviewed PAL More Rotation item-axis adapter active (version {})", version);
            return true;
        } catch (ReflectiveOperationException | LinkageError error) {
            SteelClash.LOGGER.warn("PAL More Rotation capability check failed; retaining the default PAL convention", error);
            return false;
        }
    }
}

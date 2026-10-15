package com.steelclash.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.steelclash.Config;
import com.steelclash.SteelClash;
import com.steelclash.combat.ModAttachments;
import com.steelclash.core.HitLocation;
import com.steelclash.core.HitReaction;
import com.steelclash.core.Phase;
import com.steelclash.net.HitReactionPayload;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

/**
 * Bodies reacting to where a blade met them ({@link HitReactionPayload}): blood sprays the way the blade travelled, the
 * struck part is thrown that way and the body tips with it; a killed body falls along the blow; a severed head flies
 * off; a parried blade rebounds from where it met the guard ({@link #bounce}). Presentation only. Render thread.
 */
@EventBusSubscriber(modid = SteelClash.MOD_ID, value = Dist.CLIENT)
public final class HitReactions {
    /** Ticks a severed head stays before it's gone; it shrinks away over the last {@link #HEAD_FADE_TICKS}. */
    private static final int HEAD_LIFE_TICKS = 60;
    private static final int HEAD_FADE_TICKS = 10;
    /** Seconds a bounce stays eligible to be shown, should the stagger arrive late. */
    private static final double BOUNCE_SECONDS = 1.5;

    private record Blow(long at, HitReaction reaction) {
    }

    /** A parried blade waiting for (or showing in) the stagger of the attack it belongs to. */
    private static final class Bounce {
        final int serial;
        final double progress;
        final long at;
        boolean shown;

        Bounce(int serial, double progress, long at) {
            this.serial = serial;
            this.progress = progress;
            this.at = at;
        }
    }

    private static final Map<LivingEntity, Blow> BLOWS = new WeakHashMap<>();
    /** Horizontal direction a killing blow travelled, for the death fall. */
    private static final Map<LivingEntity, double[]> FALLS = new WeakHashMap<>();
    private static final Map<LivingEntity, Boolean> HEADLESS = new WeakHashMap<>();
    private static final Map<LivingEntity, Bounce> BOUNCES = new WeakHashMap<>();
    /** Model parts hidden for the body being drawn; shown again after it, as models are shared between bodies. */
    private static final List<ModelPart> HIDDEN = new ArrayList<>();
    private static final List<FlyingHead> HEADS = new ArrayList<>();
    /** The level the flying heads are in: a new level (another dimension, another world) clears them. */
    @Nullable
    private static Level headsLevel;
    @Nullable
    private static ItemParticleOption blood;

    private HitReactions() {
    }

    public static void on(HitReactionPayload payload) {
        Level level = Minecraft.getInstance().level;
        if (level == null || !(level.getEntity(payload.entity()) instanceof LivingEntity body)) {
            return;
        }
        long now = System.nanoTime();
        if (payload.kind() == HitReactionPayload.Kind.BOUNCE) {
            Bounce current = BOUNCES.get(body);
            if (current != null && (now - current.at) / 1e9 <= BOUNCE_SECONDS
                    && payload.serial() != current.serial && payload.serial() - current.serial < 0) {
                // A delayed bounce from an older attack must not replace the newer attack's pending recoil.
                return;
            }
            BOUNCES.put(body, new Bounce(payload.serial(), payload.progress(), now));
            return;
        }
        spray(level, payload);
        HitLocation.Region region = HitLocation.Region.values()[Math.floorMod(payload.region(), HitLocation.Region.values().length)];
        double forward = HitLocation.forward(payload.dx(), payload.dz(), body.yBodyRot);
        double left = HitLocation.left(payload.dx(), payload.dz(), body.yBodyRot);
        BLOWS.put(body, new Blow(now, HitReaction.of(region, forward, left, -payload.dy(), payload.strength())));
        if (payload.kind() != HitReactionPayload.Kind.HIT) {
            FALLS.put(body, new double[]{payload.dx(), payload.dz()});
        }
        if (payload.kind() == HitReactionPayload.Kind.SEVER) {
            HEADLESS.put(body, Boolean.TRUE);
            FlyingHead head = FlyingHead.of(body, new Vec3(payload.dx(), 0, payload.dz()));
            if (head != null) {
                if (level != headsLevel) {
                    HEADS.clear();
                    headsLevel = level;
                }
                HEADS.add(head);
            }
        }
    }

    /**
     * The release progress at which this fighter's blade was parried, if the stagger now showing belongs to the attack
     * the server parried, or NaN. The stagger pose rebounds along the arc from there.
     */
    public static double bounce(LivingEntity entity, int attackSerial) {
        Bounce bounce = BOUNCES.get(entity);
        if (bounce == null || bounce.serial != attackSerial || (System.nanoTime() - bounce.at) / 1e9 > BOUNCE_SECONDS) {
            return Double.NaN;
        }
        bounce.shown = true;
        return bounce.progress;
    }

    /** After the model's own pose (and Steel Clash's combat pose): throw the struck part, hide a lost head. */
    public static void applyParts(LivingEntity entity, EntityModel<?> model) {
        if (HEADLESS.containsKey(entity)) {
            if (model instanceof HumanoidModel<?> humanoid) {
                hide(humanoid.head);
                hide(humanoid.hat); // a sibling of the head in these models
            } else if (model instanceof IllagerModel<?> illager) {
                hide(illager.getHead()); // its hat is a child of the head and goes with it
            }
        }
        double strength = Config.Client.HIT_REACTIONS.get();
        Blow blow = BLOWS.get(entity);
        if (blow == null || strength <= 0 || ownFirstPerson(entity)) {
            return;
        }
        double envelope = HitReaction.envelope((System.nanoTime() - blow.at()) / 1e9);
        if (envelope <= 0) {
            BLOWS.remove(entity);
            return;
        }
        double scale = envelope * strength;
        if (model instanceof HumanoidModel<?> humanoid) {
            throwParts(blow.reaction(), scale, humanoid.head, humanoid.rightArm, humanoid.leftArm, humanoid.rightLeg, humanoid.leftLeg);
            // The outer layers copy their parts at the end of setupAnim, which has already run.
            humanoid.hat.copyFrom(humanoid.head);
            if (model instanceof PlayerModel<?> player) {
                player.rightSleeve.copyFrom(player.rightArm);
                player.leftSleeve.copyFrom(player.leftArm);
                player.rightPants.copyFrom(player.rightLeg);
                player.leftPants.copyFrom(player.leftLeg);
            }
        } else if (model instanceof IllagerModel<?> illager) {
            throwParts(blow.reaction(), scale, illager.getHead(), illager.rightArm, illager.leftArm, illager.rightLeg, illager.leftLeg);
        }
    }

    /**
     * Right after vanilla turns the body to its yaw (and tips a dying one onto its side): a killed body falls along the
     * blow instead, and a struck one tips the way the blade went.
     *
     * @param flipDegrees how far vanilla turns this kind of body over as it dies
     */
    public static void applyBodyRotation(LivingEntity entity, PoseStack poseStack, float yBodyRot, float partialTick,
                                         float flipDegrees) {
        if (ownFirstPerson(entity)) {
            return;
        }
        double[] fall = FALLS.get(entity);
        if (entity.deathTime > 0 && fall != null && Config.Client.DEATH_FALL_FOLLOWS_BLOW.get()) {
            // Vanilla's own fall, which we undo: sqrt of 1.6 × the death time in seconds, capped at all the way over.
            float over = Math.min(1f, (float) Math.sqrt((entity.deathTime + partialTick - 1f) / 20f * 1.6f));
            double[] axis = HitReaction.tiltAxis(HitLocation.forward(fall[0], fall[1], yBodyRot),
                    HitLocation.left(fall[0], fall[1], yBodyRot));
            if (axis != null) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(-over * flipDegrees));
                poseStack.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(over * flipDegrees),
                        (float) axis[0], (float) axis[1], (float) axis[2]));
            }
            return;
        }
        Blow blow = BLOWS.get(entity);
        double strength = Config.Client.HIT_REACTIONS.get();
        if (blow == null || strength <= 0 || !entity.isAlive()) {
            return;
        }
        double tilt = blow.reaction().tilt() * strength * HitReaction.envelope((System.nanoTime() - blow.at()) / 1e9);
        double[] axis = HitReaction.tiltAxis(blow.reaction().forward(), blow.reaction().left());
        if (tilt <= 0 || axis == null) {
            return;
        }
        // Tip about the hips, so the feet stay put.
        float hips = entity.getBbHeight() / Math.max(1e-3f, entity.getScale()) * 0.45f;
        poseStack.translate(0, hips, 0);
        poseStack.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(tilt), (float) axis[0], (float) axis[1], (float) axis[2]));
        poseStack.translate(0, -hips, 0);
    }

    /** Your own body in first person: the camera shake already says you were hit. */
    public static boolean ownFirstPerson(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        return entity == mc.player && mc.options.getCameraType().isFirstPerson();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void afterBody(RenderLivingEvent.Post<?, ?> event) {
        for (ModelPart part : HIDDEN) {
            part.visible = true;
        }
        HIDDEN.clear();
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level != headsLevel) {
            HEADS.clear();
            headsLevel = level;
        }
        if (level == null || mc.isPaused()) {
            return;
        }
        for (Iterator<FlyingHead> it = HEADS.iterator(); it.hasNext(); ) {
            if (it.next().tick(level)) {
                it.remove();
            }
        }
        // A bounce belongs to one stagger: once that has shown and ended, a later stagger of the same attack (a kick
        // straight after, say) doesn't rebound again.
        long now = System.nanoTime();
        BOUNCES.entrySet().removeIf(entry -> (now - entry.getValue().at) / 1e9 > BOUNCE_SECONDS
                || entry.getValue().shown && entry.getKey().hasData(ModAttachments.COMBAT)
                && entry.getKey().getData(ModAttachments.COMBAT).machine.phase() != Phase.STAGGER);
    }

    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || HEADS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        List<RenderType> drawn = new ArrayList<>(2);
        for (FlyingHead head : HEADS) {
            if (head.render(mc, event.getPoseStack(), buffers, camera, partialTick) && !drawn.contains(head.renderType)) {
                drawn.add(head.renderType);
            }
        }
        // Only what the heads drew: other pending batches (translucent, glint) keep their place in the frame.
        for (RenderType type : drawn) {
            buffers.endBatch(type);
        }
    }

    /** Blood thrown from the wound the way the blade went (the server sends only a puff). */
    private static void spray(Level level, HitReactionPayload payload) {
        if (payload.spray() <= 0) {
            return;
        }
        if (blood == null) {
            blood = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.RED_DYE));
        }
        RandomSource random = level.random;
        for (int i = 0; i < payload.spray(); i++) {
            double speed = 0.12 + random.nextDouble() * 0.12;
            level.addParticle(blood, payload.x(), payload.y(), payload.z(),
                    payload.dx() * speed + (random.nextDouble() - 0.5) * 0.08,
                    payload.dy() * speed + 0.04 + random.nextDouble() * 0.06,
                    payload.dz() * speed + (random.nextDouble() - 0.5) * 0.08);
        }
    }

    private static void throwParts(HitReaction reaction, double scale, ModelPart head, ModelPart rightArm, ModelPart leftArm,
                                   ModelPart rightLeg, ModelPart leftLeg) {
        add(head, reaction.parts().get("head"), scale);
        add(rightArm, reaction.parts().get("rightArm"), scale);
        add(leftArm, reaction.parts().get("leftArm"), scale);
        add(rightLeg, reaction.parts().get("rightLeg"), scale);
        add(leftLeg, reaction.parts().get("leftLeg"), scale);
    }

    private static void add(ModelPart part, @Nullable double[] degrees, double scale) {
        if (degrees != null) {
            part.xRot += (float) Math.toRadians(degrees[0] * scale);
            part.yRot += (float) Math.toRadians(degrees[1] * scale);
            part.zRot += (float) Math.toRadians(degrees[2] * scale);
        }
    }

    private static void hide(ModelPart part) {
        if (part.visible) {
            part.visible = false;
            HIDDEN.add(part);
        }
    }

    /**
     * A severed head: the body's own head part with its own texture (a husk's head is a husk's, a player's wears their
     * skin), thrown along the blow and tumbling until it settles and shrinks away.
     */
    private static final class FlyingHead {
        private final ModelPart head;
        /** The outer head layer, a separate part in humanoid models; null when there is none to draw. */
        @Nullable
        private final ModelPart hat;
        final RenderType renderType;
        private final float yaw;
        private final float scale;
        private Vec3 position;
        private Vec3 previous;
        private Vec3 velocity;
        private float spin;
        private float previousSpin;
        private int age;
        private boolean resting;

        private FlyingHead(ModelPart head, @Nullable ModelPart hat, RenderType renderType, Vec3 position, Vec3 velocity,
                           float yaw, float scale) {
            this.head = head;
            this.hat = hat;
            this.renderType = renderType;
            this.position = position;
            this.previous = position;
            this.velocity = velocity;
            this.yaw = yaw;
            this.scale = scale;
        }

        /** The head of {@code body}'s model, or null when its renderer draws no humanoid or illager head. */
        @Nullable
        @SuppressWarnings("unchecked")
        static FlyingHead of(LivingEntity body, Vec3 along) {
            EntityRenderer<? super LivingEntity> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(body);
            if (!(renderer instanceof LivingEntityRenderer<?, ?> living)) {
                return null;
            }
            EntityModel<?> model = living.getModel();
            ModelPart head;
            ModelPart hat = null;
            if (model instanceof HumanoidModel<?> humanoid) {
                head = humanoid.head;
                boolean hatShown = body instanceof Player player ? player.isModelPartShown(PlayerModelPart.HAT) : humanoid.hat.visible;
                hat = hatShown ? humanoid.hat : null;
            } else if (model instanceof IllagerModel<?> illager) {
                head = illager.getHead();
            } else {
                return null;
            }
            RenderType type = RenderType.entityCutoutNoCull(((EntityRenderer<LivingEntity>) renderer).getTextureLocation(body));
            Vec3 velocity = (along.lengthSqr() < 1e-6 ? Vec3.ZERO : along.normalize().scale(0.25)).add(0, 0.3, 0);
            return new FlyingHead(head, hat, type, new Vec3(body.getX(), body.getEyeY(), body.getZ()), velocity,
                    body.getYHeadRot(), body.getScale());
        }

        /** @return whether it's gone */
        boolean tick(Level level) {
            age++;
            previous = position;
            previousSpin = spin;
            if (!resting) {
                velocity = velocity.add(0, -0.04, 0).scale(0.98);
                Vec3 next = position.add(velocity);
                BlockPos below = BlockPos.containing(next.x, next.y - 0.25 * scale, next.z);
                VoxelShape floor = level.getBlockState(below).getCollisionShape(level, below);
                if (!floor.isEmpty() && velocity.y < 0) {
                    next = new Vec3(next.x, Math.max(next.y, below.getY() + floor.max(Direction.Axis.Y) + 0.25 * scale), next.z);
                    velocity = new Vec3(velocity.x * 0.4, -velocity.y * 0.25, velocity.z * 0.4);
                    resting = velocity.lengthSqr() < 0.002;
                }
                position = next;
                spin += (float) (velocity.horizontalDistance() * 60);
            }
            return age >= HEAD_LIFE_TICKS;
        }

        /** @return whether anything was drawn */
        boolean render(Minecraft mc, PoseStack poseStack, MultiBufferSource buffers, Vec3 camera, float partialTick) {
            float size = Math.min(1f, (HEAD_LIFE_TICKS - age - partialTick) / HEAD_FADE_TICKS) * scale;
            if (size <= 0) {
                return false;
            }
            Vec3 at = previous.lerp(position, partialTick);
            poseStack.pushPose();
            poseStack.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(180 - yaw));
            poseStack.mulPose(Axis.XP.rotationDegrees(previousSpin + (spin - previousSpin) * partialTick));
            poseStack.scale(size, size, size);
            // Model space is upside down (renderers flip it) and the head sits on its neck pivot: centre it on the spin.
            poseStack.scale(-1, -1, 1);
            poseStack.translate(0, 0.25, 0);
            int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(at));
            var consumer = buffers.getBuffer(renderType);
            draw(head, poseStack, consumer, light);
            if (hat != null) {
                draw(hat, poseStack, consumer, light);
            }
            poseStack.popPose();
            return true;
        }

        /** The part as modelled, whatever pose the shared model was last left in, and back as it was. */
        private static void draw(ModelPart part, PoseStack poseStack, com.mojang.blaze3d.vertex.VertexConsumer consumer, int light) {
            PartPose pose = part.storePose();
            boolean visible = part.visible;
            part.resetPose();
            part.visible = true;
            part.render(poseStack, consumer, light, OverlayTexture.NO_OVERLAY);
            part.loadPose(pose);
            part.visible = visible;
        }
    }
}

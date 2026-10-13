package com.steelclash.client.dev;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.steelclash.SteelClash;
import com.steelclash.Config;
import com.steelclash.client.anim.CombatPresentation;
import com.steelclash.client.SwingPose;
import com.steelclash.combat.ModAttachments;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Opt-in actual baked sword vertices and draw matrices. No model, buffer, clock or gameplay writes. */
public final class BladeMeshProbe {
    private static final String OUTPUT = System.getProperty("steelclash.bladeMeshOut");
    private static final int MAX_SAMPLES = 512;
    private static final ThreadLocal<Scope> ACTIVE = new ThreadLocal<>();
    private record ArmPose(long frame, int tick, int entityId, boolean left, Matrix4f matrix, Matrix4f otherMatrix, String skin) {}
    private static final ThreadLocal<ArmPose> ARM = new ThreadLocal<>();
    private static final Set<String> FROZEN_SAMPLES = new HashSet<>();
    private static int samples;
    private static Path output;
    private BladeMeshProbe() {}

    public static final class Scope {
        final Scope previous;
        final JsonObject data = new JsonObject();
        final JsonArray quads = new JsonArray();
        final Set<String> unique = new HashSet<>();
        Scope(Scope previous) { this.previous = previous; }
    }

    /** The actual arm transform before vanilla's hand/socket and PAL item rotations. */
    public static void arm(LivingEntity entity, ItemStack stack, boolean left, PoseStack pose) {
        if(OUTPUT==null||samples>=MAX_SAMPLES||entity!=Minecraft.getInstance().player||!stack.is(Items.IRON_SWORD))return;
        net.minecraft.client.renderer.entity.EntityRenderer<?> renderer=Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        if(!(renderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer playerRenderer))return;
        var part=left?playerRenderer.getModel().leftArm:playerRenderer.getModel().rightArm;
        PoseStack armPose=new PoseStack();armPose.mulPose(pose.last().pose());part.translateAndRotate(armPose);
        var other=left?playerRenderer.getModel().rightArm:playerRenderer.getModel().leftArm;
        PoseStack otherPose=new PoseStack();otherPose.mulPose(pose.last().pose());other.translateAndRotate(otherPose);
        String skin=entity instanceof net.minecraft.client.player.AbstractClientPlayer player?player.getSkin().model().name():"unknown";
        ARM.set(new ArmPose(CombatPresentation.frameNumber(),entity.tickCount,entity.getId(),left,new Matrix4f(armPose.last().pose()),new Matrix4f(otherPose.last().pose()),skin));
    }

    public static Scope begin(LivingEntity entity, ItemStack stack, ItemDisplayContext context, boolean left, PoseStack pose) {
        Scope previous=ACTIVE.get();
        Minecraft mc=Minecraft.getInstance();
        if(OUTPUT==null||samples>=MAX_SAMPLES||entity!=mc.player||!stack.is(Items.IRON_SWORD)
                ||!entity.hasData(ModAttachments.COMBAT)||!entity.getData(ModAttachments.COMBAT).machine.phase().isAttack()) {
            ACTIVE.remove();
            return new Scope(previous);
        }
        var machine=entity.getData(ModAttachments.COMBAT).machine;
        String frozen=machine.phase()+":"+machine.phaseElapsedUs()+":"+machine.isHeavy()+":"+machine.isMirrored()
                +":"+entity.getMainArm()+":"+mc.options.getCameraType()+":"+context+":"+left+":"+FirstPersonMode.isFirstPersonPass();
        if(PoseSheet.running()&&!FROZEN_SAMPLES.add(frozen)) { ACTIVE.remove();return new Scope(previous); }
        Scope scope=new Scope(previous);ACTIVE.set(scope);
        scope.data.addProperty("sample",++samples);scope.data.addProperty("frame",CombatPresentation.frameNumber());
        scope.data.addProperty("time_ns",System.nanoTime());scope.data.addProperty("entity_tick",entity.tickCount);
        scope.data.addProperty("entity_id",entity.getId());scope.data.addProperty("context",context.name());
        scope.data.addProperty("left",left);scope.data.addProperty("first_person_pass",FirstPersonMode.isFirstPersonPass());
        ArmPose arm=ARM.get();
        if(arm!=null&&arm.frame==CombatPresentation.frameNumber()&&arm.tick==entity.tickCount&&arm.entityId==entity.getId()&&arm.left==left) {
            scope.data.add("arm_draw_matrix",matrix(arm.matrix));scope.data.addProperty("arm_skin",arm.skin);
            scope.data.add("other_arm_draw_matrix",matrix(arm.otherMatrix));
        } else scope.data.addProperty("arm_transform_missing",true);
        scope.data.addProperty("camera_type",mc.options.getCameraType().name());scope.data.addProperty("main_arm",entity.getMainArm().name());
        scope.data.addProperty("phase",machine.phase().name());scope.data.addProperty("elapsed_us",machine.phaseElapsedUs());
        scope.data.addProperty("duration_us",machine.phaseDurationUs());scope.data.addProperty("heavy",machine.isHeavy());
        scope.data.addProperty("mirrored",machine.isMirrored());scope.data.addProperty("variant",machine.variant());
        float partial=PoseSheet.running()?0:entity instanceof net.minecraft.client.player.AbstractClientPlayer player
                ? ((com.zigythebird.playeranim.accessors.IAnimatedPlayer)player).playerAnimLib$getAnimManager().getTickDelta()
                : mc.getTimer().getGameTimeDeltaPartialTick(false);
        scope.data.addProperty("partial",partial);scope.data.addProperty("view_yaw",entity.getViewYRot(partial));
        scope.data.addProperty("view_pitch",entity.getViewXRot(partial));scope.data.addProperty("body_yaw",entity.yBodyRot);
        scope.data.addProperty("fp_width",Config.Client.FIRST_PERSON_SWING_WIDTH.get());
        scope.data.addProperty("fp_lift",Config.Client.FIRST_PERSON_SWING_LIFT.get());
        scope.data.addProperty("grip_pitch",Config.Client.WEAPON_GRIP_PITCH.get());
        scope.data.addProperty("twist_axis",Config.Client.BLADE_TWIST_AXIS.get().name());
        scope.data.addProperty("two_handed_swords",Config.Client.TWO_HANDED_SWORDS.get());
        scope.data.addProperty("offhand_empty",entity.getOffhandItem().isEmpty());
        SwingPose.of(entity,entity.getData(ModAttachments.COMBAT),partial).ifPresent(swing->{
            scope.data.addProperty("render_phase",swing.phase().name());scope.data.addProperty("progress",swing.progress());
            scope.data.addProperty("weight",swing.weight());scope.data.addProperty("arc_yaw",swing.yaw());
            scope.data.addProperty("arc_pitch",swing.pitch());scope.data.addProperty("arc_extension",swing.extension());
        });
        Vector3f socket=new Vector3f().mulPosition(pose.last().pose());
        scope.data.add("item_socket_render_position",array(socket.x,socket.y,socket.z));
        scope.data.add("entity_position",array(entity.getX(),entity.getY(),entity.getZ()));
        var camera=mc.gameRenderer.getMainCamera();var pos=camera.getPosition();var rotation=camera.rotation();
        scope.data.add("camera_position",array(pos.x,pos.y,pos.z));
        scope.data.add("camera_rotation_xyzw",array(rotation.x,rotation.y,rotation.z,rotation.w));
        scope.data.add("model_view",matrix(RenderSystem.getModelViewMatrix()));
        scope.data.add("projection",matrix(RenderSystem.getProjectionMatrix()));
        scope.data.add("quads",scope.quads);
        return scope;
    }

    public static void quads(PoseStack pose, List<BakedQuad> quads) {
        Scope scope=ACTIVE.get();if(scope==null)return;
        Matrix4f draw=new Matrix4f(pose.last().pose());
        Matrix4f clip=new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).mul(draw);
        for(BakedQuad quad:quads) {
            int[] data=quad.getVertices();int stride=data.length/4;
            if(data.length%4!=0||stride<6)throw new IllegalStateException("Blade mesh probe: unsupported quad layout");
            JsonObject record=new JsonObject();record.add("draw_matrix",matrix(draw));
            record.addProperty("sprite",quad.getSprite().contents().name().toString());
            record.addProperty("face",quad.getDirection().name());
            JsonArray vertices=new JsonArray();record.add("vertices",vertices);
            for(int i=0;i<4;i++) {
                int at=i*stride;float x=Float.intBitsToFloat(data[at]),y=Float.intBitsToFloat(data[at+1]),z=Float.intBitsToFloat(data[at+2]);
                Vector3f drawn=new Vector3f(x,y,z).mulPosition(draw);Vector4f projected=new Vector4f(x,y,z,1).mul(clip);
                JsonObject vertex=new JsonObject();vertex.add("local",array(x,y,z));
                vertex.add("render_position",array(drawn.x,drawn.y,drawn.z));
                vertex.add("clip_xyzw",array(projected.x,projected.y,projected.z,projected.w));
                vertex.add("atlas_uv",array(Float.intBitsToFloat(data[at+4]),Float.intBitsToFloat(data[at+5])));
                vertices.add(vertex);
            }
            if(scope.unique.add(record.toString()))scope.quads.add(record);
            if(scope.quads.size()>256)throw new IllegalStateException("Blade mesh probe: quad bound exceeded");
        }
    }

    public static void end(Scope scope) {
        try {
            if(scope.quads.isEmpty())return;
            if(output==null) {
                output=Path.of(OUTPUT);
                Files.writeString(output,"",StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
                SteelClash.LOGGER.info("Blade mesh probe output: {}",output);
            }
            Files.writeString(output,scope.data+"\n",StandardOpenOption.APPEND,StandardOpenOption.WRITE);
        } catch(Exception error) {throw new IllegalStateException("Blade mesh evidence failed",error);}
        finally {if(scope.previous==null)ACTIVE.remove();else ACTIVE.set(scope.previous);}
    }

    private static JsonArray matrix(Matrix4f matrix) {float[] values=new float[16];matrix.get(values);JsonArray result=new JsonArray();for(float value:values)result.add(value);return result;}
    private static JsonArray array(double... values) {JsonArray result=new JsonArray();for(double value:values){if(!Double.isFinite(value))throw new IllegalStateException("Nonfinite blade mesh value");result.add(value);}return result;}
}

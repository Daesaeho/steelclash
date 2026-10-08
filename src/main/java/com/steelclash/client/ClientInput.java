package com.steelclash.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.steelclash.Config;
import com.steelclash.SteelClash;
import com.steelclash.combat.Combat;
import com.steelclash.combat.Dodge;
import com.steelclash.combat.CombatData;
import com.steelclash.combat.ModAttachments;
import com.steelclash.core.AttackType;
import com.steelclash.core.Gesture;
import com.steelclash.core.Phase;
import com.steelclash.core.SwingTurn;
import com.steelclash.net.ActionPayload;
import com.steelclash.net.AttackInputPayload;
import com.steelclash.net.BlockInputPayload;
import com.steelclash.profile.WeaponProfiles;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Turns input into attacks while a profiled weapon is held. Every input is a rebindable key (Controls → Steel Clash);
 * the client config's control scheme sets the defaults ({@link ControlSchemes}):
 * <ul>
 *     <li>CHIVALRY: slash on left click, its side alternating (or following your turn); parry on right click.</li>
 *     <li>TWO_SLASH_KEYS: left click slashes right→left, right click left→right; parry on middle click.</li>
 *     <li>Overhead: Mouse 5 / scroll up. Stab: Mouse 4 / scroll down. Hold any attack for a heavy.</li>
 *     <li>Parry also raises an offhand shield. Feint X, kick Z, jab V, dodge Left Alt, special R, throw G. Optional
 *     gestures: see below.</li>
 * </ul>
 * Mouse-bound inputs are intercepted in {@link InputEvent.MouseButton.Pre}, before Minecraft registers the click on any
 * {@link KeyMapping}. That stops vanilla attacking, block mining and using, and also stops Spartan Shields from reading
 * a click as a shield bash (docs/spikes.md (c)). Sneak + left click on a block still mines; right click on doors,
 * chests, villagers, mounts (or while sneaking, or with a bow/trident) still does the vanilla thing.
 */
@EventBusSubscriber(modid = SteelClash.MOD_ID, value = Dist.CLIENT)
public final class ClientInput {
    private static final String CATEGORY = "key.categories.steelclash";
    /** Extra blocks that right click should interact with instead of attacking (for modded blocks). */
    private static final TagKey<Block> INTERACTABLE = TagKey.create(Registries.BLOCK, SteelClash.id("interactable"));

    /** In-game only, and deliberately compatible with vanilla bindings it shares a button with (attack, use, pick block). */
    private enum SharesVanillaButton implements IKeyConflictContext {
        INSTANCE;

        @Override
        public boolean isActive() {
            return KeyConflictContext.IN_GAME.isActive();
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return false;
        }
    }

    public static final KeyMapping SLASH_RIGHT_TO_LEFT = new KeyMapping("key.steelclash.slash_right_to_left",
            SharesVanillaButton.INSTANCE, InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_LEFT, CATEGORY);
    /** Unbound in the default CHIVALRY scheme; the TWO_SLASH_KEYS scheme puts it on right click. */
    public static final KeyMapping SLASH_LEFT_TO_RIGHT = new KeyMapping("key.steelclash.slash_left_to_right",
            SharesVanillaButton.INSTANCE, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY);
    public static final KeyMapping OVERHEAD = new KeyMapping("key.steelclash.overhead",
            InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_5, CATEGORY);
    public static final KeyMapping STAB = new KeyMapping("key.steelclash.stab",
            InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_4, CATEGORY);
    /** Weapon parry; raises the shield instead when one is in the offhand. */
    public static final KeyMapping PARRY = new KeyMapping("key.steelclash.parry",
            SharesVanillaButton.INSTANCE, InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_RIGHT, CATEGORY);
    public static final KeyMapping FEINT = new KeyMapping("key.steelclash.feint",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);
    public static final KeyMapping KICK = new KeyMapping("key.steelclash.kick",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);
    public static final KeyMapping SPECIAL = new KeyMapping("key.steelclash.special",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
    public static final KeyMapping THROW = new KeyMapping("key.steelclash.throw",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
    /** Quick short thrust that interrupts at close range. */
    public static final KeyMapping JAB = new KeyMapping("key.steelclash.jab",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);
    /** Dash in the direction you're moving (backwards when standing still). */
    public static final KeyMapping DODGE = new KeyMapping("key.steelclash.dodge",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY);

    /**
     * An attack input: which attack it starts and which side it swings from ({@code null} = alternating, or chosen by
     * turning; see {@link #sideOf}). Holding it past {@link Combat#HEAVY_HOLD_TICKS} makes a heavy.
     */
    private record AttackKey(KeyMapping key, AttackType type, @Nullable Boolean mirrored) {
    }

    private static final List<AttackKey> ATTACK_KEYS = List.of(
            new AttackKey(SLASH_RIGHT_TO_LEFT, AttackType.SLASH, null), // side set by the control scheme
            new AttackKey(SLASH_LEFT_TO_RIGHT, AttackType.SLASH, true),
            new AttackKey(OVERHEAD, AttackType.OVERHEAD, null),
            new AttackKey(STAB, AttackType.STAB, null));

    /** The attack input that started the current windup (to detect holding it for a heavy). */
    @Nullable
    private static AttackKey hold;
    /** Mouse buttons we intercepted and that are still held down. */
    private static final Set<Integer> heldButtons = new HashSet<>();
    /** Mouse buttons whose press we swallowed, so we also swallow their release. */
    private static final Set<Integer> suppressedButtons = new HashSet<>();
    private static boolean parryKeyWasDown;
    /** The parry key raised the offhand shield (so releasing it lowers the shield again). */
    private static boolean shieldRaisedByParry;
    /** A weapon-guard press was sent to the server; its release must be sent even if local prediction rejected it. */
    private static boolean weaponParryRequested;

    /** Turning faster than this (degrees over the last few ticks) when attacking picks the swing side. */
    private static final float TURN_THRESHOLD = 4f;
    private static final int TURN_TICKS = 3;
    private static final float[] recentYaw = new float[TURN_TICKS];
    private static int recentYawIndex;
    private static boolean lastMirrored;

    /** Gesture attacks: the held input being read as a gesture, and where the view was when it went down. */
    @Nullable
    private static AttackKey gestureKey;
    private static float gestureYaw;
    private static float gesturePitch;
    private static int gestureTicks;
    /** Turn absorbed while the view is locked (gestureLockView), counted toward the gesture. */
    private static float gestureTurnYaw;
    private static float gestureTurnPitch;

    /** Turn cap state: the view as last allowed, and when. */
    private static final float[] NO_TURN = {0, 0};
    private static float limitYaw;
    private static float limitPitch;
    private static long limitNanos;

    private ClientInput() {
    }

    /** Mod-bus listener, registered from {@code SteelClashClient}. */
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(SLASH_RIGHT_TO_LEFT);
        event.register(SLASH_LEFT_TO_RIGHT);
        event.register(OVERHEAD);
        event.register(STAB);
        event.register(PARRY);
        event.register(FEINT);
        event.register(DODGE);
        event.register(JAB);
        event.register(KICK);
        event.register(SPECIAL);
        event.register(THROW);
    }

    // ------------------------------------------------------------------ mouse

    @SubscribeEvent
    static void onMouseButton(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        int button = event.getButton();
        if (event.getAction() == GLFW.GLFW_RELEASE) {
            heldButtons.remove(button);
            if (gestureKey != null && gestureKey.key().matchesMouse(button) && mc.player != null) {
                Gesture.Result read = readGesture(mc.player);
                fireGesture(mc.player, read); // no drag: the key's own attack
            }
            if (PARRY.matchesMouse(button)) {
                releaseGuardInput(mc);
            }
            if (suppressedButtons.remove(button)) {
                event.setCanceled(true);
            }
            return;
        }
        if (event.getAction() != GLFW.GLFW_PRESS || !inGame(mc)) {
            return;
        }
        for (AttackKey attack : ATTACK_KEYS) {
            if (attack.key().matchesMouse(button)) {
                if (holdsWeapon(mc.player) && !leaveToVanilla(mc, attack.key())) {
                    pressAttack(mc.player, attack);
                    swallow(event, button);
                }
                return;
            }
        }
        if (PARRY.matchesMouse(button) && startGuardInput(mc)) {
            swallow(event, button);
        }
    }

    private static void swallow(InputEvent.MouseButton.Pre event, int button) {
        heldButtons.add(button);
        suppressedButtons.add(button);
        event.setCanceled(true);
    }

    /**
     * Should this click do the vanilla thing instead? Sneak + left click on a block mines (versatile Spartan weapons);
     * right click keeps its vanilla meaning for doors, chests, villagers, mounts, while sneaking, and for items with
     * their own use (bows, tridents).
     */
    private static boolean leaveToVanilla(Minecraft mc, KeyMapping key) {
        if (key.same(mc.options.keyAttack)) {
            return mc.player.isShiftKeyDown() && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK;
        }
        if (key.same(mc.options.keyUse)) {
            return rightClickInteracts(mc);
        }
        return false;
    }

    private static boolean rightClickInteracts(Minecraft mc) {
        LocalPlayer player = mc.player;
        UseAnim anim = player.getMainHandItem().getUseAnimation();
        if (player.isShiftKeyDown() || (anim != UseAnim.NONE && anim != UseAnim.BLOCK)) {
            return true;
        }
        HitResult hit = mc.hitResult;
        if (hit instanceof EntityHitResult entityHit) {
            return !(entityHit.getEntity() instanceof Enemy);
        }
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            return isInteractable(mc.level.getBlockState(blockHit.getBlockPos()), blockHit);
        }
        return false;
    }

    private static boolean isInteractable(BlockState state, BlockHitResult hit) {
        Minecraft mc = Minecraft.getInstance();
        return state.getMenuProvider(mc.level, hit.getBlockPos()) != null
                || mc.level.getBlockEntity(hit.getBlockPos()) != null
                || state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.FENCE_GATES)
                || state.is(BlockTags.BUTTONS) || state.is(BlockTags.BEDS) || state.getBlock() instanceof LeverBlock
                || state.is(INTERACTABLE);
    }

    // ------------------------------------------------------------------ guard (parry / shield)

    /**
     * Parry key pressed: raise the offhand shield if there is one, otherwise a weapon parry.
     *
     * @return whether the key was used (so its press is swallowed)
     */
    private static boolean startGuardInput(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (PARRY.same(mc.options.keyUse) && rightClickInteracts(mc)) {
            return false; // parry rebound onto right click: keep its vanilla uses
        }
        if (player.getOffhandItem().canPerformAction(ItemAbilities.SHIELD_BLOCK)) {
            if (!PARRY.same(mc.options.keyUse) && mc.gameMode != null) {
                mc.gameMode.useItem(player, InteractionHand.OFF_HAND);
                shieldRaisedByParry = true;
                return true;
            }
            return false; // on right click, vanilla raises the shield itself
        }
        if (!holdsWeapon(player)) {
            return false;
        }
        CombatData data = player.getData(ModAttachments.COMBAT);
        if (player.isUsingItem()) {
            player.stopUsingItem();
        }
        Combat.startParry(player, data); // prediction; the server decides
        weaponParryRequested = true;
        PacketDistributor.sendToServer(new BlockInputPayload(true));
        return true;
    }

    private static void releaseGuardInput(Minecraft mc) {
        boolean releaseWeapon = weaponParryRequested;
        weaponParryRequested = false;
        LocalPlayer player = mc.player;
        if (player == null) {
            shieldRaisedByParry = false;
            return;
        }
        if (shieldRaisedByParry) {
            shieldRaisedByParry = false;
            if (player.isUsingItem() && mc.gameMode != null) {
                mc.gameMode.releaseUsingItem(player);
            }
            return;
        }
        if (releaseWeapon) {
            if (player.hasData(ModAttachments.COMBAT)) {
                player.getData(ModAttachments.COMBAT).machine.releaseParry();
            }
            PacketDistributor.sendToServer(new BlockInputPayload(false));
        }
    }

    // ------------------------------------------------------------------ scroll and keyboard

    @SubscribeEvent
    static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!Config.Client.SCROLL_ATTACKS.get() || !inGame(mc) || !holdsWeapon(mc.player) || event.getScrollDeltaY() == 0) {
            return;
        }
        tryAttack(mc.player, event.getScrollDeltaY() > 0 ? AttackType.OVERHEAD : AttackType.STAB, null, null);
        event.setCanceled(true);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            weaponParryRequested = false;
            shieldRaisedByParry = false;
        }
        // Keyboard-bound attack keys (mouse-bound ones were handled as clicks).
        for (AttackKey attack : ATTACK_KEYS) {
            while (attack.key().consumeClick()) {
                if (isKeyboard(attack.key()) && inGame(mc) && holdsWeapon(player)) {
                    pressAttack(player, attack);
                }
            }
        }
        // Keyboard-bound parry: held to guard.
        boolean parryDown = isKeyboard(PARRY) && PARRY.isDown();
        if (parryDown && !parryKeyWasDown && inGame(mc)) {
            startGuardInput(mc);
        } else if (!parryDown && parryKeyWasDown) {
            releaseGuardInput(mc);
        }
        parryKeyWasDown = parryDown;
        while (KICK.consumeClick()) {
            if (inGame(mc)) {
                tryAttack(player, AttackType.KICK, null, null);
            }
        }
        while (JAB.consumeClick()) {
            if (inGame(mc)) {
                tryAttack(player, AttackType.JAB, null, null);
            }
        }
        while (SPECIAL.consumeClick()) {
            if (inGame(mc) && holdsWeapon(player)) {
                tryAttack(player, AttackType.SPECIAL, null, null);
            }
        }
        while (THROW.consumeClick()) {
            if (inGame(mc) && holdsWeapon(player)) {
                tryAttack(player, AttackType.THROW, null, null);
            }
        }
        if (player != null) {
            recentYaw[recentYawIndex] = player.getYRot();
            recentYawIndex = (recentYawIndex + 1) % TURN_TICKS;
        }
        if (player == null || !player.hasData(ModAttachments.COMBAT)) {
            return;
        }
        tickGesture(mc, player);
        CombatData data = player.getData(ModAttachments.COMBAT);
        while (DODGE.consumeClick()) {
            if (inGame(mc) && Dodge.perform(player, data)) {
                hold = null;
                Vec3 burst = Dodge.velocity(player.getYRot(), player.input.leftImpulse, player.input.forwardImpulse);
                player.setDeltaMovement(burst.x, player.getDeltaMovement().y, burst.z);
                PacketDistributor.sendToServer(new ActionPayload(ActionPayload.Action.DODGE));
            }
        }
        while (FEINT.consumeClick()) {
            if (inGame(mc) && Combat.feint(player, data)) {
                hold = null;
                PacketDistributor.sendToServer(new ActionPayload(ActionPayload.Action.FEINT));
            }
        }
        // Still holding the attack that started this windup: it becomes a heavy.
        if (data.machine.phase() != Phase.WINDUP) {
            hold = null;
        } else if (hold != null && !data.machine.isHeavy() && data.machine.phaseTick() >= Combat.HEAVY_HOLD_TICKS) {
            if (isHeld(hold) && Combat.makeHeavy(player, data)) {
                PacketDistributor.sendToServer(new ActionPayload(ActionPayload.Action.HEAVY));
            }
            hold = null;
        }
    }

    private static boolean isKeyboard(KeyMapping key) {
        return key.getKey().getType() == InputConstants.Type.KEYSYM;
    }

    /** The input that started the current windup is still held, so the windup may still become a heavy. */
    public static boolean isChargingHeavy() {
        return hold != null && isHeld(hold);
    }

    private static boolean isHeld(AttackKey attack) {
        return attack.key().getKey().getType() == InputConstants.Type.MOUSE
                ? heldButtons.contains(attack.key().getKey().getValue())
                : attack.key().isDown();
    }

    // ------------------------------------------------------------------ gestures (experimental)

    /** An attack input went down: attack at once, or (gesture attacks on) start reading a gesture. */
    private static void pressAttack(LocalPlayer player, AttackKey attack) {
        if (readsGestures(attack)) {
            gestureKey = attack;
            gestureYaw = player.getYRot();
            gesturePitch = player.getXRot();
            gestureTicks = 0;
            gestureTurnYaw = 0;
            gestureTurnPitch = 0;
            return;
        }
        tryAttack(player, attack.type(), attack, sideOf(attack));
    }

    /** The side an attack key swings from: fixed for the slash keys in TWO_SLASH_KEYS, otherwise chosen per swing. */
    @Nullable
    private static Boolean sideOf(AttackKey attack) {
        if (attack.key() == SLASH_RIGHT_TO_LEFT) {
            return Config.Client.CONTROL_SCHEME.get() == Config.Client.ControlScheme.TWO_SLASH_KEYS ? Boolean.FALSE : null;
        }
        return attack.mirrored();
    }

    private static boolean readsGestures(AttackKey attack) {
        if (!Config.Client.GESTURE_ATTACKS.get()) {
            return false;
        }
        return switch (Config.Client.GESTURE_KEYS.get()) {
            case SLASH -> attack.key() == SLASH_RIGHT_TO_LEFT;
            case SECOND_SLASH -> attack.key() == SLASH_LEFT_TO_RIGHT;
            case BOTH -> attack.key() == SLASH_RIGHT_TO_LEFT || attack.key() == SLASH_LEFT_TO_RIGHT;
        };
    }

    /** The attack the drag so far asks for, or {@code null} (too small, or that direction is set to NONE). */
    @Nullable
    private static Gesture.Result readGesture(LocalPlayer player) {
        return Gesture.classify(gestureTurnYaw + Mth.wrapDegrees(player.getYRot() - gestureYaw),
                gestureTurnPitch + player.getXRot() - gesturePitch,
                Config.Client.GESTURE_THRESHOLD.get().floatValue(), Config.Client.gestureMapping());
    }

    /** Fires once the drag is big enough; a hold that never moves slashes after the gesture window. */
    private static void tickGesture(Minecraft mc, LocalPlayer player) {
        if (gestureKey == null) {
            return;
        }
        if (!inGame(mc) || !holdsWeapon(player)) {
            gestureKey = null;
            return;
        }
        gestureTicks++;
        Gesture.Result read = readGesture(player);
        if (read != null) {
            fireGesture(player, read);
        } else if (!isHeld(gestureKey) || gestureTicks >= Config.Client.GESTURE_WINDOW_TICKS.get()) {
            fireGesture(player, null);
        }
    }

    /**
     * Starts the gesture's attack, or the key's own attack if there was no (usable) drag. Still holding the button
     * afterwards turns it into a heavy, like any held attack key.
     */
    private static void fireGesture(LocalPlayer player, @Nullable Gesture.Result result) {
        AttackKey source = gestureKey;
        gestureKey = null;
        if (result == null) {
            tryAttack(player, source.type(), source, sideOf(source));
        } else {
            tryAttack(player, result.type(), source, result.mirrored());
        }
    }

    /**
     * Lock view: whatever the mouse turned since the last check is added to the gesture and undone, so the view stays
     * where it was when the button went down. Runs before each tick (so the server never sees the turn) and each frame.
     *
     * @return the yaw taken back out
     */
    private static float absorbLockedTurn(LocalPlayer player) {
        if (gestureKey == null || !Config.Client.GESTURE_LOCK_VIEW.get()) {
            return 0;
        }
        float yaw = Mth.wrapDegrees(player.getYRot() - gestureYaw);
        float pitch = player.getXRot() - gesturePitch;
        gestureTurnYaw += yaw;
        gestureTurnPitch += pitch;
        player.setYRot(gestureYaw);
        player.setXRot(gesturePitch);
        player.yRotO = gestureYaw;
        player.xRotO = gesturePitch;
        return yaw;
    }

    /**
     * Turn cap, client side: during your own windup and release the camera turns no faster than the server traces the
     * swing ({@code turnCapDegreesPerSecond}), so what you see is what hits. Mouse movement beyond it is dropped.
     *
     * @return {@code [yaw, pitch]} taken back out
     */
    private static float[] limitTurn(LocalPlayer player) {
        long now = System.nanoTime();
        double cap = Config.TURN_CAP.get();
        Phase phase = player.hasData(ModAttachments.COMBAT) ? player.getData(ModAttachments.COMBAT).machine.phase() : Phase.IDLE;
        if (cap <= 0 || (phase != Phase.WINDUP && phase != Phase.RELEASE)) {
            limitYaw = player.getYRot();
            limitPitch = player.getXRot();
            limitNanos = now;
            return NO_TURN;
        }
        float step = (float) (cap * Math.min(0.1, (now - limitNanos) / 1e9));
        float yaw = SwingTurn.approachYaw(limitYaw, player.getYRot(), step);
        float pitch = SwingTurn.approachPitch(limitPitch, player.getXRot(), step);
        float[] removed = {Mth.wrapDegrees(player.getYRot() - yaw), player.getXRot() - pitch};
        player.setYRot(yaw);
        player.setXRot(pitch);
        limitYaw = yaw;
        limitPitch = pitch;
        limitNanos = now;
        return removed;
    }

    /** Chivalry 2 footwork: backpedalling with a weapon is slower than moving forward. */
    @SubscribeEvent
    static void onMovementInput(MovementInputUpdateEvent event) {
        if (event.getInput().forwardImpulse < 0 && event.getEntity() instanceof LocalPlayer player && holdsWeapon(player)) {
            event.getInput().forwardImpulse *= Config.BACKPEDAL_SPEED.get().floatValue();
        }
    }

    @SubscribeEvent
    static void onClientTickPre(ClientTickEvent.Pre event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            absorbLockedTurn(player);
            limitTurn(player);
        }
    }

    @SubscribeEvent
    static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || event.getCamera().getEntity() != player) {
            return;
        }
        float pitchBefore = player.getXRot();
        float yaw = absorbLockedTurn(player);
        float pitch = pitchBefore - player.getXRot();
        float[] capped = limitTurn(player);
        yaw += capped[0];
        pitch += capped[1];
        if (yaw != 0 || pitch != 0) {
            // The camera was already set up from the turned view: take the turn back out (front view mirrors pitch).
            boolean mirrored = mc.options.getCameraType().isMirrored();
            event.setYaw(event.getYaw() - yaw);
            event.setPitch(event.getPitch() - (mirrored ? -pitch : pitch));
        }
    }

    // ------------------------------------------------------------------ attacks

    /**
     * Predicts the attack locally and tells the server. A different attack during a windup is a morph; during the release
     * or recovery the input is a combo (buffered until it can start), likewise after a stagger (here and on the server).
     *
     * @param source         the held input that started it (for heavies), or {@code null} (scroll, kick, special, throw)
     * @param forcedMirrored the swing side the input dictates (the two slash keys), or {@code null} to choose one
     */
    private static void tryAttack(LocalPlayer player, AttackType type, @Nullable AttackKey source, @Nullable Boolean forcedMirrored) {
        CombatData data = player.getData(ModAttachments.COMBAT);
        Phase phase = data.machine.phase();
        int variant = player.getRandom().nextInt(8); // wrapped to the attack's variant count
        boolean mirrored = forcedMirrored != null ? forcedMirrored : chooseSide(player, data);
        if (phase == Phase.WINDUP) {
            if (Combat.feintInto(player, data, type)) { // feint into a kick or jab
                hold = null;
                PacketDistributor.sendToServer(new AttackInputPayload(type, 0, false));
                return;
            }
            // The same attack from the other side only with an explicit side key (two-slash-key scheme): a counter-feint
            // to the alternate side. A second press of the single slash key mustn't restart the windup by accident.
            boolean otherSide = forcedMirrored != null && type == data.machine.type() && mirrored != data.machine.isMirrored();
            if ((type != data.machine.type() || otherSide) && Combat.morph(player, data, type, variant, mirrored)) {
                hold = source;
                lastMirrored = mirrored;
                PacketDistributor.sendToServer(new AttackInputPayload(type, variant, mirrored));
            }
            return;
        }
        if (data.machine.canStartAttack()) {
            if (player.isUsingItem()) {
                player.stopUsingItem();
            }
            if (!Combat.start(player, data, type, variant, mirrored)) {
                return;
            }
            hold = source;
        } else if (Combat.isBufferedPhase(phase)) {
            Combat.queue(data, type, variant, mirrored); // starts as soon as it can (a combo pressed in the release, too)
        } else {
            return;
        }
        lastMirrored = mirrored;
        PacketDistributor.sendToServer(new AttackInputPayload(type, variant, mirrored));
    }

    /**
     * Side for attacks whose key doesn't dictate one (the CHIVALRY slash, overheads, stabs, scroll), as in Chivalry 2:
     * every swing alternates sides; turning while attacking overrides it (turning right swings left to right), and so
     * does strafing if {@code sideFromMovement} is on.
     */
    private static boolean chooseSide(LocalPlayer player, CombatData data) {
        Config.Client.MovementSide fromMovement = Config.Client.SIDE_FROM_MOVEMENT.get();
        float strafe = player.input.leftImpulse; // > 0 while moving left
        if (fromMovement != Config.Client.MovementSide.OFF && Math.abs(strafe) > 0.1f) {
            boolean fromLeft = strafe > 0;
            return fromMovement == Config.Client.MovementSide.FROM_STRAFE_SIDE ? fromLeft : !fromLeft;
        }
        float oldest = recentYaw[recentYawIndex];
        float turn = Mth.wrapDegrees(player.getYRot() - oldest);
        if (Math.abs(turn) >= TURN_THRESHOLD) {
            return turn > 0;
        }
        if (Combat.isComboInput(data.machine)) {
            return !data.machine.isMirrored();
        }
        return !lastMirrored;
    }

    private static boolean inGame(Minecraft mc) {
        return mc.player != null && mc.screen == null && mc.getOverlay() == null && mc.mouseHandler.isMouseGrabbed()
                && !mc.player.isSpectator();
    }

    static boolean holdsWeapon(LocalPlayer player) {
        return player != null && WeaponProfiles.resolve(player.getMainHandItem(), player.level().registryAccess()).isPresent();
    }
}

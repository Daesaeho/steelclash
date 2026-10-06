package com.steelclash.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.steelclash.Config;
import com.steelclash.SteelClash;
import com.steelclash.combat.Combat;
import com.steelclash.combat.CombatData;
import com.steelclash.combat.ModAttachments;
import com.steelclash.core.AttackType;
import com.steelclash.core.Phase;
import com.steelclash.net.ActionPayload;
import com.steelclash.net.AttackInputPayload;
import com.steelclash.net.BlockInputPayload;
import com.steelclash.profile.WeaponProfiles;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
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
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Turns input into attacks while a profiled weapon is held. Every input is a rebindable key (Controls → Steel Clash):
 * <ul>
 *     <li>Slash right→left: left click. Slash left→right: right click. (Both hold for a heavy.)</li>
 *     <li>Overhead: Mouse 5 / scroll up. Stab: Mouse 4 / scroll down.</li>
 *     <li>Parry: middle click (also raises an offhand shield). Feint X, kick Z, special R, throw G.</li>
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
    public static final KeyMapping SLASH_LEFT_TO_RIGHT = new KeyMapping("key.steelclash.slash_left_to_right",
            SharesVanillaButton.INSTANCE, InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_RIGHT, CATEGORY);
    public static final KeyMapping OVERHEAD = new KeyMapping("key.steelclash.overhead",
            InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_5, CATEGORY);
    public static final KeyMapping STAB = new KeyMapping("key.steelclash.stab",
            InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_4, CATEGORY);
    /** Weapon parry; raises the shield instead when one is in the offhand. */
    public static final KeyMapping PARRY = new KeyMapping("key.steelclash.parry",
            SharesVanillaButton.INSTANCE, InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_MIDDLE, CATEGORY);
    public static final KeyMapping FEINT = new KeyMapping("key.steelclash.feint",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);
    public static final KeyMapping KICK = new KeyMapping("key.steelclash.kick",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);
    public static final KeyMapping SPECIAL = new KeyMapping("key.steelclash.special",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
    public static final KeyMapping THROW = new KeyMapping("key.steelclash.throw",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);

    /**
     * An attack input: which attack it starts and, for the two slash keys, which side it swings from ({@code null} =
     * chosen by turning / combo alternation). Holding it past {@link Combat#HEAVY_HOLD_TICKS} makes a heavy.
     */
    private record AttackKey(KeyMapping key, AttackType type, @Nullable Boolean mirrored) {
    }

    private static final List<AttackKey> ATTACK_KEYS = List.of(
            new AttackKey(SLASH_RIGHT_TO_LEFT, AttackType.SLASH, false),
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

    /** Turning faster than this (degrees over the last few ticks) when attacking picks the swing side. */
    private static final float TURN_THRESHOLD = 4f;
    private static final int TURN_TICKS = 3;
    private static final float[] recentYaw = new float[TURN_TICKS];
    private static int recentYawIndex;
    private static boolean lastMirrored;

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
                    tryAttack(mc.player, attack.type(), attack, attack.mirrored());
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
        PacketDistributor.sendToServer(new BlockInputPayload(true));
        return true;
    }

    private static void releaseGuardInput(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        if (shieldRaisedByParry) {
            shieldRaisedByParry = false;
            if (player.isUsingItem() && mc.gameMode != null) {
                mc.gameMode.releaseUsingItem(player);
            }
            return;
        }
        if (player.hasData(ModAttachments.COMBAT) && player.getData(ModAttachments.COMBAT).machine.phase() == Phase.PARRY) {
            player.getData(ModAttachments.COMBAT).machine.releaseParry();
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
        // Keyboard-bound attack keys (mouse-bound ones were handled as clicks).
        for (AttackKey attack : ATTACK_KEYS) {
            while (attack.key().consumeClick()) {
                if (isKeyboard(attack.key()) && inGame(mc) && holdsWeapon(player)) {
                    tryAttack(player, attack.type(), attack, attack.mirrored());
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
        CombatData data = player.getData(ModAttachments.COMBAT);
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

    // ------------------------------------------------------------------ attacks

    /**
     * Predicts the attack locally and tells the server. A different attack during a windup is a morph; during recovery
     * the input starts a combo after a landed hit, otherwise it's buffered (both here and on the server).
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
            if (type != data.machine.type() && Combat.morph(player, data, type, variant, mirrored)) {
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
        } else if (phase == Phase.RECOVERY || phase == Phase.GUARD_RECOVERY) {
            Combat.queue(data, type, variant, mirrored);
        } else {
            return;
        }
        lastMirrored = mirrored;
        PacketDistributor.sendToServer(new AttackInputPayload(type, variant, mirrored));
    }

    /**
     * Side for attacks whose key doesn't dictate one (overheads, stabs, scroll): turning while attacking decides it
     * (turning right swings left-to-right), otherwise combos alternate sides and fresh attacks keep the last side.
     */
    private static boolean chooseSide(LocalPlayer player, CombatData data) {
        float oldest = recentYaw[recentYawIndex];
        float turn = Mth.wrapDegrees(player.getYRot() - oldest);
        if (Math.abs(turn) >= TURN_THRESHOLD) {
            return turn > 0;
        }
        if (data.machine.phase() == Phase.RECOVERY && data.machine.isComboAllowed()) {
            return !data.machine.isMirrored();
        }
        return lastMirrored;
    }

    private static boolean inGame(Minecraft mc) {
        return mc.player != null && mc.screen == null && mc.getOverlay() == null && mc.mouseHandler.isMouseGrabbed()
                && !mc.player.isSpectator();
    }

    static boolean holdsWeapon(LocalPlayer player) {
        return player != null && WeaponProfiles.resolve(player.getMainHandItem(), player.level().registryAccess()).isPresent();
    }
}

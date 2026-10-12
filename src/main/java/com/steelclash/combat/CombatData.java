package com.steelclash.combat;

import com.steelclash.Config;
import com.steelclash.ai.BrainState;
import com.steelclash.core.ArcPath;
import com.steelclash.core.AttackType;
import com.steelclash.core.CombatStateMachine;
import com.steelclash.core.InputLimit;
import com.steelclash.core.Stamina;
import com.steelclash.profile.WeaponProfile;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import org.jetbrains.annotations.Nullable;

/**
 * Per-entity combat state, stored as a transient data attachment on any {@code LivingEntity} that fights.
 * Exists on both sides: the server copy is authoritative, client copies drive animation, HUD and debug rendering.
 */
public class CombatData {
    public final CombatStateMachine machine = new CombatStateMachine();
    public final Stamina stamina = new Stamina(Config.MAX_STAMINA.get().floatValue());

    /** The server-side entity this state belongs to; null on clients and for state made without an entity. */
    @Nullable
    private final LivingEntity owner;
    /** Already in {@link LiveAttacks}' list. */
    boolean liveAttackListed;

    public CombatData() {
        this(null);
    }

    /** As an entity's attachment. On the server its attacks are reported to {@link LiveAttacks} as they start. */
    public CombatData(@Nullable IAttachmentHolder holder) {
        owner = holder instanceof LivingEntity entity && !entity.level().isClientSide() ? entity : null;
        if (owner != null) {
            machine.onAttackStart(() -> LiveAttacks.started(this));
        }
    }

    /** The fighter, for state in {@link LiveAttacks}' list (always a server-side entity's). */
    public LivingEntity owner() {
        return owner;
    }

    /** Profile of the weapon the current attack or parry was started with. */
    @Nullable
    public ResourceKey<WeaponProfile> profileKey;

    /** Paths for the last arc used on this entity; a new profile/variant invalidates them by identity. */
    @Nullable
    private WeaponProfile.ArcSpec cachedArc;
    private ArcPath cachedPath;
    @Nullable
    private ArcPath cachedMirroredPath;

    ArcPath pathFor(WeaponProfile.ArcSpec arc, boolean mirrored) {
        if (cachedArc != arc) {
            cachedPath = arc.toPath();
            cachedMirroredPath = null;
            cachedArc = arc;
        }
        if (!mirrored) {
            return cachedPath;
        }
        if (cachedMirroredPath == null) {
            cachedMirroredPath = cachedPath.mirrored();
        }
        return cachedMirroredPath;
    }

    /** Ticks left before a downed player bleeds out; -1 while standing ({@link Downed}). */
    public int downedTicksLeft = -1;
    /** Ticks an ally has spent reviving this downed player so far, and who (entity id, -1 for nobody). */
    public int reviveTicks;
    public int reviverId = -1;

    /** Server: combat inputs from this player's client this tick ({@link InputLimit}). */
    public final InputLimit inputs = new InputLimit();

    public boolean isDowned() {
        return downedTicksLeft >= 0;
    }

    /** Attack input buffered during recovery; starts as soon as the fighter is free. */
    @Nullable
    public AttackType queuedAttack;
    public int queuedVariant;
    public boolean queuedMirrored;

    // ---- server only ----
    /** Weapon the attack was started with; switching weapons cancels the attack. */
    public ItemStack weapon = ItemStack.EMPTY;
    /** Entities already hit by the current swing (each target is hit at most once per swing). */
    public final IntSet hitThisSwing = new IntOpenHashSet();
    /** A weapon guard blocked the current swing (a special): the blade stops there ({@link Defense}). */
    public boolean swingBlocked;
    /** View and pivot at the end of the previous tick, so each tick's sweep interpolates the wielder's turning. */
    public float prevYaw;
    public float prevPitch;
    public Vec3 prevPivot = Vec3.ZERO;
    /** Current attack was started while sprinting (extra reach and damage, forward push). */
    public boolean lunge;
    /** Current attack is an overhead started in mid-air. */
    public boolean jumpAttack;
    /** Game time when the weapon special is off cooldown. */
    public long specialReadyAt;
    /** Game time before which an archer mob won't switch between bow and sidearm again ({@link Sidearms}). */
    public long sidearmReadyAt;
    /** The weapon this mob dropped when disarmed, and when: it goes back for it (RetrieveWeaponGoal). */
    @Nullable
    public UUID lostWeapon;
    public long lostWeaponAt;
    /** {@link MobCombat#keepAggressive} turned this mob's aggressive flag on (and so should turn it off). */
    public boolean forcedAggressive;
    /** Game time the next dodge is allowed. */
    public long dodgeReadyAt;
    /** Game time of the last dodge (a guard can't come up until the dash is half done; a jab right after is slower). */
    public long dodgedAt = Long.MIN_VALUE / 2;
    /** Game time the next jab is allowed (after a disarm). */
    public long jabReadyAt;
    /** Weapon unusable until this game time (HOLSTER disarm mode for mobs). */
    public long holsteredUntil;
    public float lastSentStamina = -1;
    public boolean lastSentExhausted;
    /** Game time this entity last took damage (health regeneration waits for a quiet spell). */
    public long lastHurtAt = Long.MIN_VALUE / 2;
    /** Last tick this fighter attacked, guarded or shield-blocked (pauses health regeneration). */
    public long lastCombatAt = Long.MIN_VALUE / 2;

    // ---- mob decision making ----
    /** Bot brain state (spacing, plans, opponent memory); created on first use. */
    @Nullable
    public BrainState brain;

    /** The attack (attacker id + serial) this mob already decided how to answer, so it rolls once per attack. */
    public int reactedAttackerId = -1;
    public int reactedSerial = -1;
    public boolean willParry;

    public void resetSwing() {
        hitThisSwing.clear();
        swingBlocked = false;
    }
}

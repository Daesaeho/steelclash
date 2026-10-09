package com.steelclash.gametest;

import static com.steelclash.gametest.TestSupport.check;
import static com.steelclash.gametest.TestSupport.data;
import static com.steelclash.gametest.TestSupport.swordsman;

import com.steelclash.Config;
import com.steelclash.SteelClash;
import com.steelclash.combat.Combat;
import com.steelclash.combat.CombatData;
import com.steelclash.core.AttackType;
import com.steelclash.core.Phase;
import com.steelclash.net.CombatStatePayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

@GameTestHolder(SteelClash.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ActionReplacementGameTests {
    private ActionReplacementGameTests() {}

    @GameTest(template = "arena")
    public static void rejectedBuiltInFeintsPreserveTheOriginalAttack(GameTestHelper helper) {
        for (int reason = 0; reason < 3; reason++) {
            Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), TestSupport.FACING_POSITIVE_X);
            CombatData d = data(player);
            check(helper, Combat.start(player, d, AttackType.SLASH, 0, true), "start the original slash");
            d.machine.tick();
            d.machine.tick();
            d.stamina.set(70);
            d.queuedAttack = AttackType.OVERHEAD;
            d.queuedVariant = 1;
            d.queuedMirrored = true;
            d.hitThisSwing.add(123456);
            long now = helper.getLevel().getGameTime();
            if (reason == 0) d.jabReadyAt = now + 6;
            if (reason == 1) d.holsteredUntil = now + 6;
            if (reason == 2) d.downedTicksLeft = 200;
            CombatStatePayload before = CombatStatePayload.of(player, d, true);
            float stamina = d.stamina.current();
            check(helper, !Combat.feintInto(player, d, AttackType.JAB), "ineligible replacement is rejected: " + reason);
            check(helper, before.equals(CombatStatePayload.of(player, d, true)), "all attack state is preserved: " + reason);
            check(helper, d.stamina.current() == stamina, "a rejected replacement costs no stamina: " + reason);
            check(helper, d.queuedAttack == AttackType.OVERHEAD && d.queuedVariant == 1 && d.queuedMirrored,
                    "the queued input is preserved");
            check(helper, d.hitThisSwing.contains(123456), "the current swing's hit bookkeeping is preserved");
        }
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void eligibleBuiltInFeintsStillStartAndPayOnce(GameTestHelper helper) {
        for (AttackType type : new AttackType[]{AttackType.JAB, AttackType.KICK}) {
            Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), TestSupport.FACING_POSITIVE_X);
            CombatData d = data(player);
            check(helper, Combat.start(player, d, AttackType.SLASH), "start the slash");
            d.machine.tick();
            d.stamina.set(70);
            d.jabReadyAt = helper.getLevel().getGameTime(); // eligible at the exact cooldown boundary
            if (type == AttackType.KICK) d.holsteredUntil = helper.getLevel().getGameTime() + 6;
            int serial = d.machine.attackSerial();
            float stamina = d.stamina.current();
            check(helper, Combat.feintInto(player, d, type), "an eligible " + type + " replaces the slash");
            check(helper, d.machine.phase() == Phase.WINDUP && d.machine.type() == type
                    && d.machine.attackSerial() == serial + 1, "one new action starts");
            check(helper, Math.abs(d.stamina.current() - (stamina - Config.FEINT_STAMINA_COST.get())) < .001,
                    "the accepted replacement pays its feint cost once");
        }
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void rejectedWindupReplacementSendsAnAuthoritativeCorrection(GameTestHelper helper) {
        ServerPlayer player = DownedGameTests.player(helper, 2.5, 2.5);
        RecordingListener listener = new RecordingListener(player);
        CombatData d = data(player);
        check(helper, Combat.start(player, d, AttackType.SLASH), "start the slash");
        d.machine.tick();
        d.jabReadyAt = helper.getLevel().getGameTime() + 6;
        CombatStatePayload expected = CombatStatePayload.of(player, d, true);
        Combat.requestAttack(player, AttackType.JAB, 0, false);
        check(helper, listener.states.size() == 1, "the owning client receives a correction");
        check(helper, expected.equals(listener.states.getFirst()), "the correction restores the intact authoritative slash");
        helper.succeed();
    }

    /** Capture actual production dispatch to an owning player without needing a socket or negotiated test client. */
    private static final class RecordingListener extends ServerGamePacketListenerImpl {
        private final List<CombatStatePayload> states = new ArrayList<>();

        private RecordingListener(ServerPlayer player) {
            super(player.server, new Connection(PacketFlow.SERVERBOUND), player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false));
        }

        @Override
        public boolean hasChannel(ResourceLocation id) {
            return id.equals(CombatStatePayload.TYPE.id());
        }

        @Override
        public void send(Packet<?> packet) {
            if (packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof CombatStatePayload state) {
                states.add(state);
            }
        }

        @Override
        public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
            send(packet);
        }
    }
}

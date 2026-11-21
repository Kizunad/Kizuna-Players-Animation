package dev.kizuna.playeranimation.server;

import com.google.protobuf.InvalidProtocolBufferException;
import com.mojang.authlib.GameProfile;
import dev.kizuna.playeranimation.network.AnimationPackets;
import dev.kizuna.playeranimation.network.RequiredAnimationChannels;
import dev.kizuna.playeranimation.protocol.pb.PlayerAnimationProtocol.PlayRequest;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.impl.networking.RegistrationPayload;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;

public final class PlayerLoopGameTest {
    private static final String ACTION = "calib_zero";
    private static final List<Identifier> ANIMATION_CHANNELS =
            List.of(AnimationPackets.PLAY.id(), AnimationPackets.STOP.id());
    private static final String MISSING_CHANNELS = "缺少必需的 Players-Animation play / stop / result 通道";
    private static final int ASSERTION_GRACE_TICKS = 20;

    @GameTest(environment = "kpa_gametest:player_loop")
    public void disconnectDuringTickStillSyncsOtherPlayer(GameTestHelper context) {
        var level = context.getLevel();
        // 把会被移除的连接放在健康连接之后，避免活列表恰好静默结束遍历。
        var healthy = TestClient.join(level, "pa-sync");
        var missing = TestClient.join(level, "pa-missing");

        // 先让健康连接欠一次补发，再补齐它的通道。
        String pending = ServerPlayerAnimations.play(healthy.player(), ACTION);
        healthy.receive(new RegistrationPayload(RegistrationPayload.REGISTER, ANIMATION_CHANNELS));
        // 用一次正式发包确认握手，随后撤去一个必需通道，下一次检查就会踢人。
        missing.receive(new RegistrationPayload(RegistrationPayload.REGISTER, ANIMATION_CHANNELS));
        ServerPlayerAnimations.play(missing.player(), ACTION);
        missing.receive(new RegistrationPayload(RegistrationPayload.UNREGISTER, List.of(AnimationPackets.STOP.id())));

        context.runAfterDelay(1, () -> {
            try {
                assertTickOutcome(context, healthy, missing, pending);
                context.succeed();
            } finally {
                healthy.close();
                missing.close();
            }
        });
    }

    @GameTest(maxTicks = RequiredAnimationChannels.NEGOTIATION_TICKS + ASSERTION_GRACE_TICKS,
            environment = "kpa_gametest:player_loop")
    public void broadcastToExpiredConnectionDoesNotCrashTick(GameTestHelper context) {
        var level = context.getLevel();
        var broadcaster = TestClient.join(level, "pa-broadcast");
        var missing = TestClient.join(level, "pa-expired");
        broadcaster.receive(new RegistrationPayload(RegistrationPayload.REGISTER, ANIMATION_CHANNELS));
        ServerPlayerAnimations.play(broadcaster.player(), ACTION);

        context.runAfterDelay(RequiredAnimationChannels.NEGOTIATION_TICKS, () -> {
            try {
                ServerPlayerAnimations.stop(broadcaster.player(), ACTION);
                context.runAfterDelay(1, () -> {
                    try {
                        assertExpiredConnectionOutcome(context, broadcaster, missing);
                        context.succeed();
                    } finally {
                        broadcaster.close();
                        missing.close();
                    }
                });
            } catch (RuntimeException failure) {
                missing.close();
                throw failure;
            }
        });
    }

    private static void assertTickOutcome(GameTestHelper context, TestClient healthy, TestClient missing,
                                          String pending) {
        var players = context.getLevel().getServer().getPlayerList();
        context.assertTrue(players.getPlayer(missing.player().getUUID()) == null,
                "缺少通道的连接应已从玩家列表移除");
        context.assertTrue(players.getPlayer(healthy.player().getUUID()) == healthy.player(),
                "健康连接应继续在线");
        boolean hasReason = missing.channel().outboundMessages().stream()
                .filter(ClientboundDisconnectPacket.class::isInstance)
                .map(ClientboundDisconnectPacket.class::cast)
                .anyMatch(packet -> packet.reason().getString().equals(MISSING_CHANNELS));
        context.assertTrue(hasReason, "断线包应说明缺少哪些必需动画通道");
        context.assertTrue(healthy.channel().outboundMessages().stream().anyMatch(packet -> isPlay(packet, pending)),
                "健康连接应在同一 tick 收到原请求的补发");
    }

    private static void assertExpiredConnectionOutcome(GameTestHelper context, TestClient broadcaster,
                                                        TestClient missing) {
        var players = context.getLevel().getServer().getPlayerList();
        context.assertTrue(players.getPlayer(missing.player().getUUID()) == null,
                "握手到期且缺通道的连接应被踢出");
        context.assertTrue(players.getPlayer(broadcaster.player().getUUID()) == broadcaster.player(),
                "广播方连接应继续在线");
        boolean hasReason = missing.channel().outboundMessages().stream()
                .filter(ClientboundDisconnectPacket.class::isInstance)
                .map(ClientboundDisconnectPacket.class::cast)
                .anyMatch(packet -> packet.reason().getString().equals(MISSING_CHANNELS));
        context.assertTrue(hasReason, "断线包应说明握手缺少必需动画通道");
    }

    private static boolean isPlay(Object packet, String requestId) {
        if (!(packet instanceof ClientboundCustomPayloadPacket custom)
                || !(custom.payload() instanceof AnimationPackets.Payload payload)
                || !payload.type().equals(AnimationPackets.PLAY)) {
            return false;
        }
        try {
            return AnimationPackets.read(payload, PlayRequest.parser()).getRequestId().equals(requestId);
        } catch (InvalidProtocolBufferException exception) {
            throw new IllegalStateException("动画播放包无法解码", exception);
        }
    }

    private record TestClient(ServerPlayer player, EmbeddedChannel channel) {
        static TestClient join(ServerLevel level, String name) {
            // 沿用原版 GameTest 的内存连接，再保留通道用于检查真实的出站包。
            var server = level.getServer();
            var profile = new GameProfile(UUID.randomUUID(), name);
            var cookie = CommonListenerCookie.createInitial(profile, false /* transferred */);
            var player = new ServerPlayer(server, level, profile, cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            var channel = new EmbeddedChannel(connection);
            server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setOnGround(true /* onGround */);
            return new TestClient(player, channel);
        }

        void receive(RegistrationPayload payload) {
            channel.writeInbound(new ServerboundCustomPayloadPacket(payload));
        }

        void close() {
            if (channel.isOpen()) {
                player.connection.disconnect(Component.empty());
            }
            channel.finishAndReleaseAll();
        }
    }
}

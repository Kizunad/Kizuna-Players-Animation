package dev.kizuna.playeranimation.client;

import com.google.protobuf.InvalidProtocolBufferException;
import dev.kizuna.playeranimation.network.AnimationPackets;
import dev.kizuna.playeranimation.network.AnimationRequests;
import dev.kizuna.playeranimation.network.RequiredAnimationChannels;
import dev.kizuna.playeranimation.protocol.pb.PlayerAnimationProtocol.PlayRequest;
import dev.kizuna.playeranimation.protocol.pb.PlayerAnimationProtocol.Result;
import dev.kizuna.playeranimation.protocol.pb.PlayerAnimationProtocol.StopRequest;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/** 网络线程只解码；实体查询、资源查询和播放全部安排到客户端主线程。 */
public final class ClientAnimationNetworking {
    private static final List<Result> REPLIES = new ArrayList<>();
    private static RequiredAnimationChannels channels;
    private static long ticks;
    private ClientAnimationNetworking() { }

    public static void register() {
        // 握手中的回执在主线程等待 result 通道，断开连接时不能带入下一服务器。
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            channels = new RequiredAnimationChannels(ticks);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            channels = null;
            REPLIES.clear();
        });
        ClientTickEvents.END_CLIENT_TICK.register(ClientAnimationNetworking::tick);
        ClientPlayNetworking.registerGlobalReceiver(AnimationPackets.PLAY, (client, handler, buffer, sender) -> {
            try {
                PlayRequest request = AnimationPackets.read(buffer, PlayRequest.parser());
                client.execute(() -> {
                    // 排队期间可能断线并加入另一个世界，不把旧连接的包带过去。
                    if (client.getNetworkHandler() == handler) {
                        reply(play(client, request));
                    }
                });
            } catch (InvalidProtocolBufferException | IllegalArgumentException exception) {
                PlayerAnimationClient.LOG.error("动画播放包无法解码: {}", exception.getMessage());
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(AnimationPackets.STOP, (client, handler, buffer, sender) -> {
            try {
                StopRequest request = AnimationPackets.read(buffer, StopRequest.parser());
                client.execute(() -> {
                    if (client.getNetworkHandler() == handler) {
                        reply(stop(request));
                    }
                });
            } catch (InvalidProtocolBufferException | IllegalArgumentException exception) {
                PlayerAnimationClient.LOG.error("动画停止包无法解码: {}", exception.getMessage());
            }
        });
    }

    private static Result play(MinecraftClient client, PlayRequest request) {
        UUID player;
        try {
            player = AnimationRequests.validate(request);
        } catch (IllegalArgumentException exception) {
            return result(request.getRequestId(), Result.Code.INVALID_REQUEST, exception.getMessage());
        }
        // 在边界区分业务错误，不靠异常文字推断返回码。
        if (!PlayerAnimationClient.ASSETS.animations().containsKey(request.getAnimationId())) {
            return result(request.getRequestId(), Result.Code.ANIMATION_NOT_FOUND, "客户端未加载动画");
        }
        if (client.world == null || client.world.getPlayerByUuid(player) == null) {
            return result(request.getRequestId(), Result.Code.PLAYER_NOT_LOADED, "客户端未加载玩家");
        }
        if (request.hasNormalizedProgress()) {
            PlayerAnimationClient.PLAYBACK.scrub(player, request.getAnimationId(), request.getNormalizedProgress());
        } else {
            PlayerAnimationClient.PLAYBACK.play(player, request.getAnimationId(),
                    request.hasStartTick() ? request.getStartTick() : null, request.getRequestId());
        }
        return result(request.getRequestId(), Result.Code.OK, "");
    }

    private static Result stop(StopRequest request) {
        UUID player;
        try {
            player = AnimationRequests.validate(request);
        } catch (IllegalArgumentException exception) {
            return result(request.getRequestId(), Result.Code.INVALID_REQUEST, exception.getMessage());
        }
        // 校验后只剩明确的两种停止范围，未播放的玩家也成功。
        PlayerAnimationClient.PLAYBACK.stop(player,
                request.getTargetCase() == StopRequest.TargetCase.ALL ? null : request.getAnimationId());
        return result(request.getRequestId(), Result.Code.OK, "");
    }

    private static Result result(String requestId, Result.Code code, String detail) {
        // 过长的请求 ID 无法在 32 KiB 回执中原样携带，按不可关联请求返回空 ID。
        if (requestId.length() > AnimationRequests.MAX_REQUEST_ID_LENGTH) {
            requestId = "";
        }
        return Result.newBuilder().setRequestId(requestId).setCode(code).setDetail(detail).build();
    }

    private static void reply(Result result) {
        // 诊断与发送分开；正在协商的回执排队，不丢弃也不承担身体授权。
        if (result.getCode() != Result.Code.OK) {
            PlayerAnimationClient.LOG.error("动画请求 {} 返回 {}: {}", result.getRequestId(), result.getCode(), result.getDetail());
        }
        REPLIES.add(result);
    }

    private static void tick(MinecraftClient client) {
        // 各端只检查自己要发送的通道，双方共同要求 play、stop 和 result 完整接通。
        ticks++;
        if (channels == null || client.getNetworkHandler() == null) {
            return;
        }
        try {
            if (!channels.ready(ticks, ClientPlayNetworking.canSend(AnimationPackets.RESULT))) {
                return;
            }
            for (Result result : REPLIES) {
                ClientPlayNetworking.send(AnimationPackets.RESULT, AnimationPackets.write(result));
            }
            REPLIES.clear();
        } catch (IllegalStateException missingChannels) {
            PlayerAnimationClient.LOG.error("动画接线失败", missingChannels);
            client.getNetworkHandler().getConnection().disconnect(Text.literal(missingChannels.getMessage()));
        }
    }
}

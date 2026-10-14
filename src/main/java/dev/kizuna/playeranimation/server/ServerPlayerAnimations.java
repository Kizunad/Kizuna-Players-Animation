package dev.kizuna.playeranimation.server;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.kizuna.playeranimation.network.AnimationPackets;
import dev.kizuna.playeranimation.network.AnimationRequests;
import dev.kizuna.playeranimation.protocol.pb.PlayerAnimationProtocol.PlayRequest;
import dev.kizuna.playeranimation.protocol.pb.PlayerAnimationProtocol.Result;
import dev.kizuna.playeranimation.protocol.pb.PlayerAnimationProtocol.StopRequest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.S2CPlayChannelEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 服务端主线程入口：本人及实体追踪者接收动画，不向全服广播。 */
public final class ServerPlayerAnimations implements ModInitializer {
    @FunctionalInterface
    public interface ResultListener {
        void onResult(ServerPlayerEntity viewer, Result result);
    }

    public static final Event<ResultListener> RESULT = EventFactory.createArrayBacked(ResultListener.class,
            listeners -> (viewer, result) -> {
                // 客户端回执只作诊断，玩法不得将其当成执行成功或伤害授权。
                for (ResultListener listener : listeners) {
                    listener.onResult(viewer, result);
                }
            });

    private static final Logger LOG = LoggerFactory.getLogger(AnimationPackets.MOD_ID);
    private static final AnimationSyncState STATE = new AnimationSyncState();
    private static final Set<TrackingPair> SYNC = new HashSet<>();
    private static final Map<Receipt, Integer> PENDING = new HashMap<>();
    private static final int RECEIPT_TIMEOUT_TICKS = 20 * 30;
    private record TrackingPair(UUID target, UUID viewer) { }
    private record Receipt(UUID viewer, String request) { }

    @Override
    public void onInitialize() {
        // 通用入口不引用任何 net.minecraft.client 类型，独立服务端也能加载。
        ServerPlayNetworking.registerGlobalReceiver(AnimationPackets.RESULT, (server, viewer, handler, buffer, sender) -> {
            try {
                Result result = AnimationPackets.read(buffer, Result.parser());
                server.execute(() -> {
                    if (PENDING.remove(new Receipt(viewer.getUuid(), result.getRequestId())) == null) {
                        return;
                    }
                    if (result.getCode() != Result.Code.OK) {
                        LOG.warn("客户端 {} 动画请求 {} 返回 {}: {}", viewer.getUuid(), result.getRequestId(),
                                result.getCode(), result.getDetail());
                    }
                    RESULT.invoker().onResult(viewer, result);
                });
            } catch (InvalidProtocolBufferException | IllegalArgumentException exception) {
                LOG.warn("客户端 {} 的动画回执无法解码: {}", viewer.getUuid(), exception.getMessage());
            }
        });
        EntityTrackingEvents.START_TRACKING.register((entity, viewer) -> {
            if (entity instanceof ServerPlayerEntity target) {
                // Fabric 此回调早于实体生成包，tick 末尾才发送补帧请求。
                SYNC.add(new TrackingPair(target.getUuid(), viewer.getUuid()));
            }
        });
        S2CPlayChannelEvents.REGISTER.register((handler, sender, server, channels) -> {
            if (channels.contains(AnimationPackets.PLAY) || channels.contains(AnimationPackets.STOP)) {
                server.execute(() -> {
                    for (UUID target : STATE.players()) {
                        SYNC.add(new TrackingPair(target, handler.player.getUuid()));
                    }
                });
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(ServerPlayerAnimations::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() -> {
            // 离线客户端无需再接收停止包；状态始终回到服务端主线程清理。
            UUID id = handler.player.getUuid();
            STATE.stop(id, null);
            SYNC.removeIf(pair -> pair.target.equals(id) || pair.viewer.equals(id));
            PENDING.keySet().removeIf(receipt -> receipt.viewer.equals(id));
        }));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> stopAll(player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> stopAll(newPlayer));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            STATE.clear();
            SYNC.clear();
            PENDING.clear();
        });
        registerCommands();
    }

    public static String play(ServerPlayerEntity player, String animation) {
        requireServerThread(player);
        AnimationRequests.animationId(animation);
        String requestId = UUID.randomUUID().toString();
        PlayRequest request = PlayRequest.newBuilder().setRequestId(requestId).setPlayerUuid(player.getUuidAsString())
                .setAnimationId(animation).setStartTick(player.getWorld().getTime()).build();
        // 先通过载荷边界，避免发送失败却留下无法补发的登记状态。
        checkSize(request);
        STATE.play(player.getUuid(), request);
        broadcast(player, AnimationPackets.PLAY, request, requestId);
        return requestId;
    }

    public static String stop(ServerPlayerEntity player, String animation) {
        AnimationRequests.animationId(animation);
        return stopRequest(player, animation);
    }

    public static String stopAll(ServerPlayerEntity player) {
        return stopRequest(player, null);
    }

    private static String stopRequest(ServerPlayerEntity player, String animation) {
        requireServerThread(player);
        String requestId = UUID.randomUUID().toString();
        var builder = StopRequest.newBuilder().setRequestId(requestId).setPlayerUuid(player.getUuidAsString());
        // 指定动作和停止全部使用明确的 oneof，不用空 ID 表示全部。
        if (animation == null) {
            builder.setAll(true);
        } else {
            builder.setAnimationId(animation);
        }
        StopRequest request = builder.build();
        checkSize(request);
        STATE.stop(player.getUuid(), animation);
        broadcast(player, AnimationPackets.STOP, request, requestId);
        return requestId;
    }

    private static void broadcast(ServerPlayerEntity target, Identifier channel, MessageLite message, String requestId) {
        Set<ServerPlayerEntity> viewers = new HashSet<>(PlayerLookup.tracking(target));
        viewers.add(target);
        // tracking 本身限制维度和可见范围；集合避免本人被重复发送。
        for (ServerPlayerEntity viewer : viewers) {
            send(viewer, channel, message, requestId);
        }
    }

    private static void send(ServerPlayerEntity viewer, Identifier channel, MessageLite message, String requestId) {
        // 两条下行通道都声明后才启用，防止能播放却无法停止的半接入状态。
        if (!ServerPlayNetworking.canSend(viewer, AnimationPackets.PLAY)
                || !ServerPlayNetworking.canSend(viewer, AnimationPackets.STOP)) {
            return;
        }
        PENDING.put(new Receipt(viewer.getUuid(), requestId), viewer.getServer().getTicks() + RECEIPT_TIMEOUT_TICKS);
        ServerPlayNetworking.send(viewer, channel, AnimationPackets.write(message));
    }

    private static void tick(MinecraftServer server) {
        // 同一个 tick 内重复的追踪/通道事件只补发一次，并重新检查实体仍可见。
        for (TrackingPair pair : Set.copyOf(SYNC)) {
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(pair.target);
            ServerPlayerEntity viewer = server.getPlayerManager().getPlayer(pair.viewer);
            if (target == null || viewer == null || (target != viewer && !PlayerLookup.tracking(target).contains(viewer))) {
                continue;
            }
            for (PlayRequest saved : STATE.snapshot(pair.target)) {
                // 沿用原始请求 ID 和起点，客户端可区分重复补发与同 tick 重播。
                send(viewer, AnimationPackets.PLAY, saved, saved.getRequestId());
            }
        }
        SYNC.clear();
        PENDING.values().removeIf(deadline -> server.getTicks() >= deadline);
    }

    private static void requireServerThread(ServerPlayerEntity player) {
        // 公共状态仅由服务端主线程持有，调用方异步任务应使用 server.execute。
        if (!player.getServer().isOnThread()) {
            throw new IllegalStateException("动画同步必须在服务端主线程调用");
        }
    }

    private static void checkSize(MessageLite message) {
        // 公共 API 输入也受协议载荷上限约束。
        if (message.getSerializedSize() > AnimationPackets.MAX_BYTES) {
            throw new IllegalArgumentException("动画包超过 32 KiB");
        }
    }

    private static void registerCommands() {
        // 调试广播只开放给管理员，不允许普通客户端控制其他玩家。
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
                CommandManager.literal("kpa_server").requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.literal("play")
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .then(CommandManager.argument("animation", StringArgumentType.word())
                                                .executes(context -> {
                                                    String id = play(EntityArgumentType.getPlayer(context, "player"),
                                                            StringArgumentType.getString(context, "animation"));
                                                    context.getSource().sendFeedback(() -> Text.literal("已发送动画播放请求: " + id), false);
                                                    return 1;
                                                }))))
                        .then(CommandManager.literal("stop")
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .executes(context -> {
                                            stopAll(EntityArgumentType.getPlayer(context, "player"));
                                            return 1;
                                        })
                                        .then(CommandManager.argument("animation", StringArgumentType.word())
                                                .executes(context -> {
                                                    stop(EntityArgumentType.getPlayer(context, "player"),
                                                            StringArgumentType.getString(context, "animation"));
                                                    return 1;
                                                }))))));
    }
}

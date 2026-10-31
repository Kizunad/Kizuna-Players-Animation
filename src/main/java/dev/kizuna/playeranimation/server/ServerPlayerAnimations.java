package dev.kizuna.playeranimation.server;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.kizuna.playeranimation.network.AnimationPackets;
import dev.kizuna.playeranimation.network.AnimationRequests;
import dev.kizuna.playeranimation.network.RequiredAnimationChannels;
import dev.kizuna.playeranimation.animation.PlayerAnimationAction;
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
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 服务端主线程入口：本人及实体追踪者接收动画，不向全服广播。 */
public final class ServerPlayerAnimations implements ModInitializer {
    @FunctionalInterface
    public interface ResultListener {
        void onResult(ServerPlayer viewer, Result result);
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
    private static final Map<UUID, RequiredAnimationChannels> CONNECTIONS = new HashMap<>();
    private static final Map<PlaybackKey, Long> FINITE_EXPIRIES = new HashMap<>();
    private static final VanillaAnimationScheduler VANILLA = new VanillaAnimationScheduler();
    private static final int RECEIPT_TIMEOUT_TICKS = 20 * 30;
    private record TrackingPair(UUID target, UUID viewer) { }
    private record Receipt(UUID viewer, String request) { }
    private record PlaybackKey(UUID player, String animation) { }

    @Override
    public void onInitialize() {
        // 通用入口不引用任何 net.minecraft.client 类型，独立服务端也能加载。
        ServerPlayNetworking.registerGlobalReceiver(AnimationPackets.RESULT, (server, viewer, handler, buffer, sender) -> {
            try {
                Result result = AnimationPackets.read(buffer, Result.parser());
                server.execute(() -> {
                    if (PENDING.remove(new Receipt(viewer.getUUID(), result.getRequestId())) == null) {
                        return;
                    }
                    if (result.getCode() != Result.Code.OK) {
                        LOG.warn("客户端 {} 动画请求 {} 返回 {}: {}", viewer.getUUID(), result.getRequestId(),
                                result.getCode(), result.getDetail());
                    }
                    RESULT.invoker().onResult(viewer, result);
                });
            } catch (InvalidProtocolBufferException | IllegalArgumentException exception) {
                LOG.warn("客户端 {} 的动画回执无法解码: {}", viewer.getUUID(), exception.getMessage());
            }
        });
        EntityTrackingEvents.START_TRACKING.register((entity, viewer) -> {
            if (entity instanceof ServerPlayer target) {
                // Fabric 此回调早于实体生成包，tick 末尾才发送补帧请求。
                SYNC.add(new TrackingPair(target.getUUID(), viewer.getUUID()));
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                CONNECTIONS.putIfAbsent(handler.player.getUUID(), new RequiredAnimationChannels(server.getTickCount())));
        S2CPlayChannelEvents.REGISTER.register((handler, sender, server, channels) -> {
            if (channels.contains(AnimationPackets.PLAY) || channels.contains(AnimationPackets.STOP)) {
                server.execute(() -> {
                    for (UUID target : STATE.players()) {
                        SYNC.add(new TrackingPair(target, handler.player.getUUID()));
                    }
                });
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(ServerPlayerAnimations::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() -> {
            // 离线客户端无需再接收停止包；状态始终回到服务端主线程清理。
            UUID id = handler.player.getUUID();
            STATE.stop(id, null);
            FINITE_EXPIRIES.keySet().removeIf(key -> key.player.equals(id));
            VANILLA.clear(id);
            SYNC.removeIf(pair -> pair.target.equals(id) || pair.viewer.equals(id));
            PENDING.keySet().removeIf(receipt -> receipt.viewer.equals(id));
            CONNECTIONS.remove(id);
        }));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            VANILLA.clear(player.getUUID());
            stopAll(player);
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            VANILLA.clear(oldPlayer.getUUID());
            VANILLA.clear(newPlayer.getUUID());
            stopAll(newPlayer);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            STATE.clear();
            FINITE_EXPIRIES.clear();
            SYNC.clear();
            PENDING.clear();
            CONNECTIONS.clear();
            VANILLA.clear();
        });
        registerCommands();
    }

    public static String play(ServerPlayer player, String animation) {
        // 普通播放从当前世界 tick 开始。
        return playFrom(player, animation, player.level().getGameTime());
    }

    public static String playFrom(ServerPlayer player, String animation, long startTick) {
        // 只登记一次起点，后续追踪者重用请求，不重启已经在播放的循环。
        requireServerThread(player);
        AnimationRequests.animationId(animation);
        if (startTick < 0 || startTick > player.level().getGameTime()) {
            throw new IllegalArgumentException("起播 tick 必须位于当前世界时间以内");
        }
        String requestId = UUID.randomUUID().toString();
        PlayRequest request = PlayRequest.newBuilder()
                .setRequestId(requestId)
                .setPlayerUuid(player.getStringUUID())
                .setAnimationId(animation)
                .setStartTick(startTick)
                .build();
        // 先通过载荷边界，避免发送失败却留下无法补发的登记状态。
        checkSize(request);
        STATE.play(player.getUUID(), request);
        scheduleExpiry(player, animation, request.getStartTick());
        broadcast(player, AnimationPackets.PLAY, request, requestId);
        return requestId;
    }

    public static String play(ServerPlayer player, PlayerAnimationAction action) {
        return play(player, action.id());
    }

    /**
     * 发布原版状态的当前归一化进度。服务端状态仍保留在同一个动作 ID 下，客户端收到后
     * 只移动 scrub 游标，不按本地时钟自由播放；晚加入观察者直接收到最新进度。
     */
    public static String scrub(ServerPlayer player, String animation, float normalizedProgress) {
        requireServerThread(player);
        AnimationRequests.animationId(animation);
        if (!Float.isFinite(normalizedProgress) || normalizedProgress < 0 || normalizedProgress > 1) {
            throw new IllegalArgumentException("normalized_progress 必须在 [0,1]");
        }
        String requestId = UUID.randomUUID().toString();
        PlayRequest request = PlayRequest.newBuilder().setRequestId(requestId)
                .setPlayerUuid(player.getStringUUID()).setAnimationId(animation)
                .setStartTick(player.level().getGameTime()).setNormalizedProgress(normalizedProgress).build();
        checkSize(request);
        STATE.play(player.getUUID(), request);
        FINITE_EXPIRIES.remove(new PlaybackKey(player.getUUID(), animation));
        broadcast(player, AnimationPackets.PLAY, request, requestId);
        return requestId;
    }

    public static String scrub(ServerPlayer player, PlayerAnimationAction action, float normalizedProgress) {
        return scrub(player, action.id(), normalizedProgress);
    }

    public static String stop(ServerPlayer player, String animation) {
        // 调用方只停止自己的动作，其他动作的登记保持不变。
        AnimationRequests.animationId(animation);
        return stopRequest(player, animation);
    }

    public static String stop(ServerPlayer player, PlayerAnimationAction action) {
        return stop(player, action.id());
    }

    public static String stopAll(ServerPlayer player) {
        // 世界与实体生命周期切换需要清除全部旧姿态。
        return stopRequest(player, null);
    }

    private static String stopRequest(ServerPlayer player, String animation) {
        requireServerThread(player);
        String requestId = UUID.randomUUID().toString();
        var builder = StopRequest.newBuilder().setRequestId(requestId).setPlayerUuid(player.getStringUUID());
        // 指定动作和停止全部使用明确的 oneof，不用空 ID 表示全部。
        if (animation == null) {
            builder.setAll(true);
        } else {
            builder.setAnimationId(animation);
        }
        StopRequest request = builder.build();
        checkSize(request);
        STATE.stop(player.getUUID(), animation);
        if (animation == null) {
            FINITE_EXPIRIES.keySet().removeIf(key -> key.player.equals(player.getUUID()));
        } else {
            FINITE_EXPIRIES.remove(new PlaybackKey(player.getUUID(), animation));
        }
        broadcast(player, AnimationPackets.STOP, request, requestId);
        return requestId;
    }

    private static void broadcast(ServerPlayer target, ResourceLocation channel, MessageLite message, String requestId) {
        Set<ServerPlayer> viewers = new HashSet<>(PlayerLookup.tracking(target));
        viewers.add(target);
        // tracking 本身限制维度和可见范围；集合避免本人被重复发送。
        for (ServerPlayer viewer : viewers) {
            if (!viewer.connection.isAcceptingMessages()) {
                continue;
            }
            if (!channelsReady(viewer)) {
                // 协商时只保留最终活动快照；期间已停止的动作不会在接通后重新播放。
                SYNC.add(new TrackingPair(target.getUUID(), viewer.getUUID()));
                continue;
            }
            send(viewer, channel, message, requestId);
        }
    }

    private static void send(ServerPlayer viewer, ResourceLocation channel, MessageLite message, String requestId) {
        // 调用点已完成整组通道校验，不在实际发送处吞掉缺接线错误。
        PENDING.put(new Receipt(viewer.getUUID(), requestId), viewer.getServer().getTickCount() + RECEIPT_TIMEOUT_TICKS);
        ServerPlayNetworking.send(viewer, channel, AnimationPackets.write(message));
    }

    private static void tick(MinecraftServer server) {
        expireFinite(server);
        VANILLA.tick(server);
        // 即使玩家尚未播放动作，也要让缺少必需通道的连接明确失败。
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (!viewer.connection.isAcceptingMessages()) {
                continue;
            }
            try {
                channelsReady(viewer);
            } catch (IllegalStateException missingChannels) {
                LOG.error("玩家 {} 动画接线失败", viewer.getUUID(), missingChannels);
                viewer.connection.disconnect(Component.literal(missingChannels.getMessage()));
            }
        }
        // 同一个 tick 内重复的追踪/通道事件只补发一次，并重新检查实体仍可见。
        for (TrackingPair pair : Set.copyOf(SYNC)) {
            ServerPlayer target = server.getPlayerList().getPlayer(pair.target);
            ServerPlayer viewer = server.getPlayerList().getPlayer(pair.viewer);
            if (target == null || viewer == null || !viewer.connection.isAcceptingMessages()
                    || (target != viewer && !PlayerLookup.tracking(target).contains(viewer))) {
                SYNC.remove(pair);
                continue;
            }
            if (!channelsReady(viewer)) {
                continue;
            }
            for (PlayRequest saved : STATE.snapshot(pair.target)) {
                // 沿用原始请求 ID 和起点，客户端可区分重复补发与同 tick 重播。
                send(viewer, AnimationPackets.PLAY, saved, saved.getRequestId());
            }
            SYNC.remove(pair);
        }
        PENDING.values().removeIf(deadline -> server.getTickCount() >= deadline);
    }

    private static void scheduleExpiry(ServerPlayer player, String animation, long startTick) {
        PlayerAnimationAction action = PlayerAnimationAction.find(animation);
        PlaybackKey key = new PlaybackKey(player.getUUID(), animation);
        if (action == null || !action.naturallyEnds()) {
            // 循环、保持和索引之外的动作必须由对应状态结束或调用方显式停止。
            FINITE_EXPIRIES.remove(key);
            return;
        }
        FINITE_EXPIRIES.put(key, startTick + action.naturalDurationTicks());
    }

    private static void expireFinite(MinecraftServer server) {
        for (var entry : new HashMap<>(FINITE_EXPIRIES).entrySet()) {
            PlaybackKey key = entry.getKey();
            ServerPlayer player = server.getPlayerList().getPlayer(key.player);
            if (player == null) {
                FINITE_EXPIRIES.remove(key);
                continue;
            }
            if (player.level().getGameTime() < entry.getValue()) {
                continue;
            }
            boolean active = STATE.snapshot(key.player).stream()
                    .anyMatch(request -> request.getAnimationId().equals(key.animation));
            if (!active) {
                FINITE_EXPIRIES.remove(key);
                continue;
            }
            // 到达资源末帧后统一走同一 stop 协议，观察者与晚加入者都能看到清理后的快照。
            stopRequest(player, key.animation);
        }
    }

    private static boolean channelsReady(ServerPlayer viewer) {
        // 网络回调与 JOIN 的先后不固定，第一次使用同一连接时建立一次握手期限。
        RequiredAnimationChannels channels = CONNECTIONS.computeIfAbsent(viewer.getUUID(),
                ignored -> new RequiredAnimationChannels(viewer.getServer().getTickCount()));
        boolean complete = ServerPlayNetworking.canSend(viewer, AnimationPackets.PLAY)
                && ServerPlayNetworking.canSend(viewer, AnimationPackets.STOP);
        return channels.ready(viewer.getServer().getTickCount(), complete);
    }

    private static void requireServerThread(ServerPlayer player) {
        // 公共状态仅由服务端主线程持有，调用方异步任务应使用 server.execute。
        if (!player.getServer().isSameThread()) {
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
                Commands.literal("kpa_server").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("play")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("animation", StringArgumentType.word())
                                                .executes(context -> {
                                                    String id = play(EntityArgument.getPlayer(context, "player"),
                                                            StringArgumentType.getString(context, "animation"));
                                                    context.getSource().sendSuccess(() -> Component.literal("已发送动画播放请求: " + id), false);
                                                    return 1;
                                                }))))
                        .then(Commands.literal("stop")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> {
                                            stopAll(EntityArgument.getPlayer(context, "player"));
                                            return 1;
                                        })
                                        .then(Commands.argument("animation", StringArgumentType.word())
                                                .executes(context -> {
                                                    stop(EntityArgument.getPlayer(context, "player"),
                                                            StringArgumentType.getString(context, "animation"));
                                                    return 1;
                                                }))))));
    }
}

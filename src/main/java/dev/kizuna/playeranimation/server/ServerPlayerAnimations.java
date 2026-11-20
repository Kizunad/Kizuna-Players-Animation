package dev.kizuna.playeranimation.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kizuna.playeranimation.network.AnimationPackets;
import dev.kizuna.playeranimation.animation.AnimationPlayer;
import dev.kizuna.playeranimation.network.AnimationRequests;
import dev.kizuna.playeranimation.network.RequiredAnimationChannels;
import dev.kizuna.playeranimation.animation.JsonInput;
import dev.kizuna.playeranimation.animation.PlayerAnimationAction;
import dev.kizuna.playeranimation.server.AnimationSyncState.Audience;
import dev.kizuna.playeranimation.protocol.pb.PlayerAnimationProtocol.PlayRequest;
import dev.kizuna.playeranimation.protocol.pb.PlayerAnimationProtocol.Result;
import dev.kizuna.playeranimation.protocol.pb.PlayerAnimationProtocol.StopRequest;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ClientboundPlayChannelEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
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
    private static final ScrubRetries SCRUB_RETRIES = new ScrubRetries();
    private static final Map<UUID, RequiredAnimationChannels> CONNECTIONS = new HashMap<>();
    private static final Map<PlaybackKey, Long> FINITE_EXPIRIES = new HashMap<>();
    private static final VanillaAnimationScheduler VANILLA = new VanillaAnimationScheduler();
    private static final int RECEIPT_TIMEOUT_TICKS = 20 * 30;
    /** scrub 进度没变时隔这么多 tick 才重发一次；重发只为让清过播放表的客户端（如资源重载）重新摆好姿态。 */
    static final int SCRUB_REFRESH_TICKS = 20;
    private static final String ANIMATION_INDEX = "assets/" + AnimationPackets.MOD_ID + "/player_anim_index.json";
    private record TrackingPair(UUID target, UUID viewer) { }
    private record Receipt(UUID viewer, String request) { }
    private record PlaybackKey(UUID player, String animation) { }

    @Override
    public void onInitialize() {
        // 通用入口不引用任何 net.minecraft.client 类型，独立服务端也能加载。
        AnimationPackets.register();
        // 26.2 的收包回调已在服务端主线程，解码后直接核对待回执。
        ServerPlayNetworking.registerGlobalReceiver(AnimationPackets.RESULT, (payload, context) -> {
            ServerPlayer viewer = context.player();
            try {
                Result result = AnimationPackets.read(payload, Result.parser());
                if (PENDING.remove(new Receipt(viewer.getUUID(), result.getRequestId())) == null) {
                    // scrub 不登记待回执，客户端只在没摆上时回失败：记下来，本 tick 末重发，不等刷新间隔。
                    SCRUB_RETRIES.failed(viewer.getUUID(), result);
                    return;
                }
                if (result.getCode() != Result.Code.OK) {
                    LOG.warn("客户端 {} 动画请求 {} 返回 {}: {}", viewer.getUUID(), result.getRequestId(),
                            result.getCode(), result.getDetail());
                }
                RESULT.invoker().onResult(viewer, result);
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
        ClientboundPlayChannelEvents.REGISTER.register((handler, sender, server, channels) -> {
            if (channels.contains(AnimationPackets.PLAY.id()) || channels.contains(AnimationPackets.STOP.id())) {
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
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
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
            SCRUB_RETRIES.clear();
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
        return playFrom(player, animation, startTick, 1);
    }

    public static String playFrom(ServerPlayer player, String animation, long startTick, float rate) {
        return playFrom(player, animation, startTick, rate, Audience.EVERYONE);
    }

    /**
     * 本人客户端已经自己播放的目录动作：校验、登记、到期和补发同 {@link #play(ServerPlayer, PlayerAnimationAction)}，
     * 但播放包、补发和这段动作的停止都只发给追踪者，不发本人。
     */
    public static String playForTrackers(ServerPlayer player, PlayerAnimationAction action) {
        return playFrom(player, action.id(), player.level().getGameTime(), 1, Audience.TRACKERS);
    }

    private static String playFrom(ServerPlayer player, String animation, long startTick, float rate,
                                   Audience audience) {
        // 只登记一次起点，后续追踪者重用请求，不重启已经在播放的循环。
        requireServerThread(player);
        AnimationRequests.animationId(animation);
        AnimationPlayer.validatePlaybackRate(rate);
        if (startTick < 0 || startTick > player.level().getGameTime()) {
            throw new IllegalArgumentException("起播 tick 必须位于当前世界时间以内");
        }
        String requestId = UUID.randomUUID().toString();
        PlayRequest.Builder builder = PlayRequest.newBuilder()
                .setRequestId(requestId)
                .setPlayerUuid(player.getStringUUID())
                .setAnimationId(animation)
                .setStartTick(startTick);
        if (rate != 1) {
            builder.setPlaybackRate(rate);
        }
        PlayRequest request = builder.build();
        // 先通过载荷边界，避免发送失败却留下无法补发的登记状态。
        checkSize(request);
        STATE.play(player.getUUID(), request, audience);
        scheduleExpiry(player, animation, request.getStartTick(), rate);
        broadcast(player, AnimationPackets.PLAY, request, requestId, audience);
        return requestId;
    }

    public static String play(ServerPlayer player, PlayerAnimationAction action) {
        return play(player, action.id());
    }

    public static String play(ServerPlayer player, PlayerAnimationAction action, float rate) {
        return playFrom(player, action.id(), player.level().getGameTime(), rate);
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
        long now = player.level().getGameTime();
        PlayRequest previous = STATE.saved(player.getUUID(), animation);
        if (previous != null && previous.hasNormalizedProgress()
                && previous.getNormalizedProgress() == normalizedProgress
                && now >= previous.getStartTick() && now - previous.getStartTick() < SCRUB_REFRESH_TICKS) {
            // 进度没变：客户端已经停在这个进度上，不重发。登记照旧挪到末尾，晚加入的观察者补发顺序不变。
            STATE.play(player.getUUID(), previous);
            return previous.getRequestId();
        }
        String requestId = UUID.randomUUID().toString();
        PlayRequest request = PlayRequest.newBuilder().setRequestId(requestId)
                .setPlayerUuid(player.getStringUUID()).setAnimationId(animation)
                .setStartTick(now).setNormalizedProgress(normalizedProgress).build();
        checkSize(request);
        STATE.play(player.getUUID(), request);
        FINITE_EXPIRIES.remove(new PlaybackKey(player.getUUID(), animation));
        broadcast(player, AnimationPackets.PLAY, request, requestId, Audience.EVERYONE);
        return requestId;
    }

    public static String scrub(ServerPlayer player, PlayerAnimationAction action, float normalizedProgress) {
        return scrub(player, action.id(), normalizedProgress);
    }

    /** 本模组内置动画索引中的全部 id，按索引顺序；供服务端指令补全，播放请求不据此校验。 */
    public static List<String> animationIds() {
        // 服务端不读取客户端资源包，补全直接取模组自带的索引；缺失或格式错误说明打包已损坏，直接报错。
        Path index = FabricLoader.getInstance().getModContainer(AnimationPackets.MOD_ID)
                .flatMap(mod -> mod.findPath(ANIMATION_INDEX))
                .orElseThrow(() -> new IllegalStateException("缺少内置动画索引: " + ANIMATION_INDEX));
        try (Reader reader = Files.newBufferedReader(index)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            List<String> ids = new ArrayList<>();
            for (JsonElement entry : JsonInput.array(root, "animations")) {
                ids.add(JsonInput.string(entry.getAsJsonObject(), "id"));
            }
            return List.copyOf(ids);
        } catch (IOException exception) {
            throw new UncheckedIOException("读取内置动画索引失败: " + ANIMATION_INDEX, exception);
        }
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
        // 只发追踪者的动作，停止也不发本人；停止全部照旧发给所有人。
        Audience audience = Audience.EVERYONE;
        if (animation != null) {
            audience = STATE.audience(player.getUUID(), animation);
        }
        STATE.stop(player.getUUID(), animation);
        if (animation == null) {
            FINITE_EXPIRIES.keySet().removeIf(key -> key.player.equals(player.getUUID()));
        } else {
            FINITE_EXPIRIES.remove(new PlaybackKey(player.getUUID(), animation));
        }
        broadcast(player, AnimationPackets.STOP, request, requestId, audience);
        return requestId;
    }

    private static void broadcast(ServerPlayer target, CustomPacketPayload.Type<AnimationPackets.Payload> channel,
                                  MessageLite message, String requestId, Audience audience) {
        Set<ServerPlayer> viewers = new HashSet<>(PlayerLookup.tracking(target));
        if (audience == Audience.EVERYONE) {
            viewers.add(target);
        }
        // tracking 本身限制维度和可见范围；集合避免本人被重复发送。本人客户端自己播的动作不发本人。
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

    private static void send(ServerPlayer viewer, CustomPacketPayload.Type<AnimationPackets.Payload> channel,
                             MessageLite message, String requestId) {
        // 调用点已完成整组通道校验，不在实际发送处吞掉缺接线错误。
        // scrub 每 tick 都可能发，客户端摆上了不回执，这里也不等；没摆上的失败回执走 SCRUB_RETRIES。
        if (!(message instanceof PlayRequest play && play.hasNormalizedProgress())) {
            int deadline = viewer.level().getServer().getTickCount() + RECEIPT_TIMEOUT_TICKS;
            PENDING.put(new Receipt(viewer.getUUID(), requestId), deadline);
        }
        ServerPlayNetworking.send(viewer, AnimationPackets.write(channel, message));
    }

    private static void tick(MinecraftServer server) {
        expireFinite(server);
        VANILLA.tick(server);
        // 缺少通道时断线可能同步移除玩家，因此遍历当前玩家的快照。
        for (ServerPlayer viewer : List.copyOf(server.getPlayerList().getPlayers())) {
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
                // 本人客户端自己播的动作不补发给本人。
                if (target == viewer && STATE.audience(pair.target, saved.getAnimationId()) == Audience.TRACKERS) {
                    continue;
                }
                // 沿用原始请求 ID 和起点，客户端可区分重复补发与同 tick 重播。
                send(viewer, AnimationPackets.PLAY, saved, saved.getRequestId());
            }
            SYNC.remove(pair);
        }
        for (ScrubRetries.Retry retry : SCRUB_RETRIES.drain(STATE)) {
            resendScrub(server, retry);
        }
        PENDING.values().removeIf(deadline -> server.getTickCount() >= deadline);
    }

    private static void resendScrub(MinecraftServer server, ScrubRetries.Retry retry) {
        // 和补发一样重新检查：观察者还在线、通道接通、目标仍可见；本人客户端自己播的动作不发本人。
        PlayRequest saved = retry.request();
        UUID targetId = UUID.fromString(saved.getPlayerUuid());
        ServerPlayer target = server.getPlayerList().getPlayer(targetId);
        ServerPlayer viewer = server.getPlayerList().getPlayer(retry.viewer());
        if (target == null || viewer == null || !viewer.connection.isAcceptingMessages()
                || (target != viewer && !PlayerLookup.tracking(target).contains(viewer))
                || !channelsReady(viewer)) {
            return;
        }
        if (target == viewer && STATE.audience(targetId, saved.getAnimationId()) == Audience.TRACKERS) {
            return;
        }
        send(viewer, AnimationPackets.PLAY, saved, saved.getRequestId());
    }

    private static void scheduleExpiry(ServerPlayer player, String animation, long startTick, float rate) {
        PlayerAnimationAction action = PlayerAnimationAction.find(animation);
        PlaybackKey key = new PlaybackKey(player.getUUID(), animation);
        if (action == null || !action.naturallyEnds()) {
            // 循环、保持和索引之外的动作必须由对应状态结束或调用方显式停止。
            FINITE_EXPIRIES.remove(key);
            return;
        }
        FINITE_EXPIRIES.put(key, expiryTick(action, startTick, rate));
    }

    static long expiryTick(PlayerAnimationAction action, long startTick, float rate) {
        // 向上取整保证末帧可见；极小倍率饱和到最大 tick，避免溢出后立即到期。
        long duration = (long) Math.ceil(action.naturalDurationTicks() / (double) rate);
        if (duration > Long.MAX_VALUE - startTick) {
            return Long.MAX_VALUE;
        }
        return startTick + duration;
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
                ignored -> new RequiredAnimationChannels(viewer.level().getServer().getTickCount()));
        boolean complete = ServerPlayNetworking.canSend(viewer, AnimationPackets.PLAY)
                && ServerPlayNetworking.canSend(viewer, AnimationPackets.STOP);
        return channels.ready(viewer.level().getServer().getTickCount(), complete);
    }

    private static void requireServerThread(ServerPlayer player) {
        // 公共状态仅由服务端主线程持有，调用方异步任务应使用 server.execute。
        if (!player.level().getServer().isSameThread()) {
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
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
            // 每次注册读一次内置索引，play 与 stop 的动画参数补全同一份 id。
            List<String> animations = animationIds();
            SuggestionProvider<CommandSourceStack> suggestAnimations =
                    (context, builder) -> SharedSuggestionProvider.suggest(animations, builder);
            dispatcher.register(Commands.literal("kpa_server")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.literal("play")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .then(Commands.argument("animation", StringArgumentType.word())
                                            .suggests(suggestAnimations)
                                            .executes(context -> playCommand(context, 1))
                                            .then(Commands.argument("rate",
                                                            FloatArgumentType.floatArg(Float.MIN_VALUE, 1))
                                                    .executes(context -> playCommand(context,
                                                            FloatArgumentType.getFloat(context, "rate")))))))
                    .then(Commands.literal("stop")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(context -> {
                                        stopAll(EntityArgument.getPlayer(context, "player"));
                                        return 1;
                                    })
                                    .then(Commands.argument("animation", StringArgumentType.word())
                                            .suggests(suggestAnimations)
                                            .executes(context -> {
                                                stop(EntityArgument.getPlayer(context, "player"),
                                                        StringArgumentType.getString(context, "animation"));
                                                return 1;
                                            })))));
        });
    }

    private static int playCommand(CommandContext<CommandSourceStack> context, float rate)
            throws CommandSyntaxException {
        // 指令与宿主 API 共用校验、同步和到期路径。
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        String animation = StringArgumentType.getString(context, "animation");
        String id = playFrom(player, animation, player.level().getGameTime(), rate);
        context.getSource().sendSuccess(() -> Component.literal("已发送动画播放请求: " + id),
                false /* broadcastToAdmins */);
        return 1;
    }
}

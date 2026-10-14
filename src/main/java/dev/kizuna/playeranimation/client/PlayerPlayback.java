package dev.kizuna.playeranimation.client;

import dev.kizuna.playeranimation.animation.AnimationPlayer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Arm;

public final class PlayerPlayback {
    private final Map<UUID, AnimationPlayer> players = new HashMap<>();
    private final Map<UUID, PlayerMeshRenderer.Frame> frames = new HashMap<>();
    private ClientWorld world;

    public AnimationPlayer player(UUID id) { return players.get(id); }

    public void play(UUID playerId, String animationId) {
        play(playerId, animationId, null, null);
    }

    public void play(UUID playerId, String animationId, Long startTick, String playId) {
        MinecraftClient client = MinecraftClient.getInstance();
        prepareWorld(client);
        var entry = PlayerAnimationClient.ASSETS.animations().get(animationId);
        // 公共入口只处理已加载实体与已加载动作，错误直接返回调用方。
        if (entry == null) {
            throw new IllegalArgumentException("未加载动画: " + animationId);
        }
        var entity = client.world == null ? null : client.world.getPlayerByUuid(playerId);
        if (!(entity instanceof AbstractClientPlayerEntity player)) {
            throw new IllegalArgumentException("客户端未加载玩家: " + playerId);
        }
        AnimationPlayer playback = players.computeIfAbsent(playerId, ignored -> new AnimationPlayer());
        if (startTick == null) {
            playback.play(entry, now(0), player.getMainArm() == Arm.LEFT);
        } else {
            playback.synchronize(entry, now(0), startTick / 20d, player.getMainArm() == Arm.LEFT, playId);
        }
    }

    public void stop(UUID playerId, String animationId) {
        AnimationPlayer player = players.get(playerId);
        // 未播放的实体无需创建空控制器。
        if (player != null) {
            player.stop(animationId, now(0));
        }
    }

    public void tick(MinecraftClient client) {
        prepareWorld(client);
        // 渲染帧只在同一次本体与物品绘制之间共享，不持有离开视野的实体。
        frames.clear();
        if (client.world == null) {
            clear();
            return;
        }
        double time = now(0);
        // 事件回调允许启动新动画，用当前实体快照推进，避免改变本次遍历结构。
        for (var entry : new ArrayList<>(players.entrySet())) {
            UUID id = entry.getKey();
            if (client.world.getPlayerByUuid(id) == null) {
                players.remove(id);
                frames.remove(id);
                continue;
            }
            entry.getValue().advance(time, (animation, event) -> PlayerAnimations.EVENT.invoker().onEvent(id, animation, event));
            if (entry.getValue().empty()) {
                players.remove(id);
            }
        }
    }

    public static double now(float tickDelta) {
        // 世界总 tick 不受 /time set 昼夜时间影响，暂停时也不会继续推进动画。
        var world = MinecraftClient.getInstance().world;
        return world == null ? 0 : (world.getTime() + tickDelta) / 20d;
    }

    public void clear() {
        // 断线与资源重载必须同时清理动画和用于挂载手持物的帧数据。
        players.clear();
        frames.clear();
    }

    public void remove(UUID player) {
        // 实体卸载立即清理，防止同一 tick 重入视野时继承旧实例。
        players.remove(player);
        frames.remove(player);
    }

    private void prepareWorld(MinecraftClient client) {
        // 换维度时 UUID 仍相同，但时间和实体集合已改变。
        if (world != client.world) {
            clear();
            world = client.world;
        }
    }

    public void beginRender(UUID id) { frames.remove(id); }
    public void frame(UUID id, PlayerMeshRenderer.Frame frame) { frames.put(id, frame); }
    public PlayerMeshRenderer.Frame frame(UUID id) { return frames.get(id); }
}

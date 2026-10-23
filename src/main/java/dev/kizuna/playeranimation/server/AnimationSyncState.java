package dev.kizuna.playeranimation.server;

import dev.kizuna.playeranimation.protocol.pb.PlayerAnimationProtocol.PlayRequest;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 服务端保存控制状态，不加载皮肤、骨架或客户端动画资源。 */
public final class AnimationSyncState {
    private final Map<UUID, LinkedHashMap<String, PlayRequest>> players = new HashMap<>();

    public void play(UUID player, PlayRequest request) {
        var animations = players.computeIfAbsent(player, ignored -> new LinkedHashMap<>());
        // 重播移到末尾，补发时保持相同优先级下的最新动作优先。
        animations.remove(request.getAnimationId());
        animations.put(request.getAnimationId(), request);
    }

    public void stop(UUID player, String animation) {
        var animations = players.get(player);
        // 未登记的停止不创建状态；null 只供内部表示停止全部。
        if (animations == null) {
            return;
        }
        if (animation == null) {
            players.remove(player);
            return;
        }
        animations.remove(animation);
        if (animations.isEmpty()) {
            players.remove(player);
        }
    }

    public List<PlayRequest> snapshot(UUID player) {
        // 返回快照，补发过程不暴露可变登记表。
        var animations = players.get(player);
        return animations == null ? List.of() : List.copyOf(animations.values());
    }

    public Set<UUID> players() { return Set.copyOf(players.keySet()); }

    public void clear() { players.clear(); }
}

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
    /** 登记的播放发给谁：所有人，或只发追踪者（本人客户端已经自己播了）。 */
    public enum Audience { EVERYONE, TRACKERS }

    private record Saved(PlayRequest request, Audience audience) { }

    private final Map<UUID, LinkedHashMap<String, Saved>> players = new HashMap<>();

    public void play(UUID player, PlayRequest request) {
        play(player, request, Audience.EVERYONE);
    }

    public void play(UUID player, PlayRequest request, Audience audience) {
        var animations = players.computeIfAbsent(player, ignored -> new LinkedHashMap<>());
        // 重播移到末尾，补发时保持相同优先级下的最新动作优先。
        animations.remove(request.getAnimationId());
        animations.put(request.getAnimationId(), new Saved(request, audience));
    }

    /** 这名玩家这个动作当前登记的请求，没登记返回 null。 */
    public PlayRequest saved(UUID player, String animation) {
        var animations = players.get(player);
        if (animations == null) {
            return null;
        }
        Saved saved = animations.get(animation);
        if (saved == null) {
            return null;
        }
        return saved.request();
    }

    /** 按请求 ID 找当前登记的 scrub 请求；已被新进度顶掉、已停止，或这个 ID 不是 scrub 时返回 null。 */
    public PlayRequest scrub(String requestId) {
        for (var animations : players.values()) {
            for (Saved saved : animations.values()) {
                PlayRequest request = saved.request();
                if (request.hasNormalizedProgress() && request.getRequestId().equals(requestId)) {
                    return request;
                }
            }
        }
        return null;
    }

    public Audience audience(UUID player, String animation) {
        // 没登记的动作（含已经停掉的）按所有人，停止包照旧发给本人。
        var animations = players.get(player);
        if (animations == null) {
            return Audience.EVERYONE;
        }
        Saved saved = animations.get(animation);
        if (saved == null) {
            return Audience.EVERYONE;
        }
        return saved.audience();
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
        if (animations == null) {
            return List.of();
        }
        return animations.values().stream().map(Saved::request).toList();
    }

    public Set<UUID> players() { return Set.copyOf(players.keySet()); }

    public void clear() { players.clear(); }
}

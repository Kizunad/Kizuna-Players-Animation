package dev.kizuna.playeranimation.network;

import dev.kizuna.playeranimation.protocol.pb.PlayerAnimationProtocol.PlayRequest;
import dev.kizuna.playeranimation.protocol.pb.PlayerAnimationProtocol.StopRequest;
import java.util.UUID;

/** Protobuf 请求的业务边界校验；不依赖 Minecraft 客户端。 */
public final class AnimationRequests {
    public static final int MAX_REQUEST_ID_LENGTH = 128;
    private AnimationRequests() { }

    public static UUID validate(PlayRequest request) {
        // proto3 的缺省值不表示合法请求，必须在接线处明确拒绝。
        UUID player = player(request.getRequestId(), request.getPlayerUuid());
        animationId(request.getAnimationId());
        if (request.hasStartTick() && request.getStartTick() < 0) {
            throw new IllegalArgumentException("start_tick 不能为负数");
        }
        if (request.hasNormalizedProgress()
                && (!Float.isFinite(request.getNormalizedProgress())
                || request.getNormalizedProgress() < 0 || request.getNormalizedProgress() > 1)) {
            throw new IllegalArgumentException("normalized_progress 必须在 [0,1]");
        }
        return player;
    }

    public static UUID validate(StopRequest request) {
        UUID player = player(request.getRequestId(), request.getPlayerUuid());
        // 未知 oneof 在旧版本表现为未设置，不能误当作停止全部。
        switch (request.getTargetCase()) {
            case ANIMATION_ID -> animationId(request.getAnimationId());
            case ALL -> {
                if (!request.getAll()) {
                    throw new IllegalArgumentException("停止全部必须为 all=true");
                }
            }
            case TARGET_NOT_SET -> throw new IllegalArgumentException("缺少停止目标");
        }
        return player;
    }

    public static void animationId(String id) {
        // 与动画索引使用同一组字符，协议只传短 ID。
        if (id == null || !id.matches("[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("无效动画 id");
        }
    }

    private static UUID player(String requestId, String value) {
        if (requestId.isBlank() || requestId.length() > MAX_REQUEST_ID_LENGTH) {
            throw new IllegalArgumentException("request_id 必须为 1-128 个字符");
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("player_uuid 必须为标准带连字符 UUID");
        }
        // UUID.fromString 接受缩短的数字段，这里只接受契约中的标准形式。
        if (!uuid.toString().equalsIgnoreCase(value)) {
            throw new IllegalArgumentException("player_uuid 必须为标准带连字符 UUID");
        }
        return uuid;
    }
}

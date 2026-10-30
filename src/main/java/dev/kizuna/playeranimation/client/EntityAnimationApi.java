package dev.kizuna.playeranimation.client;

import com.google.protobuf.InvalidProtocolBufferException;
import dev.kizuna.playeranimation.animation.AnimationClip;
import dev.kizuna.playeranimation.protocol.pb.EntityAnimationProtocol.EntityAnimationRequest;
import dev.kizuna.playeranimation.protocol.pb.EntityAnimationProtocol.EntityAnimationResponse;
import dev.kizuna.playeranimation.protocol.pb.EntityAnimationProtocol.Play;
import dev.kizuna.playeranimation.protocol.pb.EntityAnimationProtocol.RegisterGeometry;
import dev.kizuna.playeranimation.protocol.pb.EntityAnimationProtocol.Stop;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Entity;

/** 处理附属发来的实体注册、播放和停止请求。 */
public final class EntityAnimationApi {
    private EntityAnimationApi() { }

    public static byte[] exchange(byte[] payload) {
        if (!MinecraftClient.getInstance().isOnThread()) {
            return response("", EntityAnimationResponse.Code.INVALID_REQUEST, "必须在客户端主线程调用").toByteArray();
        }
        try {
            EntityAnimationRequest request = EntityAnimationRequest.parseFrom(payload);
            EntityAnimationResponse response = handle(request);
            return response.toByteArray();
        } catch (InvalidProtocolBufferException | IllegalArgumentException exception) {
            return response("", EntityAnimationResponse.Code.INVALID_REQUEST, exception.getMessage()).toByteArray();
        }
    }

    private static EntityAnimationResponse handle(EntityAnimationRequest request) {
        // 所有实体操作共享主线程与 Protobuf 边界，自动层不走玩家网络协议。
        return switch (request.getOperationCase()) {
            case REGISTER_GEOMETRY -> register(request);
            case PLAY -> play(request);
            case STOP -> stop(request);
            case CLEAR -> clear(request);
            case AUTOMATIC -> automatic(request);
            case OPERATION_NOT_SET -> response(request.getRequestId(), EntityAnimationResponse.Code.INVALID_REQUEST,
                    "缺少操作");
        };
    }

    private static EntityAnimationResponse automatic(EntityAnimationRequest request) {
        // 本地状态也经过同一字节边界，禁止未知资源或非法进度污染正在播放的层。
        var automatic = request.getAutomatic();
        LivingEntity entity = loadedEntity(automatic.getEntityId());
        if (entity == null) {
            return response(request.getRequestId(), EntityAnimationResponse.Code.ENTITY_NOT_LOADED, "客户端未加载目标实体");
        }
        if (entity instanceof AbstractClientPlayerEntity || MobAnimationRegistry.model(entity) == null) {
            return response(request.getRequestId(), EntityAnimationResponse.Code.INVALID_REQUEST, "自动层需要已注册生物");
        }
        float progress = automatic.getActionProgress();
        if (!Float.isFinite(progress) || progress < 0 || progress > 1) {
            return response(request.getRequestId(), EntityAnimationResponse.Code.INVALID_REQUEST, "自动动作进度必须在 [0,1]");
        }
        var animations = PlayerAnimationClient.ASSETS.animations();
        String action = automatic.getActionAnimationId();
        if (!animations.containsKey(automatic.getBaseAnimationId())
                || !action.isEmpty() && !animations.containsKey(action)) {
            return response(request.getRequestId(), EntityAnimationResponse.Code.ANIMATION_NOT_FOUND, "客户端未加载自动动画");
        }
        PlayerAnimationClient.PLAYBACK.automaticEntity(entity, automatic);
        return response(request.getRequestId(), EntityAnimationResponse.Code.OK, "");
    }

    private static EntityAnimationResponse register(EntityAnimationRequest request) {
        RegisterGeometry geometry = request.getRegisterGeometry();
        try {
            MobAnimationRegistry.Registration registration = new MobAnimationRegistry.Registration(
                    geometry.getGeometryId(), geometry.getGeometryJson().toByteArray(), geometry.getEntityTypesList(),
                    geometry.getOverlayGeometryId());
            MobAnimationRegistry.register(registration);
            return response(request.getRequestId(), EntityAnimationResponse.Code.OK, "");
        } catch (RuntimeException exception) {
            return response(request.getRequestId(), EntityAnimationResponse.Code.GEOMETRY_INVALID,
                    exception.getMessage());
        }
    }

    private static EntityAnimationResponse play(EntityAnimationRequest request) {
        Play play = request.getPlay();
        LivingEntity entity = loadedEntity(play.getEntityId());
        if (entity == null) {
            return response(request.getRequestId(), EntityAnimationResponse.Code.ENTITY_NOT_LOADED,
                    "客户端未加载目标实体");
        }
        if (!(entity instanceof AbstractClientPlayerEntity) && MobAnimationRegistry.model(entity) == null) {
            return response(request.getRequestId(), EntityAnimationResponse.Code.INVALID_REQUEST,
                    "目标实体未注册几何");
        }
        float normalizedProgress = play.getNormalizedProgress();
        if (play.hasNormalizedProgress() && (!Float.isFinite(normalizedProgress)
                || normalizedProgress < 0 || normalizedProgress > 1)) {
            return response(request.getRequestId(), EntityAnimationResponse.Code.INVALID_REQUEST,
                    "动画进度必须在 [0,1]");
        }
        if (!PlayerAnimationClient.ASSETS.animations().containsKey(play.getAnimationId())) {
            return response(request.getRequestId(), EntityAnimationResponse.Code.ANIMATION_NOT_FOUND,
                    "客户端未加载动画");
        }
        startPlayback(entity, request);
        return response(request.getRequestId(), EntityAnimationResponse.Code.OK, "");
    }

    private static void startPlayback(LivingEntity entity, EntityAnimationRequest request) {
        // 玩家沿用装备与事件语义，生物只进入独立的实体播放器。
        Play play = request.getPlay();
        AnimationClip.Loop loop = loopOverride(play);
        Long startTick = null;
        if (play.hasStartTick()) {
            startTick = play.getStartTick();
        }
        if (entity instanceof AbstractClientPlayerEntity player) {
            if (play.hasNormalizedProgress()) {
                PlayerAnimationClient.PLAYBACK.scrub(player.getUuid(), play.getAnimationId(),
                        play.getNormalizedProgress());
            } else {
                PlayerAnimationClient.PLAYBACK.play(player.getUuid(), play.getAnimationId(), startTick,
                        request.getRequestId(), loop);
            }
            return;
        }
        if (play.hasNormalizedProgress()) {
            PlayerAnimationClient.PLAYBACK.scrubEntity(play.getEntityId(), play.getAnimationId(),
                    play.getNormalizedProgress());
        } else {
            PlayerAnimationClient.PLAYBACK.playEntity(play.getEntityId(), play.getAnimationId(), startTick,
                    request.getRequestId(), loop);
        }
    }

    private static EntityAnimationResponse stop(EntityAnimationRequest request) {
        Stop stop = request.getStop();
        if (stop.getTargetCase() == Stop.TargetCase.TARGET_NOT_SET
                || stop.getTargetCase() == Stop.TargetCase.ALL && !stop.getAll()
                || stop.getTargetCase() == Stop.TargetCase.ANIMATION_ID && stop.getAnimationId().isBlank()) {
            return response(request.getRequestId(), EntityAnimationResponse.Code.INVALID_REQUEST, "缺少停止范围");
        }
        String animation = null;
        if (stop.getTargetCase() == Stop.TargetCase.ANIMATION_ID) {
            animation = stop.getAnimationId();
        }
        LivingEntity entity = loadedEntity(stop.getEntityId());
        if (entity instanceof AbstractClientPlayerEntity player) {
            PlayerAnimationClient.PLAYBACK.stop(player.getUuid(), animation);
        } else {
            PlayerAnimationClient.PLAYBACK.stopEntity(stop.getEntityId(), animation);
        }
        return response(request.getRequestId(), EntityAnimationResponse.Code.OK, "");
    }

    private static EntityAnimationResponse clear(EntityAnimationRequest request) {
        LivingEntity entity = loadedEntity(request.getClear().getEntityId());
        if (entity instanceof AbstractClientPlayerEntity player) {
            PlayerAnimationClient.PLAYBACK.remove(player.getUuid());
        }
        PlayerAnimationClient.PLAYBACK.remove(request.getClear().getEntityId());
        return response(request.getRequestId(), EntityAnimationResponse.Code.OK, "");
    }

    private static AnimationClip.Loop loopOverride(Play play) {
        if (!play.hasLoop()) {
            return null;
        }
        return play.getLoop() ? AnimationClip.Loop.REPEAT : AnimationClip.Loop.ONCE;
    }

    private static LivingEntity living(Entity entity) {
        return entity instanceof LivingEntity value ? value : null;
    }

    private static LivingEntity loadedEntity(int id) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return null;
        }
        return living(client.world.getEntityById(id));
    }

    private static EntityAnimationResponse response(String requestId, EntityAnimationResponse.Code code,
                                                    String detail) {
        return EntityAnimationResponse.newBuilder()
                .setRequestId(requestId)
                .setCode(code)
                .setDetail(detail == null ? "" : detail)
                .build();
    }
}

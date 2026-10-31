package dev.kizuna.playeranimation.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import dev.kizuna.playeranimation.api.PlayerAnimationHostImpl;
import dev.kizuna.playeranimation.protocol.pb.EntityAnimationProtocol.EntityAnimationRequest;
import dev.kizuna.playeranimation.protocol.pb.EntityAnimationProtocol.EntityAnimationResponse;
import dev.kizuna.playeranimation.protocol.pb.EntityAnimationProtocol.Play;
import dev.kizuna.playeranimation.protocol.pb.EntityAnimationProtocol.RegisterGeometry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.junit.jupiter.api.Test;

/** 生物独立播放不能触发只属于玩家的事件或继承已卸载实体的状态。 */
final class EntityPlaybackClientTest {
    private static final int ENTITY_ID = 700003;
    private static final long START_TICK = 1000;
    private static final long EVENT_TICKS = 20;
    private static final List<UUID> EVENTS = new ArrayList<>();

    @Test
    void mobPlaybackMustNotEmitPlayerEventsAndMustClearOnUnload() {
        Minecraft client = Minecraft.getInstance();
        PlayerPlayback playback = PlayerAnimationClient.PLAYBACK;
        long savedTime = client.level.getGameTime();
        Zombie zombie = new Zombie(EntityTypes.ZOMBIE, client.level);
        zombie.setId(ENTITY_ID);
        client.level.addEntity(zombie);
        PlayerAnimations.EVENT.register((player, animation, event) -> EVENTS.add(player));
        EVENTS.clear();
        try {
            assertPlayerEvents(client, playback);
            EVENTS.clear();
            playback.clear();
            client.level.setTimeFromServer(START_TICK);
            playback.playEntity(ENTITY_ID, "equip_chestplate", START_TICK, "mob-event", null);
            assertNotNull(playback.entity(ENTITY_ID));
            client.level.setTimeFromServer(START_TICK + EVENT_TICKS);
            playback.tick(client);
            assertTrue(EVENTS.isEmpty(), "生物不能向玩家事件发送 UUID");
            client.level.removeEntity(ENTITY_ID, Entity.RemovalReason.DISCARDED);
            assertNull(playback.entity(ENTITY_ID), "实体卸载必须同步清理播放状态");
            Zombie replacement = new Zombie(EntityTypes.ZOMBIE, client.level);
            replacement.setId(ENTITY_ID);
            client.level.addEntity(replacement);
            assertNull(playback.entity(ENTITY_ID), "复用 ID 的实体不能继承动画");
        } finally {
            client.level.removeEntity(ENTITY_ID, Entity.RemovalReason.DISCARDED);
            client.level.setTimeFromServer(savedTime);
            playback.clear();
        }
    }

    @Test
    void defaultNamespaceMustResolveRegisteredGeometry() throws Exception {
        // 注册和查询必须使用同一规范化实体类型，省略命名空间不能产生无效绑定。
        Minecraft client = Minecraft.getInstance();
        Zombie zombie = new Zombie(EntityTypes.ZOMBIE, client.level);
        zombie.setId(ENTITY_ID);
        client.level.addEntity(zombie);
        try (var stream = client.getResourceManager()
                .getResourceOrThrow(PlayerAssets.id("geo/player_skeleton.geo.json")).open()) {
            RegisterGeometry geometry = RegisterGeometry.newBuilder()
                    .setGeometryId("geometry.player.classic")
                    .setGeometryJson(ByteString.copyFrom(stream.readAllBytes()))
                    .addEntityTypes("zombie")
                    .build();
            EntityAnimationRequest registration = EntityAnimationRequest.newBuilder()
                    .setRequestId("register-default-namespace")
                    .setRegisterGeometry(geometry)
                    .build();
            assertEquals(EntityAnimationResponse.Code.OK, exchange(registration).getCode());
            Play play = Play.newBuilder()
                    .setEntityId(ENTITY_ID)
                    .setAnimationId("dodge_forward")
                    .setNormalizedProgress(0)
                    .build();
            EntityAnimationRequest request = EntityAnimationRequest.newBuilder()
                    .setRequestId("play-default-namespace")
                    .setPlay(play)
                    .build();
            assertEquals(EntityAnimationResponse.Code.OK, exchange(request).getCode());
        } finally {
            client.level.removeEntity(ENTITY_ID, Entity.RemovalReason.DISCARDED);
            PlayerAnimationClient.PLAYBACK.clear();
        }
    }

    private static EntityAnimationResponse exchange(EntityAnimationRequest request) throws Exception {
        var host = new PlayerAnimationHostImpl();
        EntityAnimationResponse response = EntityAnimationResponse.parseFrom(host.exchange(request.toByteArray()));
        assertEquals(request.getRequestId(), response.getRequestId());
        return response;
    }

    private static void assertPlayerEvents(Minecraft client, PlayerPlayback playback) {
        // 同一事件窗口先验证玩家会收到回调，避免隔离断言因事件源未推进而空通过。
        client.level.setTimeFromServer(START_TICK);
        UUID player = client.player.getUUID();
        playback.play(player, "equip_chestplate", START_TICK, "player-event");
        client.level.setTimeFromServer(START_TICK + EVENT_TICKS);
        playback.tick(client);
        assertEquals(List.of(player), EVENTS);
    }
}

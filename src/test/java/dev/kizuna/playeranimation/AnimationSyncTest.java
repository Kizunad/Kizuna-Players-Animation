package dev.kizuna.playeranimation;

import com.google.protobuf.InvalidProtocolBufferException;
import dev.kizuna.playeranimation.network.AnimationPackets;
import dev.kizuna.playeranimation.network.AnimationRequests;
import dev.kizuna.playeranimation.protocol.pb.PlayerAnimationProtocol.PlayRequest;
import dev.kizuna.playeranimation.protocol.pb.PlayerAnimationProtocol.StopRequest;
import dev.kizuna.playeranimation.server.AnimationSyncState;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnimationSyncTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static PlayRequest play(String animation, long tick) {
        return PlayRequest.newBuilder().setRequestId("play-" + tick).setPlayerUuid(PLAYER.toString())
                .setAnimationId(animation).setStartTick(tick).build();
    }

    @Test
    void snapshotPreservesStartTimeAndLatestTieOrderWithoutCrossingPlayers() {
        AnimationSyncState state = new AnimationSyncState();
        state.play(PLAYER, play("walk", 20));
        state.play(PLAYER, play("meditate", 40));
        List<PlayRequest> before = state.snapshot(PLAYER);
        state.play(PLAYER, play("walk", 60));
        assertEquals(List.of(play("meditate", 40), play("walk", 60)), state.snapshot(PLAYER));
        assertEquals(List.of(play("walk", 20), play("meditate", 40)), before);
        assertTrue(state.snapshot(UUID.randomUUID()).isEmpty());
        state.stop(PLAYER, "walk");
        assertEquals(List.of(play("meditate", 40)), state.snapshot(PLAYER));
        state.stop(PLAYER, "absent");
        assertEquals(1, state.snapshot(PLAYER).size());
        state.stop(PLAYER, null);
        assertTrue(state.players().isEmpty());
        state.play(PLAYER, play("run", 80));
        state.clear();
        assertTrue(state.players().isEmpty());
    }

    @Test
    void trackersOnlyAudienceFollowsTheLatestRegistrationAndClearsOnStop() {
        // 本人客户端自己播的动作只记「发追踪者」；同一动作再按普通播放登记就恢复发给所有人，停掉后按所有人。
        AnimationSyncState state = new AnimationSyncState();
        state.play(PLAYER, play("herb_knife_draw", 20), AnimationSyncState.Audience.TRACKERS);
        state.play(PLAYER, play("walk", 20));
        assertEquals(AnimationSyncState.Audience.TRACKERS, state.audience(PLAYER, "herb_knife_draw"));
        assertEquals(AnimationSyncState.Audience.EVERYONE, state.audience(PLAYER, "walk"));
        assertEquals(AnimationSyncState.Audience.EVERYONE, state.audience(UUID.randomUUID(), "herb_knife_draw"));
        assertEquals(List.of(play("herb_knife_draw", 20), play("walk", 20)), state.snapshot(PLAYER));
        state.play(PLAYER, play("herb_knife_draw", 40));
        assertEquals(AnimationSyncState.Audience.EVERYONE, state.audience(PLAYER, "herb_knife_draw"));
        state.play(PLAYER, play("herb_knife_draw", 60), AnimationSyncState.Audience.TRACKERS);
        state.stop(PLAYER, "herb_knife_draw");
        assertEquals(AnimationSyncState.Audience.EVERYONE, state.audience(PLAYER, "herb_knife_draw"));
        assertEquals(List.of(play("walk", 20)), state.snapshot(PLAYER));
    }

    @Test
    void lateObserverSnapshotPreservesPlaybackRate() throws Exception {
        // 补发保留原始请求的起点、倍率和 ID，不从当前时刻重建请求。
        AnimationSyncState state = new AnimationSyncState();
        PlayRequest request = play("meditate", 40).toBuilder().setPlaybackRate(0.8f).build();
        state.play(PLAYER, request);
        PlayRequest replay = PlayRequest.parseFrom(state.snapshot(PLAYER).getFirst().toByteArray());
        assertEquals(request, replay);
        assertTrue(replay.hasPlaybackRate());
        assertEquals(0.8f, replay.getPlaybackRate());
    }

    @Test
    void requestBoundaryRejectsMalformedValuesAndAmbiguousStops() throws Exception {
        PlayRequest valid = play("meditate", 0);
        assertEquals(PLAYER, AnimationRequests.validate(valid));
        assertEquals(PLAYER, AnimationRequests.validate(valid.toBuilder().clearStartTick().build()));
        assertEquals(PLAYER, AnimationRequests.validate(valid.toBuilder().setNormalizedProgress(0.5f).build()));
        assertThrows(IllegalArgumentException.class,
                () -> AnimationRequests.validate(valid.toBuilder().setPlayerUuid("0-0-0-0-1").build()));
        assertThrows(IllegalArgumentException.class,
                () -> AnimationRequests.validate(valid.toBuilder().setRequestId(" ").build()));
        assertThrows(IllegalArgumentException.class,
                () -> AnimationRequests.validate(valid.toBuilder().setRequestId("a".repeat(129)).build()));
        assertThrows(IllegalArgumentException.class,
                () -> AnimationRequests.validate(valid.toBuilder().setAnimationId("namespace:anim").build()));
        assertThrows(IllegalArgumentException.class,
                () -> AnimationRequests.validate(valid.toBuilder().setStartTick(-1).build()));
        assertThrows(IllegalArgumentException.class,
                () -> AnimationRequests.validate(valid.toBuilder().setNormalizedProgress(1.1f).build()));
        var stop = StopRequest.newBuilder().setRequestId("s1").setPlayerUuid(PLAYER.toString());
        assertThrows(IllegalArgumentException.class, () -> AnimationRequests.validate(stop.build()));
        assertThrows(IllegalArgumentException.class, () -> AnimationRequests.validate(stop.setAll(false).build()));
        assertThrows(IllegalArgumentException.class, () -> AnimationRequests.validate(stop.setAnimationId("").build()));
        assertEquals(PLAYER, AnimationRequests.validate(stop.setAll(true).build()));
        assertEquals(PLAYER, AnimationRequests.validate(stop.setAnimationId("meditate").build()));
        StopRequest unknown = StopRequest.parseFrom(new byte[]{42, 0}).toBuilder()
                .setRequestId("s2").setPlayerUuid(PLAYER.toString()).build();
        assertThrows(IllegalArgumentException.class, () -> AnimationRequests.validate(unknown));
    }

    @Test
    void fabricPayloadContainsOnlyProtobufAndRejectsOversizeOrTruncation() throws Exception {
        PlayRequest request = play("meditate", 400);
        AnimationPackets.Payload payload = AnimationPackets.write(AnimationPackets.PLAY, request);
        // 线上只有 Protobuf 字节，不带长度前缀；解码读回同一请求。
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            AnimationPackets.codec(AnimationPackets.PLAY).encode(buffer, payload);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.getBytes(0, bytes);
            assertArrayEquals(request.toByteArray(), bytes);
            AnimationPackets.Payload decoded = AnimationPackets.codec(AnimationPackets.PLAY).decode(buffer);
            assertEquals(request, AnimationPackets.read(decoded, PlayRequest.parser()));
        } finally {
            buffer.release();
        }
        var oversized = new AnimationPackets.Payload(AnimationPackets.PLAY, new byte[AnimationPackets.MAX_BYTES + 1]);
        var truncated = new AnimationPackets.Payload(AnimationPackets.PLAY, new byte[]{10, 5, 1});
        assertThrows(IllegalArgumentException.class, () -> AnimationPackets.read(oversized, PlayRequest.parser()));
        assertThrows(InvalidProtocolBufferException.class,
                () -> AnimationPackets.read(truncated, PlayRequest.parser()));
        assertThrows(IllegalArgumentException.class, () -> AnimationPackets.write(AnimationPackets.PLAY,
                request.toBuilder().setAnimationId("a".repeat(AnimationPackets.MAX_BYTES)).build()));
    }
}

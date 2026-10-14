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
import net.minecraft.network.PacketByteBuf;
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
    void requestBoundaryRejectsMalformedValuesAndAmbiguousStops() throws Exception {
        PlayRequest valid = play("meditate", 0);
        assertEquals(PLAYER, AnimationRequests.validate(valid));
        assertEquals(PLAYER, AnimationRequests.validate(valid.toBuilder().clearStartTick().build()));
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
        PacketByteBuf buffer = AnimationPackets.write(request);
        try {
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.getBytes(0, bytes);
            assertArrayEquals(request.toByteArray(), bytes);
            assertEquals(request, AnimationPackets.read(buffer, PlayRequest.parser()));
        } finally {
            buffer.release();
        }
        PacketByteBuf oversized = new PacketByteBuf(Unpooled.wrappedBuffer(new byte[AnimationPackets.MAX_BYTES + 1]));
        PacketByteBuf truncated = new PacketByteBuf(Unpooled.wrappedBuffer(new byte[]{10, 5, 1}));
        try {
            assertThrows(IllegalArgumentException.class, () -> AnimationPackets.read(oversized, PlayRequest.parser()));
            assertThrows(InvalidProtocolBufferException.class, () -> AnimationPackets.read(truncated, PlayRequest.parser()));
        } finally {
            oversized.release();
            truncated.release();
        }
        assertThrows(IllegalArgumentException.class, () -> AnimationPackets.write(request.toBuilder()
                .setAnimationId("a".repeat(AnimationPackets.MAX_BYTES)).build()));
    }
}

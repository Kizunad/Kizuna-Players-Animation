package dev.kizuna.playeranimation;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import dev.kizuna.playeranimation.api.PlayerAnimationHostImpl;
import dev.kizuna.playeranimation.protocol.pb.EntityAnimationProtocol.Clear;
import dev.kizuna.playeranimation.protocol.pb.EntityAnimationProtocol.EntityAnimationRequest;
import dev.kizuna.playeranimation.protocol.pb.EntityAnimationProtocol.EntityAnimationResponse;
import dev.kizuna.playeranimation.protocol.pb.EntityAnimationProtocol.Play;
import dev.kizuna.playeranimation.protocol.pb.EntityAnimationProtocol.RegisterGeometry;
import dev.kizuna.playeranimation.protocol.pb.EntityAnimationProtocol.Stop;
import java.util.List;
import org.junit.jupiter.api.Test;

final class EntityProtocolContractTest {
    @Test
    void allOperationsRoundTripWithTheirPresence() throws Exception {
        // optional 的 false 与未设置不同，追踪补发必须保留这一差别。
        RegisterGeometry geometry = RegisterGeometry.newBuilder()
                .setGeometryId("geometry.mob.skeleton")
                .setGeometryJson(ByteString.copyFromUtf8("{}"))
                .addEntityTypes("minecraft:skeleton")
                .setOverlayGeometryId("geometry.mob.stray_overlay")
                .build();
        Play play = Play.newBuilder()
                .setEntityId(1)
                .setAnimationId("dodge_forward")
                .setStartTick(1)
                .setLoop(false /* loop */)
                .build();
        Stop stop = Stop.newBuilder()
                .setEntityId(1)
                .setAll(true /* all */)
                .build();
        Clear clear = Clear.newBuilder().setEntityId(1).build();
        List<EntityAnimationRequest> requests = List.of(
                EntityAnimationRequest.newBuilder().setRegisterGeometry(geometry).build(),
                EntityAnimationRequest.newBuilder().setPlay(play).build(),
                EntityAnimationRequest.newBuilder().setStop(stop).build(),
                EntityAnimationRequest.newBuilder().setClear(clear).build());
        for (EntityAnimationRequest request : requests) {
            assertEquals(request, EntityAnimationRequest.parseFrom(request.toByteArray()));
        }
        assertTrue(play.hasLoop());
        assertFalse(play.getLoop());
        assertFalse(play.hasNormalizedProgress());
    }

    @Test
    void malformedWireIsRejected() {
        assertThrows(InvalidProtocolBufferException.class,
                () -> EntityAnimationRequest.parseFrom(new byte[]{(byte) 0x80}));
    }

    @Test
    void unboundHostReturnsExplicitCode() throws Exception {
        PlayerAnimationHostImpl host = new PlayerAnimationHostImpl();
        EntityAnimationResponse response = EntityAnimationResponse.parseFrom(host.exchange(new byte[0]));
        assertEquals(EntityAnimationResponse.Code.UNBOUND, response.getCode());
    }
}

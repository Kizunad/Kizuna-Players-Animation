package dev.kizuna.playeranimation.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kizuna.playeranimation.protocol.pb.PlayerAnimationProtocol.PlayRequest;
import dev.kizuna.playeranimation.protocol.pb.PlayerAnimationProtocol.Result;
import org.junit.jupiter.api.Test;

class ScrubReceiptTest {
    private static final PlayRequest PLAY = PlayRequest.newBuilder().setRequestId("play-1")
            .setAnimationId("walk").setStartTick(40).build();
    private static final PlayRequest SCRUB = PlayRequest.newBuilder().setRequestId("scrub-1")
            .setAnimationId("spyglass").setNormalizedProgress(0.5f).build();

    private static Result result(Result.Code code) {
        return Result.newBuilder().setCode(code).build();
    }

    @Test
    void scrubIsAcknowledgedOnlyWhenItFailed() {
        // scrub 摆上了不回执（每 tick 都可能来）；没摆上要回失败，服务端靠它立即重发。
        assertFalse(ClientAnimationNetworking.acknowledged(SCRUB, result(Result.Code.OK)));
        assertTrue(ClientAnimationNetworking.acknowledged(SCRUB, result(Result.Code.PLAYER_NOT_LOADED)));
        assertTrue(ClientAnimationNetworking.acknowledged(SCRUB, result(Result.Code.ANIMATION_NOT_FOUND)));
        assertTrue(ClientAnimationNetworking.acknowledged(SCRUB, result(Result.Code.INVALID_REQUEST)));
    }

    @Test
    void ordinaryPlayIsAlwaysAcknowledged() {
        assertTrue(ClientAnimationNetworking.acknowledged(PLAY, result(Result.Code.OK)));
        assertTrue(ClientAnimationNetworking.acknowledged(PLAY, result(Result.Code.PLAYER_NOT_LOADED)));
    }
}

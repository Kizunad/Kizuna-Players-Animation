package dev.kizuna.playeranimation.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kizuna.playeranimation.protocol.pb.PlayerAnimationProtocol.PlayRequest;
import dev.kizuna.playeranimation.protocol.pb.PlayerAnimationProtocol.Result;
import dev.kizuna.playeranimation.server.ScrubRetries.Retry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScrubRetriesTest {
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID VIEWER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID OTHER_VIEWER = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private static PlayRequest scrub(String requestId, float progress) {
        return PlayRequest.newBuilder().setRequestId(requestId).setPlayerUuid(TARGET.toString())
                .setAnimationId("spyglass").setStartTick(40).setNormalizedProgress(progress).build();
    }

    private static Result result(String requestId, Result.Code code) {
        return Result.newBuilder().setRequestId(requestId).setCode(code).build();
    }

    @Test
    void failedScrubIsResentOncePerViewerInTheSameTick() {
        // 客户端没摆上就回失败：本 tick 末把登记的请求原样重发给它，同一回执来几次都只发一次。
        var state = new AnimationSyncState();
        PlayRequest saved = scrub("scrub-1", 0.5f);
        state.play(TARGET, saved);
        var retries = new ScrubRetries();
        retries.failed(VIEWER, result("scrub-1", Result.Code.PLAYER_NOT_LOADED));
        retries.failed(VIEWER, result("scrub-1", Result.Code.PLAYER_NOT_LOADED));
        retries.failed(OTHER_VIEWER, result("scrub-1", Result.Code.ANIMATION_NOT_FOUND));
        assertEquals(List.of(new Retry(VIEWER, saved), new Retry(OTHER_VIEWER, saved)), retries.drain(state));
        assertTrue(retries.drain(state).isEmpty(), "重发过的不留到下一 tick");
    }

    @Test
    void replacedOrStoppedScrubIsNotResent() {
        // 回执到的时候进度已经换了新请求，或者动作已经停了：旧请求不能再发出去。
        var state = new AnimationSyncState();
        state.play(TARGET, scrub("scrub-1", 0.5f));
        var retries = new ScrubRetries();
        retries.failed(VIEWER, result("scrub-1", Result.Code.PLAYER_NOT_LOADED));
        state.play(TARGET, scrub("scrub-2", 0.6f));
        assertTrue(retries.drain(state).isEmpty());
        retries.failed(VIEWER, result("scrub-2", Result.Code.PLAYER_NOT_LOADED));
        state.stop(TARGET, "spyglass");
        assertTrue(retries.drain(state).isEmpty());
    }

    @Test
    void onlyRetryableScrubFailuresAreResent() {
        // 成功回执、请求本身不合法、以及不是 scrub 的请求，都不重发。
        var state = new AnimationSyncState();
        state.play(TARGET, scrub("scrub-1", 0.5f));
        state.play(TARGET, PlayRequest.newBuilder().setRequestId("play-1").setPlayerUuid(TARGET.toString())
                .setAnimationId("walk").setStartTick(40).build());
        var retries = new ScrubRetries();
        retries.failed(VIEWER, result("scrub-1", Result.Code.OK));
        retries.failed(VIEWER, result("scrub-1", Result.Code.INVALID_REQUEST));
        retries.failed(VIEWER, result("play-1", Result.Code.PLAYER_NOT_LOADED));
        retries.failed(VIEWER, result("unknown", Result.Code.PLAYER_NOT_LOADED));
        assertTrue(retries.drain(state).isEmpty());
    }
}

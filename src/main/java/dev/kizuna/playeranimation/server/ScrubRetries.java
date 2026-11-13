package dev.kizuna.playeranimation.server;

import dev.kizuna.playeranimation.protocol.pb.PlayerAnimationProtocol.PlayRequest;
import dev.kizuna.playeranimation.protocol.pb.PlayerAnimationProtocol.Result;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 客户端没摆上的 scrub。scrub 进度不变时服务端不重发，客户端要是没处理成（玩家或动画还没加载），
 * 得等满刷新间隔才补上；客户端为此回一个失败回执，这里记到本 tick 末，由服务端重发一次。
 */
final class ScrubRetries {
    /** 要重发的一条：发给谁、重发哪个登记的请求。 */
    record Retry(UUID viewer, PlayRequest request) { }

    private record Failed(UUID viewer, String requestId) { }

    private final Set<Failed> failed = new LinkedHashSet<>();

    /** 记下一个失败回执。请求本身不合法的重发也没用，不记。 */
    void failed(UUID viewer, Result result) {
        if (result.getCode() == Result.Code.OK || result.getCode() == Result.Code.INVALID_REQUEST) {
            return;
        }
        failed.add(new Failed(viewer, result.getRequestId()));
    }

    /**
     * 取出本 tick 要重发的并清空。同一观察者对同一请求只重发一次；
     * 请求已被新进度顶掉、已停止，或根本不是 scrub 的，不重发。
     */
    List<Retry> drain(AnimationSyncState state) {
        List<Retry> retries = new ArrayList<>();
        for (Failed entry : failed) {
            PlayRequest saved = state.scrub(entry.requestId());
            if (saved != null) {
                retries.add(new Retry(entry.viewer(), saved));
            }
        }
        failed.clear();
        return retries;
    }

    void clear() {
        failed.clear();
    }
}

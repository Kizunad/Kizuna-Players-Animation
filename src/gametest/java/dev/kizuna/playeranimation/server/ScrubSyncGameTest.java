package dev.kizuna.playeranimation.server;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

public final class ScrubSyncGameTest {
    private static final String ACTION = "calib_zero";
    private static final int WITHIN_REFRESH_TICKS = 5;
    // 要等过一个完整的刷新间隔，默认的 20 tick 上限不够。
    private static final int MAX_TICKS = 3 * ServerPlayerAnimations.SCRUB_REFRESH_TICKS;

    @GameTest(maxTicks = MAX_TICKS)
    public void unchangedScrubIsNotResentUntilRefresh(GameTestHelper context) {
        // 进度没变不产生新请求；进度变了立即产生；一直不变时隔 SCRUB_REFRESH_TICKS 才补一次。
        var server = context.getLevel().getServer();
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        String first = ServerPlayerAnimations.scrub(player, ACTION, 1);
        String repeated = ServerPlayerAnimations.scrub(player, ACTION, 1);
        String moved = ServerPlayerAnimations.scrub(player, ACTION, 0.5f);
        context.runAfterDelay(WITHIN_REFRESH_TICKS, () -> {
            String held = ServerPlayerAnimations.scrub(player, ACTION, 0.5f);
            context.runAfterDelay(ServerPlayerAnimations.SCRUB_REFRESH_TICKS, () -> {
                String refreshed = ServerPlayerAnimations.scrub(player, ACTION, 0.5f);
                // 假连接不能留到后续人体同步 tick。
                ServerPlayerAnimations.stopAll(player);
                server.getPlayerList().remove(player);
                context.assertTrue(first.equals(repeated), "相同进度不应产生新请求");
                context.assertFalse(first.equals(moved), "进度变化必须产生新请求");
                context.assertTrue(moved.equals(held), "刷新间隔内相同进度不应产生新请求");
                context.assertFalse(moved.equals(refreshed), "满刷新间隔后必须重发一次");
                context.succeed();
            });
        });
    }
}

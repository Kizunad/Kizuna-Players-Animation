package dev.kizuna.playeranimation.client;

import net.minecraft.client.Minecraft;

final class ClientPlaybackTestTime {
    private ClientPlaybackTestTime() { }

    static void advance(long ticks) {
        // 推进真实客户端世界生命周期；服务器校时只设置起点，不能再充当播放时间推进。
        var level = Minecraft.getInstance().level;
        for (long tick = 0; tick < ticks; tick++) {
            level.tick(() -> true);
        }
    }
}

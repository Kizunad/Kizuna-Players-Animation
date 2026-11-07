package dev.kizuna.playeranimation.client;

import net.minecraft.client.multiplayer.ClientLevel;

/** 跟随客户端逻辑 tick 与帧内插值，独立于服务器对世界总时间的校正。 */
final class LocalAnimationClock {
    private static final double TICKS_PER_SECOND = 20;
    private static ClientLevel world;
    private static long ticks;

    private LocalAnimationClock() { }

    static void tick(ClientLevel current) {
        prepare(current);
        // 暂停时不调用世界 tick；冻结时世界 tick 仍可能执行，但不推进动画。
        if (current.tickRateManager().runsNormally()) {
            ticks++;
        }
    }

    static double now(ClientLevel current, float partialTick) {
        prepare(current);
        if (current == null) {
            return 0;
        }
        return (ticks + partialTick) / TICKS_PER_SECOND;
    }

    private static void prepare(ClientLevel current) {
        // 换世界重新计时，资源重载和世界校时不会触碰本地计数。
        if (world != current) {
            world = current;
            ticks = 0;
        }
    }
}

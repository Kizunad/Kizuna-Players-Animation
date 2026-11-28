package dev.kizuna.playeranimation.client;

import net.minecraft.client.multiplayer.ClientLevel;

/** 跟随客户端逻辑 tick 与帧内插值，独立于服务器对世界总时间的校正。 */
final class LocalAnimationClock {
    private static final double TICKS_PER_SECOND = 20;
    private static final double NORMAL_RATE = 1;
    private static final double MIN_SLOW_RATE = 0.1;
    private static ClientLevel world;
    private static double ticks;
    private static long heldTicks;
    private static long slowTicks;
    private static double slowRate = NORMAL_RATE;

    private LocalAnimationClock() { }

    static void tick(ClientLevel current) {
        prepare(current);
        if (current == null) {
            return;
        }
        // 这是客户端动画自己的刻数，不读取世界调速器，避免把表现状态带进游戏时钟。
        if (heldTicks > 0) {
            heldTicks--;
        } else {
            ticks += activeRate();
            if (slowTicks > 0) {
                slowTicks--;
            }
        }
    }

    static double now(ClientLevel current, float partialTick) {
        prepare(current);
        if (current == null) {
            return 0;
        }
        return (ticks + partialTick * activeRate()) / TICKS_PER_SECOND;
    }

    static void hold(long durationTicks) {
        // 顿帧只停本地动画时钟，不冻结世界或服务端身体时间。
        if (durationTicks > heldTicks) {
            heldTicks = durationTicks;
        }
    }

    static void slow(long durationTicks, double rate) {
        // 破架慢动作只改变动画时钟，重叠触发时保留更慢的倍率和更长的剩余时间。
        double clamped = Math.clamp(rate, MIN_SLOW_RATE, NORMAL_RATE);
        if (durationTicks > slowTicks || clamped < slowRate) {
            slowTicks = Math.max(slowTicks, durationTicks);
            slowRate = clamped;
        }
    }

    static void resetFeel() {
        // 断线和换世界清除上一次命中的本地表现。
        heldTicks = 0;
        slowTicks = 0;
        slowRate = NORMAL_RATE;
    }

    private static double activeRate() {
        return slowTicks > 0 ? slowRate : NORMAL_RATE;
    }

    private static void prepare(ClientLevel current) {
        // 换世界重新计时，资源重载和世界校时不会触碰本地计数。
        if (world != current) {
            world = current;
            ticks = 0;
            resetFeel();
        }
    }
}

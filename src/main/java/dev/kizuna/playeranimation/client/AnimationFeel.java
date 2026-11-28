package dev.kizuna.playeranimation.client;

/** 供必装玩法附属使用的本地动画时钟手感入口；不触碰服务端时间。 */
public final class AnimationFeel {
    private AnimationFeel() {
        // 手感控制只转发到播放器自己的本地时钟。
    }

    public static void pauseTicks(long durationTicks) {
        // 命中顿帧暂停所有客户端动画，世界模拟仍按原速运行。
        LocalAnimationClock.hold(durationTicks);
    }

    public static void slowMotion(long durationTicks, float rate) {
        // 破架慢动作降低本地动画时钟，不改变服务端判定或身体时间。
        LocalAnimationClock.slow(durationTicks, rate);
    }

    public static void reset() {
        // 断线时显式清除本地残留的顿帧与慢动作。
        LocalAnimationClock.resetFeel();
    }
}

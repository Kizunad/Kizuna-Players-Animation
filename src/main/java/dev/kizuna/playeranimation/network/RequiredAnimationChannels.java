package dev.kizuna.playeranimation.network;

/** 每条连接只在握手期间等待通道；完成后撤销通道属于接线错误。 */
public final class RequiredAnimationChannels {
    public static final int NEGOTIATION_TICKS = 20 * 30;
    private final long deadline;
    private boolean established;

    public RequiredAnimationChannels(long connectedTick) {
        // Fabric 的通道声明晚于连接建立，等待期限使用本端单调 tick。
        deadline = connectedTick + NEGOTIATION_TICKS;
    }

    public boolean ready(long tick, boolean complete) {
        // 正常协商允许稍后补发，但不能在已接通后静默退回无动画模式。
        if (complete) {
            established = true;
            return true;
        }
        if (established || tick >= deadline) {
            throw new IllegalStateException("缺少必需的 Players-Animation play / stop / result 通道");
        }
        return false;
    }
}

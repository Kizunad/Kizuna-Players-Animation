package dev.kizuna.playeranimation.api;

import dev.kizuna.playeranimation.protocol.pb.EntityAnimationProtocol.EntityAnimationResponse;

/** Fabric 发现的唯一 PA 入口；客户端初始化后才绑定实际处理器。 */
public final class PlayerAnimationHostImpl implements PlayerAnimationHost {
    @FunctionalInterface
    public interface Exchange {
        byte[] call(byte[] payload);
    }

    private static Exchange exchange;

    public static void bind(Exchange handler) {
        // 绑定发生在客户端初始化阶段，之后所有调用都进入同一个 PA 主线程状态。
        exchange = handler;
    }

    @Override
    public byte[] exchange(byte[] payload) {
        if (exchange == null) {
            return EntityAnimationResponse.newBuilder()
                    .setCode(EntityAnimationResponse.Code.UNBOUND)
                    .setDetail("PA 客户端尚未初始化")
                    .build()
                    .toByteArray();
        }
        return exchange.call(payload);
    }
}

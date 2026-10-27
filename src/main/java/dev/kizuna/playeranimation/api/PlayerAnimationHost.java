package dev.kizuna.playeranimation.api;

/** PA 的 Fabric 入口只交换 protobuf 字节，不暴露渲染器、骨架或播放对象。 */
public interface PlayerAnimationHost {
    String ENTRYPOINT = "kizuna_player_animation";

    byte[] exchange(byte[] payload);
}

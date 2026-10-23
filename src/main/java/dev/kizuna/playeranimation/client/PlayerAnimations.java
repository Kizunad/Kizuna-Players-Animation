package dev.kizuna.playeranimation.client;

import java.util.UUID;
import dev.kizuna.playeranimation.animation.PlayerAnimationAction;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/** 客户端主线程入口；事件仅表示动画时间线，不代表服务端伤害授权。 */
public final class PlayerAnimations {
    @FunctionalInterface
    public interface Listener {
        void onEvent(UUID player, String animation, String event);
    }

    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(Listener.class,
            listeners -> (player, animation, event) -> {
                // 保留注册顺序，将时间线事件转给实际玩法调用方。
                for (Listener listener : listeners) {
                    listener.onEvent(player, animation, event);
                }
            });

    private PlayerAnimations() { }

    public static void play(UUID player, String animation) {
        PlayerAnimationClient.PLAYBACK.play(player, animation);
    }

    public static void play(UUID player, PlayerAnimationAction action) {
        play(player, action.id());
    }

    public static void scrub(UUID player, String animation, float normalizedProgress) {
        PlayerAnimationClient.PLAYBACK.scrub(player, animation, normalizedProgress);
    }

    public static void scrub(UUID player, PlayerAnimationAction action, float normalizedProgress) {
        scrub(player, action.id(), normalizedProgress);
    }

    public static void stop(UUID player, String animation) {
        PlayerAnimationClient.PLAYBACK.stop(player, animation);
    }

    public static void stop(UUID player, PlayerAnimationAction action) {
        stop(player, action.id());
    }

    public static void stopAll(UUID player) {
        PlayerAnimationClient.PLAYBACK.stop(player, null);
    }
}

package dev.kizuna.playeranimation.server;

import dev.kizuna.playeranimation.animation.PlayerAnimationAction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlaybackRateExpiryTest {
    @Test
    void finitePlaybackRoundsUpScaledTicksWithoutOverflow() {
        // 用目录中时长为 100 tick 的一次性动作验证原速与迟滞到期。
        PlayerAnimationAction action = PlayerAnimationAction.EVOKER_METAL_ARMS;
        assertEquals(100, action.naturalDurationTicks());
        assertEquals(140, ServerPlayerAnimations.expiryTick(action, 40, 1));
        assertEquals(165, ServerPlayerAnimations.expiryTick(action, 40, 0.8f));
        assertEquals(374, ServerPlayerAnimations.expiryTick(action, 40, 0.3f));
        assertEquals(Long.MAX_VALUE, ServerPlayerAnimations.expiryTick(action, 40, Float.MIN_VALUE));
    }
}

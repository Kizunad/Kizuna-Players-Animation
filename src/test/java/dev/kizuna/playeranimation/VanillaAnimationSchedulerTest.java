package dev.kizuna.playeranimation.server;

import org.junit.jupiter.api.Test;
import dev.kizuna.playeranimation.animation.PlayerAnimationAction;
import net.minecraft.util.Hand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class VanillaAnimationSchedulerTest {
    @Test
    void miningCompletesFullLoopsAtEightyPercentSpeed() {
        var state = new VanillaAnimationScheduler.ToolSwingState();
        // 默认 6 tick 挥手时长：原有 4 tick 视觉循环延长为 5 tick，幅度不缩小。
        for (int tick = 0; tick <= 20; tick++) {
            assertEquals((tick % 5) * 0.08f, state.progress(true, (tick % 4) / 6f, tick, 6), 0.00001f);
        }
    }

    @Test
    void miningReleaseContinuesFromCurrentPoseAndCanResume() {
        var state = new VanillaAnimationScheduler.ToolSwingState();
        assertEquals(0, state.progress(true, 0, 40, 6), 0.00001f);
        assertEquals(0.16f, state.progress(true, 2f / 6, 42, 6), 0.00001f);
        assertEquals(0.24f, state.progress(false, 0, 43, 6), 0.00001f);
        assertEquals(0.32f, state.progress(true, 0, 44, 6), 0.00001f);
        assertEquals(0.40f, state.progress(false, 0, 45, 6), 0.00001f);
        assertEquals(0.48f, state.progress(false, 0, 46, 6), 0.00001f);
        assertEquals(1, state.progress(false, 0, 53, 6), 0.00001f);
    }

    @Test
    void miningRespectsHasteWithoutRestartingItsVisualClock() {
        var state = new VanillaAnimationScheduler.ToolSwingState();
        assertEquals(0, state.progress(true, 0, 0, 6), 0.00001f);
        assertEquals(0.08f, state.progress(true, 1f / 6, 1, 6), 0.00001f);
        assertEquals(0.20f, state.progress(true, 0.5f, 2, 4), 0.00001f);
        assertEquals(0.32f, state.progress(true, 0, 3, 4), 0.00001f);
        assertEquals(0.04f, state.progress(true, 0.25f, 4, 4), 0.00001f);
    }

    @Test
    void aSingleToolClickDoesNotStartASecondStrike() {
        var state = new VanillaAnimationScheduler.ToolSwingState();
        for (int tick = 0; tick < 6; tick++) {
            assertEquals(tick * 0.08f, state.progress(true, tick / 6f, tick, 6), 0.00001f);
        }
        assertEquals(0.48f, state.progress(false, 0, 6, 6), 0.00001f);
    }

    @Test
    void jumpOnlyStartsAndStopsAtGroundEdges() {
        var state = new VanillaAnimationScheduler.JumpState();
        assertNull(state.progress(true, true, 0, 0));
        assertEquals(0, state.progress(true, false, 0.42, 1), 0.001);
        assertEquals(1f / 3f, state.progress(true, false, 0, 6), 0.001);
        assertEquals(2f / 3f, state.progress(true, true, 0, 12), 0.001);
        assertEquals(0, state.progress(true, false, 0.42, 14), 0.001);
        assertEquals(2f / 3f, state.progress(true, true, 0, 25), 0.001);
        assertNull(state.progress(true, true, 0, 31));
    }

    @Test
    void excludedMovementCannotStartJumpOrLeaveAStaleLandingBuffer() {
        var state = new VanillaAnimationScheduler.JumpState();
        // 骑乘、飞行和流体状态不因 onGround=false 而进入跳跃。
        assertNull(state.progress(false, false, 0.42, 0));
        assertNull(state.progress(true, true, 0, 1));
        assertEquals(0, state.progress(true, false, 0.42, 2), 0.001);
        assertNull(state.progress(false, false, -0.2, 3));
        assertNull(state.progress(true, true, 0, 4));
        // 离开飞行后确实仍在空中，则按下落速度重新采样，不演出虚假的蹬地。
        assertEquals(2f / 3f, state.progress(true, false, -0.42, 5), 0.001);
        assertEquals(2f / 3f, state.progress(true, true, 0, 6), 0.001);
        assertNull(state.progress(false, true, 0, 7));
        assertNull(state.progress(true, true, 0, 8));
    }

    @Test
    void originalProgressSourcesAreNormalizedAndClamped() {
        assertEquals(0.0f, VanillaAnimationScheduler.useProgress(32, 32), 0.0001f);
        assertEquals(0.5f, VanillaAnimationScheduler.useProgress(16, 32), 0.0001f);
        assertEquals(1.0f, VanillaAnimationScheduler.useProgress(0, 32), 0.0001f);
        assertEquals(0.0f, VanillaAnimationScheduler.useProgress(4, 0), 0.0001f);
        assertEquals(0.0f, VanillaAnimationScheduler.jumpAirProgress(0.5), 0.0001f);
        assertEquals(1.0f, VanillaAnimationScheduler.jumpAirProgress(-0.5), 0.0001f);
        assertEquals(0.0f, VanillaAnimationScheduler.jumpVelocityProgress(0.42, 0.42), 0.0001f);
        assertEquals(2f / 3f, VanillaAnimationScheduler.jumpVelocityProgress(-0.48, 0.42), 0.0001f);
        assertEquals(2f / 3f, VanillaAnimationScheduler.jumpLandingProgress(0), 0.0001f);
        assertEquals(1.0f, VanillaAnimationScheduler.jumpLandingProgress(6), 0.0001f);
        assertEquals(0.60f, VanillaAnimationScheduler.attackTailProgress(0, 6), 0.0001f);
        assertEquals(1.0f, VanillaAnimationScheduler.attackTailProgress(4, 6), 0.0001f);
    }

    @Test
    void continuousStatesUseStablePhaseAndHandMirror() {
        assertEquals(0.0f, VanillaAnimationScheduler.phase(40, 20), 0.0001f);
        assertEquals(0.5f, VanillaAnimationScheduler.phase(10, 20), 0.0001f);
        assertEquals("attack_main", VanillaAnimationScheduler.handAction("attack_main", "attack_off", Hand.MAIN_HAND));
        assertEquals("attack_off", VanillaAnimationScheduler.handAction("attack_main", "attack_off", Hand.OFF_HAND));
        assertEquals(VanillaAnimationScheduler.Transition.START,
                VanillaAnimationScheduler.armorTransition(false, true));
        assertEquals(VanillaAnimationScheduler.Transition.NONE,
                VanillaAnimationScheduler.armorTransition(true, true));
        assertEquals(18, PlayerAnimationAction.JUMP.naturalDurationTicks());
        assertEquals(10, PlayerAnimationAction.ATTACK_OFF.naturalDurationTicks());
        assertEquals(26, PlayerAnimationAction.EQUIP_HELMET.naturalDurationTicks());
        assertEquals(50, PlayerAnimationAction.EQUIP_LEGGINGS.naturalDurationTicks());
        assertEquals(51, PlayerAnimationAction.EQUIP_BOOTS.naturalDurationTicks());
        assertEquals(47, PlayerAnimationAction.SPYGLASS.naturalDurationTicks());
        assertEquals(25, PlayerAnimationAction.CROSSBOW_CHARGE.naturalDurationTicks());
    }
}

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
    void eatingBitesStayOnTheVanillaConsumptionBeat() {
        // 原版 32 tick 食物在已使用 8、12…28 tick 播放进食音效；剪辑 0.40 / 0.60 秒是两次咬下。
        for (int used = 8; used <= 28; used += 4) {
            float seconds = VanillaAnimationScheduler.loopedSecondsProgress(used, 0.80f, 0.40f, 0.80f) * 0.80f;
            float bite = used % 8 == 0 ? 0.40f : 0.60f;
            assertEquals(bite, seconds, 0.0001f, "used=" + used);
        }
    }

    @Test
    void shieldIsRaisedExactlyWhenVanillaBlockingStarts() {
        // 原版已使用 5 tick 才格挡；剪辑 0.25 秒到位，之后在 0.35–1.95 秒回绕而不回到举盾过程。
        assertEquals(0.25f, VanillaAnimationScheduler.loopedSecondsProgress(5, 1.95f, 0.35f, 1.95f) * 1.95f, 0.0001f);
        float later = VanillaAnimationScheduler.loopedSecondsProgress(45, 1.95f, 0.35f, 1.95f) * 1.95f;
        assertEquals(0.65f, later, 0.0001f);
    }

    @Test
    void bowFollowsVanillaPullAndLoopsOnlyAfterFullDraw() {
        // 原版 20 tick 满弓：拉弦段剪辑秒数 = 拉弓进度，满弓后在 1.0–1.6 秒回绕，与拉弦段首尾连续。
        // 拉弓进度按原版 BowItem.getPullProgress：f = t/20，(f² + 2f)/3，封顶 1。
        float pull5 = (0.25f * 0.25f + 0.5f) / 3f;
        assertEquals(0, VanillaAnimationScheduler.bowProgress(0, 0) * 1.9f, 0.0001f);
        assertEquals(pull5, VanillaAnimationScheduler.bowProgress(pull5, 5) * 1.9f, 0.0001f);
        assertEquals(1.0f, VanillaAnimationScheduler.bowProgress(1, 20) * 1.9f, 0.0001f);
        assertEquals(1.3f, VanillaAnimationScheduler.bowProgress(1, 26) * 1.9f, 0.0001f);
        assertEquals(1.0f, VanillaAnimationScheduler.bowProgress(1, 32) * 1.9f, 0.0001f);
    }

    @Test
    void tridentIsReadyExactlyAtTheVanillaThrowThreshold() {
        // 原版已使用 10 tick 才能投掷：剪辑 0.50 秒后引到位，之后在 0.50–1.70 秒回绕，不进入投掷跟随段。
        assertEquals(0.50f, VanillaAnimationScheduler.loopedSecondsProgress(10, 2.15f, 0.50f, 1.70f) * 2.15f, 0.0001f);
        assertEquals(0.60f, VanillaAnimationScheduler.loopedSecondsProgress(36, 2.15f, 0.50f, 1.70f) * 2.15f, 0.0001f);
    }

    @Test
    void releaseTailPlaysOnceOnlyWhenTheShotFires() {
        var tail = new VanillaAnimationScheduler.ReleaseTail();
        tail.using("bow_draw", true, 1.6f, 1.9f, 1.9f);
        assertEquals(1.6f / 1.9f, tail.released(100), 0.0001f);
        assertEquals(1.9f / 1.9f, tail.released(106), 0.0001f);
        assertNull(tail.released(107));
        tail.using("bow_draw", false, 1.6f, 1.9f, 1.9f);
        assertNull(tail.released(120));
    }

    @Test
    void climbPhaseFollowsVerticalDistanceBothWays() {
        // 一个循环 = 爬升 1 格；下滑时相位倒退，挂住不动时相位不变。
        var state = new VanillaAnimationScheduler.ClimbState();
        assertEquals(0, state.progress(64.0), 0.0001f);
        assertEquals(0.25f, state.progress(64.25), 0.0001f);
        assertEquals(0.25f, state.progress(64.25), 0.0001f);
        assertEquals(0.10f, state.progress(65.10), 0.0001f);
        assertEquals(0.85f, state.progress(64.85), 0.0001f);
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

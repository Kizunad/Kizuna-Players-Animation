package dev.kizuna.playeranimation;

import dev.kizuna.playeranimation.network.RequiredAnimationChannels;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RequiredChannelsTest {
    @Test
    void negotiationWaitsThenAcceptsTheCompleteChannelSet() {
        // 延迟到达的通道声明是正常握手，完整接通后才允许补发。
        var channels = new RequiredAnimationChannels(100);
        assertFalse(channels.ready(100, false));
        assertFalse(channels.ready(699, false));
        assertTrue(channels.ready(700, true));
        assertTrue(channels.ready(900, true));
    }

    @Test
    void missingChannelsFailEvenWithoutAnAnimationRequest() {
        // 固定握手期限避免连接永久停留在无动画、无错误的状态。
        var channels = new RequiredAnimationChannels(100);
        assertThrows(IllegalStateException.class, () -> channels.ready(700, false));
    }

    @Test
    void establishedConnectionCannotSilentlyLoseChannels() {
        // 已完整接通后的通道撤销必须立即报错，不能重新获得等待期。
        var channels = new RequiredAnimationChannels(100);
        assertTrue(channels.ready(101, true));
        assertThrows(IllegalStateException.class, () -> channels.ready(102, false));
    }
}

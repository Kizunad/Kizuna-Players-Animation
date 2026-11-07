package dev.kizuna.playeranimation.client;

import dev.kizuna.playeranimation.animation.AnimationPlayer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.illager.Pillager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PlaybackClockClientTest {
    private static final int ENTITY_ID = 700005;
    private static final long SERVER_CORRECTION_TICKS = 100;
    private static final long ELAPSED_TICKS = 10;
    private static final double TICK_SECONDS = 0.05;
    private static final double EPSILON = 0.00001;
    private static final float PARTIAL_TICK = 0.5f;
    private static final String ANIMATION = "wayfarer_full_draw";

    @Test
    void worldTimeCorrectionMustNotRewindPlayerOrPillagerPlayback() throws Exception {
        Minecraft client = Minecraft.getInstance();
        PlayerPlayback playback = PlayerAnimationClient.PLAYBACK;
        long originalTime = client.level.getGameTime();
        Pillager pillager = new Pillager(EntityTypes.PILLAGER, client.level);
        pillager.setId(ENTITY_ID);
        client.level.addEntity(pillager);
        try {
            registerGeometry(client);
            long startTick = originalTime - ELAPSED_TICKS;
            playback.play(client.player.getUUID(), ANIMATION, startTick, "player-clock");
            playback.playEntity(ENTITY_ID, ANIMATION, startTick, "pillager-clock", null);
            double before = PlayerPlayback.now(PARTIAL_TICK);
            float playerBefore = progress(playback.player(client.player.getUUID()), before);
            float mobBefore = progress(playback.entity(ENTITY_ID), before);
            client.level.setTimeFromServer(originalTime - SERVER_CORRECTION_TICKS);
            assertEquals(before, PlayerPlayback.now(PARTIAL_TICK), EPSILON, "服务器校时不能改动播放时钟");
            playback.play(client.player.getUUID(), ANIMATION, startTick, "player-clock");
            playback.playEntity(ENTITY_ID, ANIMATION, startTick, "pillager-clock", null);
            assertEquals(playerBefore, progress(playback.player(client.player.getUUID()), before), EPSILON);
            assertEquals(mobBefore, progress(playback.entity(ENTITY_ID), before), EPSILON);
            client.level.tick(() -> true);
            double next = PlayerPlayback.now(PARTIAL_TICK);
            assertEquals(before + TICK_SECONDS, next, EPSILON);
            assertTrue(progress(playback.player(client.player.getUUID()), next) > playerBefore);
            assertTrue(progress(playback.entity(ENTITY_ID), next) > mobBefore);
        } finally {
            client.level.removeEntity(ENTITY_ID, Entity.RemovalReason.DISCARDED);
            playback.clear();
            client.level.setTimeFromServer(originalTime);
        }
    }

    private static float progress(AnimationPlayer player, double now) {
        return player.normalizedProgress(ANIMATION, now);
    }

    private static void registerGeometry(Minecraft client) throws Exception {
        // 用真实加载资源驱动同一客户端内的玩家与生物入口，检验共用播放时钟。
        try (var stream = client.getResourceManager()
                .getResourceOrThrow(PlayerAssets.id("geo/player_skeleton.geo.json")).open()) {
            var registration = new MobAnimationRegistry.Registration("geometry.player.classic",
                    stream.readAllBytes(), List.of("minecraft:pillager"), "");
            MobAnimationRegistry.register(registration);
        }
    }
}

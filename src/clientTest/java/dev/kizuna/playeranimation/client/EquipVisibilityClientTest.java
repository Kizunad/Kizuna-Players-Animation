package dev.kizuna.playeranimation.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/** 通过真实客户端玩家、资源与播放入口验证穿戴显隐。 */
final class EquipVisibilityClientTest {
    private static final String CHESTPLATE = "equip_chestplate";
    private static final long START_TICK = 1000;
    private static final long AFTER_FIRST_EQUIP_TICKS = 20;
    private static final long AFTER_SECOND_EQUIP_TICKS = 40;
    private static final long AFTER_ANIMATION_TICKS = 80;
    private static final List<String> EVENTS = new ArrayList<>();
    private final Minecraft client = Minecraft.getInstance();
    private PlayerPlayback playback;
    private UUID playerId;
    private long savedTime;
    private ItemStack savedChest;
    private ItemStack savedBoots;
    private HumanoidArm savedMainArm;

    @BeforeAll
    static void recordEvents() {
        // 同时检查内部显隐恢复没有向公开接口补发历史事件。
        PlayerAnimations.EVENT.register((player, animation, event) -> EVENTS.add(animation + ":" + event));
    }

    @BeforeEach
    void preparePlayer() {
        // 使用已完成资源加载的真实客户端玩家，保存测试会改变的世界与装备状态。
        assertTrue(client.isSameThread(), "测试必须运行在客户端主线程");
        playerId = client.player.getUUID();
        savedTime = client.level.getGameTime();
        savedChest = client.player.getItemBySlot(EquipmentSlot.CHEST).copy();
        savedBoots = client.player.getItemBySlot(EquipmentSlot.FEET).copy();
        savedMainArm = client.player.getMainArm();
        client.player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        client.player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        client.player.setMainArm(HumanoidArm.RIGHT);
        playback = new PlayerPlayback();
        EVENTS.clear();
    }

    @AfterEach
    void restorePlayer() {
        // 测试之间不继承播放、装备、惯用手和时钟状态。
        playback.clear();
        client.level.setTimeFromServer(savedTime);
        client.player.setItemSlot(EquipmentSlot.CHEST, savedChest);
        client.player.setItemSlot(EquipmentSlot.FEET, savedBoots);
        client.player.setMainArm(savedMainArm);
    }

    @Test
    void lateJoinAfterEquipEventMustShowChestplate() {
        // 当前进度为 1 秒，已经越过真实资源中 0.8 秒的 equip 事件。
        client.level.setTimeFromServer(START_TICK + AFTER_FIRST_EQUIP_TICKS);
        playback.play(playerId, CHESTPLATE, START_TICK, "late-join");
        assertFalse(playback.shouldHideArmor(playerId, EquipmentSlot.CHEST));
        assertTrue(playback.equipItem(playerId).isEmpty());
        playback.tick(client);
        assertFalse(playback.shouldHideArmor(playerId, EquipmentSlot.CHEST));
        assertTrue(EVENTS.isEmpty(), "晚加入不得补发历史 equip 回调");
    }

    @Test
    void repeatedSynchronizationMustKeepChestplateVisible() {
        // 事件前入场，正常显示胸甲后再次接收相同起点与请求 ID。
        client.level.setTimeFromServer(START_TICK + 1);
        playback.play(playerId, CHESTPLATE, START_TICK, "same-request");
        playback.tick(client);
        assertTrue(playback.shouldHideArmor(playerId, EquipmentSlot.CHEST));

        ClientPlaybackTestTime.advance(AFTER_FIRST_EQUIP_TICKS - 1);
        playback.tick(client);
        assertFalse(playback.shouldHideArmor(playerId, EquipmentSlot.CHEST));

        playback.play(playerId, CHESTPLATE, START_TICK, "same-request");
        assertFalse(playback.shouldHideArmor(playerId, EquipmentSlot.CHEST));
        playback.tick(client);
        assertFalse(playback.shouldHideArmor(playerId, EquipmentSlot.CHEST));
        assertTrue(playback.equipItem(playerId).isEmpty());
        assertEquals(List.of(CHESTPLATE + ":equip"), EVENTS);
    }

    @Test
    void newPlaybackMustHideChestplateAgain() {
        // 新请求与本地重播都应重新演出，不能被重复同步的去重逻辑吞掉。
        client.level.setTimeFromServer(START_TICK + AFTER_FIRST_EQUIP_TICKS);
        playback.play(playerId, CHESTPLATE, START_TICK, "first-request");
        playback.tick(client);
        playback.play(playerId, CHESTPLATE, client.level.getGameTime(), "new-request");
        playback.tick(client);
        assertTrue(playback.shouldHideArmor(playerId, EquipmentSlot.CHEST));

        ClientPlaybackTestTime.advance(AFTER_SECOND_EQUIP_TICKS - AFTER_FIRST_EQUIP_TICKS);
        playback.tick(client);
        assertFalse(playback.shouldHideArmor(playerId, EquipmentSlot.CHEST));
        playback.play(playerId, CHESTPLATE);
        playback.tick(client);
        assertTrue(playback.shouldHideArmor(playerId, EquipmentSlot.CHEST));
    }

    @Test
    void completedLatePlaybackMustKeepArmorVisible() {
        // 整段动画与淡出都已结束，处理补发不能产生一帧隐藏的胸甲。
        client.level.setTimeFromServer(START_TICK + AFTER_ANIMATION_TICKS);
        playback.play(playerId, CHESTPLATE, START_TICK, "completed");
        assertFalse(playback.shouldHideArmor(playerId, EquipmentSlot.CHEST));
        playback.tick(client);
        assertFalse(playback.shouldHideArmor(playerId, EquipmentSlot.CHEST));
        assertTrue(EVENTS.isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"equip_boots, RIGHT, RIGHT", "equip_boots_off, RIGHT, LEFT",
            "equip_boots, LEFT, LEFT", "equip_boots_off, LEFT, RIGHT"})
    void lateJoinBetweenBootEventsMustRestoreFirstBoot(String animation, HumanoidArm mainArm, HumanoidArm firstBoot) {
        // 两次 equip 之间只有第一只靴子可见，手中仍持有第二只。
        client.player.setMainArm(mainArm);
        client.level.setTimeFromServer(START_TICK + AFTER_FIRST_EQUIP_TICKS);
        playback.play(playerId, animation, START_TICK, "late-first-boot");
        assertBoots(Set.of(firstBoot));
        assertFalse(playback.equipItem(playerId).isEmpty());
        playback.tick(client);
        assertBoots(Set.of(firstBoot));
        assertTrue(EVENTS.isEmpty());

        ClientPlaybackTestTime.advance(AFTER_SECOND_EQUIP_TICKS - AFTER_FIRST_EQUIP_TICKS);
        playback.tick(client);
        assertBoots(Set.of(HumanoidArm.RIGHT, HumanoidArm.LEFT));
        assertTrue(playback.equipItem(playerId).isEmpty());
        assertEquals(List.of(animation + ":equip"), EVENTS, "第二次事件只能通知一次");
    }

    @ParameterizedTest
    @CsvSource({"equip_boots, RIGHT", "equip_boots_off, RIGHT", "equip_boots, LEFT", "equip_boots_off, LEFT"})
    void lateJoinAfterBothBootEventsMustShowBothBoots(String animation, HumanoidArm mainArm) {
        // 已越过两次事件时，左右靴子及手中物品应一次恢复到当前进度。
        client.player.setMainArm(mainArm);
        client.level.setTimeFromServer(START_TICK + AFTER_SECOND_EQUIP_TICKS);
        playback.play(playerId, animation, START_TICK, "late-both-boots");
        assertBoots(Set.of(HumanoidArm.RIGHT, HumanoidArm.LEFT));
        assertTrue(playback.equipItem(playerId).isEmpty());
        playback.tick(client);
        assertBoots(Set.of(HumanoidArm.RIGHT, HumanoidArm.LEFT));
        assertTrue(EVENTS.isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"equip_boots, RIGHT, RIGHT", "equip_boots_off, RIGHT, LEFT",
            "equip_boots, LEFT, LEFT", "equip_boots_off, LEFT, RIGHT"})
    void repeatedSynchronizationMustKeepBootSidesAndFinishSecondBoot(
            String animation, HumanoidArm mainArm, HumanoidArm firstBoot) {
        // 同一请求在两次事件前后补发，都不能重置已经恢复的一侧或重复计数。
        client.player.setMainArm(mainArm);
        client.level.setTimeFromServer(START_TICK + 1);
        playback.play(playerId, animation, START_TICK, "same-boots");
        playback.tick(client);
        assertBoots(Set.of());

        ClientPlaybackTestTime.advance(AFTER_FIRST_EQUIP_TICKS - 1);
        playback.tick(client);
        assertBoots(Set.of(firstBoot));
        playback.play(playerId, animation, START_TICK, "same-boots");
        playback.tick(client);
        assertBoots(Set.of(firstBoot));

        ClientPlaybackTestTime.advance(AFTER_SECOND_EQUIP_TICKS - AFTER_FIRST_EQUIP_TICKS);
        playback.tick(client);
        playback.play(playerId, animation, START_TICK, "same-boots");
        playback.tick(client);
        assertBoots(Set.of(HumanoidArm.RIGHT, HumanoidArm.LEFT));
        assertTrue(playback.equipItem(playerId).isEmpty());
        assertEquals(List.of(animation + ":equip", animation + ":equip"), EVENTS);
    }

    private void assertBoots(Set<HumanoidArm> equipped) {
        // 左右侧分别检查，同时核对整层剔除不会隐藏已穿好的一只靴子。
        for (HumanoidArm side : HumanoidArm.values()) {
            assertEquals(!equipped.contains(side), playback.shouldHideBoot(playerId, side == HumanoidArm.LEFT),
                    side.toString());
        }
        assertEquals(equipped.isEmpty(), playback.shouldHideArmor(playerId, EquipmentSlot.FEET));
    }
}

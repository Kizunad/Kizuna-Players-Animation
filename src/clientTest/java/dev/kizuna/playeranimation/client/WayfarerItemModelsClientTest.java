package dev.kizuna.playeranimation.client;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.item.properties.conditional.IsUsingItem;
import net.minecraft.client.renderer.item.properties.numeric.CrossbowPull;
import net.minecraft.client.renderer.item.properties.numeric.UseDuration;
import net.minecraft.client.renderer.item.properties.select.Charge;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

/** 实际模型属性经过 Mixin 后读取动画轨道，且不会修改服务端同步的物品组件。 */
final class WayfarerItemModelsClientTest {
    private static final int ENTITY_ID = 700004;
    private static final long BLEND_TICKS = 4;
    private static final float DRAW_SECONDS = 3.2f;
    private static final float CHARGED_SECONDS = 1.58f;
    private static final float CHARGED_PULL = 0.9f;
    private static final float MODEL_TOLERANCE = 0.00001f;
    private static final float EXPECTED_DRAW_TICKS = 20;
    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000012");
    private static final ItemDisplayContext DISPLAY = ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;

    @Test
    void playerAndPillagerItemModelsFollowSelectedHand() {
        // 左右惯用手只改变骨骼镜像；逻辑主副手、物品身份和原版回退必须一致。
        Minecraft client = Minecraft.getInstance();
        long savedTime = client.level.getGameTime();
        try {
            for (LivingEntity owner : List.of(player(HumanoidArm.RIGHT), player(HumanoidArm.LEFT),
                    new Pillager(EntityTypes.PILLAGER, client.level))) {
                owner.setId(ENTITY_ID);
                client.level.addEntity(owner);
                for (EquipmentSlot slot : List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) {
                    assertBow(owner, slot);
                    assertCrossbow(owner, slot);
                }
                client.level.removeEntity(ENTITY_ID, Entity.RemovalReason.DISCARDED);
            }
        } finally {
            client.level.removeEntity(ENTITY_ID, Entity.RemovalReason.DISCARDED);
            client.level.setTimeFromServer(savedTime);
            PlayerAnimationClient.PLAYBACK.clear();
        }
    }

    private static RemotePlayer player(HumanoidArm arm) {
        // 只替换惯用手事实，其余行为使用真实客户端玩家。
        return new RemotePlayer(Minecraft.getInstance().level, new GameProfile(PLAYER_ID, "PA_wayfarer_test")) {
            @Override
            public HumanoidArm getMainArm() {
                return arm;
            }
        };
    }

    private static void assertBow(LivingEntity owner, EquipmentSlot slot) {
        // 两手放同类武器，确认轨道不能仅按物品类型接管另一手或库存副本。
        equip(owner, slot, Items.BOW.getDefaultInstance(), Items.BOW.getDefaultInstance());
        ItemStack bow = owner.getItemBySlot(slot);
        ItemStack original = bow.copy();
        ItemStack other = owner.getItemBySlot(other(slot));
        sample(owner, slot, "wayfarer_full_draw", DRAW_SECONDS);
        var level = Minecraft.getInstance().level;
        var using = new IsUsingItem();
        var duration = new UseDuration(false /* remaining */);
        var arrow = new AnimationArrowProperty();
        assertTrue(using.get(bow, level, owner, 0, DISPLAY));
        assertFalse(using.get(other, level, owner, 0, DISPLAY));
        assertFalse(using.get(original, level, owner, 0, DISPLAY));
        assertEquals(EXPECTED_DRAW_TICKS, duration.get(bow, level, owner, 0));
        assertTrue(arrow.get(bow, level, owner, 0, DISPLAY));
        sample(owner, slot, "wayfarer_dry_draw", DRAW_SECONDS);
        assertFalse(arrow.get(bow, level, owner, 0, DISPLAY));
        assertTrue(arrow.get(other, level, owner, 0, DISPLAY));
        assertTrue(ItemStack.matches(original, bow), "模型采样不得修改真实物品");
        PlayerAnimationClient.PLAYBACK.clear();
        assertFalse(using.get(bow, level, owner, 0, DISPLAY));
        assertEquals(0, duration.get(bow, level, owner, 0));
        assertTrue(arrow.get(bow, level, owner, 0, DISPLAY));
    }

    private static void assertCrossbow(LivingEntity owner, EquipmentSlot slot) {
        // 视觉上弦使用箭或烟花模型，真实未上弦组件在整段测试中保持不变。
        equip(owner, slot, Items.CROSSBOW.getDefaultInstance(), ItemStack.EMPTY);
        ItemStack crossbow = owner.getItemBySlot(slot);
        ItemStack original = crossbow.copy();
        var level = Minecraft.getInstance().level;
        var charge = new Charge();
        var pull = new CrossbowPull();
        float vanillaPull = pull.get(crossbow, level, owner, 0);
        sample(owner, slot, "wayfarer_repeater", CHARGED_SECONDS);
        assertFalse(new IsUsingItem().get(crossbow, level, owner, 0, DISPLAY));
        // 上弦帧之后 pull 已开始复位，charged 仍在开启窗口内。
        assertEquals(CHARGED_PULL, pull.get(crossbow, level, owner, 0), MODEL_TOLERANCE);
        assertEquals(CrossbowItem.ChargeType.ARROW, charge.get(crossbow, level, owner, 0, DISPLAY));
        owner.setItemSlot(other(slot), Items.FIREWORK_ROCKET.getDefaultInstance());
        assertEquals(CrossbowItem.ChargeType.ROCKET, charge.get(crossbow, level, owner, 0, DISPLAY));
        assertTrue(ItemStack.matches(original, crossbow), "视觉上弦不能写真实弹药组件");
        PlayerAnimationClient.PLAYBACK.clear();
        assertEquals(CrossbowItem.ChargeType.NONE, charge.get(crossbow, level, owner, 0, DISPLAY));
        assertEquals(vanillaPull, pull.get(crossbow, level, owner, 0));
    }

    private static void sample(LivingEntity owner, EquipmentSlot slot, String move, float seconds) {
        // 公共播放入口加载真实资源，再跨过淡入；scrub 固定剪辑时刻以避开窄射击窗口的帧抖动。
        var playback = PlayerAnimationClient.PLAYBACK;
        playback.clear();
        String animation = move;
        if (slot == EquipmentSlot.OFFHAND) {
            animation = move + "_off";
        }
        var entry = PlayerAnimationClient.ASSETS.animations().get(animation);
        assertNotNull(entry, animation);
        float progress = seconds / entry.clip().duration();
        if (owner instanceof Player) {
            playback.scrub(owner.getUUID(), animation, progress);
        } else {
            playback.scrubEntity(owner.getId(), animation, progress);
        }
        var level = Minecraft.getInstance().level;
        ClientPlaybackTestTime.advance(BLEND_TICKS);
    }

    private static void equip(LivingEntity owner, EquipmentSlot slot, ItemStack weapon, ItemStack other) {
        // 每个场景显式重置双手，避免上个场景的弹药或武器影响判断。
        owner.setItemSlot(slot, weapon);
        owner.setItemSlot(other(slot), other);
    }

    private static EquipmentSlot other(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
    }
}

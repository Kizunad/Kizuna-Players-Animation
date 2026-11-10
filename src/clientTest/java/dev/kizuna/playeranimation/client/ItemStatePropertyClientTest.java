package dev.kizuna.playeranimation.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import dev.kizuna.playeranimation.animation.ItemStates;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

/** 数值属性 item_state 读持有者剪辑的刀刃轨道；另一只手、库存副本、没有持有者和没有剪辑时都取 idle。 */
final class ItemStatePropertyClientTest {
    private static final int ENTITY_ID = 700005;
    private static final long BLEND_TICKS = 4;
    private static final float IDLE = 1;
    private static final float DRAW_SECONDS = 0.6f;
    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000013");

    @Test
    void bladeFollowsTheHolderClipAndFallsBackToIdle() {
        // 两只手放同一种物品，确认属性按物品身份认手，不按物品种类接管。
        Minecraft client = Minecraft.getInstance();
        var level = client.level;
        long savedTime = level.getGameTime();
        var owner = new RemotePlayer(level, new GameProfile(PLAYER_ID, "PA_item_state_test"));
        owner.setId(ENTITY_ID);
        level.addEntity(owner);
        try {
            owner.setItemSlot(EquipmentSlot.MAINHAND, Items.SHEARS.getDefaultInstance());
            owner.setItemSlot(EquipmentSlot.OFFHAND, Items.SHEARS.getDefaultInstance());
            ItemStack knife = owner.getMainHandItem();
            var property = new AnimationItemStateProperty(ItemStates.Channel.OPEN, IDLE);
            assertEquals(IDLE, property.get(knife, level, owner, 0));
            scrub(owner, 0.1f);
            assertEquals(0, property.get(knife, level, owner, 0));
            assertEquals(IDLE, property.get(owner.getOffhandItem(), level, owner, 0));
            assertEquals(IDLE, property.get(knife.copy(), level, owner, 0));
            assertEquals(IDLE, property.get(knife, level, null, 0));
            scrub(owner, 0.3f);
            assertEquals(1, property.get(knife, level, owner, 0));
            PlayerAnimationClient.PLAYBACK.clear();
            assertEquals(IDLE, property.get(knife, level, owner, 0));
        } finally {
            level.removeEntity(ENTITY_ID, Entity.RemovalReason.DISCARDED);
            level.setTimeFromServer(savedTime);
            PlayerAnimationClient.PLAYBACK.clear();
        }
    }

    @Test
    void itemDefinitionsDecodeThePropertyWithIdleDefaultZero() {
        // 物品定义经原版 range_dispatch 的属性编解码读入；未知通道在资源加载时报错。
        var codec = RangeSelectItemModelProperties.MAP_CODEC.codec();
        var full = JsonParser.parseString("{\"property\":\"kizuna_players_animation:item_state\","
                + "\"channel\":\"open\",\"idle\":1}");
        assertEquals(new AnimationItemStateProperty(ItemStates.Channel.OPEN, 1),
                codec.parse(JsonOps.INSTANCE, full).getOrThrow());
        var bare = JsonParser.parseString("{\"property\":\"kizuna_players_animation:item_state\","
                + "\"channel\":\"open\"}");
        assertEquals(new AnimationItemStateProperty(ItemStates.Channel.OPEN, 0),
                codec.parse(JsonOps.INSTANCE, bare).getOrThrow());
        var unknown = JsonParser.parseString("{\"property\":\"kizuna_players_animation:item_state\","
                + "\"channel\":\"blade\"}");
        assertTrue(codec.parse(JsonOps.INSTANCE, unknown).isError());
    }

    private static void scrub(RemotePlayer owner, float seconds) {
        // 公共播放入口加载真实开刃资源，固定剪辑时刻后跨过淡入。
        var playback = PlayerAnimationClient.PLAYBACK;
        playback.clear();
        assertNotNull(PlayerAnimationClient.ASSETS.animations().get("herb_knife_draw"));
        playback.scrub(owner.getUUID(), "herb_knife_draw", seconds / DRAW_SECONDS);
        ClientPlaybackTestTime.advance(BLEND_TICKS);
    }
}

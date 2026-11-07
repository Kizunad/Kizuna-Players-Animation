package dev.kizuna.playeranimation.client;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.npc.BabyVillagerModel;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import org.junit.jupiter.api.Test;

/** 同一实体类型的幼年模型不能继承成年几何的部件和 UV。 */
final class MobModelVariantsClientTest {
    private static final int ENTITY_ID = 700004;
    private static final int TEXTURE_SIZE = 64;

    @Test
    void babyVillagerMustKeepVanillaModelAndResumeAnimationWhenAdult() throws Exception {
        Minecraft client = Minecraft.getInstance();
        Villager villager = new Villager(EntityTypes.VILLAGER, client.level);
        villager.setId(ENTITY_ID);
        client.level.addEntity(villager);
        try {
            registerGeometry(client);
            PlayerAnimationClient.PLAYBACK.playEntity(ENTITY_ID, "dodge_forward",
                    client.level.getGameTime(), "villager-model", null);
            VillagerRenderer renderer = (VillagerRenderer) client.getEntityRenderDispatcher().getRenderer(villager);
            assertNotNull(EntityFrame.of(renderer.createRenderState(villager, 0)), "成年村民应使用已注册几何");
            villager.setBaby(true /* baby */);
            assertNull(EntityFrame.of(renderer.createRenderState(villager, 0)), "幼年村民必须保留原版独立模型");
            villager.setBaby(false /* baby */);
            assertNotNull(EntityFrame.of(renderer.createRenderState(villager, 0)), "成年后应恢复 PA 动画");
        } finally {
            client.level.removeEntity(ENTITY_ID, Entity.RemovalReason.DISCARDED);
            PlayerAnimationClient.PLAYBACK.clear();
        }
    }

    @Test
    void vanillaBabyVillagerHasNoAdultJacket() {
        // 直接核对 26.2 的真实模型工厂，防止把幼年几何当成成年模型的等比缩放。
        var adult = LayerDefinition.create(VillagerModel.createBodyModel(), TEXTURE_SIZE, TEXTURE_SIZE).bakeRoot();
        var baby = LayerDefinition.create(BabyVillagerModel.createBodyModel(), TEXTURE_SIZE, TEXTURE_SIZE).bakeRoot();
        assertTrue(adult.getChild("body").hasChild("jacket"));
        assertFalse(baby.getChild("body").hasChild("jacket"));
    }

    private static void registerGeometry(Minecraft client) throws Exception {
        // 只验证帧接管边界；真实村民几何与职业纹理由生物附属的截图用例覆盖。
        try (var stream = client.getResourceManager()
                .getResourceOrThrow(PlayerAssets.id("geo/player_skeleton.geo.json")).open()) {
            var registration = new MobAnimationRegistry.Registration("geometry.player.classic",
                    stream.readAllBytes(), List.of("minecraft:villager"), "");
            MobAnimationRegistry.register(registration);
        }
    }
}

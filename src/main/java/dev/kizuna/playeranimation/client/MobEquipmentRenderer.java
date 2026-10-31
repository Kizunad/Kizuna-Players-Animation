package dev.kizuna.playeranimation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kizuna.playeranimation.model.BoneId;
import dev.kizuna.playeranimation.model.Rig;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.CrossedArmsItemLayer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** 为 PA 生物补齐原版渲染器缺少的装备层，并统一手骨和头骨挂点。 */
public final class MobEquipmentRenderer {
    private final HumanoidArmorLayer<HumanoidRenderState, HumanoidModel<HumanoidRenderState>,
            HumanoidModel<HumanoidRenderState>> armor;
    private final CustomHeadLayer<LivingEntityRenderState, HeadAnchor> head;

    /** 头饰层只需要一个停在绑定姿态的头部节点，头骨变换由 PA 帧另行提供。 */
    private static final class HeadAnchor extends EntityModel<LivingEntityRenderState> implements HeadedModel {
        private final ModelPart headPart;

        HeadAnchor(ModelPart root) {
            super(root);
            headPart = root.getChild("head");
        }

        @Override
        public ModelPart getHead() {
            return headPart;
        }
    }

    public MobEquipmentRenderer(EntityRendererProvider.Context context) {
        // 使用原版材质、染色、纹饰及附魔入口；盔甲顶点继续由 PA 绘制钩子按盔甲模型变形。
        HumanoidModel<HumanoidRenderState> body = new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER));
        var armorModels = ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, context.getModelSet(),
                part -> new HumanoidModel<HumanoidRenderState>(part));
        armor = new HumanoidArmorLayer<>(() -> body, armorModels, context.getEquipmentRenderer());
        HeadAnchor anchor = new HeadAnchor(context.bakeLayer(ModelLayers.PLAYER));
        head = new CustomHeadLayer<>(() -> anchor, context.getModelSet(), context.getPlayerSkinRenderCache());
    }

    public void submit(EntityFrame frame, LivingEntityRenderState state, PoseStack matrices,
                       SubmitNodeCollector collector) {
        // 物品在原版实体变换之内，再应用同帧手骨变换。
        EntityFrame.MobParts parts = frame.mob();
        int light = state.lightCoords;
        for (EntityFrame.HeldItem item : parts.items()) {
            item.submit(matrices, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
        }
        if (parts.armorState() != null) {
            armor.submit(matrices, collector, light, parts.armorState(), 0, 0);
        }
        // 原版头饰入口同时处理头颅、方块和袭击旗帜，应用 PA 头骨后不再叠原版头部姿势。
        Matrix4f transform = Rig.toVanilla(frame.pose().matrices()[BoneId.HEAD.ordinal()]);
        matrices.pushPose();
        matrices.last().pose().mul(transform);
        matrices.last().normal().mul(new Matrix3f(transform));
        head.submit(matrices, collector, light, state, 0, 0);
        matrices.popPose();
    }

    public static boolean replaces(RenderLayer<?, ?> feature, EntityFrame frame) {
        // 只跳过已由 PA 绘制的装备层，村民职业服装等材质层仍使用原版入口。
        return frame != null && frame.mob() != null
                && (feature instanceof ItemInHandLayer<?, ?>
                || feature instanceof CrossedArmsItemLayer<?, ?>
                || feature instanceof CustomHeadLayer<?, ?>);
    }
}

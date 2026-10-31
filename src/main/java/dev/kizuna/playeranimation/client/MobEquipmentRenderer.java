package dev.kizuna.playeranimation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kizuna.playeranimation.model.BoneId;
import dev.kizuna.playeranimation.model.Rig;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CrossedArmsItemLayer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;

/** 为 PA 生物补齐原版渲染器缺少的装备层，并统一手骨和头骨挂点。 */
public final class MobEquipmentRenderer
        implements RenderLayerParent<LivingEntity, HumanoidModel<LivingEntity>> {
    private final HumanoidModel<LivingEntity> model;
    private final HumanoidArmorLayer<LivingEntity, HumanoidModel<LivingEntity>,
            HumanoidModel<LivingEntity>> armor;
    private final CustomHeadLayer<LivingEntity, HumanoidModel<LivingEntity>> head;
    private final ItemInHandRenderer heldItems;

    public record Draw(LivingEntity entity, PoseStack matrices, MultiBufferSource buffers,
                       int light, float delta) { }

    public MobEquipmentRenderer(EntityRendererProvider.Context context) {
        // 使用原版材质、染色、纹饰及附魔入口；盔甲顶点继续由已有 PA armor mixin 变形。
        model = new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER));
        var inner = new HumanoidModel<LivingEntity>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
        var outer = new HumanoidModel<LivingEntity>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
        armor = new HumanoidArmorLayer<>(this, inner, outer, context.getModelManager());
        heldItems = context.getItemInHandRenderer();
        head = new CustomHeadLayer<>(this, context.getModelSet(), heldItems);
    }

    public void render(Draw draw, EntityModel<?> vanilla) {
        // 原版会按举斧、交叉臂等状态跳过持物，这里始终从同帧 PA 手骨绘制双手。
        LivingEntity entity = draw.entity();
        var frame = MobAnimationRenderer.frame(entity.getId());
        if (frame == null) {
            return;
        }
        HumanoidArm mainArm = entity.getMainArm();
        item(draw, entity.getMainHandItem(), mainArm);
        item(draw, entity.getOffhandItem(), mainArm.getOpposite());
        PoseStack matrices = draw.matrices();
        MultiBufferSource buffers = draw.buffers();
        int light = draw.light();
        float delta = draw.delta();
        if (!(vanilla instanceof HumanoidModel<?>)) {
            armor.render(matrices, buffers, light, entity, 0, 0, delta, 0, 0, 0);
        }
        // 原版头饰入口同时处理头颅、方块和袭击旗帜，应用 PA 头骨后不再叠原版头部姿势。
        var transform = Rig.toVanilla(frame.matrices()[BoneId.HEAD.ordinal()]);
        matrices.pushPose();
        matrices.last().pose().mul(transform);
        matrices.last().normal().mul(new Matrix3f(transform));
        head.render(matrices, buffers, light, entity, 0, 0, delta, 0, 0, 0);
        matrices.popPose();
    }

    private void item(Draw draw, ItemStack stack, HumanoidArm arm) {
        // 左右手保持原版物品展示模式，具体位置由独立 item 骨提供。
        ItemDisplayContext mode = ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        if (arm == HumanoidArm.LEFT) {
            mode = ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        }
        var item = new MobAnimationRenderer.ItemDraw(stack, mode, arm, draw.matrices(), draw.buffers(), draw.light());
        MobAnimationRenderer.item(draw.entity(), item, heldItems);
    }

    public static boolean replaces(RenderLayer<?, ?> feature, Entity entity) {
        // 只跳过已由 PA 绘制的装备层，村民职业服装等材质层仍使用原版入口。
        return MobAnimationRenderer.frame(entity.getId()) != null
                && (feature instanceof ItemInHandLayer<?, ?>
                || feature instanceof CrossedArmsItemLayer<?, ?>
                || feature instanceof CustomHeadLayer<?, ?>);
    }

    @Override
    public HumanoidModel<LivingEntity> getModel() {
        return model;
    }

    @Override
    public ResourceLocation getTextureLocation(LivingEntity entity) {
        // 装备 feature 自行选材质，不使用身体贴图。
        throw new UnsupportedOperationException("装备上下文不提供身体贴图");
    }
}

package dev.kizuna.playeranimation.client;

import dev.kizuna.playeranimation.model.BoneId;
import dev.kizuna.playeranimation.model.Rig;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.feature.HeadFeatureRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.feature.VillagerHeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;

/** 为 PA 生物补齐原版渲染器缺少的装备层，并统一手骨和头骨挂点。 */
public final class MobEquipmentRenderer
        implements FeatureRendererContext<LivingEntity, BipedEntityModel<LivingEntity>> {
    private final BipedEntityModel<LivingEntity> model;
    private final ArmorFeatureRenderer<LivingEntity, BipedEntityModel<LivingEntity>,
            BipedEntityModel<LivingEntity>> armor;
    private final HeadFeatureRenderer<LivingEntity, BipedEntityModel<LivingEntity>> head;
    private final HeldItemRenderer heldItems;

    public record Draw(LivingEntity entity, MatrixStack matrices, VertexConsumerProvider buffers,
                       int light, float delta) { }

    public MobEquipmentRenderer(EntityRendererFactory.Context context) {
        // 使用原版材质、染色、纹饰及附魔入口；盔甲顶点继续由已有 PA armor mixin 变形。
        model = new BipedEntityModel<>(context.getPart(EntityModelLayers.PLAYER));
        var inner = new BipedEntityModel<LivingEntity>(context.getPart(EntityModelLayers.PLAYER_INNER_ARMOR));
        var outer = new BipedEntityModel<LivingEntity>(context.getPart(EntityModelLayers.PLAYER_OUTER_ARMOR));
        armor = new ArmorFeatureRenderer<>(this, inner, outer, context.getModelManager());
        heldItems = context.getHeldItemRenderer();
        head = new HeadFeatureRenderer<>(this, context.getModelLoader(), heldItems);
    }

    public void render(Draw draw, EntityModel<?> vanilla) {
        // 原版会按举斧、交叉臂等状态跳过持物，这里始终从同帧 PA 手骨绘制双手。
        LivingEntity entity = draw.entity();
        var frame = MobAnimationRenderer.frame(entity.getId());
        if (frame == null) {
            return;
        }
        Arm mainArm = entity.getMainArm();
        item(draw, entity.getMainHandStack(), mainArm);
        item(draw, entity.getOffHandStack(), mainArm.getOpposite());
        MatrixStack matrices = draw.matrices();
        VertexConsumerProvider buffers = draw.buffers();
        int light = draw.light();
        float delta = draw.delta();
        if (!(vanilla instanceof BipedEntityModel<?>)) {
            armor.render(matrices, buffers, light, entity, 0, 0, delta, 0, 0, 0);
        }
        // 原版头饰入口同时处理头颅、方块和袭击旗帜，应用 PA 头骨后不再叠原版头部姿势。
        var transform = Rig.toVanilla(frame.matrices()[BoneId.HEAD.ordinal()]);
        matrices.push();
        matrices.peek().getPositionMatrix().mul(transform);
        matrices.peek().getNormalMatrix().mul(new Matrix3f(transform));
        head.render(matrices, buffers, light, entity, 0, 0, delta, 0, 0, 0);
        matrices.pop();
    }

    private void item(Draw draw, ItemStack stack, Arm arm) {
        // 左右手保持原版物品展示模式，具体位置由独立 item 骨提供。
        ModelTransformationMode mode = ModelTransformationMode.THIRD_PERSON_RIGHT_HAND;
        if (arm == Arm.LEFT) {
            mode = ModelTransformationMode.THIRD_PERSON_LEFT_HAND;
        }
        var item = new MobAnimationRenderer.ItemDraw(stack, mode, arm, draw.matrices(), draw.buffers(), draw.light());
        MobAnimationRenderer.item(draw.entity(), item, heldItems);
    }

    public static boolean replaces(FeatureRenderer<?, ?> feature, Entity entity) {
        // 只跳过已由 PA 绘制的装备层，村民职业服装等材质层仍使用原版入口。
        return MobAnimationRenderer.frame(entity.getId()) != null
                && (feature instanceof HeldItemFeatureRenderer<?, ?>
                || feature instanceof VillagerHeldItemFeatureRenderer<?, ?>
                || feature instanceof HeadFeatureRenderer<?, ?>);
    }

    @Override
    public BipedEntityModel<LivingEntity> getModel() {
        return model;
    }

    @Override
    public Identifier getTexture(LivingEntity entity) {
        // 装备 feature 自行选材质，不使用身体贴图。
        throw new UnsupportedOperationException("装备上下文不提供身体贴图");
    }
}

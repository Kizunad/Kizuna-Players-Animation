package dev.kizuna.playeranimation.client;

import dev.kizuna.playeranimation.model.BoneId;
import dev.kizuna.playeranimation.model.Pose;
import dev.kizuna.playeranimation.model.Rig;
import dev.kizuna.playeranimation.model.Skeleton;
import dev.kizuna.playeranimation.model.SkinnedMesh;
import dev.kizuna.playeranimation.model.Vec3;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class PlayerMeshRenderer {
    private static final float SLIM_ITEM_SHOULDER_OFFSET = 0.5f;
    public record Frame(PlayerAssets.Model model, Matrix4f[] matrices) { }
    private PlayerMeshRenderer() { }

    public static Frame frame(AbstractClientPlayerEntity player, PlayerEntityModel<?> vanilla, float tickDelta, boolean firstPerson) {
        PlayerAssets.Model model = PlayerAnimationClient.ASSETS.model("slim".equals(player.getModel()));
        if (model == null) {
            return null;
        }
        // 先还原原版姿态，再按实际存在的轨道覆盖，未写的腿仍保持原版走路。
        Pose[] base = basePose(model.skeleton(), vanilla, firstPerson, "slim".equals(player.getModel()));
        var animation = PlayerAnimationClient.PLAYBACK.player(player.getUuid());
        Pose[] pose = animation == null ? base : animation.sample(base, PlayerPlayback.now(tickDelta), firstPerson);
        return new Frame(model, Rig.matrices(model.skeleton(), pose));
    }

    public static Pose[] basePose(Skeleton skeleton, PlayerEntityModel<?> model, boolean firstPerson, boolean slim) {
        Map<BoneId, Matrix4f> absolute = new EnumMap<>(BoneId.class);
        Pose[] pose = Rig.zeroPose();
        // 原版六部件在绑定空间的变换转换为 25 骨局部姿态；保留蹲伏等枢轴位移。
        for (BoneId bone : BoneId.ALL) {
            ModelPart part = part(model, bone, false);
            Matrix4f target = bone == BoneId.ROOT || (firstPerson && !bone.arm())
                    ? new Matrix4f() : vanillaTransform(part);
            absolute.put(bone, target);
            Matrix4f local = bone.parent == null ? target : new Matrix4f(absolute.get(bone.parent)).invert().mul(target);
            if (slim && bone.socket()) {
                // 原版细臂持物先沿模型 X 内移肩部，再旋转手臂；挂点已包含零姿态内移。
                // 将差值写入底层局部姿态，让后续 root、胸、前臂和手腕动画共同带动它。
                float inward = bone == BoneId.R_ITEM ? SLIM_ITEM_SHOULDER_OFFSET : -SLIM_ITEM_SHOULDER_OFFSET;
                Vector3f correction = new Matrix4f(target).invert()
                        .transformDirection(inward, 0, 0, new Vector3f()).sub(inward, 0, 0);
                local.translate(correction);
            }
            pose[bone.ordinal()] = Rig.localPose(local, skeleton.bone(bone).pivot());
        }
        return pose;
    }

    private static Matrix4f vanillaTransform(ModelPart part) {
        ModelTransform bind = part.getDefaultTransform();
        // Y 轴反射使 X/Z 旋转反号；每节矩阵最终仍作用于全局像素位置。
        return new Matrix4f().translate(part.pivotX, Rig.MODEL_ORIGIN_HEIGHT - part.pivotY, part.pivotZ)
                .rotateZYX(-part.roll, part.yaw, -part.pitch)
                .translate(-bind.pivotX, bind.pivotY - Rig.MODEL_ORIGIN_HEIGHT, -bind.pivotZ);
    }

    public static ModelPart part(PlayerEntityModel<?> model, BoneId bone, boolean outer) {
        // 原版部件可见性保留皮肤设置、旁观者状态等原版渲染行为。
        return switch (bone.skinPart()) {
            case "head" -> outer ? model.hat : model.head;
            case "body" -> outer ? model.jacket : model.body;
            case "r_arm" -> outer ? model.rightSleeve : model.rightArm;
            case "l_arm" -> outer ? model.leftSleeve : model.leftArm;
            case "r_leg" -> outer ? model.rightPants : model.rightLeg;
            case "l_leg" -> outer ? model.leftPants : model.leftLeg;
            default -> throw new IllegalArgumentException("未知皮肤区域: " + bone);
        };
    }

    public static void draw(Frame frame, MatrixStack stack, VertexConsumer buffer, int light, int overlay,
                            float red, float green, float blue, float alpha, Predicate<SkinnedMesh.Surface> visible) {
        MatrixStack.Entry entry = stack.peek();
        Vector3f position = new Vector3f();
        Vector3f normal = new Vector3f();
        // 顶点与法线使用同一套骨骼权重，皮肤 UV 始终保留导出的原始像素采样。
        for (SkinnedMesh.Surface surface : frame.model.mesh().surfaces()) {
            if (!visible.test(surface)) {
                continue;
            }
            for (SkinnedMesh.Vertex vertex : surface.vertices()) {
                SkinnedMesh.deform(vertex.position(), vertex.weights(), frame.matrices, false, position);
                SkinnedMesh.deform(vertex.normal(), vertex.weights(), frame.matrices, true, normal);
                buffer.vertex(entry.getPositionMatrix(), position.x / Rig.PIXELS_PER_BLOCK,
                                (Rig.MODEL_ORIGIN_HEIGHT - position.y) / Rig.PIXELS_PER_BLOCK, position.z / Rig.PIXELS_PER_BLOCK)
                        .color(red, green, blue, alpha).texture(vertex.u(), vertex.v()).overlay(overlay).light(light)
                        .normal(entry.getNormalMatrix(), normal.x, -normal.y, normal.z).next();
            }
        }
    }

    public static void item(Frame frame, AbstractClientPlayerEntity player, Arm arm, ItemStack item,
                            MatrixStack stack, VertexConsumerProvider buffers, int light, HeldItemRenderer renderer) {
        if (item.isEmpty()) {
            return;
        }
        BoneId socket = arm == Arm.RIGHT ? BoneId.R_ITEM : BoneId.L_ITEM;
        stack.push();
        // 挂点先跟随手掌矩阵，再叠原版物品坐标方向与 display 变换。
        Matrix4f transform = Rig.itemTransform(frame.model.skeleton(), frame.matrices, socket);
        stack.peek().getPositionMatrix().mul(transform);
        stack.peek().getNormalMatrix().mul(new org.joml.Matrix3f(transform));
        renderer.renderItem(player, item, arm == Arm.RIGHT ? ModelTransformationMode.THIRD_PERSON_RIGHT_HAND
                : ModelTransformationMode.THIRD_PERSON_LEFT_HAND, arm == Arm.LEFT, stack, buffers, light);
        stack.pop();
    }

    public static boolean firstPerson(AbstractClientPlayerEntity player, float tickDelta, MatrixStack stack,
                                       VertexConsumerProvider buffers, int light, HeldItemRenderer items) {
        var animation = PlayerAnimationClient.PLAYBACK.player(player.getUuid());
        if (animation == null || !animation.hasFirstPerson(PlayerPlayback.now(tickDelta))
                || PlayerAnimationClient.ASSETS.model("slim".equals(player.getModel())) == null) {
            return false;
        }
        PlayerEntityRenderer renderer = (PlayerEntityRenderer) MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(player);
        ((dev.kizuna.playeranimation.mixin.PlayerEntityRendererAccessor) renderer).kpa$setModelPose(player);
        PlayerEntityModel<AbstractClientPlayerEntity> model = renderer.getModel();
        model.animateModel(player, 0, 0, tickDelta);
        model.setAngles(player, 0, 0, player.age + tickDelta, 0, player.getPitch(tickDelta));
        Frame frame = frame(player, model, tickDelta, true);
        stack.push();
        // 第一人称在眼睛坐标绘制实际手臂，只采样 fpv 骨；身体骨动画不进入相机。
        stack.translate(0, Rig.MODEL_ORIGIN_HEIGHT / Rig.PIXELS_PER_BLOCK - player.getEyeHeight(player.getPose()), 0);
        stack.scale(-1, -1, 1);
        if (!player.isInvisible()) {
            draw(frame, stack, buffers.getBuffer(RenderLayer.getEntityTranslucent(player.getSkinTexture())), light,
                    OverlayTexture.DEFAULT_UV, 1, 1, 1, 1,
                    surface -> surface.bone().arm() && part(model, surface.bone(), surface.outer()).visible);
        }
        Arm main = player.getMainArm();
        item(frame, player, main, player.getMainHandStack(), stack, buffers, light, items);
        item(frame, player, main.getOpposite(), player.getOffHandStack(), stack, buffers, light, items);
        stack.pop();
        return true;
    }
}

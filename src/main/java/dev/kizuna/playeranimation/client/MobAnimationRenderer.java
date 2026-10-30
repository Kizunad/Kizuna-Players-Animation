package dev.kizuna.playeranimation.client;

import dev.kizuna.playeranimation.animation.AnimationPlayer;
import dev.kizuna.playeranimation.model.BoneId;
import dev.kizuna.playeranimation.model.Pose;
import dev.kizuna.playeranimation.model.Rig;
import dev.kizuna.playeranimation.model.SkinnedMesh;
import dev.kizuna.playeranimation.model.Vec3;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.IllagerEntityModel;
import net.minecraft.client.render.entity.model.VillagerResemblingModel;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.joml.Matrix4f;
import org.joml.Matrix3f;

/** 使用 PA 24 骨蒙皮网格替换已注册生物的本体绘制。 */
public final class MobAnimationRenderer {
    private static final float RADIANS_PER_DEGREE = (float) (Math.PI / 180);
    private static final float CROSSED_SHOULDER_DEGREES = -43;
    private static final float CROSSED_ELBOW_DEGREES = -90;
    private static final Map<Integer, Frame> FRAMES = new HashMap<>();

    public record Frame(MobAnimationRegistry.Model model, Matrix4f[] matrices) { }

    private MobAnimationRenderer() { }

    public static void begin(int entityId) {
        FRAMES.remove(entityId);
    }

    public static void restore(int entityId) {
        FRAMES.remove(entityId);
    }

    public static void frame(LivingEntity entity, EntityModel<?> vanilla, float tickDelta) {
        // 自动层完全替代原版姿态；显式动作最后混合，结束后回到持续推进的移动层。
        int entityId = entity.getId();
        AnimationPlayer animation = PlayerAnimationClient.PLAYBACK.entity(entityId);
        AnimationPlayer automatic = PlayerAnimationClient.PLAYBACK.automaticEntity(entityId);
        MobAnimationRegistry.Model model = MobAnimationRegistry.model(entity);
        if (animation == null && automatic == null || model == null) {
            return;
        }
        double now = PlayerPlayback.now(tickDelta);
        boolean crouching = entity.isInSneakingPose();
        Pose[] base;
        if (automatic == null) {
            base = basePose(vanilla);
        } else {
            base = automatic.sample(Rig.zeroPose(), now, false /* firstPerson */, crouching);
        }
        Pose[] sampled = base;
        if (animation != null) {
            sampled = animation.sample(base, now, false /* firstPerson */, crouching);
        }
        Matrix4f[] matrices = Rig.matrices(model.body().skeleton(), sampled);
        FRAMES.put(entityId, new Frame(model, matrices));
    }

    public static Frame frame(int entityId) {
        return FRAMES.get(entityId);
    }

    public record ItemDraw(ItemStack item, ModelTransformationMode mode, Arm arm, MatrixStack matrices,
                           VertexConsumerProvider buffers, int light) { }

    public static boolean item(LivingEntity entity, ItemDraw draw, HeldItemRenderer renderer) {
        Frame frame = FRAMES.get(entity.getId());
        ItemStack item = draw.item();
        if (frame == null || item.isEmpty()) {
            return false;
        }
        // 物品在原版实体变换之内，再应用同帧手骨变换。
        Arm arm = draw.arm();
        BoneId socket = arm == Arm.RIGHT ? BoneId.R_ITEM : BoneId.L_ITEM;
        Matrix4f transform = Rig.itemTransform(frame.model().body().skeleton(), frame.matrices(), socket);
        MatrixStack matrices = draw.matrices();
        matrices.push();
        matrices.peek().getPositionMatrix().mul(transform);
        matrices.peek().getNormalMatrix().mul(new Matrix3f(transform));
        renderer.renderItem(entity, item, draw.mode(), arm == Arm.LEFT,
                matrices, draw.buffers(), draw.light());
        matrices.pop();
        return true;
    }

    public record Draw(MatrixStack matrices, VertexConsumer vertices, int light, int overlay,
                       float red, float green, float blue, float alpha) { }

    public static void draw(Frame frame, Draw draw, EntityModel<?> vanilla) {
        // 顶点提交与玩家共用实现，保留原版层选择和部件显隐。
        draw(frame, frame.model().body().mesh(), draw, vanilla);
    }

    public static void overlay(Frame frame, Draw draw, EntityModel<?> vanilla) {
        draw(frame, frame.model().overlay(), draw, vanilla);
    }

    private static void draw(Frame frame, SkinnedMesh mesh, Draw draw, EntityModel<?> vanilla) {
        PlayerMeshRenderer.Frame shared = new PlayerMeshRenderer.Frame(frame.model().body(), frame.matrices());
        PlayerMeshRenderer.draw(shared, mesh.surfaces(), draw.matrices(), draw.vertices(), draw.light(),
                draw.overlay(), draw.red(), draw.green(), draw.blue(), draw.alpha(),
                surface -> visible(vanilla, surface));
    }

    private static boolean visible(EntityModel<?> model, SkinnedMesh.Surface surface) {
        String path = surface.part();
        if (model instanceof VillagerResemblingModel<?> villager) {
            return visible(villager.getPart(), path);
        }
        if (model instanceof IllagerEntityModel<?> illager) {
            // 播放时双臂由独立骨驱动，不受原版交叉臂模式的隐藏开关限制。
            if (path.endsWith("_arm")) {
                return true;
            }
            return visible(illager.getPart(), path);
        }
        if (model instanceof BipedEntityModel<?> biped) {
            return visible(biped, path);
        }
        return true;
    }

    private static boolean visible(BipedEntityModel<?> biped, String path) {
        String[] children = path.split("/");
        ModelPart part = switch (children[0]) {
            case "head" -> biped.head;
            case "hat" -> biped.hat;
            case "body", "jacket" -> biped.body;
            case "right_arm", "right_sleeve" -> biped.rightArm;
            case "left_arm", "left_sleeve" -> biped.leftArm;
            case "right_leg", "right_pants" -> biped.rightLeg;
            case "left_leg", "left_pants" -> biped.leftLeg;
            default -> null;
        };
        if (part != null) {
            // 子部件必须继承祖先显隐，例如猪灵耳朵与僵尸村民帽沿。
            for (int i = 1; i < children.length; i++) {
                if (!part.visible || part.hidden) {
                    return false;
                }
                part = part.getChild(children[i]);
            }
            return part.visible && !part.hidden;
        }
        return true;
    }

    private static boolean visible(ModelPart root, String path) {
        ModelPart part = root;
        for (String name : path.split("/")) {
            if (!part.visible || part.hidden) {
                return false;
            }
            // 村民动画展开的手臂来自 arms 的纹理，没有原版独立节点。
            if (name.endsWith("_arm")) {
                return true;
            }
            part = part.getChild(name);
        }
        return part.visible && !part.hidden;
    }

    private static Pose[] basePose(EntityModel<?> model) {
        Pose[] pose = Rig.zeroPose();
        if (model instanceof BipedEntityModel<?> biped) {
            setRotation(pose, BoneId.CHEST, biped.body);
            setRotation(pose, BoneId.HEAD, biped.head);
            setRotation(pose, BoneId.R_UPPER_ARM, biped.rightArm);
            setRotation(pose, BoneId.L_UPPER_ARM, biped.leftArm);
            setRotation(pose, BoneId.R_THIGH, biped.rightLeg);
            setRotation(pose, BoneId.L_THIGH, biped.leftLeg);
            return pose;
        }
        if (model instanceof VillagerResemblingModel<?> villager) {
            ModelPartAccess parts = ModelPartAccess.villager(villager);
            setRotation(pose, BoneId.CHEST, parts.body());
            setRotation(pose, BoneId.HEAD, parts.head());
            setRotation(pose, BoneId.R_THIGH, parts.rightLeg());
            setRotation(pose, BoneId.L_THIGH, parts.leftLeg());
            crossArms(pose);
            return pose;
        }
        if (model instanceof IllagerEntityModel<?> illager) {
            ModelPartAccess parts = ModelPartAccess.illager(illager);
            setRotation(pose, BoneId.CHEST, parts.body());
            setRotation(pose, BoneId.HEAD, parts.head());
            setRotation(pose, BoneId.R_UPPER_ARM, parts.rightArm());
            setRotation(pose, BoneId.L_UPPER_ARM, parts.leftArm());
            setRotation(pose, BoneId.R_THIGH, parts.rightLeg());
            setRotation(pose, BoneId.L_THIGH, parts.leftLeg());
        }
        return pose;
    }

    private static void crossArms(Pose[] pose) {
        // 原版交叉臂由横向前臂连接，展开几何以双肘弯曲提供动画的底层姿态。
        Vec3 shoulder = new Vec3(CROSSED_SHOULDER_DEGREES, 0, 0);
        pose[BoneId.R_UPPER_ARM.ordinal()] = new Pose(Vec3.ZERO, shoulder);
        pose[BoneId.L_UPPER_ARM.ordinal()] = new Pose(Vec3.ZERO, shoulder);
        pose[BoneId.R_FOREARM.ordinal()] = new Pose(Vec3.ZERO, new Vec3(0, 0, CROSSED_ELBOW_DEGREES));
        pose[BoneId.L_FOREARM.ordinal()] = new Pose(Vec3.ZERO, new Vec3(0, 0, -CROSSED_ELBOW_DEGREES));
    }

    private static void setRotation(Pose[] pose, BoneId bone, ModelPart part) {
        if (part == null) {
            return;
        }
        pose[bone.ordinal()] = new Pose(Vec3.ZERO,
                new Vec3(-part.pitch / RADIANS_PER_DEGREE, part.yaw / RADIANS_PER_DEGREE,
                        -part.roll / RADIANS_PER_DEGREE));
    }

    private record ModelPartAccess(ModelPart body,
                                   ModelPart head,
                                   ModelPart rightArm,
                                   ModelPart leftArm,
                                   ModelPart rightLeg,
                                   ModelPart leftLeg) {
        static ModelPartAccess villager(VillagerResemblingModel<?> model) {
            ModelPart root = model.getPart();
            return new ModelPartAccess(root.getChild("body"), root.getChild("head"), null, null,
                    root.getChild("right_leg"), root.getChild("left_leg"));
        }

        static ModelPartAccess illager(IllagerEntityModel<?> model) {
            ModelPart root = model.getPart();
            return new ModelPartAccess(root.getChild("body"), root.getChild("head"), root.getChild("right_arm"),
                    root.getChild("left_arm"), root.getChild("right_leg"), root.getChild("left_leg"));
        }
    }
}

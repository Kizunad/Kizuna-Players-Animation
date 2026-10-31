package dev.kizuna.playeranimation.client;

import dev.kizuna.playeranimation.animation.AnimationPlayer;
import dev.kizuna.playeranimation.model.BoneId;
import dev.kizuna.playeranimation.model.Pose;
import dev.kizuna.playeranimation.model.Rig;
import dev.kizuna.playeranimation.model.SkinnedMesh;
import dev.kizuna.playeranimation.model.Vec3;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/** 使用 PA 24 骨蒙皮网格替换已注册生物的本体绘制。 */
public final class MobAnimationRenderer {
    private static final float RADIANS_PER_DEGREE = (float) (Math.PI / 180);
    private static final float CROSSED_SHOULDER_DEGREES = -43;
    private static final float CROSSED_ELBOW_DEGREES = -90;

    private MobAnimationRenderer() { }

    public static EntityFrame extract(LivingEntity entity, LivingEntityRenderState state, EntityModel<?> vanilla,
                                      float tickDelta) {
        // 自动层完全替代原版姿态；显式动作最后混合，结束后回到持续推进的移动层。
        int entityId = entity.getId();
        AnimationPlayer animation = PlayerAnimationClient.PLAYBACK.entity(entityId);
        AnimationPlayer automatic = PlayerAnimationClient.PLAYBACK.automaticEntity(entityId);
        MobAnimationRegistry.Model model = MobAnimationRegistry.model(entity);
        if (animation == null && automatic == null || model == null) {
            return null;
        }
        double now = PlayerPlayback.now(tickDelta);
        boolean crouching = entity.isCrouching();
        Pose[] base;
        if (automatic == null) {
            // 底层取原版本帧姿态；提交阶段原版还会再套一次，不影响原版自己的绘制。
            setupAnim(vanilla, state);
            base = basePose(vanilla);
        } else {
            base = automatic.sample(Rig.zeroPose(), now, false /* firstPerson */, crouching);
        }
        Pose[] sampled = base;
        if (animation != null) {
            sampled = animation.sample(base, now, false /* firstPerson */, crouching);
        }
        Matrix4f[] matrices = Rig.matrices(model.body().skeleton(), sampled);
        PlayerMeshRenderer.Frame pose = new PlayerMeshRenderer.Frame(model.body(), matrices);
        HumanoidRenderState armor = null;
        if (!(vanilla instanceof HumanoidModel<?>)) {
            armor = armorState(entity, state, tickDelta);
        }
        EntityFrame frame = EntityFrame.mob(pose, new EntityFrame.MobParts(model.overlay(),
                items(entity, pose), armor));
        if (armor != null) {
            armor.setData(EntityFrame.KEY, frame);
        }
        return frame;
    }

    @SuppressWarnings("unchecked")
    private static void setupAnim(EntityModel<?> vanilla, LivingEntityRenderState state) {
        // 渲染器的模型总是接受自己创建的状态类型。
        ((EntityModel<LivingEntityRenderState>) vanilla).setupAnim(state);
    }

    private static List<EntityFrame.HeldItem> items(LivingEntity entity, PlayerMeshRenderer.Frame pose) {
        // 原版会按举斧、交叉臂等状态跳过持物，这里始终从同帧 PA 手骨绘制双手。
        List<EntityFrame.HeldItem> items = new ArrayList<>();
        HumanoidArm mainArm = entity.getMainArm();
        addItem(items, entity, entity.getMainHandItem(), mainArm, pose);
        addItem(items, entity, entity.getOffhandItem(), mainArm.getOpposite(), pose);
        return List.copyOf(items);
    }

    private static void addItem(List<EntityFrame.HeldItem> items, LivingEntity entity, ItemStack stack,
                                HumanoidArm arm, PlayerMeshRenderer.Frame pose) {
        if (stack.isEmpty()) {
            return;
        }
        // 左右手保持原版物品展示模式，具体位置由独立 item 骨提供。
        ItemStackRenderState item = new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForLiving(item, stack,
                PlayerMeshRenderer.displayContext(arm), entity);
        BoneId socket = arm == HumanoidArm.RIGHT ? BoneId.R_ITEM : BoneId.L_ITEM;
        Matrix4f transform = Rig.itemTransform(pose.model().skeleton(), pose.matrices(), socket);
        items.add(new EntityFrame.HeldItem(item, transform));
    }

    private static HumanoidRenderState armorState(LivingEntity entity, LivingEntityRenderState state,
                                                  float tickDelta) {
        // 非人形原版模型没有盔甲层，按人形状态补一份，材质与纹饰仍由原版盔甲层选择。
        ItemModelResolver resolver = Minecraft.getInstance().getItemModelResolver();
        HumanoidRenderState armor = new HumanoidRenderState();
        HumanoidMobRenderer.extractHumanoidRenderState(entity, armor, tickDelta, resolver);
        armor.entityType = state.entityType;
        armor.isBaby = state.isBaby;
        armor.lightCoords = state.lightCoords;
        armor.outlineColor = state.outlineColor;
        return armor;
    }

    public static boolean visible(Model<?> model, SkinnedMesh.Surface surface) {
        String path = surface.part();
        if (model instanceof VillagerModel villager) {
            return visible(villager.root(), path);
        }
        if (model instanceof IllagerModel<?> illager) {
            // 播放时双臂由独立骨驱动，不受原版交叉臂模式的隐藏开关限制。
            if (path.endsWith("_arm")) {
                return true;
            }
            return visible(illager.root(), path);
        }
        if (model instanceof HumanoidModel<?> biped) {
            return visible(biped, path);
        }
        return true;
    }

    private static boolean visible(HumanoidModel<?> biped, String path) {
        String[] children = path.split("/");
        // 26.2 的 hat 挂在 head 下，头部隐藏时帽层一并隐藏。
        if (children[0].equals("hat") && (!biped.head.visible || biped.head.skipDraw)) {
            return false;
        }
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
                if (!part.visible || part.skipDraw) {
                    return false;
                }
                part = part.getChild(children[i]);
            }
            return part.visible && !part.skipDraw;
        }
        return true;
    }

    private static boolean visible(ModelPart root, String path) {
        ModelPart part = root;
        for (String name : path.split("/")) {
            if (!part.visible || part.skipDraw) {
                return false;
            }
            // 村民动画展开的手臂来自 arms 的纹理，没有原版独立节点。
            if (name.endsWith("_arm")) {
                return true;
            }
            part = part.getChild(name);
        }
        return part.visible && !part.skipDraw;
    }

    private static Pose[] basePose(EntityModel<?> model) {
        Pose[] pose = Rig.zeroPose();
        if (model instanceof HumanoidModel<?> biped) {
            setRotation(pose, BoneId.CHEST, biped.body);
            setRotation(pose, BoneId.HEAD, biped.head);
            setRotation(pose, BoneId.R_UPPER_ARM, biped.rightArm);
            setRotation(pose, BoneId.L_UPPER_ARM, biped.leftArm);
            setRotation(pose, BoneId.R_THIGH, biped.rightLeg);
            setRotation(pose, BoneId.L_THIGH, biped.leftLeg);
            return pose;
        }
        if (model instanceof VillagerModel villager) {
            ModelPartAccess parts = ModelPartAccess.villager(villager);
            setRotation(pose, BoneId.CHEST, parts.body());
            setRotation(pose, BoneId.HEAD, parts.head());
            setRotation(pose, BoneId.R_THIGH, parts.rightLeg());
            setRotation(pose, BoneId.L_THIGH, parts.leftLeg());
            crossArms(pose);
            return pose;
        }
        if (model instanceof IllagerModel<?> illager) {
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
                new Vec3(-part.xRot / RADIANS_PER_DEGREE, part.yRot / RADIANS_PER_DEGREE,
                        -part.zRot / RADIANS_PER_DEGREE));
    }

    private record ModelPartAccess(ModelPart body,
                                   ModelPart head,
                                   ModelPart rightArm,
                                   ModelPart leftArm,
                                   ModelPart rightLeg,
                                   ModelPart leftLeg) {
        static ModelPartAccess villager(VillagerModel model) {
            ModelPart root = model.root();
            return new ModelPartAccess(root.getChild("body"), root.getChild("head"), null, null,
                    root.getChild("right_leg"), root.getChild("left_leg"));
        }

        static ModelPartAccess illager(IllagerModel<?> model) {
            ModelPart root = model.root();
            return new ModelPartAccess(root.getChild("body"), root.getChild("head"), root.getChild("right_arm"),
                    root.getChild("left_arm"), root.getChild("right_leg"), root.getChild("left_leg"));
        }
    }
}

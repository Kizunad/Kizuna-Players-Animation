package dev.kizuna.playeranimation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kizuna.playeranimation.model.BoneId;
import dev.kizuna.playeranimation.model.Pose;
import dev.kizuna.playeranimation.model.Rig;
import dev.kizuna.playeranimation.model.Skeleton;
import dev.kizuna.playeranimation.model.SkinnedMesh;
import dev.kizuna.playeranimation.model.Vec3;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class PlayerMeshRenderer {
    private static final float SLIM_ITEM_SHOULDER_OFFSET = 0.5f;
    private static final float LOOK_PITCH_FULL = 1;
    private static final float LOOK_PITCH_DRINK = 0.5f;
    private static final String[] PITCH_COMPENSATED = {"spyglass", "spyglass_off", "toot_horn", "toot_horn_off",
            "crossbow_hold", "crossbow_hold_off", "bow_draw", "bow_draw_off"};
    public record Frame(PlayerAssets.Model model, Matrix4f[] matrices) { }
    private PlayerMeshRenderer() { }

    public static EntityFrame extract(AbstractClientPlayer player, AvatarRenderState state, PlayerModel vanilla,
                                      float tickDelta) {
        // 先套一次原版姿态，读出各部件变换；提交阶段原版还会再套一次，部件显隐在绘制时读取。
        vanilla.setupAnim(state);
        boolean firstPersonPass = FirstPersonBody.rendering(player);
        // 第一人称身体这一趟也是完整身体，只有独立手臂入口按 fpv 取样。
        Frame frame = frame(player, state, vanilla, tickDelta, false /* firstPerson */);
        if (frame == null) {
            return null;
        }
        UUID id = player.getUUID();
        Set<EquipmentSlot> hidden = EnumSet.noneOf(EquipmentSlot.class);
        for (EquipmentSlot slot : EntityFrame.armorSlots()) {
            if (PlayerAnimationClient.PLAYBACK.shouldHideArmor(id, slot)) {
                hidden.add(slot);
            }
        }
        boolean vanillaHands = firstPersonPass && FirstPersonBody.vanillaHands(player);
        // 原版双手都空时不进入持物层，穿戴动作手里的装备也随之不画。
        boolean emptyHands = state.rightHandItemStack.isEmpty() && state.leftHandItemStack.isEmpty();
        EntityFrame.PlayerParts parts = new EntityFrame.PlayerParts(firstPersonPass, vanillaHands, hidden,
                PlayerAnimationClient.PLAYBACK.shouldHideBoot(id, true),
                PlayerAnimationClient.PLAYBACK.shouldHideBoot(id, false), emptyHands,
                heldItem(frame, player, HumanoidArm.RIGHT, state.rightHandItemState),
                heldItem(frame, player, HumanoidArm.LEFT, state.leftHandItemState));
        return EntityFrame.player(frame, parts);
    }

    public static Frame frame(AbstractClientPlayer player, AvatarRenderState state, PlayerModel vanilla,
                              float tickDelta, boolean firstPerson) {
        boolean slim = state.skin.model() == PlayerModelType.SLIM;
        PlayerAssets.Model model = PlayerAnimationClient.ASSETS.model(slim);
        if (model == null) {
            return null;
        }
        // 先还原原版姿态，再按实际存在的轨道覆盖，未写的腿仍保持原版走路。
        // 视线层启用时底层去掉原版头部偏航/俯仰，最后由视线层统一加回，避免动作头轨道吞掉视线或重复计算。
        LookLayer.Clips look = null;
        if (lookEnabled(player, firstPerson)) {
            look = LookLayer.clips(PlayerAnimationClient.ASSETS.animations());
        }
        Pose[] base = basePose(model.skeleton(), vanilla, firstPerson, slim, look != null);
        var animation = PlayerAnimationClient.PLAYBACK.player(player.getUUID());
        double now = PlayerPlayback.now(tickDelta);
        Pose[] pose = base;
        if (animation != null) {
            pose = animation.sample(base, now, firstPerson, state.isCrouching);
            applyArmPosePitch(pose, model.skeleton(), animation, now, player.getViewXRot(tickDelta),
                    player.getMainArm());
        }
        if (look != null) {
            float yaw = (float) Math.toDegrees(vanilla.head.yRot);
            float pitch = (float) Math.toDegrees(vanilla.head.xRot);
            LookLayer.apply(pose, look, yaw, pitch, lookPitchWeight(animation, now));
        }
        return new Frame(model, Rig.matrices(model.skeleton(), pose));
    }

    private static boolean lookEnabled(AbstractClientPlayer player, boolean firstPerson) {
        // 睡眠、泳姿/爬行过渡、鞘翅、激流和死亡时原版头部有专用姿态，视线层不接管；独立手臂入口不画头。
        return !firstPerson && !player.isSleeping() && !player.isVisuallySwimming() && !player.isFallFlying()
                && !player.isAutoSpinAttack() && player.deathTime == 0 && player.getSwimAmount(0) == 0;
    }

    static float lookPitchWeight(dev.kizuna.playeranimation.animation.AnimationPlayer animation, double now) {
        // 望远镜、号角、持弩已在 applyArmPosePitch 中把俯仰加到头部，视线层只叠偏航；
        // 饮用末段本身已仰头，俯仰层按一半叠加。
        if (animation == null) {
            return LOOK_PITCH_FULL;
        }
        for (String id : PITCH_COMPENSATED) {
            if (animation.hasActive(id, now)) {
                return 0;
            }
        }
        if (animation.hasActive("drink", now) || animation.hasActive("drink_off", now)) {
            return LOOK_PITCH_DRINK;
        }
        return LOOK_PITCH_FULL;
    }

    static void applyArmPosePitch(Pose[] pose, Skeleton skeleton,
                                  dev.kizuna.playeranimation.animation.AnimationPlayer animation,
                                  double now, float pitch, HumanoidArm mainArm) {
        // 望远镜、号角和弩资源按平视制作；把原版视线俯仰叠到头与瞄准上臂，
        // 保持镜筒/吹口/弩托沿视线而不是固定在世界水平面。
        String active = null;
        for (String candidate : new String[]{"spyglass", "spyglass_off", "toot_horn", "toot_horn_off",
                "crossbow_hold", "crossbow_hold_off", "bow_draw", "bow_draw_off"}) {
            if (animation.hasActive(candidate, now)) {
                active = candidate;
                break;
            }
        }
        if (active == null) {
            return;
        }
        // Minecraft 正俯仰表示低头；模型空间 Y 向上，X 旋转需随 Y 反射取反。
        float modelPitch = -pitch;
        pose[BoneId.HEAD.ordinal()] = withPitch(pose[BoneId.HEAD.ordinal()], modelPitch);
        if (active.startsWith("spyglass") || active.startsWith("bow_draw")) {
            // 望远镜托臂与拉弓弦手都必须保持双手相对位置：两臂绕同一肩线做刚性旋转；分别增加局部 X 欧拉角会因 Y/Z 不同而拉开托臂接触点。
            Vec3 right = skeleton.bone(BoneId.R_UPPER_ARM).pivot();
            Vec3 left = skeleton.bone(BoneId.L_UPPER_ARM).pivot();
            Vec3 center = right.lerp(left, 0.5f);
            float radians = (float) (Math.PI / 180);
            Matrix4f turn = new Matrix4f().translate(center.x(), center.y(), center.z())
                    .rotateX(modelPitch * radians).translate(-center.x(), -center.y(), -center.z());
            for (BoneId upper : new BoneId[]{BoneId.R_UPPER_ARM, BoneId.L_UPPER_ARM}) {
                Vec3 pivot = skeleton.bone(upper).pivot();
                Pose arm = pose[upper.ordinal()];
                Matrix4f local = new Matrix4f(turn)
                        .translate(arm.position().x(), arm.position().y(), arm.position().z())
                        .translate(pivot.x(), pivot.y(), pivot.z())
                        .rotateZYX(arm.rotation().z() * radians, arm.rotation().y() * radians,
                                arm.rotation().x() * radians)
                        .translate(-pivot.x(), -pivot.y(), -pivot.z());
                pose[upper.ordinal()] = Rig.localPose(local, pivot);
            }
            return;
        }
        if (active.startsWith("crossbow_hold")) {
            pose[BoneId.R_UPPER_ARM.ordinal()] = withPitch(pose[BoneId.R_UPPER_ARM.ordinal()], modelPitch);
            pose[BoneId.L_UPPER_ARM.ordinal()] = withPitch(pose[BoneId.L_UPPER_ARM.ordinal()], modelPitch);
            return;
        }
        HumanoidArm actionArm = active.endsWith("_off") ? mainArm.getOpposite() : mainArm;
        BoneId upper = actionArm == HumanoidArm.RIGHT ? BoneId.R_UPPER_ARM : BoneId.L_UPPER_ARM;
        pose[upper.ordinal()] = withPitch(pose[upper.ordinal()], modelPitch);
    }

    private static Pose withPitch(Pose pose, float delta) {
        Vec3 rotation = pose.rotation();
        return new Pose(pose.position(), new Vec3(rotation.x() + delta, rotation.y(), rotation.z()));
    }

    public static Pose[] basePose(Skeleton skeleton, PlayerModel model, boolean firstPerson, boolean slim) {
        return basePose(skeleton, model, firstPerson, slim, false);
    }

    public static Pose[] basePose(Skeleton skeleton, PlayerModel model, boolean firstPerson, boolean slim,
                                  boolean stripLook) {
        Map<BoneId, Matrix4f> absolute = new EnumMap<>(BoneId.class);
        Pose[] pose = Rig.zeroPose();
        // 原版六部件在绑定空间的变换转换为 25 骨局部姿态；保留蹲伏等枢轴位移。
        for (BoneId bone : BoneId.ALL) {
            ModelPart part = part(model, bone, false);
            // 去视线时头部只保留枢轴位移（潜行下移）与滚转，偏航/俯仰交给视线层。
            boolean lookless = stripLook && bone == BoneId.HEAD;
            Matrix4f target = bone == BoneId.ROOT || (firstPerson && !bone.arm())
                    ? new Matrix4f() : vanillaTransform(part, lookless);
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

    private static Matrix4f vanillaTransform(ModelPart part, boolean lookless) {
        PartPose bind = part.getInitialPose();
        float yaw = lookless ? 0 : part.yRot;
        float pitch = lookless ? 0 : part.xRot;
        // Y 轴反射使 X/Z 旋转反号；每节矩阵最终仍作用于全局像素位置。
        return new Matrix4f().translate(part.x, Rig.MODEL_ORIGIN_HEIGHT - part.y, part.z)
                .rotateZYX(-part.zRot, yaw, -pitch)
                .translate(-bind.x(), bind.y() - Rig.MODEL_ORIGIN_HEIGHT, -bind.z());
    }

    public static ModelPart part(PlayerModel model, BoneId bone, boolean outer) {
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

    public static boolean visible(PlayerModel model, BoneId bone, boolean outer) {
        // 26.2 的皮肤外层是基础部件的子节点，父部件隐藏时外层也不画。
        ModelPart base = part(model, bone, false);
        if (!outer) {
            return base.visible;
        }
        return base.visible && part(model, bone, true).visible;
    }

    public static void draw(Frame frame, List<SkinnedMesh.Surface> surfaces, PoseStack.Pose entry,
                            VertexConsumer buffer, int light, int overlay, int color,
                            Predicate<SkinnedMesh.Surface> visible) {
        Vector3f position = new Vector3f();
        Vector3f normal = new Vector3f();
        // 皮肤和盔甲复用同一帧骨骼与顶点提交路径，各自保留原始 UV。
        for (SkinnedMesh.Surface surface : surfaces) {
            if (!visible.test(surface)) {
                continue;
            }
            for (SkinnedMesh.Vertex vertex : surface.vertices()) {
                SkinnedMesh.deform(vertex.position(), vertex.weights(), frame.matrices, false, position);
                SkinnedMesh.deform(vertex.normal(), vertex.weights(), frame.matrices, true, normal);
                position.set(position.x / Rig.PIXELS_PER_BLOCK,
                        (Rig.MODEL_ORIGIN_HEIGHT - position.y) / Rig.PIXELS_PER_BLOCK, position.z / Rig.PIXELS_PER_BLOCK);
                entry.pose().transformPosition(position);
                normal.y = -normal.y;
                entry.normal().transform(normal);
                // 完整顶点交给原包装器；纹饰的单属性调用返回底层缓冲，链式调用会丢失图集 UV 转换。
                buffer.addVertex(position.x, position.y, position.z, color, vertex.u(), vertex.v(), overlay, light,
                        normal.x, normal.y, normal.z);
            }
        }
    }

    static EntityFrame.HeldItem heldItem(Frame frame, AbstractClientPlayer player, HumanoidArm arm,
                                         ItemStackRenderState own) {
        double now = PlayerPlayback.now(0);
        // 穿戴动作需要暂时用装备物品替换当前手持物；装备来自物品栏时原手也可能并非空手。
        ItemStack equip = PlayerAnimationClient.PLAYBACK.equipItem(player.getUUID(), arm, now);
        boolean twoHanded = PlayerAnimationClient.PLAYBACK.twoHandedItem(player.getUUID(), arm, now);
        if (twoHanded && arm != player.getMainArm()) {
            return null;
        }
        ItemStackRenderState rendered = own;
        if (!equip.isEmpty()) {
            rendered = new ItemStackRenderState();
            Minecraft.getInstance().getItemModelResolver()
                    .updateForLiving(rendered, equip, displayContext(arm), player);
        }
        if (rendered.isEmpty()) {
            return null;
        }
        BoneId socket = arm == HumanoidArm.RIGHT ? BoneId.R_ITEM : BoneId.L_ITEM;
        // 挂点先跟随手掌矩阵，再叠原版物品坐标方向与 display 变换。
        Matrix4f transform;
        if (twoHanded) {
            transform = Rig.itemTransformMidpoint(frame.model.skeleton(), frame.matrices);
        } else {
            transform = Rig.itemTransform(frame.model.skeleton(), frame.matrices, socket);
        }
        return new EntityFrame.HeldItem(rendered, transform);
    }

    static ItemDisplayContext displayContext(HumanoidArm arm) {
        if (arm == HumanoidArm.LEFT) {
            return ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        }
        return ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }

    public static boolean firstPerson(LocalPlayer player, float tickDelta, PoseStack stack,
                                      SubmitNodeCollector collector, int light) {
        var animation = PlayerAnimationClient.PLAYBACK.player(player.getUUID());
        if (animation == null || !animation.hasFirstPerson(PlayerPlayback.now(tickDelta))) {
            return false;
        }
        AvatarRenderer<AbstractClientPlayer> renderer =
                Minecraft.getInstance().getEntityRenderDispatcher().getPlayerRenderer(player);
        AvatarRenderState state = renderer.createRenderState(player, tickDelta);
        boolean slim = state.skin.model() == PlayerModelType.SLIM;
        if (PlayerAnimationClient.ASSETS.model(slim) == null) {
            return false;
        }
        PlayerModel model = renderer.getModel();
        model.setupAnim(state);
        Frame frame = frame(player, state, model, tickDelta, true /* firstPerson */);
        stack.pushPose();
        // 第一人称在眼睛坐标绘制实际手臂，只采样 fpv 骨；身体骨动画不进入相机。
        stack.translate(0, Rig.MODEL_ORIGIN_HEIGHT / Rig.PIXELS_PER_BLOCK - player.getEyeHeight(player.getPose()), 0);
        stack.scale(-1, -1, 1);
        if (!player.isInvisible()) {
            RenderType type = RenderTypes.entityTranslucent(renderer.getTextureLocation(state));
            collector.submitCustomGeometry(stack, type, (pose, buffer) -> draw(frame, frame.model.mesh().surfaces(),
                    pose, buffer, light, OverlayTexture.NO_OVERLAY, -1,
                    surface -> surface.bone().arm() && visible(model, surface.bone(), surface.outer())));
        }
        HumanoidArm main = player.getMainArm();
        firstPersonItem(frame, player, main, player.getMainHandItem(), stack, collector, light);
        firstPersonItem(frame, player, main.getOpposite(), player.getOffhandItem(), stack, collector, light);
        stack.popPose();
        return true;
    }

    private static void firstPersonItem(Frame frame, AbstractClientPlayer player, HumanoidArm arm, ItemStack stack,
                                        PoseStack matrices, SubmitNodeCollector collector, int light) {
        // 与第三人称同一挂点规则，物品仍按第三人称手持方式展示。
        ItemStackRenderState own = new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForLiving(own, stack, displayContext(arm), player);
        EntityFrame.HeldItem held = heldItem(frame, player, arm, own);
        if (held != null) {
            held.submit(matrices, collector, light, OverlayTexture.NO_OVERLAY, 0);
        }
    }
}

package dev.kizuna.playeranimation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kizuna.playeranimation.model.BoneId;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.phys.Vec3;

/** 第一人称复用世界中的玩家渲染，视线与交互位置仍由原版相机决定。 */
public final class FirstPersonBody {
    // 单位为方块，仅移动本次绘制的模型，给相机近裁面留出空间。
    private static final double BODY_BACK_OFFSET = 0.25;
    private static final double CROUCH_BACK_OFFSET = 0.30;
    private static boolean enabled = true;
    private static AbstractClientPlayer renderingPlayer;

    private FirstPersonBody() { }

    public static void setEnabled(boolean value) { enabled = value; }

    public static boolean active(AbstractClientPlayer player) {
        Minecraft client = Minecraft.getInstance();
        // 横躺/旋转类姿态需要各自的相机空间适配，当前保留其原版视角。
        float delta = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        boolean slim = player.getSkin().model() == PlayerModelType.SLIM;
        return enabled && client.options.getCameraType().isFirstPerson()
                && client.getCameraEntity() == player && player.isAlive() && !player.isSpectator()
                && !player.isSleeping() && !player.isFallFlying() && !player.isAutoSpinAttack()
                && !player.isVisuallySwimming() && player.getSwimAmount(delta) == 0
                && PlayerAnimationClient.ASSETS.model(slim) != null;
    }

    public static boolean rendering(Entity entity) { return renderingPlayer == entity; }

    public static boolean vanillaHands(AbstractClientPlayer player) {
        // 地图、镜片、矛回弹和收纳袋倒出反馈沿用原版双手，身体仍采用同帧全身姿态。
        return player.isScoping() || vanillaHandItem(player.getMainHandItem())
                || vanillaHandItem(player.getOffhandItem());
    }

    private static boolean vanillaHandItem(ItemStack stack) {
        ItemUseAnimation use = stack.getUseAnimation();
        return stack.is(Items.FILLED_MAP) || use == ItemUseAnimation.SPEAR || use == ItemUseAnimation.BUNDLE;
    }

    public static boolean visible(EntityFrame.PlayerParts parts, BoneId bone) {
        // 只限制这一趟本地身体绘制，第三人称和物品栏预览保留完整头部。
        return !parts.firstPersonPass() || bone != BoneId.HEAD
                && (!bone.arm() || !parts.vanillaHands());
    }

    static Vec3 bodyOffset(float yawDegrees, boolean crouching) {
        // Minecraft 偏航零度朝 +Z；模型偏移取身体朝向的反方向。
        double radians = Math.toRadians(yawDegrees);
        double distance = crouching ? CROUCH_BACK_OFFSET : BODY_BACK_OFFSET;
        return new Vec3(Math.sin(radians) * distance, 0, -Math.cos(radians) * distance);
    }

    public static void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        Camera camera = client.gameRenderer.mainCamera();
        if (!(camera.entity() instanceof AbstractClientPlayer player) || camera.isDetached() || !active(player)) {
            return;
        }
        float delta = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        AvatarRenderer<AbstractClientPlayer> renderer = client.getEntityRenderDispatcher().getPlayerRenderer(player);
        CameraRenderState cameraState = context.levelState().cameraRenderState;
        PoseStack matrices = context.poseStack();
        matrices.pushPose();
        renderingPlayer = player;
        try {
            // 状态在本趟标记下提取，帧数据据此隐藏头部与近裁面附近的装饰。
            AvatarRenderState state = renderer.createRenderState(player, delta);
            Vec3 position = player.getPosition(delta).subtract(cameraState.pos)
                    .add(renderer.getRenderOffset(state))
                    .add(bodyOffset(Mth.rotLerp(delta, player.yBodyRotO, player.yBodyRot),
                            player.isCrouching()));
            // 直接调用玩家渲染器，沿用皮肤、骨架、装备和持物；不补画影子或碰撞箱。
            matrices.translate(position.x, position.y, position.z);
            renderer.submit(state, matrices, context.submitNodeCollector(), cameraState);
        } finally {
            renderingPlayer = null;
            matrices.popPose();
        }
    }
}

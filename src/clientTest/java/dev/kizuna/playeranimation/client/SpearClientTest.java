package dev.kizuna.playeranimation.client;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.kizuna.playeranimation.animation.AnimationEntry;
import dev.kizuna.playeranimation.animation.AnimationPlayer;
import dev.kizuna.playeranimation.model.BoneId;
import dev.kizuna.playeranimation.model.Pose;
import dev.kizuna.playeranimation.model.Rig;
import java.util.List;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.effects.SpearAnimations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 真实客户端资源与原版矛变换的左右手回归。 */
final class SpearClientTest {
    private static final float EPSILON = 0.0001f;
    private static final float ATTACK_PROGRESS = 0.35f;
    private static final float USED_TICKS = 8;
    private static final double SETTLED = 1;
    private static final float CHARGE_SECONDS = 9.15f;
    private static final float READY_SECONDS = 0.4f;
    private static final float SOCKET_X = 0.25f;
    private static final float SOCKET_Y = 1.5f;
    private static final float SOCKET_Z = -0.5f;
    private static final float SOCKET_YAW_RADIANS = 0.4f;
    private static final float LOOK_PITCH = 20;
    private static final List<String> BASES = List.of("walk", "run", "sneak", "ride", "swim");

    @ParameterizedTest
    @CsvSource({"RIGHT, RIGHT", "RIGHT, LEFT", "LEFT, RIGHT", "LEFT, LEFT"})
    void itemTransformMatchesVanillaAndClipOwnershipPreventsDoubleTransform(HumanoidArm mainArm, HumanoidArm arm) {
        // 使用原版函数计算预期矩阵，同时覆盖戳刺和使用中物品变换的组合顺序。
        HumanoidRenderState state = state(mainArm, arm);
        state.attackTime = ATTACK_PROGRESS;
        state.attackArm = arm;
        state.swingAnimationType = net.minecraft.world.item.SwingAnimationType.STAB;
        Matrix4f socket = new Matrix4f().translation(SOCKET_X, SOCKET_Y, SOCKET_Z).rotateY(SOCKET_YAW_RADIANS);
        PoseStack expected = new PoseStack();
        expected.last().pose().set(socket);
        SpearAnimations.thirdPersonAttackItem(state, expected);
        HumanoidModel.ArmPose.SPEAR.animateUseItem(state, expected, USED_TICKS, arm,
                state.getUseItemStackForArm(arm));
        Matrix4f actual = SpearItemTransforms.apply(socket, state, arm, SpearItemTransforms.PoseSource.VANILLA);
        assertTrue(expected.last().pose().equals(actual, EPSILON));
        assertFalse(socket.equals(actual, EPSILON), "使用中的矛必须确实改变矩阵");

        AnimationPlayer playback = new AnimationPlayer();
        SpearPoses.update(playback, state, 0);
        var source = SpearItemTransforms.source(playback, arm, mainArm, SETTLED);
        assertEquals(SpearItemTransforms.PoseSource.CLIP, source);
        assertEquals(socket, SpearItemTransforms.apply(socket, state, arm, source));
        assertEquals(SpearItemTransforms.PoseSource.VANILLA,
                SpearItemTransforms.source(playback, arm.getOpposite(), mainArm, SETTLED));
    }

    @ParameterizedTest
    @CsvSource({"RIGHT, RIGHT", "RIGHT, LEFT", "LEFT, RIGHT", "LEFT, LEFT"})
    void heldSpearOwnsOnlyItsArmOnEveryLocomotionBase(HumanoidArm mainArm, HumanoidArm arm) {
        // 未使用也保留持矛姿态，下身和空手臂逐骨保持移动层结果。
        HumanoidRenderState state = state(mainArm, arm);
        state.isUsingItem = false;
        state.ticksUsingItem = 0;
        for (String base : BASES) {
            AnimationPlayer alone = base(base, mainArm);
            AnimationPlayer combined = base(base, mainArm);
            SpearPoses.update(combined, state, 0);
            Pose[] expected = alone.sample(Rig.zeroPose(), SETTLED, false /* firstPerson */);
            Pose[] actual = combined.sample(Rig.zeroPose(), SETTLED, false /* firstPerson */);
            AnimationEntry.Mask mask = AnimationEntry.Mask.R_ARM;
            BoneId upper = BoneId.R_UPPER_ARM;
            if (arm == HumanoidArm.LEFT) {
                mask = AnimationEntry.Mask.L_ARM;
                upper = BoneId.L_UPPER_ARM;
            }
            for (BoneId bone : BoneId.ALL) {
                if (!mask.includes(bone)) {
                    assertEquals(expected[bone.ordinal()], actual[bone.ordinal()], base + ":" + bone);
                }
            }
            assertNotEquals(expected[upper.ordinal()], actual[upper.ordinal()], base);
        }
    }

    @Test
    void firstPersonSpearAndBundleKeepVanillaHandsOnEitherSide() {
        // 特殊物品的原版手持变换不能被 PA 的全身第一人称绘制吞掉。
        var player = Minecraft.getInstance().player;
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        try {
            for (var item : List.of(Items.NETHERITE_SPEAR, Items.BUNDLE)) {
                for (var hand : InteractionHand.values()) {
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                    player.setItemInHand(hand, item.getDefaultInstance());
                    assertTrue(FirstPersonBody.vanillaHands(player));
                }
            }
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, main);
            player.setItemInHand(InteractionHand.OFF_HAND, off);
        }
    }

    @Test
    void chargeWindowsFollowEachMaterialAndStopAtTheLastPose() {
        // 每种材质在真实延迟边界到达就绪姿态，不能统一套下界合金矛的时长。
        for (var item : List.of(Items.WOODEN_SPEAR, Items.COPPER_SPEAR, Items.NETHERITE_SPEAR)) {
            var weapon = new ItemStack(item).get(DataComponents.KINETIC_WEAPON);
            assertEquals(0, SpearPoses.chargeProgress(weapon, 0));
            assertEquals(READY_SECONDS / CHARGE_SECONDS,
                    SpearPoses.chargeProgress(weapon, weapon.delayTicks()), EPSILON);
            int end = weapon.delayTicks() + weapon.damageConditions().orElseThrow().maxDurationTicks();
            assertEquals(1, SpearPoses.chargeProgress(weapon, end));
            assertEquals(1, SpearPoses.chargeProgress(weapon, end + 1));
        }
    }

    @Test
    void unmirroredOverlayOccludesThePhysicalArmForLeftHandedPlayers() {
        // 不镜像的右臂覆盖层只能挡住物理右臂，不能挡住左惯用手持矛的瞄准。
        HumanoidRenderState state = state(HumanoidArm.LEFT, HumanoidArm.LEFT);
        state.xRot = LOOK_PITCH;
        AnimationPlayer playback = new AnimationPlayer();
        SpearPoses.update(playback, state, 0);
        var raw = JsonParser.parseString("""
                {"id":"physical_right_overlay", "file":"animations/spear_charge.animation.json",
                 "mask":"r_arm", "priority":30, "blend_in":0, "blend_out":0,
                 "hand":"none", "fpv":false}
                """).getAsJsonObject();
        var charge = PlayerAnimationClient.ASSETS.animations().get("spear_charge");
        AnimationEntry overlay = AnimationEntry.parse(raw, charge.clip());
        var request = new AnimationPlayer.PlaybackRequest(0, AnimationPlayer.Handedness.LEFT, null, null);
        playback.play(overlay, 0, request);
        Pose[] before = playback.sample(Rig.zeroPose(), SETTLED, false /* firstPerson */);
        Pose[] after = before.clone();
        SpearPoses.applyPitch(after, playback, state, SETTLED);
        assertEquals(before[BoneId.R_UPPER_ARM.ordinal()], after[BoneId.R_UPPER_ARM.ordinal()]);
        assertEquals(before[BoneId.L_UPPER_ARM.ordinal()].rotation().x() - LOOK_PITCH,
                after[BoneId.L_UPPER_ARM.ordinal()].rotation().x(), EPSILON);
    }

    @Test
    void changingMainArmMovesTheHeldPoseAndRemovingSpearClearsBothSides() {
        // 游戏设置可在持物时切换惯用手，旧镜像实例必须淡出，不能继续留在原来的手臂。
        HumanoidRenderState state = state(HumanoidArm.RIGHT, HumanoidArm.RIGHT);
        AnimationPlayer playback = new AnimationPlayer();
        SpearPoses.update(playback, state, 0);
        state.mainArm = HumanoidArm.LEFT;
        state.leftHandItemStack = state.rightHandItemStack;
        state.rightHandItemStack = ItemStack.EMPTY;
        SpearPoses.update(playback, state, SETTLED);
        Pose[] pose = playback.sample(Rig.zeroPose(), SETTLED + SETTLED, false /* firstPerson */);
        assertEquals(Pose.ZERO, pose[BoneId.R_UPPER_ARM.ordinal()]);
        assertNotEquals(Pose.ZERO, pose[BoneId.L_UPPER_ARM.ordinal()]);
        state.leftHandItemStack = ItemStack.EMPTY;
        SpearPoses.update(playback, state, SETTLED + SETTLED);
        playback.advance(SETTLED + SETTLED + SETTLED, (action, event) -> { });
        assertTrue(playback.empty(), "换下矛后不得残留本地覆盖层");
    }

    private static AnimationPlayer base(String action, HumanoidArm mainArm) {
        AnimationPlayer playback = new AnimationPlayer();
        var entry = PlayerAnimationClient.ASSETS.animations().get(action);
        playback.play(entry, 0, mainArm == HumanoidArm.LEFT);
        return playback;
    }

    private static HumanoidRenderState state(HumanoidArm mainArm, HumanoidArm arm) {
        HumanoidRenderState state = new HumanoidRenderState();
        state.mainArm = mainArm;
        state.attackArm = arm;
        state.isUsingItem = true;
        state.useItemHand = arm == mainArm ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        state.ticksUsingItem = USED_TICKS;
        if (arm == HumanoidArm.RIGHT) {
            state.rightHandItemStack = new ItemStack(Items.NETHERITE_SPEAR);
            state.rightArmPose = HumanoidModel.ArmPose.SPEAR;
        } else {
            state.leftHandItemStack = new ItemStack(Items.NETHERITE_SPEAR);
            state.leftArmPose = HumanoidModel.ArmPose.SPEAR;
        }
        return state;
    }
}

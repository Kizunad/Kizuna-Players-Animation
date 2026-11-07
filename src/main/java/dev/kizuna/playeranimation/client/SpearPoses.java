package dev.kizuna.playeranimation.client;

import dev.kizuna.playeranimation.animation.AnimationPlayer;
import dev.kizuna.playeranimation.model.BoneId;
import dev.kizuna.playeranimation.model.Pose;
import dev.kizuna.playeranimation.model.Vec3;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.component.KineticWeapon;

/** 从原版本帧状态采样矛剪辑，不发送额外的逐 tick 播放包。 */
final class SpearPoses {
    private static final float CHARGE_SECONDS = 9.15f;
    private static final float READY_SECONDS = 0.40f;
    private static final float SWAY_START_SECONDS = 1.90f;
    private static final float SWAY_END_SECONDS = 2.90f;
    private static final float LOWER_START_SECONDS = 4.90f;
    private static final float LOWER_END_SECONDS = 6.90f;
    private static final float RETURN_START_SECONDS = 8.90f;
    private static final int SWAY_TICKS = 20;
    private static final int LOWER_OFFSET_TICKS = 20;
    private static final int RETURN_TICKS = 5;
    private static final float LOWEST_ARM_PITCH = -30;
    private static final float HIGHEST_ARM_PITCH = 120;
    private static final float MAX_ARM_YAW = 60;

    private SpearPoses() { }

    static boolean holding(ArmedEntityRenderState state) {
        if (!eligible(state)) {
            return false;
        }
        return state.rightHandItemStack.getUseAnimation() == ItemUseAnimation.SPEAR
                || state.leftHandItemStack.getUseAnimation() == ItemUseAnimation.SPEAR
                || state.swingAnimationType == SwingAnimationType.STAB && state.attackTime > 0;
    }

    static void update(AnimationPlayer playback, ArmedEntityRenderState state, double now) {
        // 主副手模板由惯用手镜像；两手持矛可同时保持，挥手只覆盖正在戳刺的一侧。
        if (!eligible(state)) {
            for (String action : new String[]{"spear_charge", "spear_charge_off", "spear_stab", "spear_stab_off"}) {
                playback.stop(action, now);
            }
            return;
        }
        for (HumanoidArm arm : HumanoidArm.values()) {
            ItemStack stack = state.rightHandItemStack;
            if (arm == HumanoidArm.LEFT) {
                stack = state.leftHandItemStack;
            }
            String suffix = arm == state.mainArm ? "" : "_off";
            String charge = "spear_charge" + suffix;
            if (stack.getUseAnimation() == ItemUseAnimation.SPEAR) {
                float progress = chargeProgress(stack.get(DataComponents.KINETIC_WEAPON), state.ticksUsingItem(arm));
                sample(playback, charge, progress, state.mainArm, now);
            } else {
                playback.stop(charge, now);
            }
            String stab = "spear_stab" + suffix;
            if (state.swingAnimationType == SwingAnimationType.STAB && state.attackArm == arm && state.attackTime > 0) {
                sample(playback, stab, state.attackTime, state.mainArm, now);
            } else {
                playback.stop(stab, now);
            }
        }
    }

    private static boolean eligible(ArmedEntityRenderState state) {
        return state.deathTime <= 0 && !state.hasPose(net.minecraft.world.entity.Pose.SLEEPING)
                && !state.isAutoSpinAttack;
    }

    private static void sample(AnimationPlayer playback, String action, float progress, HumanoidArm mainArm,
                               double now) {
        var entry = PlayerAnimationClient.ASSETS.animations().get(action);
        playback.scrub(entry, now, progress, mainArm == HumanoidArm.LEFT);
    }

    static void applyPitch(Pose[] pose, AnimationPlayer playback, ArmedEntityRenderState state, double now) {
        // 只补仍由矛拥有的手臂；显式功法覆盖该臂时不把原版瞄准再次叠上去。
        var activePlaybacks = playback.active(now).reversed();
        for (BoneId target : new BoneId[]{BoneId.R_UPPER_ARM, BoneId.L_UPPER_ARM}) {
            float remaining = 1;
            float spearWeight = 0;
            for (var active : activePlaybacks) {
                var entry = active.entry();
                BoneId source = target;
                if (active.mirrored()) {
                    source = target.mirrored();
                }
                if (!entry.mask().includes(source) || !entry.clip().tracks().containsKey(source)) {
                    continue;
                }
                if (entry.id().startsWith("spear_")) {
                    spearWeight += remaining * active.weight();
                }
                remaining *= 1 - active.weight();
            }
            if (spearWeight <= 0) {
                continue;
            }
            Pose current = pose[target.ordinal()];
            Vec3 rotation = current.rotation();
            float pitch = Math.clamp(rotation.x() - state.xRot * spearWeight, LOWEST_ARM_PITCH, HIGHEST_ARM_PITCH);
            float yaw = Math.clamp(rotation.y() + state.yRot * spearWeight, -MAX_ARM_YAW, MAX_ARM_YAW);
            pose[target.ordinal()] = new Pose(current.position(), new Vec3(pitch, yaw, rotation.z()));
        }
    }

    static float chargeProgress(KineticWeapon weapon, float usedTicks) {
        // 每个窗口独立映射，保留原版的固定 20/40/5 tick 过渡，不按整段长度统一缩放。
        if (weapon == null || usedTicks <= 0) {
            return 0;
        }
        float delay = weapon.delayTicks();
        float dismount = delay + weapon.dismountConditions().map(KineticWeapon.Condition::maxDurationTicks).orElse(0);
        float knockback = delay + weapon.knockbackConditions().map(KineticWeapon.Condition::maxDurationTicks).orElse(0);
        float damage = delay + weapon.damageConditions().map(KineticWeapon.Condition::maxDurationTicks).orElse(0);
        float[] ticks = {0, delay, dismount - SWAY_TICKS, dismount,
                knockback - LOWER_OFFSET_TICKS, knockback + LOWER_OFFSET_TICKS, damage - RETURN_TICKS, damage};
        float[] seconds = {0, READY_SECONDS, SWAY_START_SECONDS, SWAY_END_SECONDS,
                LOWER_START_SECONDS, LOWER_END_SECONDS, RETURN_START_SECONDS, CHARGE_SECONDS};
        float start = 0;
        for (int index = 1; index < ticks.length; index++) {
            float end = Math.max(start, ticks[index]);
            if (usedTicks < end) {
                float fraction = (usedTicks - start) / (end - start);
                float sampled = seconds[index - 1] + fraction * (seconds[index] - seconds[index - 1]);
                return sampled / CHARGE_SECONDS;
            }
            start = end;
        }
        return 1;
    }
}

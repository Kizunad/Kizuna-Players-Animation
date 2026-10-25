package dev.kizuna.playeranimation.server;

import dev.kizuna.playeranimation.animation.PlayerAnimationAction;

import dev.kizuna.playeranimation.mixin.LivingEntityAccessor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MiningToolItem;
import net.minecraft.util.Hand;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 把原版实体状态转换成动作 ID 和归一化 scrub 进度。进度每 tick 从实体真实字段计算，
 * 客户端只采样该位置；未接入自定义资源的状态继续使用原版姿态和渲染。
 */
final class VanillaAnimationScheduler {
    enum Transition { NONE, START, STOP }
    private final Map<UUID, JumpState> jumps = new HashMap<>();
    private final Map<UUID, ArmorSnapshot> armor = new HashMap<>();
    private final Map<UUID, Set<String>> scrubbed = new HashMap<>();
    private final Map<UUID, AttackState> attacks = new HashMap<>();
    private final Map<UUID, HoldState> crossbowHolds = new HashMap<>();
    private final Map<UUID, Vec3d> previousPositions = new HashMap<>();

    private static final int LANDING_BUFFER_TICKS = 6;
    private static final float TOOL_PLAYBACK_SPEED = 0.8f;
    private static final double MINING_LOOP_PROGRESS = 0.40;
    // 每 tick 位移不超过 0.02 方块视作静止；比较水平位移平方（方块²/tick²）。
    private static final double STATIONARY_SPEED_SQUARED = 0.0004;

    static final class JumpState {
        private boolean airborne;
        private double vy0 = 0.42;
        private long landingStart = -1;

        Float progress(boolean allowed, boolean grounded, double velocity, long tick) {
            // 非陆地运动必须清掉整个周期，不能把骑乘/飞行的结束误当成落地。
            if (!allowed) {
                airborne = false;
                landingStart = -1;
                return null;
            }
            if (!grounded) {
                if (!airborne) {
                    vy0 = initialJumpVelocity(velocity);
                }
                airborne = true;
                landingStart = -1;
                return jumpVelocityProgress(velocity, vy0);
            }
            if (airborne) {
                airborne = false;
                landingStart = tick;
            }
            if (landingStart < 0) {
                return null;
            }
            long elapsed = tick - landingStart;
            if (elapsed >= LANDING_BUFFER_TICKS) {
                landingStart = -1;
                return null;
            }
            return jumpLandingProgress(elapsed);
        }
    }

    private static final class AttackState {
        String action;
        boolean swinging;
        long tailStart;
        int durationTicks = 6;
        ToolSwingState tool;
    }

    static final class ToolSwingState {
        private long previousTick = -1;
        private double progress;
        private float previousSwing;
        private boolean repeatRequested;

        float progress(boolean swinging, float swingProgress, long tick, int durationTicks) {
            // 工具使用独立视觉游标；原版重挥不能截断 0.20 秒的完整砸击循环。
            if (swinging && swingProgress < previousSwing) {
                repeatRequested = true;
            }
            previousSwing = swingProgress;
            if (previousTick >= 0) {
                progress += Math.max(0, tick - previousTick) * 0.60 * TOOL_PLAYBACK_SPEED
                        / Math.max(1, durationTicks);
            }
            previousTick = tick;
            // 只有原版确实重挥才续播一轮；单次点击超过循环长度时直接进入收势。
            if (swinging && repeatRequested && progress >= MINING_LOOP_PROGRESS) {
                progress %= MINING_LOOP_PROGRESS;
                repeatRequested = false;
            } else if (!swinging) {
                repeatRequested = false;
            }
            // 松手后继续向剪辑末端前进，不跳到固定的收势起点。
            return clampProgress((float) progress);
        }
    }

    private static final class HoldState {
        String action;
        long startTick;
    }

    void tick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID id = player.getUuid();
            ArmorSnapshot previousArmor = armor.put(id, ArmorSnapshot.capture(player));
            playArmorEquip(player, previousArmor);
            Set<String> desired = new HashSet<>();
            boolean stationary = updateStationary(player);
            scheduleJump(player, desired);
            scheduleOriginalProgress(player, desired);
            scheduleCrouchWork(player, desired, stationary);
            scheduleSneak(player, desired, stationary);
            Set<String> previous = scrubbed.put(id, desired);
            if (previous != null) {
                for (String action : previous) {
                    if (!desired.contains(action)) {
                        ServerPlayerAnimations.stop(player, action);
                    }
                }
            }
        }
        jumps.keySet().removeIf(id -> server.getPlayerManager().getPlayer(id) == null);
        attacks.keySet().removeIf(id -> server.getPlayerManager().getPlayer(id) == null);
        crossbowHolds.keySet().removeIf(id -> server.getPlayerManager().getPlayer(id) == null);
        armor.keySet().removeIf(id -> server.getPlayerManager().getPlayer(id) == null);
        scrubbed.keySet().removeIf(id -> server.getPlayerManager().getPlayer(id) == null);
        previousPositions.keySet().removeIf(id -> server.getPlayerManager().getPlayer(id) == null);
    }

    void clear(UUID player) {
        jumps.remove(player);
        attacks.remove(player);
        crossbowHolds.remove(player);
        armor.remove(player);
        scrubbed.remove(player);
        previousPositions.remove(player);
    }

    void clear() {
        jumps.clear();
        attacks.clear();
        crossbowHolds.clear();
        armor.clear();
        scrubbed.clear();
        previousPositions.clear();
    }

    static float clampProgress(float value) {
        if (!Float.isFinite(value)) {
            return 0;
        }
        return Math.max(0, Math.min(1, value));
    }

    static float useProgress(int useTimeLeft, int maxUseTime) {
        if (maxUseTime <= 0) {
            return 0;
        }
        return clampProgress(1f - useTimeLeft / (float) maxUseTime);
    }

    static float phase(long tick, int periodTicks) {
        if (periodTicks <= 0) {
            throw new IllegalArgumentException("动作循环周期必须为正数");
        }
        return ((tick % periodTicks + periodTicks) % periodTicks) / (float) periodTicks;
    }

    static String handAction(String main, String off, Hand hand) {
        return hand == Hand.OFF_HAND ? off : main;
    }

    static Transition armorTransition(boolean previousEquipped, boolean equipped) {
        return !previousEquipped && equipped ? Transition.START : Transition.NONE;
    }

    static float jumpAirProgress(double verticalVelocity) {
        // 速度从上升到下降映射到 [0,1]，不把实体位移交给动画 root。
        return clampProgress((float) (0.5 - verticalVelocity));
    }

    static float jumpVelocityProgress(double verticalVelocity, double initialVelocity) {
        double vy0 = initialVelocity > 0.2 ? initialVelocity : 0.42;
        double t = Math.max(0, Math.min(0.60, 0.30 * (1.0 - verticalVelocity / vy0)));
        return (float) (t / 0.90);
    }

    static float jumpLandingProgress(long elapsedTicks) {
        return clampProgress((float) ((0.60 + Math.min(0.30, Math.max(0, elapsedTicks) / 20d)) / 0.90));
    }

    static float attackTailProgress(long elapsedTicks, int swingDurationTicks) {
        int duration = Math.max(1, swingDurationTicks);
        return clampProgress(0.60f + Math.max(0, elapsedTicks) * 0.60f / duration);
    }

    private void scheduleJump(ServerPlayerEntity player, Set<String> desired) {
        // 着地仅表示碰撞结果；骑乘、飞行、流体和攀爬有各自的姿态与支撑来源。
        boolean allowed = player.isAlive() && !player.isSpectator() && !player.hasVehicle()
                && !player.isFallFlying() && !player.getAbilities().flying && !player.isSleeping()
                && !player.isSwimming() && !player.isTouchingWater() && !player.isInLava() && !player.isClimbing();
        JumpState state = jumps.computeIfAbsent(player.getUuid(), ignored -> new JumpState());
        Float progress = state.progress(allowed, player.isOnGround(), player.getVelocity().y,
                player.getWorld().getTime());
        if (progress != null) {
            syncScrub(player, "jump", progress, desired);
        }
    }

    private boolean updateStationary(ServerPlayerEntity player) {
        // 静止判断取服务端相邻 tick 的实际水平位置差；首个 tick 没有上一位置，按移动处理。
        Vec3d position = player.getPos();
        Vec3d previousPosition = previousPositions.put(player.getUuid(), position);
        return previousPosition != null
                && position.subtract(previousPosition).horizontalLengthSquared() <= STATIONARY_SPEED_SQUARED;
    }

    private static boolean groundedSneak(ServerPlayerEntity player) {
        // 潜行底层只在着地的普通潜行中使用；骑乘、入水和攀爬有各自的姿态来源。
        return player.isInSneakingPose() && player.isOnGround() && !player.hasVehicle()
                && !player.isTouchingWater() && !player.isClimbing();
    }

    private void scheduleCrouchWork(ServerPlayerEntity player, Set<String> desired, boolean stationary) {
        // 只在已制作的工具动作下提供静止支撑；移动时交回潜行步态，避免锁脚滑行。
        boolean working = desired.stream().anyMatch(action -> action.startsWith("attack_")
                || action.startsWith("mine_") || action.startsWith("place_block")
                || action.startsWith("use_item") || action.startsWith("spyglass")
                || action.startsWith("toot_horn") || action.startsWith("crossbow_") || action.startsWith("brush"));
        if (!working || !groundedSneak(player) || !stationary) {
            return;
        }
        playLoop(player, "crouch_work", desired);
    }

    private void scheduleSneak(ServerPlayerEntity player, Set<String> desired, boolean stationary) {
        // sneak、sneak_walk、crouch_work 三选一：操作支撑已接管时不再叠加通用潜行。
        if (desired.contains("crouch_work") || !groundedSneak(player)) {
            return;
        }
        String action = stationary ? "sneak" : "sneak_walk";
        playLoop(player, action, desired);
    }

    private void playLoop(ServerPlayerEntity player, String action, Set<String> desired) {
        // 循环动作按自由时钟播放，只在本 tick 新进入时发送一次；离开状态由 tick 末尾统一停止。
        desired.add(action);
        Set<String> previous = scrubbed.get(player.getUuid());
        if (previous == null || !previous.contains(action)) {
            ServerPlayerAnimations.play(player, action);
        }
    }

    private static double initialJumpVelocity(double velocity) {
        return velocity > 0.2 ? velocity : 0.42;
    }

    private void scheduleOriginalProgress(ServerPlayerEntity player, Set<String> desired) {
        UUID id = player.getUuid();
        if (player.isUsingItem()) {
            crossbowHolds.remove(id);
            attacks.remove(id);
            ItemStack stack = player.getActiveItem();
            Hand hand = player.getActiveHand();
            UseAction useAction = stack.getItem().getUseAction(stack);
            String action = armPoseAction(stack, hand, useAction);
            if (action != null) {
                float progress = armPoseProgress(player, stack, action);
                syncScrub(player, action, progress, desired);
            }
        } else if (player.handSwinging) {
            crossbowHolds.remove(id);
            scheduleHandSwing(player, desired);
        } else {
            AttackState state = attacks.get(id);
            if (state != null) {
                state.swinging = false;
            }
            scheduleHandSwingTail(player, desired);
            scheduleCrossbowHold(player, desired);
        }
    }

    private void scheduleHandSwing(ServerPlayerEntity player, Set<String> desired) {
        UUID id = player.getUuid();
        Hand hand = player.getActiveHand();
        ItemStack swung = player.getStackInHand(hand);
        boolean mining = swung.getItem() instanceof MiningToolItem;
        boolean placing = swung.getItem() instanceof BlockItem;
        String action = handAction(placing ? "place_block" : mining ? "mine_main" : "attack_main",
                placing ? "place_block_off" : mining ? "mine_off" : "attack_off", hand);
        AttackState state = attacks.computeIfAbsent(id, ignored -> new AttackState());
        state.durationTicks = Math.max(1, ((LivingEntityAccessor) (Object) player).kpa$getHandSwingDuration());
        if (!action.equals(state.action)) {
            state.tool = mining ? new ToolSwingState() : null;
        }
        if (!state.swinging || !action.equals(state.action)) {
            state.action = action;
            state.tailStart = -1;
        }
        state.swinging = true;
        desired.add(action);
        float progress = state.tool == null ? 0.60f * clampProgress(player.getHandSwingProgress(1f))
                : state.tool.progress(true, player.getHandSwingProgress(1f),
                        player.getWorld().getTime(), state.durationTicks);
        syncScrub(player, action, progress, desired);
    }

    private void scheduleHandSwingTail(ServerPlayerEntity player, Set<String> desired) {
        AttackState state = attacks.get(player.getUuid());
        if (state == null || state.swinging || state.action == null) {
            return;
        }
        if (state.tailStart < 0) {
            state.tailStart = player.getWorld().getTime();
        }
        long elapsed = Math.max(0, player.getWorld().getTime() - state.tailStart);
        float progress = state.tool == null ? attackTailProgress(elapsed, state.durationTicks)
                : state.tool.progress(false, player.getHandSwingProgress(1f),
                        player.getWorld().getTime(), state.durationTicks);
        if (progress >= 1) {
            ServerPlayerAnimations.stop(player, state.action);
            attacks.remove(player.getUuid());
            return;
        }
        desired.add(state.action);
        ServerPlayerAnimations.scrub(player, state.action, progress);
    }

    private void scheduleCrossbowHold(ServerPlayerEntity player, Set<String> desired) {
        ItemStack stack;
        Hand hand;
        if (player.getMainHandStack().getItem() instanceof CrossbowItem
                && CrossbowItem.isCharged(player.getMainHandStack())) {
            stack = player.getMainHandStack();
            hand = Hand.MAIN_HAND;
        } else if (player.getOffHandStack().getItem() instanceof CrossbowItem
                && CrossbowItem.isCharged(player.getOffHandStack())) {
            stack = player.getOffHandStack();
            hand = Hand.OFF_HAND;
        } else {
            crossbowHolds.remove(player.getUuid());
            return;
        }
        String action = handAction("crossbow_hold", "crossbow_hold_off", hand);
        HoldState state = crossbowHolds.computeIfAbsent(player.getUuid(), ignored -> new HoldState());
        if (!action.equals(state.action)) {
            state.action = action;
            state.startTick = player.getWorld().getTime();
        }
        long elapsed = Math.max(0, player.getWorld().getTime() - state.startTick);
        syncScrub(player, action, clampProgress(elapsed / (20f * 0.40f)), desired);
    }

    private static int usedTicks(ServerPlayerEntity player, ItemStack stack) {
        return Math.max(0, stack.getMaxUseTime() - player.getItemUseTimeLeft());
    }

    private static float clipSecondsProgress(float seconds, float duration) {
        return clampProgress(seconds / duration);
    }

    private static float loopedSecondsProgress(float usedTicks, float duration, float loopStart, float loopEnd) {
        float seconds = usedTicks / 20f;
        if (seconds >= loopStart) {
            float length = loopEnd - loopStart;
            seconds = loopStart + ((seconds - loopStart) % length + length) % length;
        }
        return clipSecondsProgress(seconds, duration);
    }

    private static float armPoseProgress(ServerPlayerEntity player, ItemStack stack, String action) {
        int used = usedTicks(player, stack);
        String base = action.endsWith("_off") ? action.substring(0, action.length() - 4) : action;
        return switch (base) {
            case "spyglass" -> loopedSecondsProgress(used, 2.35f, 0.35f, 2.35f);
            case "toot_horn" -> clipSecondsProgress(Math.min(used / 20f, 2.60f), 2.60f);
            case "crossbow_charge" -> {
                int pullTime = Math.max(1, CrossbowItem.getPullTime(stack));
                yield clipSecondsProgress(1.25f * clampProgress(used / (float) pullTime), 1.25f);
            }
            case "brush" -> loopedSecondsProgress(used * TOOL_PLAYBACK_SPEED, 0.925f, 0.30f, 0.80f);
            case "use_item" -> clipSecondsProgress(used / 20f * TOOL_PLAYBACK_SPEED, 0.40f);
            default -> useProgress(player.getItemUseTimeLeft(), stack.getMaxUseTime());
        };
    }

    private static String armPoseAction(ItemStack stack, Hand hand, UseAction useAction) {
        if (stack.getItem() instanceof CrossbowItem) {
            return handAction("crossbow_charge", "crossbow_charge_off", hand);
        }
        return switch (useAction) {
            case SPYGLASS -> handAction("spyglass", "spyglass_off", hand);
            case TOOT_HORN -> handAction("toot_horn", "toot_horn_off", hand);
            case BRUSH -> handAction("brush", "brush_off", hand);
            case EAT, DRINK, BOW, SPEAR, BLOCK -> null;
            default -> handAction("use_item", "use_item_off", hand);
        };
    }

    private static void syncScrub(ServerPlayerEntity player, String action, float progress, Set<String> desired) {
        // 尚未交付资源的动作不发请求，避免接收端逐 tick 报 ANIMATION_NOT_FOUND；该状态保留原版姿态。
        if (!PlayerAnimationAction.find(action).delivered()) {
            return;
        }
        desired.add(action);
        ServerPlayerAnimations.scrub(player, action, progress);
    }

    private static void playArmorEquip(ServerPlayerEntity player, ArmorSnapshot previous) {
        if (previous == null) {
            return;
        }
        for (int slot = 0; slot < 4; slot++) {
            if (armorTransition(previous.equipped(slot), !player.getInventory().getArmorStack(slot).isEmpty()) != Transition.START) {
                continue;
            }
            String part = switch (slot) {
                case 0 -> "boots";
                case 1 -> "leggings";
                case 2 -> "chestplate";
                case 3 -> "helmet";
                default -> throw new IllegalStateException("未知盔甲槽: " + slot);
            };
            // 记录上一 tick 手持的盔甲，右键从副手装备时选择对应左臂模板。
            String suffix = previous.offHandArmor() && !previous.mainHandArmor() ? "_off" : "";
            ServerPlayerAnimations.play(player, "equip_" + part + suffix);
        }
    }

    private record ArmorSnapshot(boolean[] slots, boolean mainHandArmor, boolean offHandArmor) {
        static ArmorSnapshot capture(ServerPlayerEntity player) {
            boolean[] slots = new boolean[4];
            for (int slot = 0; slot < slots.length; slot++) {
                slots[slot] = !player.getInventory().getArmorStack(slot).isEmpty();
            }
            ItemStack main = player.getMainHandStack();
            ItemStack off = player.getOffHandStack();
            return new ArmorSnapshot(slots, main.getItem() instanceof ArmorItem, off.getItem() instanceof ArmorItem);
        }

        boolean equipped(int slot) {
            return slots[slot];
        }
    }
}

package dev.kizuna.playeranimation.animation;

import dev.kizuna.playeranimation.model.BoneId;
import dev.kizuna.playeranimation.model.Pose;
import dev.kizuna.playeranimation.model.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;

public final class AnimationPlayer {
    private static final double SCRUB_RESTART_BLEND_SECONDS = 0.05;
    private static final double SCRUB_INTERPOLATION_SECONDS = 0.05;
    private static final float SCRUB_REWIND_THRESHOLD = 0.25f;
    private static final float MINING_LOOP_SECONDS = 0.20f;
    // 循环段边界判断的归一化容差，吸收 tick 取样与浮点误差。
    private static final float LOOP_REGION_EPSILON = 0.001f;
    private final List<Playback> playing = new ArrayList<>();
    private long sequence;

    public enum Handedness { RIGHT, LEFT }
    private enum PlaybackMode { FREE, SCRUB }
    public record PlaybackRequest(double start, Handedness handedness, String playId,
                                  AnimationClip.Loop loopOverride) { }

    public void play(AnimationEntry entry, double now, boolean leftHanded) {
        Handedness handedness = leftHanded ? Handedness.LEFT : Handedness.RIGHT;
        play(entry, now, new PlaybackRequest(now, handedness, null, null));
    }

    /**
     * 按原版动作的归一化进度取样。相同动作在连续 tick 中只更新游标，不重建播放实例，
     * 因而不会反复触发淡入或丢失当前的上层叠加。
     */
    public void scrub(AnimationEntry entry, double now, float normalizedProgress, boolean leftHanded) {
        if (!Float.isFinite(normalizedProgress) || normalizedProgress < 0 || normalizedProgress > 1) {
            throw new IllegalArgumentException("动画 scrub 进度必须在 [0,1]");
        }
        for (Playback playback : playing) {
            if (playback.scrub && playback.entry.id().equals(entry.id()) && Double.isNaN(playback.stopped)) {
                playback.updateScrub(now, normalizedProgress);
                return;
            }
        }
        // 自由播放与 scrub 不能同时占用同一动作 ID；旧实例从当前姿态淡出。
        stop(entry.id(), now);
        Handedness handedness = leftHanded ? Handedness.LEFT : Handedness.RIGHT;
        PlaybackRequest request = new PlaybackRequest(now, handedness, null, null);
        Playback playback = new Playback(entry, request, sequence++, PlaybackMode.SCRUB);
        playback.scrubFrom = normalizedProgress;
        playback.scrubTarget = normalizedProgress;
        playback.scrubUpdatedAt = now;
        playing.add(playback);
        playing.sort(Comparator.comparingInt((Playback p) -> p.loop() == AnimationClip.Loop.REPEAT ? 0 : 1)
                .thenComparingInt(p -> p.entry.priority()).thenComparingLong(p -> p.sequence));
    }

    /** 返回是否处理了新的播放请求；同一实例的补发返回 false，供调用方保留附属视觉状态。 */
    public boolean synchronize(AnimationEntry entry, double now, double start, boolean leftHanded, String playId) {
        Handedness handedness = leftHanded ? Handedness.LEFT : Handedness.RIGHT;
        return synchronize(entry, now, new PlaybackRequest(start, handedness, playId, null));
    }

    public boolean synchronize(AnimationEntry entry, double now, PlaybackRequest request) {
        // 同一播放请求的补发不重置播放器；同 tick 的再次播放仍有不同请求 ID。
        for (Playback playback : playing) {
            if (playback.entry.id().equals(entry.id()) && request.playId().equals(playback.playId)
                    && playback.start == request.start()
                    && Double.isNaN(playback.stopped)) {
                return false;
            }
        }
        play(entry, now, request);
        return true;
    }

    public void play(AnimationEntry entry, double now, PlaybackRequest request) {
        // 重播同名动画时旧实例从当前时刻淡出，新实例按指定起点取样。
        stop(entry.id(), now);
        Playback playback = new Playback(entry, request, sequence++, PlaybackMode.FREE);
        if (request.playId() != null && now > request.start()) {
            playback.lastEventTime = now - request.start();
        }
        if (playback.finished(now)) {
            return;
        }
        playing.add(playback);
        playing.sort(Comparator.comparingInt((Playback p) -> p.loop() == AnimationClip.Loop.REPEAT ? 0 : 1)
                .thenComparingInt(p -> p.entry.priority()).thenComparingLong(p -> p.sequence));
    }

    public void stop(String id, double now) {
        // 停止时冻结当前动画时间，淡出只改变它相对下层的权重。
        for (Playback playback : playing) {
            if ((id == null || playback.entry.id().equals(id)) && Double.isNaN(playback.stopped)) {
                playback.stopped = now;
            }
        }
    }

    public boolean empty() {
        return playing.isEmpty();
    }

    public boolean hasActive(String id, double now) {
        return playing.stream().anyMatch(playback -> playback.entry.id().equals(id)
                && !playback.finished(now) && playback.weight(now) > 0);
    }

    public boolean hasTwoHandedAction(double now) {
        return playing.stream().anyMatch(playback -> !playback.finished(now) && playback.weight(now) > 0
                && playback.entry.hand() == AnimationEntry.Hand.BOTH);
    }

    public float normalizedProgress(String id, double now) {
        for (Playback playback : playing) {
            if (playback.entry.id().equals(id) && !playback.finished(now)) {
                if (playback.scrub) {
                    return playback.foldedTarget();
                }
                return (float) Math.min(1, playback.time(now) / playback.entry.clip().duration());
            }
        }
        return 0;
    }

    public boolean hasFirstPerson(double now) {
        return playing.stream().anyMatch(p -> p.entry.firstPerson() && p.weight(now) > 0
                && p.entry.clip().tracks().keySet().stream().anyMatch(b -> b.arm() && p.entry.mask().includes(b)));
    }

    public void advance(double now, BiConsumer<String, String> events) {
        // 事件仅随逻辑时钟前进；多次渲染同一帧不会重复触发 hit 或 release。
        List<String[]> pending = new ArrayList<>();
        for (Playback playback : playing) {
            if (now < playback.start) {
                // 客户端世界时钟短暂落后于服务端起点时，不能提前发送零秒事件。
                continue;
            }
            playback.collectEvents(now, pending);
        }
        playing.removeIf(p -> p.finished(now));
        // 回调可能启动或停止动画，遍历结束后再交给外部调用方。
        for (String[] event : pending) {
            events.accept(event[0], event[1]);
        }
    }

    public Pose[] sample(Pose[] base, double now, boolean firstPerson) {
        return sample(base, now, firstPerson, false);
    }

    public Pose[] sample(Pose[] base, double now, boolean firstPerson, boolean crouching) {
        Pose[] result = base.clone();
        // 从低层到高层混合；缺失的骨与通道直接保留下层已有结果。
        for (Playback playback : playing) {
            AnimationEntry entry = playback.entry;
            if (firstPerson && !entry.firstPerson()) {
                continue;
            }
            float weight = playback.weight(now);
            float time = playback.time(now);
            AnimationEntry.Mask mask = effectiveMask(playback, now, crouching);
            for (var trackEntry : entry.clip().tracks().entrySet()) {
                BoneId source = trackEntry.getKey();
                if (!mask.includes(source) || (firstPerson && !source.arm())) {
                    continue;
                }
                BoneId target = playback.mirror ? source.mirrored() : source;
                AnimationClip.Track track = trackEntry.getValue();
                Pose lower = result[target.ordinal()];
                Vec3 position = lower.position();
                Vec3 rotation = lower.rotation();
                // Bedrock 导出值先恢复到 Blockbench，随后才做解剖左右镜像。
                if (track.position() != null) {
                    Vec3 raw = track.position().sample(time, playback.loop() == AnimationClip.Loop.REPEAT);
                    position = new Vec3(playback.mirror ? raw.x() : -raw.x(), raw.y(), raw.z());
                }
                if (track.rotation() != null) {
                    Vec3 raw = track.rotation().sample(time, playback.loop() == AnimationClip.Loop.REPEAT);
                    rotation = new Vec3(-raw.x(), playback.mirror ? raw.y() : -raw.y(),
                            playback.mirror ? -raw.z() : raw.z());
                }
                result[target.ordinal()] = lower.blend(new Pose(position, rotation), weight);
            }
        }
        return result;
    }

    private AnimationEntry.Mask effectiveMask(Playback playback, double now, boolean crouching) {
        if (playback.entry.mask() == AnimationEntry.Mask.FULL && stanceOverlay(playback.entry.id())) {
            // 格挡、受击、调息、蓄力的腿锁在架势踝位：静止架势上整段生效，移动底层、闪避和轻步上只取上身。
            boolean moving = crouching || playing.stream().anyMatch(lower -> !lower.finished(now) && lower.weight(now) > 0
                    && (lowerBase(lower.entry.id()) || movingStance(lower.entry.id())));
            return moving ? AnimationEntry.Mask.UPPER : AnimationEntry.Mask.FULL;
        }
        if (playback.entry.mask() != AnimationEntry.Mask.FULL || !upperOverlay(playback.entry.id())) {
            return playback.entry.mask();
        }
        // 潜行时工具动作只接管上身，腿部继续使用原版姿态；底层比工具动作晚开始同样有效。
        boolean climbing = playing.stream().anyMatch(lower -> !lower.finished(now) && lower.weight(now) > 0
                && lower.entry.id().equals("climb"));
        if (climbing && singleArmWhileClimbing(playback.entry.id())) {
            // 攀爬中攻击、挖掘、放置只接管动作手臂，另一只手和双脚继续抓梯。
            return playback.entry.id().endsWith("_off") ? AnimationEntry.Mask.L_ARM : AnimationEntry.Mask.R_ARM;
        }
        // 架势底层（含静止的 stance_idle）是错步站姿：原版剪辑的腿是并步零位，整段生效会拖动脚，只取上身。
        boolean movingBase = crouching || climbing || playing.stream().anyMatch(lower -> !lower.finished(now)
                && lower.weight(now) > 0 && (lowerBase(lower.entry.id()) || movingStance(lower.entry.id())
                || lower.entry.id().equals("stance_idle") || stanceOverlay(lower.entry.id())));
        return movingBase ? AnimationEntry.Mask.UPPER : AnimationEntry.Mask.FULL;
    }

    private static boolean upperOverlay(String id) {
        return id.startsWith("attack") || id.startsWith("mine")
                || id.startsWith("use_item") || id.startsWith("place_block")
                || id.startsWith("eat") || id.startsWith("drink") || id.startsWith("block_shield")
                || id.startsWith("bow_draw") || id.startsWith("trident_charge")
                || id.startsWith("equip_")
                || id.startsWith("spyglass") || id.startsWith("toot_horn")
                || id.startsWith("crossbow_") || id.startsWith("brush");
    }

    private static boolean stanceOverlay(String id) {
        return id.startsWith("block_one_hand") || id.startsWith("block_two_hand")
                || id.equals("technique_breath") || id.equals("technique_charge");
    }

    private static boolean movingStance(String id) {
        return id.startsWith("stance_walk_") || id.equals("stance_run") || id.equals("stance_enter")
                || id.equals("stance_exit") || id.startsWith("dodge_") || id.startsWith("technique_dash_");
    }

    private static boolean singleArmWhileClimbing(String id) {
        return id.startsWith("attack") || id.startsWith("mine") || id.startsWith("place_block");
    }

    private static boolean lowerBase(String id) {
        return switch (id) {
            case "walk", "run", "crouch_work", "sneak", "sneak_walk", "ride", "elytra", "jump", "jump_air", "jump_takeoff", "jump_land",
                 "meditate" -> true;
            default -> false;
        };
    }

    private static final class Playback {
        final AnimationEntry entry;
        final double start;
        final boolean mirror;
        final long sequence;
        final String playId;
        final boolean scrub;
        final AnimationClip.Loop loopOverride;
        double stopped = Double.NaN;
        double lastEventTime = -1;
        float scrubFrom;
        float scrubTarget;
        double scrubUpdatedAt;
        double scrubRestartAt = Double.NaN;

        Playback(AnimationEntry entry, PlaybackRequest request, long sequence, PlaybackMode mode) {
            // 每次播放有独立时钟，多个实体和同一动画的重播互不共享状态。
            this.entry = entry;
            this.start = request.start();
            this.mirror = request.handedness() == Handedness.LEFT && entry.hand() != AnimationEntry.Hand.NONE;
            this.sequence = sequence;
            this.playId = request.playId();
            this.scrub = mode == PlaybackMode.SCRUB;
            this.loopOverride = request.loopOverride();
        }

        AnimationClip.Loop loop() {
            // 未指定覆盖时保留资源声明的循环方式。
            if (loopOverride == null) {
                return entry.clip().loop();
            }
            return loopOverride;
        }

        private void updateScrub(double now, float normalizedProgress) {
            // 连续取样复用当前实例；循环接缝和一次性重触发保留各自的插值规则。
            float previous = foldedTarget();
            boolean rewound = normalizedProgress + SCRUB_REWIND_THRESHOLD < previous;
            scrubFrom = normalizedProgress;
            scrubTarget = normalizedProgress;
            scrubUpdatedAt = now;
            float miningLoopEnd = MINING_LOOP_SECONDS / entry.clip().duration();
            float offset = wrapOffset(previous, normalizedProgress);
            if (mining(entry.id()) && normalizedProgress < previous && previous <= miningLoopEnd) {
                // 从循环尾向首帧正向插值，避免 0.8 倍及急迫下的非整 tick 回绕倒放。
                scrubFrom = previous - miningLoopEnd;
                scrubRestartAt = Double.NaN;
            } else if (offset != 0) {
                // 循环段或循环资源跨接缝：目标沿最短方向越过接缝，插值不倒放整段。
                scrubFrom = previous;
                scrubTarget = normalizedProgress + offset;
                scrubRestartAt = Double.NaN;
            } else if (rewound && directLoopRestart(entry.id())) {
                // 挖掘/放置的子循环首尾同姿态，回绕直接接回零点，不淡回底层。
                scrubRestartAt = Double.NaN;
            } else {
                scrubFrom = previous;
                if (rewound) {
                    // 一次性动作重触发仍保留短交叉淡入，避免硬切预备姿态。
                    scrubRestartAt = now;
                }
            }
        }

        void collectEvents(double now, List<String[]> pending) {
            // scrub 回绕后允许新周期事件；自由循环按绝对周期遍历跨过的时间段。
            double end = eventEnd(now);
            AnimationClip clip = entry.clip();
            if (scrub && end < lastEventTime) {
                lastEventTime = -1;
            }
            long firstCycle = 0;
            long lastCycle = 0;
            if (loop() == AnimationClip.Loop.REPEAT) {
                firstCycle = Math.max(0, (long) Math.floor(lastEventTime / clip.duration()));
                lastCycle = (long) Math.floor(end / clip.duration());
            }
            for (long cycle = firstCycle; cycle <= lastCycle; cycle++) {
                for (AnimationClip.TimelineEvent event : clip.events()) {
                    double time = cycle * (double) clip.duration() + event.seconds();
                    if (time > lastEventTime && time <= end) {
                        pending.add(new String[]{entry.id(), eventName(event)});
                    }
                }
            }
            lastEventTime = Math.max(lastEventTime, end);
        }

        private double eventEnd(double now) {
            // 一次性动作的事件不能超出资源时长，scrub 使用权威进度。
            if (scrub) {
                return scrubEventTime();
            }
            double elapsed = Math.max(0, Math.min(now, stoppedOr(now)) - start);
            if (loop() == AnimationClip.Loop.REPEAT) {
                return elapsed;
            }
            return Math.min(elapsed, entry.clip().duration());
        }

        private String eventName(AnimationClip.TimelineEvent event) {
            // 解剖左右镜像时，脚步事件与实际落脚侧保持一致。
            String name = event.name();
            if (mirror && name.startsWith("step_")) {
                return name.equals("step_l") ? "step_r" : "step_l";
            }
            return name;
        }

        double stoppedOr(double now) {
            return Double.isNaN(stopped) ? now : stopped;
        }

        double fadeStart() {
            // 一次性动画在长度结束后淡出；循环与保持动作只在显式停止时淡出。
            double natural = Double.POSITIVE_INFINITY;
            if (!scrub && loop() == AnimationClip.Loop.ONCE) {
                natural = start + entry.clip().duration();
            }
            if (Double.isNaN(stopped)) {
                return natural;
            }
            return Math.min(natural, stopped);
        }

        boolean finished(double now) {
            return now >= fadeStart() + entry.blendOut();
        }

        float weight(double now) {
            // 提前停止时保留已淡入的权重，避免停止瞬间跳到全权重。
            double elapsed = Math.max(0, Math.min(now, fadeStart()) - start);
            double fadeIn = entry.blendIn() == 0 ? 1 : Math.min(1, elapsed / entry.blendIn());
            if (scrub && !Double.isNaN(scrubRestartAt)) {
                fadeIn = Math.min(fadeIn, Math.max(0,
                        (now - scrubRestartAt) / SCRUB_RESTART_BLEND_SECONDS));
            }
            double fadeOut = now < fadeStart() ? 1 : entry.blendOut() == 0 ? 0
                    : Math.max(0, 1 - (now - fadeStart()) / entry.blendOut());
            return (float) (fadeIn * fadeOut);
        }

        float time(double now) {
            if (scrub) {
                return (float) scrubTime(now);
            }
            double elapsed = Math.max(0, stoppedOr(now) - start);
            // 非循环动画在淡出阶段保持末帧，循环动画停止后冻结停止时的局部时间。
            if (loop() == AnimationClip.Loop.REPEAT) {
                return (float) (elapsed % entry.clip().duration());
            }
            return (float) Math.min(elapsed, entry.clip().duration());
        }

        double scrubTime(double now) {
            // 更新 tick 的渲染起点立即取新权威姿态；随后一个 tickDelta 内从上一目标
            // 向新目标插值，避免服务端每 0.05 秒只显示四个离散姿态。
            double amount = now <= scrubUpdatedAt ? 1 : Math.min(1,
                    (now - scrubUpdatedAt) / SCRUB_INTERPOLATION_SECONDS);
            double progress = scrubFrom + (scrubTarget - scrubFrom) * amount;
            double time = progress * entry.clip().duration();
            if (time < 0 && mining(entry.id())) {
                time += MINING_LOOP_SECONDS;
            }
            if (entry.loopRegionStart() != null && time >= entry.loopRegionEnd()) {
                double start = entry.loopRegionStart();
                double length = entry.loopRegionEnd() - start;
                time = start + ((time - start) % length + length) % length;
            }
            if (loop() == AnimationClip.Loop.REPEAT) {
                // 循环资源的回绕目标可能落在 [0,1] 之外，按剪辑长度取模回到同一周期。
                double duration = entry.clip().duration();
                time = (time % duration + duration) % duration;
            }
            return time;
        }

        float foldedTarget() {
            // 回绕插值会把目标暂存在接缝另一侧；对外和下次比较时折回权威区间。
            if (loop() == AnimationClip.Loop.REPEAT) {
                return scrubTarget - (float) Math.floor(scrubTarget);
            }
            if (entry.loopRegionStart() != null && scrubTarget * entry.clip().duration() >= entry.loopRegionEnd()) {
                return scrubTarget - loopRegionLength();
            }
            return scrubTarget;
        }

        float wrapOffset(float previous, float target) {
            // 只有确实跨越循环接缝才返回偏移：循环资源取最短方向，循环段只会正向回绕。
            if (loop() == AnimationClip.Loop.REPEAT) {
                if (previous - target > 0.5f) {
                    return 1;
                }
                if (target - previous > 0.5f) {
                    return -1;
                }
                return 0;
            }
            if (entry.loopRegionStart() == null || target >= previous) {
                return 0;
            }
            float duration = entry.clip().duration();
            float start = entry.loopRegionStart() / duration - LOOP_REGION_EPSILON;
            float end = entry.loopRegionEnd() / duration + LOOP_REGION_EPSILON;
            boolean inside = previous >= start && previous <= end && target >= start && target <= end;
            return inside ? loopRegionLength() : 0;
        }

        private float loopRegionLength() {
            return (entry.loopRegionEnd() - entry.loopRegionStart()) / entry.clip().duration();
        }

        double scrubEventTime() {
            double time = scrubTarget * entry.clip().duration();
            if (entry.loopRegionStart() != null && time >= entry.loopRegionEnd()) {
                double start = entry.loopRegionStart();
                double length = entry.loopRegionEnd() - start;
                time = start + ((time - start) % length + length) % length;
            }
            return time;
        }
    }

    private static boolean directLoopRestart(String id) {
        return mining(id)
                || id.equals("place_block") || id.equals("place_block_off");
    }

    private static boolean mining(String id) {
        return id.equals("mine_main") || id.equals("mine_off");
    }
}

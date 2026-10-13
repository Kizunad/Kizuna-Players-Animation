package dev.kizuna.playeranimation.animation;

import dev.kizuna.playeranimation.model.BoneId;
import dev.kizuna.playeranimation.model.Pose;
import dev.kizuna.playeranimation.model.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;

public final class AnimationPlayer {
    private final List<Playback> playing = new ArrayList<>();
    private long sequence;

    public void play(AnimationEntry entry, double now, boolean leftHanded) {
        // 重播同名动画时旧实例淡出，新的实例从自己的零秒开始。
        stop(entry.id(), now);
        playing.add(new Playback(entry, now, leftHanded && entry.hand() != AnimationEntry.Hand.NONE, sequence++));
        playing.sort(Comparator.comparingInt((Playback p) -> p.entry.clip().loop() == AnimationClip.Loop.REPEAT ? 0 : 1)
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

    public boolean hasFirstPerson(double now) {
        return playing.stream().anyMatch(p -> p.entry.firstPerson() && p.weight(now) > 0
                && p.entry.clip().tracks().keySet().stream().anyMatch(b -> b.arm() && p.entry.mask().includes(b)));
    }

    public void advance(double now, BiConsumer<String, String> events) {
        // 事件仅随逻辑时钟前进；多次渲染同一帧不会重复触发 hit 或 release。
        List<String[]> pending = new ArrayList<>();
        for (Playback playback : playing) {
            double elapsed = Math.max(0, Math.min(now, playback.stoppedOr(now)) - playback.start);
            AnimationClip clip = playback.entry.clip();
            double end = clip.loop() == AnimationClip.Loop.REPEAT ? elapsed : Math.min(elapsed, clip.duration());
            long firstCycle = clip.loop() == AnimationClip.Loop.REPEAT
                    ? Math.max(0, (long) Math.floor(playback.lastEventTime / clip.duration())) : 0;
            long lastCycle = clip.loop() == AnimationClip.Loop.REPEAT ? (long) Math.floor(end / clip.duration()) : 0;
            for (long cycle = firstCycle; cycle <= lastCycle; cycle++) {
                for (AnimationClip.TimelineEvent event : clip.events()) {
                    double time = cycle * (double) clip.duration() + event.seconds();
                    if (time > playback.lastEventTime && time <= end) {
                        String name = event.name();
                        if (playback.mirror && name.startsWith("step_")) {
                            name = name.equals("step_l") ? "step_r" : "step_l";
                        }
                        pending.add(new String[]{playback.entry.id(), name});
                    }
                }
            }
            playback.lastEventTime = Math.max(playback.lastEventTime, end);
        }
        playing.removeIf(p -> p.finished(now));
        // 回调可能启动或停止动画，遍历结束后再交给外部调用方。
        for (String[] event : pending) {
            events.accept(event[0], event[1]);
        }
    }

    public Pose[] sample(Pose[] base, double now, boolean firstPerson) {
        Pose[] result = base.clone();
        // 从低层到高层混合；缺失的骨与通道直接保留下层已有结果。
        for (Playback playback : playing) {
            AnimationEntry entry = playback.entry;
            if (firstPerson && !entry.firstPerson()) {
                continue;
            }
            float weight = playback.weight(now);
            float time = playback.time(now);
            for (var trackEntry : entry.clip().tracks().entrySet()) {
                BoneId source = trackEntry.getKey();
                if (!entry.mask().includes(source) || (firstPerson && !source.arm())) {
                    continue;
                }
                BoneId target = playback.mirror ? source.mirrored() : source;
                AnimationClip.Track track = trackEntry.getValue();
                Pose lower = result[target.ordinal()];
                Vec3 position = lower.position();
                Vec3 rotation = lower.rotation();
                // Bedrock 导出值先恢复到 Blockbench，随后才做解剖左右镜像。
                if (track.position() != null) {
                    Vec3 raw = track.position().sample(time, entry.clip().loop() == AnimationClip.Loop.REPEAT);
                    position = new Vec3(playback.mirror ? raw.x() : -raw.x(), raw.y(), raw.z());
                }
                if (track.rotation() != null) {
                    Vec3 raw = track.rotation().sample(time, entry.clip().loop() == AnimationClip.Loop.REPEAT);
                    rotation = new Vec3(-raw.x(), playback.mirror ? raw.y() : -raw.y(),
                            playback.mirror ? -raw.z() : raw.z());
                }
                result[target.ordinal()] = lower.blend(new Pose(position, rotation), weight);
            }
        }
        return result;
    }

    private static final class Playback {
        final AnimationEntry entry;
        final double start;
        final boolean mirror;
        final long sequence;
        double stopped = Double.NaN;
        double lastEventTime = -1;

        Playback(AnimationEntry entry, double start, boolean mirror, long sequence) {
            // 每次播放有独立时钟，多个实体和同一动画的重播互不共享状态。
            this.entry = entry;
            this.start = start;
            this.mirror = mirror;
            this.sequence = sequence;
        }

        double stoppedOr(double now) {
            return Double.isNaN(stopped) ? now : stopped;
        }

        double fadeStart() {
            // 一次性动画在长度结束后淡出；循环与保持动作只在显式停止时淡出。
            double natural = entry.clip().loop() == AnimationClip.Loop.ONCE
                    ? start + entry.clip().duration() : Double.POSITIVE_INFINITY;
            return Double.isNaN(stopped) ? natural : Math.min(natural, stopped);
        }

        boolean finished(double now) {
            return now >= fadeStart() + entry.blendOut();
        }

        float weight(double now) {
            // 提前停止时保留已淡入的权重，避免停止瞬间跳到全权重。
            double elapsed = Math.max(0, Math.min(now, fadeStart()) - start);
            double fadeIn = entry.blendIn() == 0 ? 1 : Math.min(1, elapsed / entry.blendIn());
            double fadeOut = now < fadeStart() ? 1 : entry.blendOut() == 0 ? 0
                    : Math.max(0, 1 - (now - fadeStart()) / entry.blendOut());
            return (float) (fadeIn * fadeOut);
        }

        float time(double now) {
            double elapsed = Math.max(0, stoppedOr(now) - start);
            // 非循环动画在淡出阶段保持末帧，循环动画停止后冻结停止时的局部时间。
            return entry.clip().loop() == AnimationClip.Loop.REPEAT
                    ? (float) (elapsed % entry.clip().duration()) : (float) Math.min(elapsed, entry.clip().duration());
        }
    }
}

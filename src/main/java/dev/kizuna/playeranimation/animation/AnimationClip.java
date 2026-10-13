package dev.kizuna.playeranimation.animation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.kizuna.playeranimation.model.BoneId;
import dev.kizuna.playeranimation.model.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public record AnimationClip(float duration, Loop loop, Map<BoneId, Track> tracks, List<TimelineEvent> events) {
    public enum Loop { ONCE, REPEAT, HOLD }
    public record Track(Channel position, Channel rotation) { }
    public record TimelineEvent(float seconds, String name) { }
    public static final Set<String> EVENTS = Set.of("hit", "release", "step_l", "step_r", "cancel_ok");

    public static AnimationClip parse(JsonObject document, String key, Consumer<String> errors) {
        // 固定读取 Bedrock 动画，明确拒绝旧格式与动态表达式。
        if (!"1.8.0".equals(JsonInput.string(document, "format_version"))) {
            throw new IllegalArgumentException("动画 format_version 必须为 1.8.0");
        }
        JsonObject animation = JsonInput.object(JsonInput.object(document, "animations"), key);
        float duration = JsonInput.nonnegative(animation, "animation_length");
        if (duration == 0) {
            throw new IllegalArgumentException("animation_length 必须大于零");
        }
        Loop loop = Loop.ONCE;
        if (animation.has("loop")) {
            JsonElement value = animation.get("loop");
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
                loop = value.getAsBoolean() ? Loop.REPEAT : Loop.ONCE;
            } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                    && value.getAsString().equals("hold_on_last_frame")) {
                loop = Loop.HOLD;
            } else {
                throw new IllegalArgumentException("未知 loop 值: " + value);
            }
        }
        Map<BoneId, Track> tracks = new EnumMap<>(BoneId.class);
        for (var entry : JsonInput.object(animation, "bones").entrySet()) {
            BoneId bone;
            try {
                bone = BoneId.named(entry.getKey());
            } catch (IllegalArgumentException exception) {
                errors.accept(exception.getMessage() + "，忽略该轨道");
                continue;
            }
            if (bone.socket()) {
                errors.accept("挂点不能写动画轨道: " + bone.key);
                continue;
            }
            JsonObject raw = entry.getValue().getAsJsonObject();
            for (String channel : raw.keySet()) {
                if (!Set.of("rotation", "position", "scale").contains(channel)) {
                    throw new IllegalArgumentException("未知动画通道: " + channel);
                }
            }
            if (raw.has("scale")) {
                JsonElement scale = raw.get("scale");
                if (scale.isJsonPrimitive()) {
                    if (JsonInput.number(scale) != 1) {
                        throw new IllegalArgumentException("缩放必须恒为 1");
                    }
                } else {
                    for (Channel.Keyframe frame : Channel.parse(scale, duration).frames()) {
                        if (!frame.pre().equals(Vec3.ONE) || !frame.post().equals(Vec3.ONE)) {
                            throw new IllegalArgumentException("缩放必须恒为 1");
                        }
                    }
                }
            }
            Channel position = raw.has("position") ? Channel.parse(raw.get("position"), duration) : null;
            Channel rotation = raw.has("rotation") ? Channel.parse(raw.get("rotation"), duration) : null;
            if (position != null || rotation != null) {
                tracks.put(bone, new Track(position, rotation));
            }
        }
        List<TimelineEvent> events = new ArrayList<>();
        if (animation.has("timeline")) {
            for (var entry : JsonInput.object(animation, "timeline").entrySet()) {
                float time = Float.parseFloat(entry.getKey());
                if (!Float.isFinite(time) || time < 0 || time > duration) {
                    throw new IllegalArgumentException("事件时间超出动画范围: " + entry.getKey());
                }
                List<JsonElement> names = entry.getValue().isJsonArray()
                        ? entry.getValue().getAsJsonArray().asList() : List.of(entry.getValue());
                for (JsonElement name : names) {
                    if (!name.isJsonPrimitive() || !name.getAsJsonPrimitive().isString()
                            || !EVENTS.contains(name.getAsString())) {
                        errors.accept("未知事件: " + name + "，忽略该事件");
                        continue;
                    }
                    events.add(new TimelineEvent(time, name.getAsString()));
                }
            }
        }
        events.sort(Comparator.comparingDouble(TimelineEvent::seconds));
        return new AnimationClip(duration, loop, Map.copyOf(tracks), List.copyOf(events));
    }
}

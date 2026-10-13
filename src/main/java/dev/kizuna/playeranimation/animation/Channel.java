package dev.kizuna.playeranimation.animation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.kizuna.playeranimation.model.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record Channel(List<Keyframe> frames) {
    public enum Interpolation { LINEAR, CATMULLROM, STEP }
    public record Keyframe(float seconds, Vec3 pre, Vec3 post, Interpolation interpolation) { }

    public static Channel parse(JsonElement element, float duration) {
        // 常量通道等价于零秒关键帧；时间映射先按数值排序，不能按字符串排序。
        List<Keyframe> frames = new ArrayList<>();
        if (element.isJsonArray()) {
            Vec3 vector = JsonInput.vector(element);
            frames.add(new Keyframe(0, vector, vector, Interpolation.LINEAR));
        } else {
            for (var entry : element.getAsJsonObject().entrySet()) {
                float time = Float.parseFloat(entry.getKey());
                if (!Float.isFinite(time) || time < 0 || time > duration) {
                    throw new IllegalArgumentException("关键帧时间超出动画范围: " + entry.getKey());
                }
                JsonElement raw = entry.getValue();
                if (raw.isJsonArray()) {
                    Vec3 vector = JsonInput.vector(raw);
                    frames.add(new Keyframe(time, vector, vector, Interpolation.LINEAR));
                } else {
                    JsonObject object = raw.getAsJsonObject();
                    Vec3 post = JsonInput.vector(object.get("post"));
                    Vec3 pre = object.has("pre") ? JsonInput.vector(object.get("pre")) : post;
                    String mode = object.has("lerp_mode") ? JsonInput.string(object, "lerp_mode") : "linear";
                    Interpolation interpolation = switch (mode) {
                        case "linear" -> Interpolation.LINEAR;
                        case "catmullrom" -> Interpolation.CATMULLROM;
                        case "step" -> Interpolation.STEP;
                        default -> throw new IllegalArgumentException("未知插值: " + mode);
                    };
                    frames.add(new Keyframe(time, pre, post, interpolation));
                }
            }
        }
        frames.sort(Comparator.comparingDouble(Keyframe::seconds));
        if (frames.isEmpty()) {
            throw new IllegalArgumentException("动画通道不能为空");
        }
        for (int i = 1; i < frames.size(); i++) {
            if (frames.get(i - 1).seconds == frames.get(i).seconds) {
                throw new IllegalArgumentException("关键帧时间重复: " + frames.get(i).seconds);
            }
        }
        return new Channel(List.copyOf(frames));
    }

    public Vec3 sample(float seconds) {
        return sample(seconds, false);
    }

    public Vec3 sample(float seconds, boolean loop) {
        // 精确到达帧时使用 post，帧左侧使用 pre，从而保留 Blockbench 的跳变。
        if (seconds < frames.get(0).seconds) {
            return frames.get(0).pre;
        }
        for (int i = 0; i < frames.size() - 1; i++) {
            Keyframe left = frames.get(i);
            Keyframe right = frames.get(i + 1);
            if (seconds >= right.seconds) {
                continue;
            }
            float amount = (seconds - left.seconds) / (right.seconds - left.seconds);
            if (left.interpolation == Interpolation.STEP) {
                return left.post;
            }
            if (left.interpolation == Interpolation.CATMULLROM
                    || right.interpolation == Interpolation.CATMULLROM) {
                // 闭合循环借用倒数第二帧和第二帧的切线，和 Blockbench 循环预览一致。
                int previousIndex = i > 0 ? i - 1 : loop && frames.size() >= 3 ? frames.size() - 2 : 0;
                int nextIndex = i + 2 < frames.size() ? i + 2 : loop && frames.size() >= 3 ? 1 : frames.size() - 1;
                Vec3 previous = left.pre.equals(left.post) ? frames.get(previousIndex).post : left.post;
                Vec3 next = right.pre.equals(right.post) ? frames.get(nextIndex).pre : right.pre;
                return new Vec3(catmull(previous.x(), left.post.x(), right.pre.x(), next.x(), amount),
                        catmull(previous.y(), left.post.y(), right.pre.y(), next.y(), amount),
                        catmull(previous.z(), left.post.z(), right.pre.z(), next.z(), amount));
            }
            return left.post.lerp(right.pre, amount);
        }
        return frames.get(frames.size() - 1).post;
    }

    private static float catmull(float a, float b, float c, float d, float t) {
        // 均匀 Catmull-Rom，与 Blockbench 每段的局部参数一致。
        return 0.5f * (2 * b + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t * t
                + (-a + 3 * b - 3 * c + d) * t * t * t);
    }
}

package dev.kizuna.playeranimation.animation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import dev.kizuna.playeranimation.model.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * 索引 item_rotation：持物手挂点在剪辑时间上的旋转（docs/asset-contract.md「索引条目」）。
 * 值与 Blockbench 骨骼旋转关键帧同一约定（度），绕本手挂点转，手里的物品跟着转；
 * 相邻键之间按 smoothstep 缓动，每个键都是停顿，首键前取首值、最后一个键之后保持。
 */
public record ItemRotation(List<Key> keys) {
    private static final int KEY_COMPONENTS = 2;
    private static final int AXES = 3;
    private static final float SMOOTHSTEP_SQUARE = 3;
    private static final float SMOOTHSTEP_CUBE = 2;

    public ItemRotation {
        keys = List.copyOf(keys);
    }

    /** 一个键：剪辑秒（严格递增）与该时刻的欧拉角。 */
    public record Key(float time, Vec3 rotation) { }

    public Vec3 sample(float time) {
        Key previous = keys.getFirst();
        if (time <= previous.time()) {
            return previous.rotation();
        }
        for (int index = 1; index < keys.size(); index++) {
            Key next = keys.get(index);
            if (time < next.time()) {
                float linear = (time - previous.time()) / (next.time() - previous.time());
                float eased = linear * linear * (SMOOTHSTEP_SQUARE - SMOOTHSTEP_CUBE * linear);
                return previous.rotation().lerp(next.rotation(), eased);
            }
            previous = next;
        }
        return previous.rotation();
    }

    public static ItemRotation parse(JsonElement raw, float duration) {
        // 资源边界校验一次：非空 [[秒, [x, y, z]], ...]，
        // 时间在剪辑内且严格递增，角度为有限数。
        if (!(raw instanceof JsonArray array) || array.isEmpty()) {
            throw new IllegalArgumentException("item_rotation 必须是非空 [[秒, [x, y, z]], ...]");
        }
        List<Key> keys = new ArrayList<>();
        float previous = -1;
        for (JsonElement element : array) {
            if (!(element instanceof JsonArray key) || key.size() != KEY_COMPONENTS
                    || !(key.get(1) instanceof JsonArray angles) || angles.size() != AXES) {
                throw new IllegalArgumentException("item_rotation 的键必须是 [秒, [x, y, z]]");
            }
            float time = JsonInput.number(key.get(0));
            if (time <= previous || time < 0 || time > duration) {
                throw new IllegalArgumentException("item_rotation 时间必须在剪辑内且严格递增: " + time);
            }
            Vec3 rotation = new Vec3(JsonInput.number(angles.get(0)), JsonInput.number(angles.get(1)),
                    JsonInput.number(angles.get(2)));
            keys.add(new Key(time, rotation));
            previous = time;
        }
        return new ItemRotation(keys);
    }
}

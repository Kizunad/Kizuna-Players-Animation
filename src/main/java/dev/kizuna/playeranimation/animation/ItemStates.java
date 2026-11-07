package dev.kizuna.playeranimation.animation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 索引 item_states：弓、弩物品模型谓词的剪辑时间轨道（docs/asset-contract.md「索引条目」）。
 * pull 取 [0, 1] 线性插值，pulling、charged、arrow 为 0 / 1 阶跃；第一个键之前取第一个键的值，
 * 最后一个键之后保持。没写的通道不改原版；这些值只控制客户端模型，不修改物品使用或弹药。
 */
public record ItemStates(Map<Channel, Track> tracks) {
    private static final int KEY_COMPONENTS = 2;

    public ItemStates {
        tracks = Map.copyOf(tracks);
    }

    public enum Interpolation { LINEAR, STEP }

    public enum Channel {
        PULL("pull", Interpolation.LINEAR),
        PULLING("pulling", Interpolation.STEP),
        CHARGED("charged", Interpolation.STEP),
        ARROW("arrow", Interpolation.STEP);

        public final String key;
        final Interpolation interpolation;

        Channel(String key, Interpolation interpolation) {
            this.key = key;
            this.interpolation = interpolation;
        }

        static Channel find(String key) {
            for (Channel channel : values()) {
                if (channel.key.equals(key)) {
                    return channel;
                }
            }
            throw new IllegalArgumentException("item_states 未知通道: " + key);
        }
    }

    /** 一条通道：剪辑秒（严格递增）与对应值。 */
    public record Key(float time, float value) { }

    public record Track(List<Key> keys, Interpolation interpolation) {
        public Track {
            keys = List.copyOf(keys);
        }

        public float sample(float time) {
            // 首键前取首值、末键后保持；阶跃通道取不晚于当前时刻的最后一个键，pull 在相邻键之间线性插值。
            Key previous = keys.getFirst();
            if (time <= previous.time()) {
                return previous.value();
            }
            for (int index = 1; index < keys.size(); index++) {
                Key next = keys.get(index);
                if (time < next.time()) {
                    if (interpolation == Interpolation.STEP) {
                        return previous.value();
                    }
                    float amount = (time - previous.time()) / (next.time() - previous.time());
                    return previous.value() + (next.value() - previous.value()) * amount;
                }
                previous = next;
            }
            return previous.value();
        }
    }

    /** 该通道在剪辑时间 time 的值；索引没写这个通道时返回 null，由调用方沿用原版。 */
    public Float value(Channel channel, float time) {
        Track track = tracks.get(channel);
        if (track == null) {
            return null;
        }
        return track.sample(time);
    }

    public static ItemStates parse(JsonElement raw, float duration) {
        // 资源边界校验一次：非空对象、通道名、每条至少一个键、时间在剪辑内且严格递增、值域按通道区分。
        if (!(raw instanceof JsonObject object) || object.size() == 0) {
            throw new IllegalArgumentException("item_states 必须是非空对象");
        }
        Map<Channel, Track> tracks = new EnumMap<>(Channel.class);
        for (var entry : object.entrySet()) {
            Channel channel = Channel.find(entry.getKey());
            if (!(entry.getValue() instanceof JsonArray keys) || keys.isEmpty()) {
                throw new IllegalArgumentException("item_states." + channel.key + " 必须是非空 [[秒, 值], ...]");
            }
            tracks.put(channel, readTrack(channel, keys, duration));
        }
        return new ItemStates(tracks);
    }

    private static Track readTrack(Channel channel, JsonArray raw, float duration) {
        // 每个键把时刻和值保存在一起，不能在加载后被外部数组修改或错配。
        List<Key> keys = new ArrayList<>();
        float previous = -1;
        for (JsonElement element : raw) {
            if (!(element instanceof JsonArray key) || key.size() != KEY_COMPONENTS) {
                throw new IllegalArgumentException("item_states." + channel.key + " 的键必须是 [秒, 值]");
            }
            float time = JsonInput.number(key.get(0));
            float value = JsonInput.number(key.get(1));
            if (time <= previous || time < 0 || time > duration) {
                throw new IllegalArgumentException("item_states." + channel.key + " 时间必须在剪辑内且严格递增: " + time);
            }
            boolean invalid;
            if (channel.interpolation == Interpolation.STEP) {
                invalid = value != 0 && value != 1;
            } else {
                invalid = value < 0 || value > 1;
            }
            if (invalid) {
                throw new IllegalArgumentException("item_states." + channel.key + " 值超出范围: " + value);
            }
            keys.add(new Key(time, value));
            previous = time;
        }
        return new Track(keys, channel.interpolation);
    }
}

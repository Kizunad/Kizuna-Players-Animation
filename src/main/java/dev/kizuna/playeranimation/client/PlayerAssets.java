package dev.kizuna.playeranimation.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.kizuna.playeranimation.animation.AnimationClip;
import dev.kizuna.playeranimation.animation.AnimationEntry;
import dev.kizuna.playeranimation.animation.JsonInput;
import dev.kizuna.playeranimation.model.Skeleton;
import dev.kizuna.playeranimation.model.SkinnedMesh;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

public final class PlayerAssets implements SimpleSynchronousResourceReloadListener {
    public record Model(Skeleton skeleton, SkinnedMesh mesh) { }
    private Model classic;
    private Model slim;
    private Map<String, AnimationEntry> animations = Map.of();

    @Override
    public Identifier getFabricId() {
        return id("player_assets");
    }

    public static Identifier id(String path) {
        return new Identifier(PlayerAnimationClient.ID, path);
    }

    public Model model(boolean thinArms) {
        return thinArms ? slim : classic;
    }

    public Map<String, AnimationEntry> animations() { return animations; }

    @Override
    public void reload(ResourceManager manager) {
        // 热重载原子替换资源；失败时清空旧播放，避免跨版本骨架数据混用。
        PlayerAnimationClient.PLAYBACK.clear();
        ArmorMesh.clearCache();
        classic = null;
        slim = null;
        animations = Map.of();
        Identifier geometryId = id("geo/player_skeleton.geo.json");
        Identifier indexId = id("player_anim_index.json");
        if (manager.getResource(geometryId).isEmpty() && manager.getResource(indexId).isEmpty()) {
            PlayerAnimationClient.LOG.info("尚未提供玩家骨架和动画资源，保留原版玩家渲染");
            return;
        }
        try {
            JsonObject geometry = read(manager, geometryId);
            Skeleton classicSkeleton = Skeleton.parse(geometry, "geometry.player.classic");
            Skeleton slimSkeleton = Skeleton.parse(geometry, "geometry.player.slim");
            Model newClassic = new Model(classicSkeleton, new SkinnedMesh(classicSkeleton));
            Model newSlim = new Model(slimSkeleton, new SkinnedMesh(slimSkeleton));
            Map<String, AnimationEntry> entries = new LinkedHashMap<>();
            Map<Identifier, JsonObject> files = new LinkedHashMap<>();
            JsonObject index = read(manager, indexId);
            for (var element : JsonInput.array(index, "animations")) {
                JsonObject raw = element.getAsJsonObject();
                String name = JsonInput.string(raw, "id");
                Identifier file = id(JsonInput.string(raw, "file"));
                // 同文件的多个动作只解析一次 JSON，但各自保持独立播放参数。
                if (!files.containsKey(file)) {
                    files.put(file, read(manager, file));
                }
                AnimationClip clip = AnimationClip.parse(files.get(file),
                        "animation." + PlayerAnimationClient.ID + ".player." + name,
                        message -> PlayerAnimationClient.LOG.error("{} [{}]: {}", file, name, message));
                AnimationEntry entry = AnimationEntry.parse(raw, clip);
                if (entries.put(name, entry) != null) {
                    throw new IllegalArgumentException("动画 id 重复: " + name);
                }
            }
            classic = newClassic;
            slim = newSlim;
            animations = Map.copyOf(entries);
            PlayerAnimationClient.LOG.info("已加载玩家双体型骨架与 {} 个动画", animations.size());
        } catch (IOException | JsonParseException | IllegalArgumentException | IllegalStateException exception) {
            PlayerAnimationClient.LOG.error("玩家资源加载失败 [{} / {}]，已停用自定义玩家渲染", geometryId, indexId, exception);
        }
    }

    private static JsonObject read(ResourceManager manager, Identifier id) throws IOException {
        // Reader 与资源生命周期在加载边界关闭，不在渲染线程持有文件句柄。
        try (var reader = manager.getResourceOrThrow(id).getReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}

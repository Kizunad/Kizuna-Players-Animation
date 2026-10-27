package dev.kizuna.playeranimation.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kizuna.playeranimation.model.Skeleton;
import dev.kizuna.playeranimation.model.SkinnedMesh;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** PA 内部保存附属注册的实体几何；注册内容只在客户端渲染线程使用。 */
public final class MobAnimationRegistry {
    public record Model(PlayerAssets.Model body, SkinnedMesh overlay) { }

    private static final Map<Identifier, Model> ENTITY_MODELS = new HashMap<>();

    private MobAnimationRegistry() { }

    public record Registration(String geometryId, byte[] geometryJson, List<String> entityTypes,
                               String overlayGeometryId) { }

    public static void register(Registration registration) {
        String geometryId = registration.geometryId();
        byte[] geometryJson = registration.geometryJson();
        if (geometryId == null || geometryId.isBlank() || geometryJson.length == 0) {
            throw new IllegalArgumentException("几何注册缺少 ID 或 JSON");
        }
        String document = new String(geometryJson, StandardCharsets.UTF_8);
        var json = JsonParser.parseString(document).getAsJsonObject();
        Skeleton skeleton = Skeleton.parseEntity(json, geometryId);
        SkinnedMesh mesh = new SkinnedMesh(skeleton);
        SkinnedMesh overlay = overlay(json, skeleton, mesh, registration.overlayGeometryId());
        Model model = new Model(new PlayerAssets.Model(skeleton, mesh), overlay);
        // 先完成全部校验再替换绑定，失败的注册不能留下半份实体映射。
        List<Identifier> entityTypes = new ArrayList<>();
        for (String type : registration.entityTypes()) {
            Identifier identifier = Identifier.tryParse(type);
            if (identifier == null || !Registries.ENTITY_TYPE.containsId(identifier)) {
                throw new IllegalArgumentException("未知实体类型: " + type);
            }
            entityTypes.add(identifier);
        }
        for (Identifier type : entityTypes) {
            ENTITY_MODELS.put(type, model);
        }
    }

    private static SkinnedMesh overlay(JsonObject json, Skeleton skeleton, SkinnedMesh mesh,
                                       String overlayId) {
        // 外层使用本体同一帧的骨矩阵，绑定枢轴必须一致。
        if (!overlayId.isEmpty()) {
            Skeleton overlaySkeleton = Skeleton.parseEntity(json, overlayId);
            for (var bone : skeleton.bones()) {
                if (!bone.pivot().equals(overlaySkeleton.bone(bone.id()).pivot())) {
                    throw new IllegalArgumentException("外层枢轴与本体不同: " + bone.id().key);
                }
            }
            return new SkinnedMesh(overlaySkeleton);
        }
        return mesh;
    }

    public static Model model(LivingEntity entity) {
        Identifier type = Registries.ENTITY_TYPE.getId(entity.getType());
        return ENTITY_MODELS.get(type);
    }
}

package dev.kizuna.playeranimation.client;

import com.google.gson.JsonParser;
import dev.kizuna.playeranimation.model.BoneId;
import dev.kizuna.playeranimation.model.Pose;
import dev.kizuna.playeranimation.model.Rig;
import dev.kizuna.playeranimation.model.Skeleton;
import dev.kizuna.playeranimation.model.SkinnedMesh;
import dev.kizuna.playeranimation.model.Vec3;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.ArmorEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.resource.metadata.AnimationResourceMetadata;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.client.texture.SpriteDimensions;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArmorMeshTest {
    private static PlayerAssets.Model assets(boolean slim) throws Exception {
        // 骨架取实际交付文件，盔甲取 Minecraft 原版模型，防止两套测试夹具互相自洽。
        try (var reader = new InputStreamReader(ArmorMeshTest.class.getResourceAsStream(
                "/assets/kizuna_players_animation/geo/player_skeleton.geo.json"), StandardCharsets.UTF_8)) {
            Skeleton skeleton = Skeleton.parse(JsonParser.parseReader(reader).getAsJsonObject(),
                    "geometry.player." + (slim ? "slim" : "classic"));
            return new PlayerAssets.Model(skeleton, new SkinnedMesh(skeleton));
        }
    }

    private static ArmorEntityModel<LivingEntity> armor(float dilation) {
        return new ArmorEntityModel<>(ArmorEntityModel.getModelData(new Dilation(dilation))
                .getRoot().createPart(64, 32));
    }

    private static ModelPart part(ArmorEntityModel<?> armor, SkinnedMesh.Surface surface) {
        return switch (surface.bone()) {
            case HEAD -> surface.outer() ? armor.hat : armor.head;
            case PELVIS -> armor.body;
            case R_UPPER_ARM -> armor.rightArm;
            case L_UPPER_ARM -> armor.leftArm;
            case R_THIGH -> armor.rightLeg;
            case L_THIGH -> armor.leftLeg;
            default -> throw new AssertionError(surface.bone());
        };
    }

    @Test
    void bindAndVanillaPosesPreserveRealArmorGeometryUvsAndMirroredNormals() throws Exception {
        for (boolean slim : new boolean[]{false, true}) {
            PlayerAssets.Model assets = assets(slim);
            for (float dilation : new float[]{0.5f, 1}) {
                for (boolean posed : new boolean[]{false, true}) {
                    var armor = armor(dilation);
                    PlayerEntityModel<LivingEntity> player = new PlayerEntityModel<>(PlayerEntityModel
                            .getTexturedModelData(Dilation.NONE, slim).getRoot().createPart(64, 64), slim);
                    if (posed) {
                        // 原版蹲伏、抬手与走路的混合姿态，包括非零枢轴位移和三轴旋转。
                        player.body.pitch = 0.4f;
                        player.body.pivotY += 2;
                        player.head.yaw = -0.3f;
                        player.rightArm.pitch = -1.1f;
                        player.rightArm.yaw = 0.3f;
                        player.rightArm.roll = -0.2f;
                        player.rightArm.pivotZ += 1.4f;
                        player.leftArm.pitch = 0.7f;
                        player.rightLeg.pitch = 0.6f;
                        player.leftLeg.pitch = -0.6f;
                    }
                    player.hat.copyTransform(player.head);
                    player.copyBipedStateTo(armor);
                    ArmorMesh mesh = new ArmorMesh(armor, assets);
                    var frame = new PlayerMeshRenderer.Frame(assets, Rig.matrices(assets.skeleton(),
                            PlayerMeshRenderer.basePose(assets.skeleton(), player, false, slim)));
                    for (var surface : mesh.surfaces()) {
                        var expected = new ArmorMesh.QuadCollector();
                        part(armor, surface).render(new MatrixStack(), expected, 0, 0);
                        var actual = new ArmorMesh.QuadCollector();
                        PlayerMeshRenderer.draw(frame, List.of(surface), new MatrixStack(), actual,
                                0, 0, 1, 1, 1, 1, ignored -> true);
                        assertFalse(expected.corners.isEmpty());
                        assertTrue(actual.corners.size() > expected.corners.size());
                        for (var vertex : actual.corners) {
                            assertTrue(onVanillaFace(vertex, expected.corners),
                                    () -> "原版几何/UV 不一致: " + surface.bone() + " slim=" + slim
                                            + " dilation=" + dilation + " posed=" + posed + " " + vertex);
                        }
                        // 细分不能漏掉原版面的角点，帽层及镜像左侧同样参与比较。
                        for (var corner : expected.corners) {
                            assertTrue(actual.corners.stream().anyMatch(candidate -> matches(candidate, corner)));
                        }
                    }
                }
            }
        }
    }

    private static boolean onVanillaFace(ArmorMesh.Corner vertex, List<ArmorMesh.Corner> faces) {
        // 用原版提交的 UV 求四边形参数，再反算几何；独立检查细分顶点而非比较包围盒。
        for (int i = 0; i < faces.size(); i += 4) {
            var a = faces.get(i);
            var b = faces.get(i + 1);
            var c = faces.get(i + 2);
            var d = faces.get(i + 3);
            float bu = b.u() - a.u(), bv = b.v() - a.v();
            float du = d.u() - a.u(), dv = d.v() - a.v();
            float det = bu * dv - bv * du;
            float u = ((vertex.u() - a.u()) * dv - (vertex.v() - a.v()) * du) / det;
            float v = (bu * (vertex.v() - a.v()) - bv * (vertex.u() - a.u())) / det;
            if (u < -0.00001 || u > 1.00001 || v < -0.00001 || v > 1.00001) {
                continue;
            }
            Vec3 position = a.position().lerp(b.position(), u).lerp(d.position().lerp(c.position(), u), v);
            if (near(position, vertex.position()) && near(a.normal(), vertex.normal())) {
                return true;
            }
        }
        return false;
    }

    @Test
    void armorTrimKeepsAtlasUvsThroughVanillaSpriteConsumer() throws Exception {
        var assets = assets(false);
        var mesh = new ArmorMesh(armor(1), assets);
        Pose[] pose = Rig.zeroPose();
        pose[BoneId.R_FOREARM.ordinal()] = new Pose(Vec3.ZERO, new Vec3(75, 10, 5));
        var frame = new PlayerMeshRenderer.Frame(assets, Rig.matrices(assets.skeleton(), pose));
        var stack = new MatrixStack();
        stack.translate(0.3, 0.7, -0.4);
        stack.scale(-1, -1, 1);
        var plain = new ArmorMesh.QuadCollector();
        PlayerMeshRenderer.draw(frame, mesh.surfaces(), stack, plain, 0, 0, 1, 1, 1, 1, surface -> true);
        // 真实图集包装器的单属性调用会返回底层缓冲；链式提交不能绕过它的 UV 映射。
        try (var image = new NativeImage(64, 32, false)) {
            var contents = new SpriteContents(new Identifier("kpa_test", "trim"), new SpriteDimensions(64, 32),
                    image, AnimationResourceMetadata.EMPTY);
            var sprite = new Sprite(new Identifier("kpa_test", "atlas"), contents, 512, 256, 128, 64) { };
            var trimmed = new ArmorMesh.QuadCollector();
            PlayerMeshRenderer.draw(frame, mesh.surfaces(), stack, sprite.getTextureSpecificVertexConsumer(trimmed),
                    0, 0, 1, 1, 1, 1, surface -> true);
            assertEquals(plain.corners.size(), trimmed.corners.size());
            for (int i = 0; i < plain.corners.size(); i++) {
                var source = plain.corners.get(i);
                var actual = trimmed.corners.get(i);
                assertTrue(near(source.position(), actual.position()));
                assertTrue(near(source.normal(), actual.normal()));
                assertEquals(sprite.getFrameU(source.u() * 16), actual.u(), 0.00001,
                        "纹饰 U 必须经过图集映射");
                assertEquals(sprite.getFrameV(source.v() * 16), actual.v(), 0.00001,
                        "纹饰 V 必须经过图集映射");
            }
        }
    }

    private static boolean matches(ArmorMesh.Corner a, ArmorMesh.Corner b) {
        return near(a.position(), b.position()) && near(a.normal(), b.normal())
                && Math.abs(a.u() - b.u()) < 0.00001 && Math.abs(a.v() - b.v()) < 0.00001;
    }

    private static boolean near(Vec3 a, Vec3 b) {
        return Math.abs(a.x() - b.x()) < 0.0001 && Math.abs(a.y() - b.y()) < 0.0001
                && Math.abs(a.z() - b.z()) < 0.0001;
    }

    private record Point(long x, long y, long z) implements Comparable<Point> {
        static Point of(Vector3f p) {
            return new Point(Math.round(p.x * 1000), Math.round(p.y * 1000), Math.round(p.z * 1000));
        }
        @Override public int compareTo(Point other) {
            int order = Long.compare(x, other.x);
            if (order == 0) {
                order = Long.compare(y, other.y);
            }
            return order == 0 ? Long.compare(z, other.z) : order;
        }
    }
    private record Edge(Point a, Point b) {
        static Edge of(Point a, Point b) { return a.compareTo(b) <= 0 ? new Edge(a, b) : new Edge(b, a); }
    }

    @Test
    void bentArmorKeepsClosedSeamsAndRespondsToIndependentJoints() throws Exception {
        for (boolean slim : new boolean[]{false, true}) {
            var assets = assets(slim);
            var mesh = new ArmorMesh(armor(1), assets);
            Pose[] pose = Rig.zeroPose();
            for (BoneId bone : new BoneId[]{BoneId.WAIST, BoneId.CHEST, BoneId.R_FOREARM, BoneId.L_FOREARM,
                    BoneId.R_HAND, BoneId.L_HAND, BoneId.R_SHIN, BoneId.L_SHIN, BoneId.R_FOOT, BoneId.L_FOOT,
                    BoneId.R_TOE, BoneId.L_TOE}) {
                pose[bone.ordinal()] = new Pose(Vec3.ZERO, new Vec3(bone.arm() ? 85 : 35, 10, 5));
            }
            Matrix4f[] matrices = Rig.matrices(assets.skeleton(), pose);
            for (var surface : mesh.surfaces()) {
                Map<Edge, Integer> edges = new HashMap<>();
                var vertices = surface.vertices();
                for (int i = 0; i < vertices.size(); i += 4) {
                    for (int j = 0; j < 4; j++) {
                        var a = vertices.get(i + j);
                        var b = vertices.get(i + (j + 1) % 4);
                        Point pa = Point.of(SkinnedMesh.deform(a.position(), a.weights(), matrices, false, new Vector3f()));
                        Point pb = Point.of(SkinnedMesh.deform(b.position(), b.weights(), matrices, false, new Vector3f()));
                        edges.merge(Edge.of(pa, pb), 1, Integer::sum);
                    }
                }
                assertTrue(edges.values().stream().allMatch(count -> count % 2 == 0),
                        "弯折后出现裂口: " + surface.bone() + " slim=" + slim);
            }
            for (BoneId joint : new BoneId[]{BoneId.WAIST, BoneId.CHEST, BoneId.R_FOREARM, BoneId.L_FOREARM,
                    BoneId.R_HAND, BoneId.L_HAND, BoneId.R_SHIN, BoneId.L_SHIN}) {
                Pose[] isolated = Rig.zeroPose();
                isolated[joint.ordinal()] = new Pose(Vec3.ZERO, new Vec3(75, 0, 0));
                var transforms = Rig.matrices(assets.skeleton(), isolated);
                assertTrue(mesh.surfaces().stream().flatMap(s -> s.vertices().stream()).anyMatch(vertex -> {
                    // 必须存在实际受该独立关节驱动的盔甲顶点，刚体跟随肩/髋无法通过。
                    Vector3f moved = SkinnedMesh.deform(vertex.position(), vertex.weights(), transforms, false, new Vector3f());
                    return vertex.weights()[joint.ordinal()] > 0.99 && moved.distance(vertex.position().x(),
                            vertex.position().y(), vertex.position().z()) > 0.5f;
                }), "盔甲未接入关节: " + joint);
            }
        }
    }

    @Test
    void slotVisibilityAndResourceReloadDoNotLeakBetweenDraws() throws Exception {
        var assets = assets(false);
        var armor = armor(1);
        ArmorMesh mesh = ArmorMesh.cached(armor, assets);
        assertSame(mesh, ArmorMesh.cached(armor, assets));
        // 模拟穿靴事件与第一人称隐藏：缓存几何不能记住某次绘制的显隐。
        armor.setVisible(false);
        armor.rightLeg.visible = true;
        assertEquals(List.of(BoneId.R_THIGH), mesh.surfaces().stream()
                .filter(surface -> ArmorMesh.visible(armor, surface)).map(SkinnedMesh.Surface::bone).toList());
        armor.setVisible(true);
        assertEquals(7, mesh.surfaces().stream().filter(surface -> ArmorMesh.visible(armor, surface)).count());
        assertNotSame(mesh, ArmorMesh.cached(armor, assets(true)));
        var reloaded = ArmorMesh.cached(armor, assets);
        ArmorMesh.clearCache();
        assertNotSame(reloaded, ArmorMesh.cached(armor, assets));
    }
}

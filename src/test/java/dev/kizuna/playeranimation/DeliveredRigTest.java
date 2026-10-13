package dev.kizuna.playeranimation;

import com.google.gson.JsonParser;
import dev.kizuna.playeranimation.client.PlayerMeshRenderer;
import dev.kizuna.playeranimation.model.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeliveredRigTest {
    private record Point(long x, long y, long z) {
        static Point of(Vector3f point) {
            // 合并浮点运算误差内的同一顶点，单位仍为模型像素。
            return new Point(Math.round(point.x * 10000), Math.round(point.y * 10000),
                    Math.round(point.z * 10000));
        }
    }

    private record Edge(Point a, Point b) {
        static Edge of(Point a, Point b) {
            // 无向边按坐标排序，便于统计相邻面是否共同封住边界。
            int order = Long.compare(a.x, b.x);
            if (order == 0) {
                order = Long.compare(a.y, b.y);
            }
            if (order == 0) {
                order = Long.compare(a.z, b.z);
            }
            return order <= 0 ? new Edge(a, b) : new Edge(b, a);
        }
    }

    private static Skeleton skeleton(boolean slim) throws Exception {
        // 直接检查实际交付的资源，避免数学夹具与打包模型脱节。
        try (var reader = new InputStreamReader(DeliveredRigTest.class.getResourceAsStream(
                "/assets/kizuna_players_animation/geo/player_skeleton.geo.json"), StandardCharsets.UTF_8)) {
            return Skeleton.parse(JsonParser.parseReader(reader).getAsJsonObject(),
                    "geometry.player." + (slim ? "slim" : "classic"));
        }
    }

    @Test
    void independentlyCurledFingersHaveNoOpenCutEdgesInEitherSkinLayer() throws Exception {
        for (boolean slim : new boolean[]{false, true}) {
            Skeleton skeleton = skeleton(slim);
            SkinnedMesh mesh = new SkinnedMesh(skeleton);
            for (String side : new String[]{"r", "l"}) {
                BoneId hand = BoneId.named(side + "_hand");
                Pose[] pose = Rig.zeroPose();
                float sign = side.equals("r") ? 1 : -1;
                pose[BoneId.named(side + "_thumb").ordinal()] = new Pose(Vec3.ZERO, new Vec3(0, 0, sign * 90));
                pose[BoneId.named(side + "_fingers_a").ordinal()] = new Pose(Vec3.ZERO, new Vec3(0, 0, sign * 35));
                pose[BoneId.named(side + "_fingers_b").ordinal()] = new Pose(Vec3.ZERO, new Vec3(0, 0, sign * 10));
                Matrix4f[] matrices = Rig.matrices(skeleton, pose);
                for (boolean outer : new boolean[]{false, true}) {
                    Map<Edge, Integer> edges = new HashMap<>();
                    float wristY = Float.NEGATIVE_INFINITY;
                    for (SkinnedMesh.Surface surface : mesh.surfaces()) {
                        if (surface.outer() != outer || surface.bone().socket()
                                || surface.bone() != hand && surface.bone().parent != hand) {
                            continue;
                        }
                        var vertices = surface.vertices();
                        for (int i = 0; i < vertices.size(); i += 4) {
                            for (int j = 0; j < 4; j++) {
                                var a = vertices.get(i + j);
                                var b = vertices.get(i + (j + 1) % 4);
                                wristY = Math.max(wristY, a.position().y());
                                var first = SkinnedMesh.deform(a.position(), a.weights(), matrices, false, new Vector3f());
                                var second = SkinnedMesh.deform(b.position(), b.weights(), matrices, false, new Vector3f());
                                edges.merge(Edge.of(Point.of(first), Point.of(second)), 1, Integer::sum);
                            }
                        }
                    }
                    long wrist = Math.round(wristY * 10000);
                    assertTrue(!edges.isEmpty());
                    for (var entry : edges.entrySet()) {
                        // 手掌顶面连接前臂，除此之外不能存在只被奇数个面使用的裸边。
                        Edge edge = entry.getKey();
                        assertTrue(entry.getValue() % 2 == 0 || edge.a.y == wrist && edge.b.y == wrist,
                                "手指出现裸边: " + side + " slim=" + slim + " outer=" + outer + " " + edge);
                    }
                }
            }
        }
    }

    @Test
    void bothHeldItemSocketsMatchVanillaIncludingSlimShoulderCorrection() throws Exception {
        for (boolean slim : new boolean[]{false, true}) {
            Skeleton skeleton = skeleton(slim);
            PlayerEntityModel<?> model = new PlayerEntityModel<>(PlayerEntityModel
                    .getTexturedModelData(Dilation.NONE, slim).getRoot().createPart(64, 64), slim);
            for (float pitch : new float[]{0, -(float) Math.PI / 10, -1.2f}) {
                // 混合旋转与肩部位移覆盖站姿、抬手和姿态变化，比较完整物品矩阵。
                model.rightArm.pitch = model.leftArm.pitch = pitch;
                model.rightArm.yaw = 0.2f;
                model.leftArm.roll = -0.3f;
                model.rightArm.pivotZ = 1.4f;
                model.body.pitch = 0.4f;
                Matrix4f[] matrices = Rig.matrices(skeleton, PlayerMeshRenderer.basePose(skeleton, model, false, slim));
                for (Arm arm : Arm.values()) {
                    MatrixStack vanilla = new MatrixStack();
                    model.setArmAngle(arm, vanilla);
                    vanilla.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90));
                    vanilla.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
                    vanilla.translate((arm == Arm.RIGHT ? 1 : -1) / 16f, 0.125, -0.625);
                    BoneId socket = arm == Arm.RIGHT ? BoneId.R_ITEM : BoneId.L_ITEM;
                    Matrix4f actual = Rig.itemTransform(skeleton, matrices, socket);
                    assertTrue(vanilla.peek().getPositionMatrix().equals(actual, 0.00001f),
                            "持物偏移: slim=" + slim + " arm=" + arm + " pitch=" + pitch);
                }
            }
        }
    }

    @Test
    void slimCorrectionFollowsRootAndWristAnimation() throws Exception {
        Skeleton skeleton = skeleton(true);
        PlayerEntityModel<?> model = new PlayerEntityModel<>(PlayerEntityModel
                .getTexturedModelData(Dilation.NONE, true).getRoot().createPart(64, 64), true);
        model.rightArm.yaw = 0.3f;
        Pose[] pose = PlayerMeshRenderer.basePose(skeleton, model, false, true);
        pose[BoneId.R_HAND.ordinal()] = new Pose(Vec3.ZERO, new Vec3(35, 0, 20));
        Matrix4f item = Rig.itemTransform(skeleton, Rig.matrices(skeleton, pose), BoneId.R_ITEM);
        // 整体动画应刚性带走已弯曲手腕的物品，不能在世界 X 方向重新补偏移。
        pose[BoneId.ROOT.ordinal()] = new Pose(new Vec3(2, 3, -1), new Vec3(10, 55, 20));
        Matrix4f[] moved = Rig.matrices(skeleton, pose);
        Matrix4f expected = Rig.toVanilla(moved[BoneId.ROOT.ordinal()]).mul(item);
        assertTrue(expected.equals(Rig.itemTransform(skeleton, moved, BoneId.R_ITEM), 0.00001f));
    }
}

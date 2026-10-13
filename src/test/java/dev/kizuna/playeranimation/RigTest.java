package dev.kizuna.playeranimation;

import dev.kizuna.playeranimation.model.BoneId;
import dev.kizuna.playeranimation.model.Pose;
import dev.kizuna.playeranimation.model.Rig;
import dev.kizuna.playeranimation.model.Skeleton;
import dev.kizuna.playeranimation.model.SkinnedMesh;
import dev.kizuna.playeranimation.model.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RigTest {
    private static Skeleton skeleton() {
        List<Skeleton.Bone> bones = new ArrayList<>();
        // 这是接缝回归用几何，不作为交付模型；覆盖肘、腕、踝和脚尖的连续链。
        for (BoneId bone : BoneId.ALL) {
            Vec3 pivot = switch (bone) {
                case WAIST -> new Vec3(0, 16, 0);
                case CHEST -> new Vec3(0, 20, 0);
                case R_UPPER_ARM -> new Vec3(-5, 22, 0);
                case R_FOREARM -> new Vec3(-6, 19, 0);
                case R_HAND -> new Vec3(-6, 15, 0);
                case R_SHIN -> new Vec3(-1.9f, 6, 0);
                case R_FOOT -> new Vec3(-1.9f, 1, 0);
                case R_TOE -> new Vec3(-1.9f, 0, 0);
                default -> Vec3.ZERO;
            };
            Skeleton.Cube cube = switch (bone) {
                case R_UPPER_ARM -> cube(-8, 19, -2, 4, 5, 4, 0);
                case R_FOREARM -> cube(-8, 15, -2, 4, 4, 4, 0);
                case R_HAND -> cube(-8, 14, -2, 4, 1, 4, 0);
                case R_SHIN -> cube(-3.9f, 1, -2, 4, 5, 4, 0);
                case R_FOOT -> cube(-3.9f, 0, 0, 4, 1, 2, 0);
                case R_TOE -> cube(-3.9f, 0, -2, 4, 1, 2, 0);
                default -> null;
            };
            List<Skeleton.Cube> cubes = cube == null ? List.of() : List.of(cube,
                    new Skeleton.Cube(cube.min(), cube.size(), 0.25f, cube.faces()));
            bones.add(new Skeleton.Bone(bone, pivot, cubes));
        }
        return new Skeleton(List.copyOf(bones));
    }

    private static Skeleton.Cube cube(float x, float y, float z, float width, float height, float depth, float inflate) {
        return new Skeleton.Cube(new Vec3(x, y, z), new Vec3(width, height, depth), inflate,
                Map.of(Skeleton.Face.NORTH, new Skeleton.Uv(0, 0, width, height, 0)));
    }

    @Test
    void interfaceHasTwentyFiveJointsAndTwoSockets() {
        assertEquals(27, BoneId.ALL.length);
        assertEquals(2, java.util.Arrays.stream(BoneId.ALL).filter(BoneId::socket).count());
        assertEquals(BoneId.PELVIS, BoneId.R_THIGH.parent);
        assertEquals(BoneId.CHEST, BoneId.HEAD.parent);
        assertEquals(BoneId.R_HAND, BoneId.R_ITEM.parent);
    }

    @Test
    void zeroPoseLeavesEveryVertexInBindPosition() {
        Skeleton skeleton = skeleton();
        SkinnedMesh mesh = new SkinnedMesh(skeleton);
        Matrix4f[] matrices = Rig.matrices(skeleton, Rig.zeroPose());
        for (SkinnedMesh.Surface surface : mesh.surfaces()) {
            for (SkinnedMesh.Vertex vertex : surface.vertices()) {
                Vector3f actual = SkinnedMesh.deform(vertex.position(), vertex.weights(), matrices, false, new Vector3f());
                assertEquals(vertex.position().x(), actual.x, 0.00001);
                assertEquals(vertex.position().y(), actual.y, 0.00001);
                assertEquals(vertex.position().z(), actual.z, 0.00001);
                float sum = 0;
                for (float weight : vertex.weights()) {
                    assertTrue(weight >= 0 && weight <= 1);
                    sum += weight;
                }
                assertEquals(1, sum, 0.00001);
            }
        }
    }

    @Test
    void waistMovesUpperBodyButDoesNotMoveLegs() {
        Skeleton skeleton = skeleton();
        Pose[] pose = Rig.zeroPose();
        pose[BoneId.WAIST.ordinal()] = new Pose(Vec3.ZERO, new Vec3(30, 0, 0));
        Matrix4f[] matrices = Rig.matrices(skeleton, pose);
        assertEquals(new Matrix4f(), matrices[BoneId.R_THIGH.ordinal()]);
        Vector3f upper = matrices[BoneId.R_HAND.ordinal()].transformPosition(-6, 14, 0, new Vector3f());
        assertNotEquals(0, upper.z);
    }

    @Test
    void simultaneousElbowAndWristBendsKeepBothCutEdgesJoined() {
        Skeleton skeleton = skeleton();
        SkinnedMesh mesh = new SkinnedMesh(skeleton);
        Pose[] pose = Rig.zeroPose();
        pose[BoneId.R_FOREARM.ordinal()] = new Pose(Vec3.ZERO, new Vec3(110, 25, 0));
        pose[BoneId.R_HAND.ordinal()] = new Pose(Vec3.ZERO, new Vec3(-60, 0, 20));
        Matrix4f[] matrices = Rig.matrices(skeleton, pose);
        assertJoined(mesh, matrices, BoneId.R_UPPER_ARM, BoneId.R_FOREARM, new Vec3(-8, 19, -2));
        assertJoined(mesh, matrices, BoneId.R_FOREARM, BoneId.R_HAND, new Vec3(-4, 15, 2));
    }

    @Test
    void ankleToeIntersectionInheritsTheParentsOwnSkinWeights() {
        Skeleton skeleton = skeleton();
        SkinnedMesh mesh = new SkinnedMesh(skeleton);
        Pose[] pose = Rig.zeroPose();
        pose[BoneId.R_FOOT.ordinal()] = new Pose(Vec3.ZERO, new Vec3(40, 0, 0));
        pose[BoneId.R_TOE.ordinal()] = new Pose(Vec3.ZERO, new Vec3(-30, 0, 0));
        Matrix4f[] matrices = Rig.matrices(skeleton, pose);
        assertJoined(mesh, matrices, BoneId.R_SHIN, BoneId.R_FOOT, new Vec3(-3.9f, 1, 0));
        assertJoined(mesh, matrices, BoneId.R_FOOT, BoneId.R_TOE, new Vec3(-3.9f, 1, 0));
        assertJoined(mesh, matrices, BoneId.R_FOOT, BoneId.R_TOE, new Vec3(0.1f, 0, 0));
    }

    @Test
    void innerAndOuterLayersShareWeightsAndPreserveUnsplitSkinPixelProportions() {
        SkinnedMesh mesh = new SkinnedMesh(skeleton());
        List<SkinnedMesh.Surface> surfaces = mesh.surfaces();
        for (int i = 0; i < surfaces.size(); i += 2) {
            SkinnedMesh.Surface inner = surfaces.get(i);
            SkinnedMesh.Surface outer = surfaces.get(i + 1);
            for (int v = 0; v < inner.vertices().size(); v++) {
                assertArrayEquals(inner.vertices().get(v).weights(), outer.vertices().get(v).weights());
                assertEquals(inner.vertices().get(v).u(), outer.vertices().get(v).u());
                assertEquals(inner.vertices().get(v).v(), outer.vertices().get(v).v());
                if (inner.bone().skinPart().equals("r_arm")) {
                    // 此夹具的完整手臂从 14 到 24；原版单块外层高度为 10.5 像素。
                    float originalY = inner.vertices().get(v).position().y();
                    float vanillaOuterY = 13.75f + (originalY - 14) * 10.5f / 10;
                    assertEquals(vanillaOuterY, outer.vertices().get(v).position().y(), 0.00001);
                }
            }
        }
    }

    @Test
    void mirrorIsMatrixReflectionRatherThanNegatingPitch() {
        Pose right = new Pose(new Vec3(1, 2, 3), new Vec3(20, 30, 40));
        Pose left = right.mirrored();
        Skeleton skeleton = skeleton();
        Pose[] pose = Rig.zeroPose();
        pose[BoneId.ROOT.ordinal()] = right;
        Matrix4f original = Rig.matrices(skeleton, pose)[BoneId.ROOT.ordinal()];
        pose[BoneId.ROOT.ordinal()] = left;
        Matrix4f mirrored = Rig.matrices(skeleton, pose)[BoneId.ROOT.ordinal()];
        Matrix4f reflection = new Matrix4f().scaling(-1, 1, 1);
        assertTrue(new Matrix4f(reflection).mul(original).mul(reflection).equals(mirrored, 0.00001f));
    }

    @Test
    void matrixPoseRoundTripPreservesCombinedRotationsAndPivotTranslations() {
        Skeleton skeleton = skeleton();
        Pose[] pose = Rig.zeroPose();
        for (float yaw : new float[]{30, 90, -90, 170}) {
            Pose original = new Pose(new Vec3(2, -3, 4), new Vec3(20, yaw, -40));
            pose[BoneId.R_FOREARM.ordinal()] = original;
            Matrix4f matrix = Rig.matrices(skeleton, pose)[BoneId.R_FOREARM.ordinal()];
            Pose recovered = Rig.localPose(matrix, skeleton.bone(BoneId.R_FOREARM).pivot());
            pose[BoneId.R_FOREARM.ordinal()] = recovered;
            assertTrue(matrix.equals(Rig.matrices(skeleton, pose)[BoneId.R_FOREARM.ordinal()], 0.00001f), "yaw=" + yaw);
        }
    }

    private static void assertJoined(SkinnedMesh mesh, Matrix4f[] matrices, BoneId parent, BoneId child, Vec3 point) {
        // 两侧使用独立权重求值，不能只比较同一个生成顶点的重复引用。
        Vector3f parentPosition = SkinnedMesh.deform(point, mesh.weights(parent, point), matrices, false, new Vector3f());
        Vector3f childPosition = SkinnedMesh.deform(point, mesh.weights(child, point), matrices, false, new Vector3f());
        assertTrue(parentPosition.equals(childPosition, 0.00001f), parent + " / " + child);
    }
}

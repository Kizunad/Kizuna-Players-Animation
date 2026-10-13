package dev.kizuna.playeranimation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kizuna.playeranimation.model.BoneId;
import dev.kizuna.playeranimation.model.Skeleton;
import dev.kizuna.playeranimation.model.SkinnedMesh;
import dev.kizuna.playeranimation.model.Vec3;
import java.util.List;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeometryTest {
    private static JsonObject document() {
        JsonObject document = JsonParser.parseString("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.player.classic","texture_width":64,"texture_height":64},
                  "bones":[]
                }]}
                """).getAsJsonObject();
        JsonArray bones = geometry(document).getAsJsonArray("bones");
        // 固定的右臂坐标与逐面 UV 用于验证导出转换，其他骨仅补齐契约。
        for (BoneId id : BoneId.ALL) {
            JsonObject bone = new JsonObject();
            bone.addProperty("name", id.key);
            if (id.parent != null) {
                bone.addProperty("parent", id.parent.key);
            }
            bone.add("pivot", JsonParser.parseString("[5,22,0]"));
            JsonArray cubes = new JsonArray();
            if (id != BoneId.ROOT && !id.socket()) {
                JsonObject cube = JsonParser.parseString("""
                        {"origin":[4,19,-2],"size":[4,5,4],"uv":{
                          "north":{"uv":[44,20],"uv_size":[4,5]},
                          "south":{"uv":[52,20],"uv_size":[4,5]},
                          "east":{"uv":[40,20],"uv_size":[4,5]},
                          "west":{"uv":[48,20],"uv_size":[4,5]},
                          "up":{"uv":[44,16],"uv_size":[4,4]},
                          "down":{"uv":[48,16],"uv_size":[4,4]}
                        }}
                        """).getAsJsonObject();
                cubes.add(cube);
                JsonObject outer = cube.deepCopy();
                outer.addProperty("inflate", id == BoneId.HEAD ? 0.5 : 0.25);
                cubes.add(outer);
            }
            bone.add("cubes", cubes);
            bones.add(bone);
        }
        return document;
    }

    private static JsonObject geometry(JsonObject document) {
        return document.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
    }

    @Test
    void exportedCoordinatesAndTopUvRecoverBlockbenchCoordinates() {
        Skeleton skeleton = Skeleton.parse(document(), "geometry.player.classic");
        Skeleton.Bone arm = skeleton.bone(BoneId.R_UPPER_ARM);
        assertEquals(new Vec3(-5, 22, 0), arm.pivot());
        Skeleton.Cube cube = arm.cubes().get(0);
        assertEquals(new Vec3(-8, 19, -2), cube.min());
        assertEquals(new Vec3(4, 5, 4), cube.size());
        assertEquals(new Skeleton.Uv(48, 20, -4, -4, 0), cube.faces().get(Skeleton.Face.UP));
        assertEquals(new Skeleton.Uv(44, 20, 4, 5, 0), cube.faces().get(Skeleton.Face.NORTH));
        assertEquals(0.25, arm.cubes().get(1).inflate());
    }

    @Test
    void variantsAreSelectedByGeometryIdWithoutScaling() {
        JsonObject document = document();
        JsonObject slim = geometry(document).deepCopy();
        slim.getAsJsonObject("description").addProperty("identifier", "geometry.player.slim");
        JsonObject arm = slim.getAsJsonArray("bones").get(BoneId.R_UPPER_ARM.ordinal()).getAsJsonObject();
        arm.add("pivot", JsonParser.parseString("[5.5,22,0]"));
        arm.getAsJsonArray("cubes").get(0).getAsJsonObject().add("size", JsonParser.parseString("[3,5,4]"));
        document.getAsJsonArray("minecraft:geometry").add(slim);
        Skeleton classic = Skeleton.parse(document, "geometry.player.classic");
        Skeleton thin = Skeleton.parse(document, "geometry.player.slim");
        assertEquals(4, classic.bone(BoneId.R_UPPER_ARM).cubes().get(0).size().x());
        assertEquals(3, thin.bone(BoneId.R_UPPER_ARM).cubes().get(0).size().x());
        assertEquals(-5.5, thin.bone(BoneId.R_UPPER_ARM).pivot().x());
    }

    @Test
    void absentFacesAreOmittedAndNegativeUvSpansRemainSigned() {
        JsonObject document = document();
        JsonObject cube = geometry(document).getAsJsonArray("bones").get(BoneId.HEAD.ordinal()).getAsJsonObject()
                .getAsJsonArray("cubes").get(0).getAsJsonObject();
        cube.getAsJsonObject("uv").remove("north");
        cube.getAsJsonObject("uv").getAsJsonObject("south").add("uv_size", JsonParser.parseString("[-4,5]"));
        Skeleton.Cube parsed = Skeleton.parse(document, "geometry.player.classic").bone(BoneId.HEAD).cubes().get(0);
        assertFalse(parsed.faces().containsKey(Skeleton.Face.NORTH));
        assertEquals(-4, parsed.faces().get(Skeleton.Face.SOUTH).width());
    }

    @Test
    void windingMatchesOutwardNormalsAfterJavaModelCoordinateConversion() {
        SkinnedMesh mesh = new SkinnedMesh(Skeleton.parse(document(), "geometry.player.classic"));
        for (SkinnedMesh.Surface surface : mesh.surfaces()) {
            List<SkinnedMesh.Vertex> vertices = surface.vertices();
            for (int i = 0; i < vertices.size(); i += 4) {
                Vector3f a = javaPosition(vertices.get(i).position());
                Vector3f b = javaPosition(vertices.get(i + 1).position()).sub(a);
                Vector3f c = javaPosition(vertices.get(i + 2).position()).sub(a);
                Vec3 normal = vertices.get(i).normal();
                assertTrue(b.cross(c).dot(new Vector3f(normal.x(), -normal.y(), normal.z())) > 0,
                        surface.bone() + " / " + normal);
            }
        }
    }

    @Test
    void missingBonesWrongParentsAndEmptyVisibleBonesAreRejected() {
        JsonObject missing = document();
        geometry(missing).getAsJsonArray("bones").remove(BoneId.R_FOREARM.ordinal());
        assertThrows(IllegalArgumentException.class, () -> Skeleton.parse(missing, "geometry.player.classic"));
        JsonObject wrong = document();
        geometry(wrong).getAsJsonArray("bones").get(BoneId.R_THIGH.ordinal()).getAsJsonObject().addProperty("parent", "chest");
        assertThrows(IllegalArgumentException.class, () -> Skeleton.parse(wrong, "geometry.player.classic"));
        JsonObject empty = document();
        geometry(empty).getAsJsonArray("bones").get(BoneId.HEAD.ordinal()).getAsJsonObject().add("cubes", new JsonArray());
        assertThrows(IllegalArgumentException.class, () -> Skeleton.parse(empty, "geometry.player.classic"));
    }

    @Test
    void boxUvAndMalformedResourcesFailAtTheLoadBoundary() {
        JsonObject boxUv = document();
        geometry(boxUv).getAsJsonArray("bones").get(BoneId.HEAD.ordinal()).getAsJsonObject()
                .getAsJsonArray("cubes").get(0).getAsJsonObject().add("uv", JsonParser.parseString("[0,0]"));
        assertThrows(IllegalArgumentException.class, () -> Skeleton.parse(boxUv, "geometry.player.classic"));
        JsonObject missing = JsonParser.parseString("{\"format_version\":\"1.12.0\"}").getAsJsonObject();
        assertThrows(IllegalArgumentException.class, () -> Skeleton.parse(missing, "geometry.player.classic"));
        assertThrows(IllegalArgumentException.class, () -> Skeleton.parse(document(), "geometry.player.missing"));
    }

    private static Vector3f javaPosition(Vec3 position) {
        // 行列式为负的 Y 反射必须同步反转四边形绕序。
        return new Vector3f(position.x(), 24 - position.y(), position.z());
    }
}

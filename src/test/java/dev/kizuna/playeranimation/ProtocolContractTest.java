package dev.kizuna.playeranimation;

import dev.kizuna.playeranimation.protocol.pb.PlayerAnimationProtocol.PlayRequest;
import dev.kizuna.playeranimation.protocol.pb.PlayerAnimationProtocol.Result;
import dev.kizuna.playeranimation.protocol.pb.PlayerAnimationProtocol.StopRequest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolContractTest {
    private static final String PLAYER = "00000000-0000-0000-0000-000000000001";

    @Test
    void playMatchesV1WireFixture() throws Exception {
        // 固定由独立 protoc --encode 生成的字节，拦住字段编号或类型的意外变更。
        byte[] wire = HexFormat.of().parseHex(
                "0a027231122430303030303030302d303030302d303030302d303030302d3030303030303030303030311a086d65646974617465");
        PlayRequest request = PlayRequest.newBuilder()
                .setRequestId("r1").setPlayerUuid(PLAYER).setAnimationId("meditate").build();
        assertEquals(request, PlayRequest.parseFrom(wire));
        assertArrayEquals(wire, request.toByteArray());
    }

    @Test
    void startTickIsAnOptionalAdditiveField() throws Exception {
        // 字段 4 使用 varint，旧请求不携带此字段；显式零与缺省必须可区分。
        PlayRequest missing = PlayRequest.parseFrom(new byte[0]);
        PlayRequest zero = PlayRequest.parseFrom(HexFormat.of().parseHex("2000"));
        PlayRequest timed = PlayRequest.parseFrom(HexFormat.of().parseHex("209003"));
        assertFalse(missing.hasStartTick());
        assertTrue(zero.hasStartTick());
        assertEquals(0, zero.getStartTick());
        assertEquals(400, timed.getStartTick());
        assertArrayEquals(HexFormat.of().parseHex("209003"), timed.toByteArray());
    }

    @Test
    void stopDistinguishesOneAnimationFromAll() throws Exception {
        // 两种停止范围保持独立的 wire tag，调用方不以空字符串隐式表示全部。
        byte[] oneWire = HexFormat.of().parseHex(
                "0a027331122430303030303030302d303030302d303030302d303030302d3030303030303030303030311a086d65646974617465");
        byte[] allWire = HexFormat.of().parseHex(
                "0a027332122430303030303030302d303030302d303030302d303030302d3030303030303030303030312001");
        StopRequest one = StopRequest.newBuilder()
                .setRequestId("s1").setPlayerUuid(PLAYER).setAnimationId("meditate").build();
        StopRequest all = StopRequest.newBuilder()
                .setRequestId("s2").setPlayerUuid(PLAYER).setAll(true).build();
        assertEquals(one, StopRequest.parseFrom(oneWire));
        assertEquals(all, StopRequest.parseFrom(allWire));
        assertArrayEquals(oneWire, one.toByteArray());
        assertArrayEquals(allWire, all.toByteArray());
        assertEquals(StopRequest.TargetCase.ANIMATION_ID, one.getTargetCase());
        assertEquals(StopRequest.TargetCase.ALL, all.getTargetCase());
    }

    @Test
    void absentFalseAndFutureStopTargetsRemainDistinguishable() throws Exception {
        // proto3 的 oneof 保留显式 false；缺失或未来目标不能被解析成 all=true。
        StopRequest absent = StopRequest.parseFrom(new byte[0]);
        StopRequest explicitFalse = StopRequest.parseFrom(HexFormat.of().parseHex("2000"));
        byte[] futureWire = HexFormat.of().parseHex("2a00");
        StopRequest future = StopRequest.parseFrom(futureWire);
        assertEquals(StopRequest.TargetCase.TARGET_NOT_SET, absent.getTargetCase());
        assertEquals(StopRequest.TargetCase.ALL, explicitFalse.getTargetCase());
        assertFalse(explicitFalse.getAll());
        assertEquals(StopRequest.TargetCase.TARGET_NOT_SET, future.getTargetCase());
        assertFalse(future.getAll());
        assertArrayEquals(futureWire, future.toByteArray());
    }

    @ParameterizedTest
    @CsvSource({
            "0a027231, CODE_UNSPECIFIED",
            "0a0272311001, OK",
            "0a0272311002, INVALID_REQUEST",
            "0a0272311003, ANIMATION_NOT_FOUND",
            "0a0272311004, PLAYER_NOT_LOADED"
    })
    void resultCodesMatchV1WireFixtures(String hex, Result.Code code) throws Exception {
        // 数值是跨语言契约，不能随 Java 枚举顺序调整；结果始终回传原请求 ID。
        byte[] wire = HexFormat.of().parseHex(hex);
        Result result = Result.newBuilder().setRequestId("r1").setCode(code).build();
        assertEquals(result, Result.parseFrom(wire));
        assertArrayEquals(wire, result.toByteArray());
    }

    @Test
    void resultPreservesDiagnosticsUnknownCodesAndUnknownFields() throws Exception {
        // 未知数值保留为 UNRECOGNIZED；诊断文字和未知字段可以跨版本传回调用方。
        byte[] rejectedWire = HexFormat.of().parseHex("0a02733310021a10616c6c206d7573742062652074727565");
        Result rejected = Result.newBuilder().setRequestId("s3").setCode(Result.Code.INVALID_REQUEST)
                .setDetail("all must be true").build();
        assertEquals(rejected, Result.parseFrom(rejectedWire));
        assertArrayEquals(rejectedWire, rejected.toByteArray());

        byte[] futureWire = HexFormat.of().parseHex("0a02723110637801");
        Result future = Result.parseFrom(futureWire);
        assertEquals("r1", future.getRequestId());
        assertEquals(Result.Code.UNRECOGNIZED, future.getCode());
        assertEquals(99, future.getCodeValue());
        assertArrayEquals(futureWire, future.toByteArray());
    }
}

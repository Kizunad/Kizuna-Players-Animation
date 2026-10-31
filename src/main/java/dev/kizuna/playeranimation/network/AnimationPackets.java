package dev.kizuna.playeranimation.network;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Fabric 载荷直接携带 Protobuf 字节，两端共用同一通道与大小限制。 */
public final class AnimationPackets {
    public static final String MOD_ID = "kizuna_players_animation";
    public static final CustomPacketPayload.Type<Payload> PLAY = type("play");
    public static final CustomPacketPayload.Type<Payload> STOP = type("stop");
    public static final CustomPacketPayload.Type<Payload> RESULT = type("result");
    public static final int MAX_BYTES = 32 * 1024;

    private AnimationPackets() { }

    /** 一份未解码的动画包；载荷只是剩余全部字节，不加长度前缀。 */
    public record Payload(CustomPacketPayload.Type<Payload> type, byte[] bytes) implements CustomPacketPayload { }

    public static void register() {
        // 两端都登记三条通道：play/stop 由服务端发往客户端，result 由客户端回执。
        PayloadTypeRegistry.clientboundPlay().register(PLAY, codec(PLAY));
        PayloadTypeRegistry.clientboundPlay().register(STOP, codec(STOP));
        PayloadTypeRegistry.serverboundPlay().register(RESULT, codec(RESULT));
    }

    public static <T extends MessageLite> T read(Payload payload, Parser<T> parser)
            throws InvalidProtocolBufferException {
        // 在解码前检查协议边界，避免把任意大小的自定义载荷交给解码器。
        if (payload.bytes().length > MAX_BYTES) {
            throw new IllegalArgumentException("动画包超过 32 KiB");
        }
        return parser.parseFrom(payload.bytes());
    }

    public static Payload write(CustomPacketPayload.Type<Payload> channel, MessageLite message) {
        // 每位接收者使用独立载荷，交由 Fabric 发包流程管理。
        if (message.getSerializedSize() > MAX_BYTES) {
            throw new IllegalArgumentException("动画包超过 32 KiB");
        }
        return new Payload(channel, message.toByteArray());
    }

    private static CustomPacketPayload.Type<Payload> type(String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(MOD_ID, path));
    }

    public static StreamCodec<FriendlyByteBuf, Payload> codec(CustomPacketPayload.Type<Payload> channel) {
        // 读出剩余全部字节，限长留给收包方检查并记日志，与旧版只丢弃坏包、不断开连接一致。
        return StreamCodec.of((buffer, payload) -> buffer.writeBytes(payload.bytes()), buffer -> {
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return new Payload(channel, bytes);
        });
    }
}

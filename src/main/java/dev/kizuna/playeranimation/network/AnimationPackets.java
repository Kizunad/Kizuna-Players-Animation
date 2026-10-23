package dev.kizuna.playeranimation.network;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/** Fabric 载荷直接携带 Protobuf 字节，两端共用同一通道与大小限制。 */
public final class AnimationPackets {
    public static final String MOD_ID = "kizuna_players_animation";
    public static final Identifier PLAY = new Identifier(MOD_ID, "play");
    public static final Identifier STOP = new Identifier(MOD_ID, "stop");
    public static final Identifier RESULT = new Identifier(MOD_ID, "result");
    public static final int MAX_BYTES = 32 * 1024;

    private AnimationPackets() { }

    public static <T extends MessageLite> T read(PacketByteBuf buffer, Parser<T> parser)
            throws InvalidProtocolBufferException {
        // 在分配数组前检查网络边界，避免把任意大小的自定义载荷交给解码器。
        int size = buffer.readableBytes();
        if (size > MAX_BYTES) {
            throw new IllegalArgumentException("动画包超过 32 KiB");
        }
        byte[] bytes = new byte[size];
        buffer.readBytes(bytes);
        return parser.parseFrom(bytes);
    }

    public static PacketByteBuf write(MessageLite message) {
        // 每位接收者使用独立 buffer，交由 Fabric 发包流程管理。
        if (message.getSerializedSize() > MAX_BYTES) {
            throw new IllegalArgumentException("动画包超过 32 KiB");
        }
        PacketByteBuf buffer = PacketByteBufs.create();
        buffer.writeBytes(message.toByteArray());
        return buffer;
    }
}

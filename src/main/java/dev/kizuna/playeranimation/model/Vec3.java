package dev.kizuna.playeranimation.model;

public record Vec3(float x, float y, float z) {
    public static final Vec3 ZERO = new Vec3(0, 0, 0);
    public static final Vec3 ONE = new Vec3(1, 1, 1);

    public Vec3 lerp(Vec3 other, float amount) {
        // 动画通道保留欧拉角的完整转数，不在关键帧之间强制走最短圆弧。
        return new Vec3(x + (other.x - x) * amount, y + (other.y - y) * amount,
                z + (other.z - z) * amount);
    }

    public float axis(int axis) {
        return switch (axis) { case 0 -> x; case 1 -> y; case 2 -> z;
            default -> throw new IllegalArgumentException("无效坐标轴: " + axis); };
    }
}

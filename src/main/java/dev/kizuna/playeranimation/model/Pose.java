package dev.kizuna.playeranimation.model;

public record Pose(Vec3 position, Vec3 rotation) {
    public static final Pose ZERO = new Pose(Vec3.ZERO, Vec3.ZERO);

    public Pose blend(Pose other, float amount) {
        // 分层淡入淡出只混合当前通道值，不改变动画本身的播放速度。
        return new Pose(position.lerp(other.position, amount), rotation.lerp(other.rotation, amount));
    }

    public Pose mirrored() {
        // 沿 X 镜像的位置反 X；旋转是轴向量，反的是 Y/Z。
        return new Pose(new Vec3(-position.x(), position.y(), position.z()),
                new Vec3(rotation.x(), -rotation.y(), -rotation.z()));
    }
}

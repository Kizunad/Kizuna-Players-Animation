package dev.kizuna.playeranimation.model;

import java.util.Locale;

public enum BoneId {
    ROOT(null), PELVIS(ROOT), WAIST(PELVIS), CHEST(WAIST), HEAD(CHEST),
    R_UPPER_ARM(CHEST), R_FOREARM(R_UPPER_ARM), R_HAND(R_FOREARM),
    R_THUMB(R_HAND), R_FINGERS_A(R_HAND), R_FINGERS_B(R_HAND), R_ITEM(R_HAND),
    L_UPPER_ARM(CHEST), L_FOREARM(L_UPPER_ARM), L_HAND(L_FOREARM),
    L_THUMB(L_HAND), L_FINGERS_A(L_HAND), L_FINGERS_B(L_HAND), L_ITEM(L_HAND),
    R_THIGH(PELVIS), R_SHIN(R_THIGH), R_FOOT(R_SHIN), R_TOE(R_FOOT),
    L_THIGH(PELVIS), L_SHIN(L_THIGH), L_FOOT(L_SHIN), L_TOE(L_FOOT);

    public static final BoneId[] ALL = values();
    public final BoneId parent;
    public final String key;

    BoneId(BoneId parent) {
        // 声明顺序保证父骨先于子骨，矩阵求值无需递归或额外拓扑排序。
        this.parent = parent;
        this.key = name().toLowerCase(Locale.ROOT);
    }

    public static BoneId named(String name) {
        // 严格匹配接口大小写，避免悄悄接受拼错的模型或动画骨名。
        for (BoneId bone : ALL) {
            if (bone.key.equals(name)) {
                return bone;
            }
        }
        throw new IllegalArgumentException("未知骨名: " + name);
    }

    public boolean socket() {
        return this == R_ITEM || this == L_ITEM;
    }

    public boolean arm() {
        return skinPart().endsWith("arm");
    }

    public String skinPart() {
        // 原版六个皮肤区域同时决定外层开关与允许蒙皮连接的分段范围。
        if (this == HEAD) {
            return "head";
        }
        if (this == ROOT || this == PELVIS || this == WAIST || this == CHEST) {
            return "body";
        }
        boolean leg = key.endsWith("thigh") || key.endsWith("shin")
                || key.endsWith("foot") || key.endsWith("toe");
        return key.substring(0, 2) + (leg ? "leg" : "arm");
    }

    public BoneId mirrored() {
        // 镜像只交换解剖左右，中央骨仍使用原来的对接键。
        if (key.startsWith("r_")) {
            return named("l_" + key.substring(2));
        }
        return key.startsWith("l_") ? named("r_" + key.substring(2)) : this;
    }
}

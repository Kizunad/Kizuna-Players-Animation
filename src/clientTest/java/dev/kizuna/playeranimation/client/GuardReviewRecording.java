package dev.kizuna.playeranimation.client;

import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 从真实世界帧缓冲读取连续镜头，一帧对应一个游戏 tick。 */
final class GuardReviewRecording {
    private enum Shot { STANDING, WALKING, SWORD, FIRST_PERSON, FRONT, SIDE, EYES }
    private static final Shot[] ORIGINAL = {Shot.STANDING, Shot.WALKING, Shot.SWORD, Shot.FIRST_PERSON};
    private static final Shot[] UNARMED = {Shot.FRONT, Shot.SIDE, Shot.EYES};
    private static final int CLIP_TICKS = 88;
    private static final int WARMUP_TICKS = 80;
    private static final int RAISE = 8;
    private static final int FIRST_HIT = 54;
    private static final int SECOND_HIT = 61;
    private static final int LOWER = 75;
    private static final int THIRD_FOV = 63;
    private static final int FIRST_FOV = 92;
    private static final float FRONT_VIEW_PITCH = -9;
    private static final float HAND_VIEW_PITCH = 37;
    private static final double SUBJECT_DISTANCE = 3.3;
    private static final float SUBJECT_PITCH = 10;
    private static final int SUBJECT_ENTITY_ID = Integer.MAX_VALUE;
    private static final UUID SUBJECT_ID = UUID.fromString("90e1736e-97ad-4cc1-bec2-42f4286077fc");
    private static final float FRONT_YAW = 180;
    private static final float SIDE_YAW = 90;
    private static final int REVIEW_FOV = 44;
    private final Path directory;
    private final Shot[] shots;
    private RemotePlayer subject;
    private int frame = -WARMUP_TICKS;
    private int saved;

    private GuardReviewRecording(Path directory, Shot[] shots) {
        this.directory = directory;
        this.shots = shots;
    }

    static GuardReviewRecording requested() {
        String rework = System.getenv("KPA_UNARMED_FRAMES");
        if (rework != null) {
            return new GuardReviewRecording(Path.of(rework), UNARMED);
        }
        String output = System.getenv("KPA_GUARD_FRAMES");
        if (output == null) {
            return null;
        }
        return new GuardReviewRecording(Path.of(output), ORIGINAL);
    }

    boolean tick(Minecraft client) {
        // 等区块和相机稳定再开始取样，异步回读全部落盘后才允许测试退出。
        if (client.gui.screen() != null) {
            return false;
        }
        if (frame == -WARMUP_TICKS) {
            prepare(client);
        }
        int total = CLIP_TICKS * shots.length;
        if (frame >= total) {
            return saved == total;
        }
        if (frame >= 0) {
            int local = frame % CLIP_TICKS;
            Shot shot = shots[frame / CLIP_TICKS];
            if (local == 0) {
                setupShot(client, shot);
            }
            UUID actor = client.player.getUUID();
            if (subject != null) {
                actor = subject.getUUID();
            }
            action(actor, local);
            capture(client, frame);
        }
        frame++;
        return false;
    }

    private void prepare(Minecraft client) {
        try {
            Files.createDirectories(directory);
        } catch (IOException failure) {
            throw new IllegalStateException("无法创建审片帧目录", failure);
        }
        var server = client.getSingleplayerServer();
        server.execute(() -> {
            var source = server.createCommandSourceStack();
            for (String command : List.of("time set noon", "weather clear", "gamemode creative @a",
                    "execute at @p run fill ~-10 ~1 ~-10 ~10 ~1 ~18 smooth_sandstone",
                    "execute at @p run tp @p ~ ~2 ~ 0 0",
                    "attribute @p minecraft:movement_speed base set 0.04")) {
                server.getCommands().performPrefixedCommand(source, command);
            }
        });
        var hud = client.gui.hud;
        if (!hud.isHidden()) {
            hud.toggle();
        }
        client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
    }

    private void setupShot(Minecraft client, Shot shot) {
        if (subject != null) {
            client.level.removeEntity(subject.getId(), Entity.RemovalReason.DISCARDED);
            subject = null;
        }
        UUID id = client.player.getUUID();
        PlayerAnimations.stopAll(id);
        client.setCameraEntity(client.player);
        if (shot == Shot.FRONT || shot == Shot.SIDE) {
            filmSubject(client, shot);
            return;
        } else if (shot == Shot.EYES) {
            client.options.setCameraType(CameraType.FIRST_PERSON);
            client.options.fov().set(THIRD_FOV);
            client.player.setXRot(0);
        } else if (shot == Shot.FIRST_PERSON) {
            client.options.setCameraType(CameraType.FIRST_PERSON);
            client.options.fov().set(FIRST_FOV);
            client.player.setXRot(HAND_VIEW_PITCH);
        } else {
            client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            client.options.fov().set(THIRD_FOV);
            client.player.setXRot(FRONT_VIEW_PITCH);
        }
        boolean walking = shot == Shot.WALKING;
        client.options.keyUp.setDown(walking);
        ItemStack item = ItemStack.EMPTY;
        if (shot == Shot.SWORD) {
            item = new ItemStack(Items.IRON_SWORD);
        }
        client.player.setItemInHand(InteractionHand.MAIN_HAND, item);
        String stance = walking ? "stance_walk_forward" : "stance_idle";
        PlayerAnimations.play(id, stance);
    }

    private void filmSubject(Minecraft client, Shot shot) {
        // 用观察者视角拍另一名客户端玩家；头、躯干和脚的朝向一起固定。
        subject = new RemotePlayer(client.level, new GameProfile(SUBJECT_ID, "Guard"));
        subject.setId(SUBJECT_ENTITY_ID);
        var target = client.player.position().add(0, 0, SUBJECT_DISTANCE);
        float yaw = shot == Shot.FRONT ? FRONT_YAW : SIDE_YAW;
        subject.snapTo(target, yaw, 0);
        subject.setYHeadRot(yaw);
        subject.setYBodyRot(yaw);
        subject.setOldPosAndRot();
        client.level.addEntity(subject);
        PlayerAnimations.stopAll(SUBJECT_ID);
        PlayerAnimations.play(SUBJECT_ID, "stance_idle");
        client.player.setYRot(0);
        client.player.setXRot(SUBJECT_PITCH);
        client.options.setCameraType(CameraType.FIRST_PERSON);
        client.options.fov().set(REVIEW_FOV);
    }

    private static void action(UUID id, int tick) {
        // 按宿主次序播放：受击覆盖保持，收势同时停掉两者。
        switch (tick) {
            case RAISE -> PlayerAnimations.play(id, "guard_arms");
            case FIRST_HIT, SECOND_HIT -> PlayerAnimations.play(id, "guard_arms_hit");
            case LOWER -> {
                PlayerAnimations.stop(id, "guard_arms_hit");
                PlayerAnimations.stop(id, "guard_arms");
                PlayerAnimations.play(id, "guard_arms_lower");
            }
            default -> { }
        }
    }

    private void capture(Minecraft client, int ordinal) {
        Path path = directory.resolve("guard-%04d.png".formatted(ordinal));
        Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(), image -> {
            try (image) {
                image.writeToFile(path);
                saved++;
            } catch (IOException failure) {
                throw new IllegalStateException("无法写入审片帧 " + path, failure);
            }
        });
    }
}

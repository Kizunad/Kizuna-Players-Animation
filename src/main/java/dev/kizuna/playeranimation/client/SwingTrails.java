package dev.kizuna.playeranimation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kizuna.playeranimation.animation.AnimationPlayer;
import dev.kizuna.playeranimation.animation.SwingWindow;
import dev.kizuna.playeranimation.model.BoneId;
import dev.kizuna.playeranimation.model.Rig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 挥击的风痕和破风声。剪辑在索引 swings 里标了哪个部位在哪段时间挥过去：这段时间里每帧记下这个部位的两端，
 * 连成一条淡出的带子画出来；起挥那一刻在本机放一声破风。玩家和生物走同一条路，全在客户端，不发包。
 *
 * <p>位置记在实体自己的坐标里，带子跟着人走。兵器的带子从刃根到刃尖；拳脚只记手、脚尖这一点，沿它走过的路画一道十字细条。</p>
 */
public final class SwingTrails {
    /** 一条带子上的一处：两端的位置和它已经过去了多久（0 到 1，1 是该消失了）。 */
    public record Edge(Vector3f inner, Vector3f outer, float age) { }

    /** 本帧要画的一条带子。 */
    public record Ribbon(List<Edge> edges, boolean heavy, boolean weapon) { }

    private enum Kind { WEAPON, HAND, FOOT }

    private record Limb(Kind kind, boolean right) { }

    private record Sample(double time, Vector3f inner, Vector3f outer) { }

    private static final class Track {
        final Deque<Sample> samples = new ArrayDeque<>();
        boolean heavy;
    }

    private static final class State {
        final Map<Limb, Track> tracks = new HashMap<>();
        /** 每个挥击段上一次出声的时刻。 */
        final Map<String, Double> sounded = new HashMap<>();
        double seen;
    }

    /** 兵器在挂点坐标里的刃根和刃尖：照原版手持物（handheld）第三人称的显示变换算出来的。 */
    private static final Vector3f WEAPON_INNER = new Vector3f(0, 0.2f, 0.03f);
    private static final Vector3f WEAPON_OUTER = new Vector3f(0, 0.84f, -0.07f);
    private static final double WEAPON_SECONDS = 0.2;
    private static final double LIMB_SECONDS = 0.16;
    /** 拳脚风痕半宽，格。 */
    private static final float LIMB_HALF_WIDTH = 0.07f;
    private static final float STILL_SQUARED = 1.0e-8f;
    private static final float NEARLY_VERTICAL = 0.9f;
    private static final double REPEAT_GAP_SECONDS = 0.1;
    /** 相邻两处之间再插几段，转弯处是弧不是折线。 */
    private static final int SUBDIVISIONS = 4;
    private static final float HEAVY_ALPHA = 0.6f;
    private static final float LIGHT_ALPHA = 0.38f;
    /** 带子靠里一侧淡到这么多，看起来是刃尖在发亮。 */
    private static final float INNER_ALPHA_SCALE = 0.12f;
    private static final float FADE_POWER = 1.5f;
    private static final float HEAVY_VOLUME = 0.8f;
    private static final float LIGHT_VOLUME = 0.55f;
    private static final float LOW_LAYER_PITCH = 0.85f;
    /** 同一式每次的音高在这个幅度里浮动，听着不重复。 */
    private static final float PITCH_JITTER = 0.08f;
    private static final int COLOR = 255;
    private static final int COLOR_RED = 235;
    private static final int COLOR_GREEN = 245;
    private static final double FORGET_SECONDS = 5;
    private static final int FORGET_ABOVE = 64;
    private static final Map<Integer, State> STATES = new HashMap<>();

    private SwingTrails() {
        // 只有静态入口；状态按实体编号记，断线和换世界时清掉。
    }

    public static void clear() {
        STATES.clear();
    }

    /** 取帧时调用：记下这一帧正在挥的部位，起挥的放一声，返回这一帧要画的带子。 */
    public static List<Ribbon> observe(LivingEntity entity, AnimationPlayer animation, double now,
                                       PlayerMeshRenderer.Frame pose, boolean rightItem, boolean leftItem) {
        if (animation == null && !STATES.containsKey(entity.getId())) {
            return List.of();
        }
        State state = state(entity, now);
        if (animation != null) {
            swinging(animation, now, rightItem, leftItem, (limb, swing, key) -> {
                sound(state, entity, limb, swing, key, now);
                record(state, limb, swing.weight() == SwingWindow.Weight.HEAVY, now, pose);
            });
        }
        return ribbons(state, now);
    }

    /**
     * 每个逻辑 tick 调用：只管出声。声音不能只靠取帧：第一人称时自己的身体不画风痕，
     * 背后、视野外的人根本不取帧，可破风声都该听得见。和取帧那边共用「一段只响一声」，谁先到谁放。
     */
    public static void listen(LivingEntity entity, AnimationPlayer animation, double now) {
        boolean mainRight = entity.getMainArm() == HumanoidArm.RIGHT;
        boolean mainItem = !entity.getMainHandItem().isEmpty();
        boolean offItem = !entity.getOffhandItem().isEmpty();
        State state = state(entity, now);
        swinging(animation, now, mainRight ? mainItem : offItem, mainRight ? offItem : mainItem,
                (limb, swing, key) -> sound(state, entity, limb, swing, key, now));
    }

    private interface Swinging {
        void accept(Limb limb, SwingWindow swing, String key);
    }

    private static State state(LivingEntity entity, double now) {
        State state = STATES.get(entity.getId());
        if (state == null) {
            forgetStale(now);
            state = new State();
            STATES.put(entity.getId(), state);
        }
        state.seen = now;
        return state;
    }

    private static void swinging(AnimationPlayer animation, double now, boolean rightItem, boolean leftItem,
                                 Swinging each) {
        // 此刻正在挥、而且对得上手里拿没拿东西的每一段。
        for (AnimationPlayer.ActivePlayback active : animation.active(now)) {
            for (SwingWindow swing : active.entry().swings()) {
                if (!swing.contains(active.time())) {
                    continue;
                }
                Limb limb = limb(swing.part(), active.mirrored());
                if (applies(limb, rightItem, leftItem)) {
                    each.accept(limb, swing, active.entry().id() + '/' + swing.start() + '/' + swing.part());
                }
            }
        }
    }

    private static void sound(State state, LivingEntity entity, Limb limb, SwingWindow swing, String key,
                              double now) {
        if (starts(state, key, now, swing.end() - swing.start())) {
            whoosh(entity, limb.kind() == Kind.WEAPON, swing.weight() == SwingWindow.Weight.HEAVY);
        }
    }

    private static boolean starts(State state, String key, double now, double span) {
        // 一段挥击只响一声。时间在段的边上来回晃（游戏冻结、卡顿时一帧里的小数部分会倒回去）不算又挥了一次；
        // 过了一整段再进来，或者时钟整个往回跳了，才是新的一次。
        Double last = state.sounded.get(key);
        if (last != null && Math.abs(now - last) <= span + REPEAT_GAP_SECONDS) {
            return false;
        }
        state.sounded.put(key, now);
        return true;
    }

    private static Limb limb(SwingWindow.Part part, boolean mirrored) {
        // 索引按右撇子写：主手在右。镜像播放时左右对调。
        boolean right = switch (part) {
            case MAIN, R_HAND, R_FOOT -> !mirrored;
            case OFF, L_HAND, L_FOOT -> mirrored;
        };
        Kind kind = switch (part) {
            case MAIN, OFF -> Kind.WEAPON;
            case R_HAND, L_HAND -> Kind.HAND;
            case R_FOOT, L_FOOT -> Kind.FOOT;
        };
        return new Limb(kind, right);
    }

    private static boolean applies(Limb limb, boolean rightItem, boolean leftItem) {
        // 兵器的挥击段要这只手里真有东西；空手的挥击段要这只手空着。脚不看手里拿什么。
        boolean holding = limb.right() ? rightItem : leftItem;
        return switch (limb.kind()) {
            case WEAPON -> holding;
            case HAND -> !holding;
            case FOOT -> true;
        };
    }

    private static void record(State state, Limb limb, boolean heavy, double now, PlayerMeshRenderer.Frame pose) {
        Track track = state.tracks.computeIfAbsent(limb, ignored -> new Track());
        track.heavy = heavy;
        // 游戏暂停或冻结时时间不走，同一刻只记一次。
        if (!track.samples.isEmpty() && track.samples.getLast().time() >= now) {
            return;
        }
        var skeleton = pose.model().skeleton();
        Matrix4f[] matrices = pose.matrices();
        Vector3f inner;
        Vector3f outer;
        switch (limb.kind()) {
            case WEAPON -> {
                Matrix4f socket = Rig.itemTransform(skeleton, matrices, limb.right() ? BoneId.R_ITEM : BoneId.L_ITEM);
                inner = socket.transformPosition(new Vector3f(WEAPON_INNER));
                outer = socket.transformPosition(new Vector3f(WEAPON_OUTER));
            }
            case HAND -> {
                outer = origin(pose, limb.right() ? BoneId.R_HAND : BoneId.L_HAND);
                inner = outer;
            }
            default -> {
                outer = origin(pose, limb.right() ? BoneId.R_TOE : BoneId.L_TOE);
                inner = outer;
            }
        }
        track.samples.addLast(new Sample(now, inner, outer));
    }

    private static Vector3f origin(PlayerMeshRenderer.Frame pose, BoneId bone) {
        // 这根骨的枢轴此刻在实体坐标里的位置。
        return Rig.itemTransform(pose.model().skeleton(), pose.matrices(), bone).transformPosition(new Vector3f());
    }

    private static List<Ribbon> ribbons(State state, double now) {
        List<Ribbon> ribbons = new ArrayList<>();
        for (var entry : state.tracks.entrySet()) {
            boolean weapon = entry.getKey().kind() == Kind.WEAPON;
            double life = weapon ? WEAPON_SECONDS : LIMB_SECONDS;
            Deque<Sample> samples = entry.getValue().samples;
            while (!samples.isEmpty() && now - samples.getFirst().time() > life) {
                samples.removeFirst();
            }
            if (samples.size() < 2) {
                continue;
            }
            List<Edge> edges = edges(new ArrayList<>(samples), now, life);
            if (weapon) {
                ribbons.add(new Ribbon(edges, entry.getValue().heavy, true /* weapon */));
                continue;
            }
            ribbons.add(new Ribbon(streak(edges, false /* upright */), entry.getValue().heavy, false /* weapon */));
            ribbons.add(new Ribbon(streak(edges, true /* upright */), entry.getValue().heavy, false /* weapon */));
        }
        return ribbons;
    }

    private static List<Edge> edges(List<Sample> samples, double now, double life) {
        // 相邻两处之间按样条插几段；两头没有邻居时拿自己顶替。
        List<Edge> edges = new ArrayList<>();
        for (int index = 0; index + 1 < samples.size(); index++) {
            Sample before = samples.get(Math.max(0, index - 1));
            Sample from = samples.get(index);
            Sample to = samples.get(index + 1);
            Sample after = samples.get(Math.min(samples.size() - 1, index + 2));
            for (int step = 0; step < SUBDIVISIONS; step++) {
                float amount = step / (float) SUBDIVISIONS;
                double time = from.time() + (to.time() - from.time()) * amount;
                edges.add(new Edge(spline(before.inner(), from.inner(), to.inner(), after.inner(), amount),
                        spline(before.outer(), from.outer(), to.outer(), after.outer(), amount),
                        (float) ((now - time) / life)));
            }
        }
        Sample last = samples.getLast();
        edges.add(new Edge(last.inner(), last.outer(), (float) ((now - last.time()) / life)));
        return edges;
    }

    private static List<Edge> streak(List<Edge> path, boolean upright) {
        // 拳脚的风痕跟着拳头、脚尖走成一道细条。直拳是顺着小臂捅出去的，拿小臂当带子的宽会缩成一条线，
        // 所以宽度取和行进方向垂直的方向；横竖各一片交成十字，从哪边看都有面。
        List<Edge> edges = new ArrayList<>();
        Vector3f side = new Vector3f(1, 0, 0);
        for (int index = 0; index < path.size(); index++) {
            Vector3f center = path.get(index).outer();
            Vector3f heading = new Vector3f(path.get(Math.min(path.size() - 1, index + 1)).outer())
                    .sub(path.get(Math.max(0, index - 1)).outer());
            if (heading.lengthSquared() > STILL_SQUARED) {
                heading.normalize();
                // 行进方向接近竖直时拿前后方向当参照，免得叉积退化。
                Vector3f reference = Math.abs(heading.y) > NEARLY_VERTICAL
                        ? new Vector3f(0, 0, 1) : new Vector3f(0, 1, 0);
                side = new Vector3f(heading).cross(reference).normalize();
                if (upright) {
                    side = new Vector3f(heading).cross(side).normalize();
                }
            }
            Vector3f half = new Vector3f(side).mul(LIMB_HALF_WIDTH);
            edges.add(new Edge(new Vector3f(center).sub(half), new Vector3f(center).add(half), path.get(index).age()));
        }
        return edges;
    }

    private static Vector3f spline(Vector3f before, Vector3f from, Vector3f to, Vector3f after, float amount) {
        // Catmull-Rom：经过 from 和 to，弯向前后两处。
        float squared = amount * amount;
        float cubed = squared * amount;
        return new Vector3f(
                spline(before.x, from.x, to.x, after.x, amount, squared, cubed),
                spline(before.y, from.y, to.y, after.y, amount, squared, cubed),
                spline(before.z, from.z, to.z, after.z, amount, squared, cubed));
    }

    private static float spline(float before, float from, float to, float after, float amount, float squared,
                                float cubed) {
        return 0.5f * (2 * from + (to - before) * amount + (2 * before - 5 * from + 4 * to - after) * squared
                + (3 * from - before - 3 * to + after) * cubed);
    }

    /** 提交时调用：已经在原版实体变换之内。 */
    public static void submit(List<Ribbon> ribbons, PoseStack matrices, SubmitNodeCollector collector) {
        if (ribbons.isEmpty()) {
            return;
        }
        collector.submitCustomGeometry(matrices, RenderTypes.lightning(), (pose, buffer) -> {
            for (Ribbon ribbon : ribbons) {
                draw(ribbon, pose, buffer);
            }
        });
    }

    private static void draw(Ribbon ribbon, PoseStack.Pose pose, VertexConsumer buffer) {
        float strength = ribbon.heavy() ? HEAVY_ALPHA : LIGHT_ALPHA;
        // 兵器的带子靠刃根一侧淡下去；拳脚的细条两边一样亮。
        float inner = ribbon.weapon() ? INNER_ALPHA_SCALE : 1;
        for (int index = 0; index + 1 < ribbon.edges().size(); index++) {
            Edge from = ribbon.edges().get(index);
            Edge to = ribbon.edges().get(index + 1);
            float fromAlpha = strength * fade(from.age());
            float toAlpha = strength * fade(to.age());
            // 两面都画：带子从哪边看都在。
            vertex(buffer, pose, from.inner(), fromAlpha * inner);
            vertex(buffer, pose, from.outer(), fromAlpha);
            vertex(buffer, pose, to.outer(), toAlpha);
            vertex(buffer, pose, to.inner(), toAlpha * inner);
            vertex(buffer, pose, to.inner(), toAlpha * inner);
            vertex(buffer, pose, to.outer(), toAlpha);
            vertex(buffer, pose, from.outer(), fromAlpha);
            vertex(buffer, pose, from.inner(), fromAlpha * inner);
        }
    }

    private static float fade(float age) {
        // 越旧越淡，尾巴收得快一些。
        return (float) Math.pow(Math.clamp(1 - age, 0, 1), FADE_POWER);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vector3f position, float alpha) {
        buffer.addVertex(pose, position.x, position.y, position.z)
                .setColor(COLOR_RED, COLOR_GREEN, COLOR, Math.round(alpha * COLOR));
    }

    private static void whoosh(LivingEntity entity, boolean weapon, boolean heavy) {
        // 本机放、不经服务端：声音和画面上的动作是同一个时钟。重的再叠一层低的。
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        String name = "swing." + (weapon ? "weapon" : "limb") + '.' + (heavy ? "heavy" : "light");
        SoundSource source = entity instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE;
        float pitch = 1 + (entity.getRandom().nextFloat() * 2 - 1) * PITCH_JITTER;
        double height = entity.getY() + entity.getBbHeight() * 0.6;
        level.playLocalSound(entity.getX(), height, entity.getZ(), sound(name), source,
                heavy ? HEAVY_VOLUME : LIGHT_VOLUME, pitch, false /* distanceDelay */);
        if (heavy) {
            level.playLocalSound(entity.getX(), height, entity.getZ(), sound("swing.low"), source,
                    HEAVY_VOLUME, pitch * LOW_LAYER_PITCH, false /* distanceDelay */);
        }
    }

    private static SoundEvent sound(String name) {
        return SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(PlayerAnimationClient.ID, name));
    }

    private static void forgetStale(double now) {
        // 实体多了才清：好一阵没露面的（卸载了、走远了）不留着。
        if (STATES.size() > FORGET_ABOVE) {
            STATES.values().removeIf(state -> now - state.seen > FORGET_SECONDS);
        }
    }
}

package dev.kizuna.playeranimation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kizuna.playeranimation.animation.AimWindow;
import dev.kizuna.playeranimation.animation.AnimationClip;
import dev.kizuna.playeranimation.animation.AnimationEntry;
import dev.kizuna.playeranimation.animation.AnimationPlayer;
import dev.kizuna.playeranimation.animation.ItemStates;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 行矢引入的索引字段 item_states、aim_windows 与弓梢打击时段（docs/asset-contract.md「索引条目」）。 */
class WayfarerIndexFieldsTest {
    private static final String KEY = "animation.kizuna_players_animation.player.test";

    private static AnimationClip clip() {
        JsonObject document = JsonParser.parseString("{\"format_version\":\"1.8.0\",\"animations\":{\"" + KEY
                + "\":{\"animation_length\":6,\"bones\":{\"r_forearm\":{\"rotation\":[0,0,0]}}}}}").getAsJsonObject();
        return AnimationClip.parse(document, KEY, error -> { throw new AssertionError(error); });
    }

    private static JsonObject entry(String id, String hand, String extra) {
        return JsonParser.parseString("{\"id\":\"" + id + "\",\"file\":\"animations/" + id + ".animation.json\","
                + "\"mask\":\"full\",\"priority\":24,\"blend_in\":0.1,\"blend_out\":0.25,\"hand\":\"" + hand
                + "\",\"fpv\":false" + extra + "}").getAsJsonObject();
    }

    @Test
    void itemStatesSampleStepSwitchesAndLinearPullWithHeldEnds() {
        AnimationEntry parsed = AnimationEntry.parse(entry("wayfarer_full_draw", "main", """
                ,"item_states":{"pulling":[[0,0],[1.55,1],[3.6,0]],"pull":[[2.3,0],[3.1,1]],"arrow":[[0,0]]}
                """), clip());
        ItemStates states = parsed.itemStates();
        // 阶跃：键当帧取新值，之前保持上一键。
        assertEquals(0f, states.value(ItemStates.Channel.PULLING, 1.54f));
        assertEquals(1f, states.value(ItemStates.Channel.PULLING, 1.55f));
        assertEquals(0f, states.value(ItemStates.Channel.PULLING, 5.9f));
        // 线性：首键前取首值、末键后保持。
        assertEquals(0f, states.value(ItemStates.Channel.PULL, 1.0f));
        assertEquals(0.5f, states.value(ItemStates.Channel.PULL, 2.7f), 0.0001f);
        assertEquals(1f, states.value(ItemStates.Channel.PULL, 4.0f));
        assertEquals(0f, states.value(ItemStates.Channel.ARROW, 3.0f));
        // 没写的通道交给原版。
        assertNull(states.value(ItemStates.Channel.CHARGED, 3.0f));
        assertTrue(AnimationEntry.parse(entry("plain", "main", ""), clip()).aimWindows().isEmpty());
    }

    @Test
    void itemStatesRejectMalformedTracks() {
        for (String bad : new String[]{
                ",\"item_states\":{}",
                ",\"item_states\":{\"draw\":[[0,1]]}",
                ",\"item_states\":{\"pull\":[]}",
                ",\"item_states\":{\"pull\":[[1,0],[1,1]]}",
                ",\"item_states\":{\"pull\":[[0,1.2]]}",
                ",\"item_states\":{\"pulling\":[[0,0.5]]}",
                ",\"item_states\":{\"charged\":[[6.5,1]]}",
                ",\"item_states\":{\"pulling\":[[0,1,2]]}"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> AnimationEntry.parse(entry("wayfarer_pearls", "main", bad), clip()), bad);
        }
        // 物品状态挂在持物手上，hand none / both 不能声明。
        assertThrows(IllegalArgumentException.class, () -> AnimationEntry.parse(
                entry("wayfarer_pearls", "none", ",\"item_states\":{\"pulling\":[[0,1]]}"), clip()));
    }

    @Test
    void aimWindowsFadeInsideTheWindowAndRejectOverlap() {
        AnimationEntry parsed = AnimationEntry.parse(entry("wayfarer_kneeling", "off", """
                ,"aim_windows":[{"start":2.25,"end":2.75,"mode":"pitch"},{"start":3.35,"end":3.85,"mode":"yaw"}]
                """), clip());
        AimWindow first = parsed.aimWindows().get(0);
        assertEquals(AimWindow.Mode.PITCH, first.mode());
        assertEquals(AimWindow.Mode.YAW, parsed.aimWindows().get(1).mode());
        // 渐入渐出都在时段内：端点为 0，起点后 0.15 s 到满权重。
        assertEquals(0, first.weight(2.25f), 0.0001f);
        assertEquals(0.5f, first.weight(2.325f), 0.0001f);
        assertEquals(1, first.weight(2.5f), 0.0001f);
        assertEquals(0, first.weight(3.0f), 0.0001f);
        for (String bad : new String[]{
                ",\"aim_windows\":[]",
                ",\"aim_windows\":[{\"start\":2,\"end\":1,\"mode\":\"pitch\"}]",
                ",\"aim_windows\":[{\"start\":1,\"end\":7,\"mode\":\"pitch\"}]",
                ",\"aim_windows\":[{\"start\":1,\"end\":2,\"mode\":\"pitch_yaw\"}]",
                ",\"aim_windows\":[{\"start\":1,\"end\":2,\"mode\":\"pitch\",\"weight\":1}]",
                ",\"aim_windows\":[{\"start\":1,\"end\":3,\"mode\":\"pitch\"},"
                        + "{\"start\":2.5,\"end\":4,\"mode\":\"yaw\"}]"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> AnimationEntry.parse(entry("wayfarer_kneeling", "main", bad), clip()), bad);
        }
    }

    @Test
    void bowStrikeMayDeclareAnImpactWindow() {
        AnimationEntry parsed = AnimationEntry.parse(entry("wayfarer_bow_strike_off", "off",
                ",\"impact_window\":[1.25,1.45]"), clip());
        assertEquals(1.45f, parsed.impactWindows().getFirst().end(), 0.0001f);
        assertThrows(IllegalArgumentException.class, () -> AnimationEntry.parse(entry("wayfarer_pearls", "main",
                ",\"impact_window\":[1.25,1.45]"), clip()));
    }

    @Test
    void itemStateUsesPlaybackOffsetSelectedHandAndFadeWeight() {
        // 承接从实际剪辑位置采样；物品状态不能泄漏到另一手或淡出后的原版模型。
        AnimationEntry parsed = AnimationEntry.parse(entry("wayfarer_full_draw", "main", """
                ,"item_states":{"pull":[[0,0],[2,1]],"pulling":[[0,1],[3.6,0]]}
                """), clip());
        AnimationPlayer player = new AnimationPlayer();
        double start = 10;
        player.play(parsed, start, new AnimationPlayer.PlaybackRequest(start,
                AnimationPlayer.Handedness.LEFT, "draw", null));
        assertNull(player.itemValue(AnimationEntry.Hand.MAIN, ItemStates.Channel.PULL, start + 0.02));
        assertEquals(1f, player.itemValue(AnimationEntry.Hand.MAIN, ItemStates.Channel.PULL, start + 2));
        assertNull(player.itemValue(AnimationEntry.Hand.OFF, ItemStates.Channel.PULL, start + 2));
        assertNull(player.itemValue(AnimationEntry.Hand.MAIN, ItemStates.Channel.CHARGED, start + 2));
        player.stop(parsed.id(), start + 2);
        assertNull(player.itemValue(AnimationEntry.Hand.MAIN, ItemStates.Channel.PULL, start + 2.2));
        player.play(parsed, start, new AnimationPlayer.PlaybackRequest(start - 2,
                AnimationPlayer.Handedness.RIGHT, "continuation", null));
        assertEquals(1f, player.itemValue(AnimationEntry.Hand.MAIN, ItemStates.Channel.PULL, start));
    }
}

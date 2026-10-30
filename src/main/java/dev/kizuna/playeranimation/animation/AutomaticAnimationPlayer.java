package dev.kizuna.playeranimation.animation;

/** 自动层保持底层时钟，只在状态变化时交叉淡入；动作按宿主进度取样。 */
public final class AutomaticAnimationPlayer {
    private final AnimationPlayer playback = new AnimationPlayer();
    private String baseId = "";
    private String actionId = "";

    public void update(AnimationEntry base, AnimationEntry action, float progress,
                       AnimationPlayer.Handedness hand, double now) {
        // 移动与动作共用 PA 的混合规则，相同状态每 tick 更新不会重启底层。
        String nextBase = base.id();
        if (!baseId.equals(nextBase)) {
            playback.stop(baseId, now);
            var request = new AnimationPlayer.PlaybackRequest(now, hand, null, AnimationClip.Loop.REPEAT);
            playback.play(base, now, request);
            baseId = nextBase;
        }
        String nextAction = "";
        if (action != null) {
            nextAction = action.id();
        }
        if (!actionId.equals(nextAction)) {
            playback.stop(actionId, now);
            actionId = nextAction;
        }
        if (action != null) {
            playback.scrub(action, now, progress, hand == AnimationPlayer.Handedness.LEFT);
        }
    }

    public AnimationPlayer playback() {
        return playback;
    }
}

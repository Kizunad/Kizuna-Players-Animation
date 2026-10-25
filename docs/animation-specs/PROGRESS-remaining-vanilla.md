# 剩余原版动作进度（分支 `anim/remaining-vanilla`）

更新：2026-10-26。接口与验收规则以 [资产契约](../asset-contract.md) 和 [制作与验收规则](../animation-acceptance.md) 为准。

## 状态：本批全部完成制作与离线验证，游戏内验收未执行

| 动作 | 设计稿 | 骨 / 关键帧（导出 JSON） | 驱动 | 门禁（粗臂/细臂） |
| --- | --- | --- | --- | --- |
| `sneak` | [sneak.md](sneak.md) | 24 / 155 | 静止潜行循环 | 通过（站立循环检查） |
| `sneak_walk` | [sneak_walk.md](sneak_walk.md) | 24 / 273 | 潜行移动循环 | 通过（步态支撑检查） |
| `eat` / `eat_off` | [eat.md](eat.md) | 23 / 138 | 已使用 tick，咀嚼对齐进食音效 | 通过 |
| `drink` / `drink_off` | [drink.md](drink.md) | 23 / 136 | 已使用 tick，32 tick 仰头 | 通过 |
| `head_follow` / `head_follow_pitch` | [head_follow.md](head_follow.md) | 7 / 77、5 / 55 | 客户端视线角度（加性层） | 通过（视线层判据） |
| `block_shield` / `_off` | [block_shield.md](block_shield.md) | 23 / 136 | 已使用 tick，5 tick 举盾到位 | 通过 |
| `bow_draw` / `_off` | [bow_draw.md](bow_draw.md) | 23 / 219 | 原版拉弓进度 + 撒放尾段 | 通过（侧身改为 40°，见设计稿） |
| `trident_charge` / `_off` | [trident_charge.md](trident_charge.md) | 23 / 179 | 已使用 tick，10 tick 就绪 + 投掷尾段 | 通过 |
| `ride` | [ride.md](ride.md) | 22 / 184 | 骑乘状态循环 | 通过 |
| `sleep` | [sleep.md](sleep.md) | 22 / 159 | 睡眠状态循环 | 通过（睡眠姿势判据） |
| `elytra` | [elytra.md](elytra.md) | 24 / 168 | 滑翔 > 4 tick 循环 | 通过（去掉展开段，见设计稿） |
| `climb` | [climb.md](climb.md) | 24 / 465 | 累计竖直位移 scrub | 通过 |
| `swim` | [swim.md](swim.md) | 24 / 600 | 原版肢体时钟 scrub | 通过 |

离线 GIF 与接触表：`build/anim-review/2026-10-26/<id>/`（粗臂、细臂各一份，不入库）。

## 与审阅稿不同的地方（请复核）

- `bow_draw`：审阅通过的侧身 65° 在 Steve 比例下锚点不可达（弦臂伸直、前臂穿胸），改为侧身 40°、弓把在身前中线偏右、
  弦手拉到下颌下方；可见拉弓主要来自弦臂肘 39° → 65°，弦手行程约 1.5 px。
- `elytra`：去掉 0–0.5 s 展开段，改为纯循环，进入由淡入完成。
- `climb`：手的踏棍平面放到身前 6.2 px；只做面朝梯子（已知限制）。
- `swim`：换气改为向右；外展按手臂高度切换 Z 符号。

## 未做 / 后续

- 游戏内 Windows Native 录制、双客户端一致性、`calib_item` 对盾面/弓/三叉戟朝向的校准。
- 闪避、通用单手/双手格挡依赖未定玩法，本批不做。
- 攀爬中吃喝/拉弓等双手动作仍以 `upper` 叠加（设计稿建议不叠加，调度未限制）。
- 骑乘不随坐骑速度改变节奏（协议无播放速率字段）。

## 设计期原型脚本

不入库，保存在本机 `~/Code/Kizuna-remote-session/handoff/kpa-remaining-vanilla-proto/`（`d_*.py` 生成各动作关键帧，
`bbtool.py` 写入 `.bbmodel` 并调用仓库导出脚本）。

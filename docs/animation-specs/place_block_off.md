# place_block_off：副手放置方块（place_block 的镜像）

> 审阅状态：随 [place_block](place_block.md) 一起审阅；本页只说明镜像规则，不单独设计关键帧。

- **来源**：`.bbmodel` 中的 `place_block_off` 由 `place_block` 的关键帧逐帧镜像得到，规则与[资产契约](../asset-contract.md)的左主手镜像一致：交换 `r_*` / `l_*` 骨，旋转 Y、Z 取反，位移 X 取反，旋转 X 不变；无时间线事件。
- **触发**：副手持方块放置时调用；时长、阶段、0–0.20 s 循环回绕和 scrub 驱动与 `place_block` 完全相同。
- **索引**：与 `place_block` 相同，只把 `hand` 改为 `off`。
- **门禁与 GIF**：单独导出、单独跑门禁并出 GIF；握持检查改查左手手指。
- 修改主手动作后必须重新生成本动作，不允许只改一边。

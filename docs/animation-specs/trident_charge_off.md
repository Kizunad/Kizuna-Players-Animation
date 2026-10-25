# trident_charge_off：副手三叉戟蓄力（trident_charge 的镜像）

> 审阅状态：设计稿，随 [trident_charge](trident_charge.md) 一起审阅（2026-10-26）；本页只说明镜像规则，不单独设计关键帧。

- **来源**：`.bbmodel` 中的 `trident_charge_off` 由 `trident_charge` 的关键帧逐帧镜像得到，规则与[资产契约](../asset-contract.md)的左主手镜像一致：交换 `r_*` / `l_*` 骨，旋转 Y、Z 取反，位移 X 取反，旋转 X 不变。
- **触发**：物品在副手、原版对副手臂套用同一 ArmPose 时调用；时长、阶段、驱动方式与 `trident_charge` 完全相同。副手三叉戟同样可投掷/激流；投掷尾段同主手。
- **索引**：与 `trident_charge` 相同，只把 `hand` 改为 `off`。
- **门禁与 GIF**：单独导出、单独跑门禁并出粗臂/细臂 GIF；握持检查改查左手手指。
- 修改主手动作后必须重新生成本动作，不允许只改一边。

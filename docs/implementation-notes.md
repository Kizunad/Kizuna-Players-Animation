# 实现说明与参考代码

## 参考依据

本项目采用 MIT，参考下列项目的设计与文件格式，运行时仅依赖 Fabric API 及 Minecraft 自带的 Gson、JOML；没有引入它们作为动画依赖。

| 项目与源码 | 借鉴内容 | 处理方式 |
| --- | --- | --- |
| [Epic Fight：Armature、Joint、SkinnedMesh、PPlayerRenderer](https://github.com/Antikythera-Studios/epicfight/tree/9d25f19c121c5e51b1db9efe01833a563706eeee/src/main/java/yesman/epicfight) | 父子关节矩阵、顶点权重蒙皮、玩家皮肤和原版可见性 | GPL-3.0；只参考设计，独立实现本仓代码，未移植源码或资源 |
| [PlayerAnimator：玩家及手持物 Mixin](https://github.com/KosmX/minecraftPlayerAnimator/tree/a17ecb85e7978e63286697bc0f5ff8a7348ef055/minecraft/common/src/main/java/dev/kosmx/playerAnim/mixin) | 原版玩家姿态作为底层、渲染和持物入口 | MIT；采用原版渲染生命周期，不接入旧动画格式 |
| [GeckoLib](https://github.com/bernie-g/geckolib)，本地核对 `geckolib-fabric-1.20.1:4.4.9` 源码包 | `BakedModelFactory`、`RenderUtils`、`KeyFramesAdapter` 的 Bedrock 几何与事件语义 | MIT；核实格式，不依赖完整动画框架 |
| [Blockbench 导出器与关键帧代码](https://github.com/JannisX11/blockbench/tree/7c92e27d237e365110030d73e92c5d1605f0d311/js) | 几何 X 镜像、旋转 X/Y 反号、顶底 UV、pre/post 和循环 Catmull-Rom | 按实际导出规则实现读取器；不把面板数值与 JSON 数值混为一谈 |

还核对了 Yarn `1.20.1+build.10` 中原版 `LivingEntityRenderer`、`HeldItemFeatureRenderer`、`PlayerEntityModel` 的调用顺序与矩阵入口。

## 最小实现

- 单个 Fabric 客户端工程，不引入动画表达式引擎、通用插件接口或跨加载器模块。
- 骨名及父子关系固定；粗细臂枢轴、方块和 UV 由交付几何决定。动画资源只在重载时读取，逐帧仅取样与绘制。
- 顶点在绑定姿态的全局像素空间保存，每节矩阵按父骨变换、位移、枢轴、ZYX 旋转、反向枢轴组合。原版六部件的矩阵先转换成本骨架的局部姿态，避免额外重写走路、蹲伏和抬手动作。
- 同一原版部件的相邻切口采用线性混合蒙皮。子段切口顶点继承父段在相同位置的权重，向子段内部 1 像素过渡至子骨；切口顶点位置由双方共同决定，肘与腕可以同时弯曲。脚尖还继承脚跟在脚踝处的权重。
- 内外层以未膨胀坐标计算相同权重。外层先按完整原版部件包围盒统一外扩，再计算分段位置，保留原版外层沿整面的像素比例。每像素细分表面使关节带具备变形顶点，UV 仍连续采样原面，不增加皮肤图集。
- `AnimationPlayer` 只做时间、层叠和事件；Minecraft 生命周期、渲染与资源重载留在 `client` / `mixin` 包，便于用纯数值回归验证骨架与播放行为。
- 时间线在客户端 tick 推进，渲染可以多次取样而不重复发送事件；回调在遍历结束后触发，允许调用方启动或停止动画。

## 已发现并处理的边界

- 左右镜像需要交换骨名，并反转位移 X 和旋转 Y/Z；直接把旋转 X 反号会改变前后弯曲。
- Blockbench 的 step 可导出成下一帧的 `pre/post`，不能只识别 `lerp_mode: step`；循环 Catmull-Rom 还需要首尾相邻帧的切线。
- JOML 1.10.5 的 `Quaternionf.getEulerAnglesZYX` 中 X 分量分母使用了错误符号，复合旋转回归发现 20° 被还原成约 17.38°。本仓直接从矩阵提取 ZYX，覆盖 ±90° 万向锁，并与原版 `ModelPart.rotate` 结果对照。
- 模型空间的 Y 反射会翻转面绕序，因此同时调整四边形绕序和法线方向。

## 验证边界

已通过 Java 17 构建与自动化回归；使用 Xvfb 启动 Fabric 客户端，确认入口、Mixin 和缺少正式资源时的原版渲染保留路径，窗口由定时器结束。环境音频配置警告不影响模组初始化。

尚无正式模型与校准动画。数学测试证明切口连续和矩阵契约，不证明真实皮肤逐像素一致、弯折体积美观、挂点准确或第一人称观感。上述项目必须在正式资源到位后完成游戏内验收，当前构建不是已发布版本。

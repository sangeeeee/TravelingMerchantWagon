# TravelingMerchantWagon

A Minecraft 1.21.1 NeoForge mod in development, focused on adding traveling merchant wagons.

- Mod ID: `tm_wagon`
- Java package: `com.sange.tm_wagon`
- Java version: 21
- Required dependency: [GeckoLib](https://modrinth.com/mod/geckolib) 4.9.3 or newer in the 4.x series, using the NeoForge build for Minecraft 1.21.1. Install it on both the client and server.

Development builds resolve GeckoLib automatically through Gradle. Its build version and supported runtime range are configured in `gradle.properties`.

Editable wagon models and previews are in the [open cargo wagon modeling subproject](modeling/open_cargo_wagon/README.md).

The oak assembly frame block model is in the [wagon assembly frame modeling subproject](modeling/wagon_assembly_frame/README.md).

## 第一阶段：方块与放置

创造模式中打开「旅行商人马车」物品栏，可取得装配架、载荷主体、单马辕、双马辕、单人座位、双人座位、小车轮和大车轮。配件物品显示为对应模型的微缩版；物品栏图标及模组 logo 共用一张 64 × 64 的透明马车像素画。

装配架采用前后两组交叠的「> / <」铰接木臂，关节有铁铆钉。放置时默认展开，平台顶面约 1.375 格高。空手右键支撑柱或平台可折叠，再次右键展开，完整收放约 1 秒；折叠后平台约 0.584 格高，碰撞按 1/32 格精度定义，动画期间保持切换前的形状，结束时一次性切换到新形状。动画中再次右键可以反向收放，状态和进度同步到客户端并保存。

收放前检查整个运动范围内的方块、实体、保护和区块加载情况；动画期间预留所需格，保持原有碰撞；结束时一次性更新碰撞并释放多余代理格。当前只有装配架运动，已安装货架保持原有高度；货架转为实体与装配完成逻辑仍在后续阶段实现。

先在地面放置装配架，等它完全展开，再用载荷主体右键上方的平台顶面（包括边缘）。一次放置会生成完整货台、倾斜脚板、两侧上下车踏板和车轴的多方块结构。装配架朝向由放置方向决定，车头朝向放置者。

所有安装坐标都以装配架所在格为原点。「前方」随装配架朝向旋转；朝北时前方为负 Z、左侧为负 X。

| 配件 | 安装格（相对装配架） |
| --- | --- |
| 载荷主体 | 正上方 1 格 |
| 单马辕 / 双马辕 | 前方 2 格，上方 1 格，中央 |
| 单人座位 / 双人座位 | 前方 2 格，上方 2 格，中央 |
| 两个小车轮 | 前方 1 格，左右各 1 格，与装配架同层 |
| 两个大车轮 | 后方 1 格，左右各 1 格，与装配架同层 |

手持对应配件，右键安装格内已有的主体结构，或右键相邻方块朝向安装格的一面。两种座位共用一个位置，两种辕也共用一个位置；更换前需要拆下原部件。前轴拒绝大轮，后轴拒绝小轮。安装前会检查整个模型占用的空间、实体阻挡、可建造范围及区块加载情况，失败时不消耗物品。

破坏某个部件的任意可选中部分，会拆除完整部件。多个部件共用一格时，按玩家实际瞄准的部分判定拆除对象。拆除载荷主体会同时拆下其附属配件；拆除装配架会拆下全部结构。生存模式每个已安装部件仅掉落一个物品，创造模式不掉落。存档记录各部件状态和每格碰撞形状，未加载的装配架区块不会被误判为结构已损坏。

装配架也是多方块结构：下方简化为 1 格宽的支撑柱，展开时延伸至 1.25 格高；顶部平台展开时碰撞到 1.375 格高，对齐货板底面。支撑柱与平台碰撞在动画结束时一次性切换。破坏平台任意部分会拆除整个装配架。平台模型在水平方向缩小 2%，顶面下移 0.02 模型单位，避免与货板共面闪烁。货板长度为 3.75 格，比原模型长半格。

碰撞与装饰模型独立：货厢使用无顶空心盒、脚板和两侧踏板，车轮使用八段填实的近圆形轮廓，车辕使用水平细长长方体，座位使用简化底座、座面、靠背和扶手。铆钉、铁箍及轮辐不单独生成碰撞。每格碰撞仍使用 1/32 格精度。物品微缩模型先居中再缩放，装配架使用独立的物品显示比例。配件齐全后支持方块与实体双向转换，本阶段没有合成表。

### 装配与实体还原

仅完全展开且停止运动的装配架接受配件。必需组件为载荷主体、单人或双人座位、单马或双马辕、两只小前轮和两只大后轮；必需槽位单独定义，后续可选组件不会自动变为必需。配件未齐全时右键提示错误，保持展开。配件齐全后右键立即生成一个 `tm_wagon:wagon` 实体并移除配件方块，装配架同时开始收缩。没有配件的装配架仍可自由伸缩。

在缩回的装配架旁停放马车后右键，装配架升起，动画结束时统一检查并还原所有配件方块。允许马车中心与装配架中心水平偏离 1.5 格、竖直偏离 0.75 格，还原时按装配架方向对齐。还原检查涵盖方块、流体、实体、区块、世界边界和操作权限；失败保留实体与配件数据、提示原因并自动收架，可以再次尝试。还原过程中其他装配架不能同时接管同一辆车。

实体当前静止，不受重力或推动。单人座位容纳一名乘客，双人座位容纳两名；右键瞄准座位入座，使用原版潜行下车。实体乘客采用原版骑乘机制，还原成功时自动下车并移至车体外的空位。配件组合、方向和骑乘关系支持存档；当前不保存货物，也不包含驾驶、牵引或合成配方。

实体只注册一个主体，复用方块的简化碰撞体，不创建会逐个更新的碰撞子实体。碰撞形状按配件组合与朝向共享缓存，世界形状只在位置变化时更新；原版空间查询后对马车使用复合形状，保留车厢内部空隙；另外只为马车维护跨区块索引，避免长车辕伸入相邻区块时漏掉碰撞。选中检测也使用部件形状，避免实体的大范围包围盒挡住装配架的点击。此阶段朝向为四个水平方向，后续行驶与任意角度旋转需要继续扩展。

### 验证与开发

- `gradlew runGameTestServer`：验证旋转、安装、失败不扣物品、共享格拆除、存档、外部移除、实体阻挡及掉落行为。
- `gradlew runClientSmokeTest`：自动打开开发客户端，渲染八种方块物品、图标、马车组合和装配架两种状态，截图后自动退出。截图位于 `run/screenshots/`，其中 `wagon-entity-variants.png` 显示四种实体车型与四个方向。
- `python tools/verify_item_screenshot.py`：检查客户端截图中标准 16 × 16 格与放大格的物品边界和居中情况。
- `gradlew build`：生成 `build/libs/tm_wagon-1.0.0.jar`；测试类与测试结构不打包进发布 JAR。
- `tools/export_wagon_parts.py`：从工作区 Blockbench 模型导出部件几何、贴图和物品显示资源，简化碰撞形状由 `tools/simplified_collision.py` 定义。生成结果已放入 `src/main/resources/`，正常构建不依赖建模子项目。

像素图通过内置 imagegen 工具生成，并以最近邻缩放保存为 64 × 64；[完整生成提示词](tools/wagon_icon_prompt.txt)。最终资源为 `src/main/resources/assets/tm_wagon/textures/item/wagon_icon.png` 和 `src/main/resources/tm_wagon.png`，两者内容相同。


Installation information
=======

This template repository can be directly cloned to get you started with a new
mod. Simply create a new repository cloned from this one, by following the
instructions provided by [GitHub](https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-repository-from-a-template).

Once you have your clone, simply open the repository in the IDE of your choice. The usual recommendation for an IDE is either IntelliJ IDEA or Eclipse.

If at any point you are missing libraries in your IDE, or you've run into problems you can
run `gradlew --refresh-dependencies` to refresh the local cache. `gradlew clean` to reset everything 
{this does not affect your code} and then start the process again.

Mapping Names:
============
By default, the MDK is configured to use the official mapping names from Mojang for methods and fields 
in the Minecraft codebase. These names are covered by a specific license. All modders should be aware of this
license. For the latest license text, refer to the mapping file itself, or the reference copy here:
https://github.com/NeoForged/NeoForm/blob/main/Mojang.md

Additional Resources: 
==========
Community Documentation: https://docs.neoforged.net/  
NeoForged Discord: https://discord.neoforged.net/

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

先在地面放置装配架，再用载荷主体右键装配架顶面。一次放置会生成完整货台、倾斜脚板、两侧上下车踏板和车轴的多方块结构。装配架朝向由放置方向决定，车头朝向放置者。

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

装配架的碰撞箱固定为一格，平台模型顶面仍为 1.375 格，对齐货板底面。其他部件的碰撞箱由现有模型裁切至各占用格；斜面和车轮采用 1/32 格精度的阶梯近似。结构尚不转化为实体，本阶段没有合成表。

### 验证与开发

- `gradlew runGameTestServer`：验证旋转、安装、失败不扣物品、共享格拆除、存档、外部移除、实体阻挡及掉落行为。
- `gradlew runClientSmokeTest`：自动打开开发客户端，渲染八种方块物品、图标及马车组合，截图后自动退出。截图位于 `run/screenshots/`。
- `gradlew build`：生成 `build/libs/tm_wagon-1.0.0.jar`；测试类与测试结构不打包进发布 JAR。
- `tools/export_wagon_parts.py`：从工作区 Blockbench 模型导出部件几何、碰撞输入、贴图和物品显示资源。生成结果已放入 `src/main/resources/`，正常构建不依赖建模子项目。

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

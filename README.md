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

装配架采用前后两组交叠的「> / <」铰接木臂，关节有铁铆钉。放置时默认展开，平台顶面约 1.375 格高。空手右键支撑柱或平台可折叠，再次右键展开，完整收放约 1 秒；折叠后平台约 0.584 格高，碰撞按 1/32 格精度定义，动画期间保持切换前的形状，结束时一次性切换到新形状。动画期间的重复右键无效，动画完成并提交状态后才接受下一次点击；状态和进度同步到客户端并保存。

收放前检查整个运动范围内的方块、实体、保护和区块加载情况；动画期间预留所需格，保持原有碰撞；结束时一次性更新碰撞并释放多余代理格。已安装货架在方块状态保持原有高度；完整装配与实体、方块之间的转换见下文。

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

仅完全展开且停止运动的装配架接受配件。必需组件为载荷主体、单人或双人座位、单马或双马辕、两只小前轮和两只大后轮；必需槽位单独定义，后续可选组件不会自动变为必需。配件未齐全时右键提示错误，保持展开。配件齐全后右键立即生成一个 `tm_wagon:wagon` 实体并移除配件方块，装配架同时开始收缩。没有配件的装配架仍可伸缩。切换开始后，右键输入在动画结束并提交最终状态之前均被忽略；长按右键只触发一次，松开后才能再次操作。

在缩回的装配架旁停放马车后右键，装配架升起，动画结束时统一检查并还原所有配件方块。允许马车中心与装配架中心水平偏离 1.5 格、竖直偏离 0.75 格，还原时按装配架方向对齐。还原检查涵盖方块、流体、实体、区块、世界边界和操作权限；失败保留实体与配件数据、提示原因并自动收架，可以再次尝试。还原过程中其他装配架不能同时接管同一辆车。

实体由服务端的专用支撑与重力系统控制，支持牵引驾驶、坡面、坠落及无马时的玩家推动。单人座位容纳一名乘客，双人座位容纳两名；右键瞄准座位入座；双人座按点击的左右半边分配座位，座位占用时提示点击另一侧。座位独立记录并同步，其他乘客下车不会改变剩余乘客的位置。使用原版潜行下车时站到对应座椅的坐垫上方。实体乘客采用原版骑乘机制，还原成功时自动下车并移至车体外的空位。配件组合、连续朝向、姿态、骑乘和牵引关系支持存档；当前不保存货物，也不包含合成配方。

实体只注册一个主体，复用方块的简化碰撞体，不创建会逐个更新的碰撞子实体。方块碰撞按配件组合与朝向共享缓存；移动实体将主要体积分段，按连续朝向、前后倾角、左右倾角及车辕姿态更新保守包围体，并直接参与原版碰撞查询，保留车厢内部空隙；另外只为马车维护跨区块索引，避免长车辕伸入相邻区块时漏掉碰撞。选中检测也使用部件形状，避免实体的大范围包围盒挡住装配架的点击。双马辕中央杆较初版加长半格，前横杆及铁环、挂钩一起前移半格，方块、物品、实体与碰撞同步更新；旧存档的配件碰撞会按当前碰撞数据重新生成。实体支持连续转向，方块化时按装配架的四方向朝向对齐。

### 牵引、驾驶与简化物理

用原版拴绳牵引成年马或驴，在实体马车的单马辕两根杆头任意一端右键，或在双马辕前横杆左右两侧分别右键。每个位置绑定一匹动物，移动至牵引位置，转移玩家已经使用的拴绳，不再次扣除背包中的绳子。幼年动物、骡、其他动物、已有乘客的动物及已绑定动物不接受安装；位置需要有地面和足够空间。单马辕需要一匹，双马辕需要两匹才能驾驶。

绑定后的可见拴绳连接至车夫脚板下方木横梁的前侧，连接点随整车朝向、倾斜、前轴转向及车辕升降插值移动。马车实际行驶时，牵引马和驴停止吃草及扬蹄，头与身体朝向牵引方向，腿部使用原版骑乘马的行走动画计算；开始移动时立即隐藏残留的吃草、扬蹄动画混合。停车后恢复原版待机行为，卸载牵引后不再限制动物动作。

单人座位玩家或双人座左侧玩家是唯一驾驶员，右侧乘客始终不能操作。使用游戏设置中的前后左右移动键；前进速度 0.234 格/刻（4.68 格/秒），倒车 0.0585 格/刻（1.17 格/秒），无加速。左右键调整前轴与车辕转向，只有行进或倒车时改变整车朝向，不能横移或原地旋转。服务端校验驾驶员，输入超过 10 刻未更新、驾驶员离座或装配架锁定时停止接收旧操作。

没有绑定任何马匹时，站在地面上的玩家可贴近车厢、车轮或轮轴等任意实际碰撞部位，沿车轮滚动的方向走动来推动前进或后退，速度均为 0.025 格/刻（0.5 格/秒）。不再限定车头和车尾区域，贴着车轮侧面沿车辆前后方向走动也可推动。站着不动、远处输入、走离接触面及纯横向挤压不会推动，车辆不会左右平移。车上左侧驾驶员可用左右键控制推动时的转向，右侧乘客不能控制；驾驶员的前后键不会代替车外的推动。多人相反方向的推动相互抵消，不叠加速度。离开接触、松开移动键或输入中断后停止推动，使用原有碰撞、地面支撑、车轮、音效和粒子逻辑。绑定任意一匹马（包括双马辕只绑定一匹）、还原锁定、坠落、前后倾斜达到 35° 或左右倾斜达到 30° 时禁止玩家推动；正常坡面可推动。

重心固定在车体局部坐标 `(0, 1.5, 0)`，不计算乘客、货物重量、摩擦或刚体关节。四轮检测附近方块的实际碰撞顶面，一格以内的上下坡采用平滑姿态调整；正常前后倾斜最大约 25°，左右倾斜最大约 22°，允许部分轮子悬空。为了避免低矮上下车踏板卡住台阶，前方使用短的虚拟过渡坡提前抬升；栅栏、墙和过高障碍不能作为可攀爬地面。车身、踏板、车辕负责主要运动阻挡，车轮以地面接触计算承重，不模拟独立车轮刚体。

马匹以自身当前脚下高度判断下一步，四轮分别记录最近的地面接触高度，支持连续的一格台阶，不要求后轮先追上马匹的高度。单次高度变化仍不得超过一格；过陡坡面可让部分轮子悬空，车身倾角保持上述上限。连续上坡时车辕允许向上摆动至 55°，向下仍限制为约一格的牵引高度差。轮位接触记录随存档保存，坠落及明显位置跳变时重新采样。

马匹无法在允许范围内着地时，车辕逐渐下降至约一格高度差对应的角度。每匹马连续悬空 60 刻（正常游戏速度约 3 秒）后自动脱离并掉落一根拴绳；期间成功恢复着地则计时清零，下一次悬空重新计时，坠落过程中也遵循此规则。落点需有足够空间，不能仅因附近存在地面就清零。两个前轮都失去支撑时，进入重力与前倾滑落；后轮先失去支撑则向后处理。坠落时保留运动趋势，允许更大倾角；角部或车辕首次碰地后仍保留重力与滑动，直到轮子落稳且车身能安全摆正，才恢复普通驾驶。车轮相位按实际轮位行程除以各自半径计算，倒车反转，转弯时内外轮独立变化。

失稳时继续接受驾驶员的前进、倒车和转向输入。有地面接触的侧翻、前后翻或完全倒扣状态下，前后键配合左右键可逐步挪动并摆正车身；每个小幅姿态变化检查地面、上方空间、障碍物和其他实体。马匹已脱落时也允许驾驶员以普通速度的 35% 尝试脱困，摆正后恢复必须有马牵引的规则。空中仍遵循重力，不能靠无马脱困操作悬浮或翻正。

移动并接地时播放木轮滚动声，在轮后按地面材质生成少量粒子，粒子约 0.3–0.45 秒消失。音效来自 [Sonicquinn 的 CC0 木轮小车录音](https://freesound.org/people/Sonicquinn/sounds/518790/)，使用单声道 OGG 流式播放，来源与许可随资源打包。动态骨骼在绘制时按实例恢复，避免运动实体的转向和轮相位泄漏到静态方块模型。

手持剪刀右键绑定动物可卸载并返还一根绳子。成功方块化或实体移除也会解除牵引、恢复动物控制并返还绳子；转换失败保持绑定。存档保存牵引 UUID 和坠落状态；区块卸载保留绑定，已返还但暂未加载的动物通过清理记录恢复，避免重复掉落。实体耐久为 20 点，损毁时取消乘坐与牵引，掉落 6–10 块橡木木板和 1–2 个绿色羊毛，不返还完整部件。方块部件硬度 2、爆炸抗性 3，并加入斧子加速挖掘标签。

### 验证与开发

- `gradlew runGameTestServer`：验证旋转、安装、失败不扣物品、共享格拆除、存档、实体阻挡、驾驶权限、车速与轮速、台阶、侧倾、悬崖、三秒悬空与重新着地、无马推动及转向、牵引卸载、方块化及损毁掉落。
- `gradlew runClientSmokeTest`：自动打开开发客户端，渲染八种方块物品、图标、马车组合和装配架两种状态，检查运动骨骼的缓存隔离及单声道 OGG 解码，截图后自动退出。截图位于 `run/screenshots/`，其中 `wagon-entity-variants.png` 显示四种实体车型与四个方向。
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

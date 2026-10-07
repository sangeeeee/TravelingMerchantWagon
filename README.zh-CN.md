# TravelingMerchantWagon · 旅行商人马车

[English](README.md) | **简体中文**

TravelingMerchantWagon 为 Minecraft 添加了可以自由搭配的木制马车。亲手装好零件，拴上马匹，带着货物和同伴踏上旅途。无论是搬家、远行，还是扮演旅行商人，马车都可以成为随行的仓库、工作间和休息处。


支持 NeoForge／Fabric 26.3、NeoForge 26.2、NeoForge／Fabric 1.21.1 和 Forge 1.20.1；各版本兼容范围见 [26.3 版本说明](versions/mc-26.3/README.md)及 [26.2 版本说明](versions/mc-26.2/README.md)。

## 可以做什么？

- **组装自己的马车。** 选择普通、加长或加宽货物车厢，搭配单马或双马辕，以及单人、双人或三人车夫座椅。木制零件可以混搭，坐垫和布料也有多种配色。
- **驾车或手推。** 用拴绳连接马、驴、骡、骆驼、骷髅马或僵尸马，坐在驾驶位控制前进、加速、刹车和倒车。拴上两匹拉车动物后，加速更快，货物对冲刺速度的影响也更小。未拴马时，可以手推马车调整位置或帮助脱困。
- **装载实用货物。** 将允许的方块放到货位上，在车上直接打开已适配的容器、使用工作方块。马车转换形态时，货物和存储的物品会一起保留。
- **带上同伴。** 在货箱中放置木凳供乘客入座，铺上寝具供夜间休息（26.3 使用原版麦秆床，26.2 和旧版本使用马车草席）。已睡着的玩家和女仆会随行驶中的马车继续安睡。
- **安装可选配件。** 座椅下方可以加装马车柜，车厢上方可以选择可卷起的覆盖布，或带有前后布帘的车篷。布料组件会适应车厢尺寸。
- **停靠、改装和回收。** 在装配架上将马车切换为方块形态或可行驶的实体形态；用木工锤拆卸马车，回收零件和货物。

## 如何开始？

1. 放下马车装配架，将货物车厢安装到展开的平台上。
2. 装上车夫座椅、车辕、两个小前轮和两个大后轮。
3. 右键装配架，将马车装配为实体。
4. 用拴绳牵来拉车动物，右键车辕将它们连接到马车。
5. 坐到驾驶位，使用移动键驾驶。前进时按一下疾跑键可以进入更快的行驶状态，松开前进键后恢复普通速度。

安装 **Patchouli** 后，可以通过 **《马车夫手册》** 查看装配示意图、合成配方和操作说明。玩家进入世界时会自动获得手册，服务器可关闭赠送；也可以用书、拴绳和任意木板合成。

## 模组兼容

以下兼容均为可选，按需安装对应模组即可。兼容范围随游戏版本有所不同：26.2 NeoForge 及 26.3 的两种加载器均支持 Carry On 与旅行者背包，NeoForge 版还支持精妙背包；Patchouli、车万女仆／TACZ 及 Sable 兼容由旧版本提供。

| 模组 | 可以怎样配合马车使用 |
| --- | --- |
| [Patchouli](https://modrinth.com/mod/patchouli) | 提供《马车夫手册》，包含装配示意图、配方和玩法说明。 |
| [旅行者背包](https://modrinth.com/mod/travelersbackpack) | 将背包装到货位上，直接打开原本的存储界面并保留其中物品。独立睡袋占两个货位，供玩家睡觉且不改变重生点。 |
| [精妙背包](https://www.curseforge.com/minecraft/mc-mods/sophisticated-backpacks) | 运输背包，并在车上打开它原本的存储和设置界面。 |
| [Carry On](https://modrinth.com/mod/carry-on) | 将允许搬运的货物抱上车或抱下车，也可以将抱着的实体放到马车木凳上入座。使用当前设置的搬运键和搬运规则。 |
| [车万女仆](https://www.curseforge.com/minecraft/mc-mods/touhou-little-maid) | 提供“马车副驾驶”和“乘坐马车”任务；副驾驶可以在座位上使用弓、弩或 TACZ 枪械攻击敌对实体，休息时可以睡在草席上；原本的照片、魂符和相机也能按各自规则配合马车使用。 |
| [Sable](https://modrinth.com/mod/sable) | 马车移动与乘客入座时会考虑外部物理结构。先在普通世界中完成方块组件装配，再驾驶实体马车到结构附近。 |

采用兼容原版存储方式的模组箱子、木桶，也能作为可交互货物使用，包括已有适配的旧版本 **[BetterEnd](https://modrinth.com/mod/betterend) 中使用 BCLib 的木桶**。具体兼容情况取决于容器的存储方式。

对于上述两种背包：手持背包潜行右键空货位即可装载，右键已放置的背包打开界面，潜行右键取出。满足 Carry On 的搬运条件时，优先执行搬运。

马车动画需要安装 **[GeckoLib](https://modrinth.com/mod/geckolib)**。服务器可以通过 `tm_wagon-server.toml` 调整货物规则、驾驶体验和手册赠送。

默认情况下，实际拴上两匹拉车动物后，**前进和倒车加速度提高 20%**，货物对冲刺速度的扣减**减少 15%**。这两项加成均可在同一配置文件的 `draftTeam` 分组中调整。

Fabric 版本还需要 Fabric API 与 [Forge Config API Port](https://modrinth.com/mod/forge-config-api-port)。可选兼容模组使用对应 Fabric 版本，其中女仆使用 Orihime 移植版，Sophisticated Backpacks/Core 和 TaCZ 使用 Fabric 移植版。

## 许可证

源码构建方式及多版本项目结构见 [BUILDING.md](BUILDING.md)。

TravelingMerchantWagon 使用 **GNU 通用公共许可证第 2 版，仅限该版本（GPL-2.0-only）**。完整条款见 [LICENSE](LICENSE)，版权说明见 [NOTICE](NOTICE)。原始 NeoForged MDK 模板的 [MIT 许可声明](TEMPLATE_LICENSE.txt) 予以保留。

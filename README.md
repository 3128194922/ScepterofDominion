# Scepter of Dominion

`ScepterofDominion` 是一个基于 Minecraft Forge 1.20.1 的 RTS 风格宠物/生物指挥模组。

它把“收编队伍、设置焦点、下达移动/攻击命令、编队、路径点、收容/释放”这些功能整合到两把权杖里：

- `统御权杖`：控制已归属于玩家的驯服生物
- `支配权杖`：控制未驯服的普通 `Mob`

项目当前自带一个简短说明文件 `README.txt`，而本 `README.md` 用于补充完整用法与接入说明。

# 参考MOD：
宠物收容功能
https://github.com/MCMostWolf/PetConnect

权杖阵型系统和最初的指挥体系
https://github.com/misaka10843/ShinColle

权杖指挥体系参考
https://github.com/Minecraft-LightLand/ModularGolems

## 环境

- Minecraft: `1.20.1`
- Forge: `47.4.6`
- Java: `17`
- Mod ID: `scepterofdominion`

## 核心物品

### 1. 统御权杖

物品 ID：

```text
scepterofdominion:scepter_of_dominion
```

作用：

- 只能控制归属于当前玩家的生物
- 典型对象是 `OwnableEntity`
- 对常见驯服动物会注入额外 AI，用于接受移动、攻击和编队命令

默认规则：

- 如果实体在 `scepterBlacklist` 黑名单中，则无法控制
- 如果实体已经被其他权杖控制，则不能重复加入

### 2. 支配权杖

物品 ID：

```text
scepterofdominion:dominion_scepter
```

作用：

- 主要用于控制未驯服的普通 `Mob`
- 不允许控制 `TamableAnimal`
- 控制成功后会给目标打上 `DominionOwner` 持久化标记

默认规则：

- 若 `dominionWhitelist` 为空，则允许控制所有非可驯服 `Mob`
- 若 `dominionWhitelist` 不为空，则只能控制白名单内实体

## 基础玩法

### 1. 组建队伍

使用权杖左键点击可控制目标：

- 若目标尚未加入当前权杖队伍：加入队伍
- 若目标已经在队伍中：设为焦点单位

队伍上限：

- 最多 `6` 个成员

加入成功后：

- 权杖会把成员 UUID、名称等信息写进物品 NBT
- 第一个加入的成员会自动成为焦点
- 目标会被标记为已被该权杖控制
- 会自动执行一次“收容”逻辑，把该单位转移到存储维度中等待调度

### 2. 移除队伍成员

潜行 + 左键点击已在队伍中的单位：

- 从当前权杖队伍移除
- 清除实体上的控制标记

### 3. 切换模式

潜行 + 左键空挥：

- 在 `单选模式` 与 `阵形模式` 之间切换

两种模式区别：

- `单选模式`：主要对焦点单位下令
- `阵形模式`：对整个队伍按编队位置统一下令

### 4. 下达移动与攻击命令

右键是主要指挥入口：

- 右键地面：命令移动
- 右键敌对生物：命令攻击

其中：

- 移动命令会记录一个 `CommandTarget`
- 攻击命令会记录一个 `AttackTarget`
- 客户端会把当前命令目标高亮显示出来

### 5. 路径点模式

冲刺 + 右键：

- 添加一个路径点

冲刺 + 左键：

- 执行当前已记录的路径点任务

路径点支持两类任务：

- `MOVE`：移动到某个位置
- `ATTACK`：攻击某个目标

默认最大路径点数量：

- `6`

可在配置中修改。

## 管理界面

潜行 + 右键主手权杖，会打开管理界面。

界面主要包含三部分：

- 阵形选择
- 收容 / 释放按钮
- 队伍成员列表

### 阵形选择

当前一共提供 `6` 种阵形：

- `0`：单纵
- `1`：复纵
- `2`：轮形
- `3`：梯形
- `4`：单横
- `5`：无

在界面中点击阵形按钮后，会把阵形编号写入权杖 NBT。

### 队伍成员列表

右侧列表会显示当前权杖记录的最多 6 名成员：

- 点击成员名：将其设为焦点
- 点击 `X`：将其从队伍中移除

### 收容 / 释放

界面左下包含两个按钮：

- `收容`
- `释放`

作用：

- `收容`：把队伍成员转移到 `scepterofdominion:storage` 维度
- `释放`：把存储中的队伍成员传送回玩家当前位置

## 收容系统

这个模组自带一个专用存储维度：

```text
scepterofdominion:storage
```

对应资源：

- `data/scepterofdominion/dimension/storage.json`
- `data/scepterofdominion/dimension_type/storage.json`

收容时的行为：

- 目标被传送到存储维度固定位置附近
- 临时设置 `NoAI`
- 开启 `NoGravity`
- 保存并保留血量

释放时的行为：

- 目标被传送回玩家当前位置
- 恢复 `NoAI = false`
- 恢复重力
- 会把最新释放坐标写回权杖内的成员信息

这套设计适合做：

- 战斗单位收纳
- 跨维度携带宠物/部队
- 避免单位在常规世界中到处乱跑

## 编队逻辑

阵形模式下，队伍会围绕你指定的中心点重新分配站位。

站位会受以下因素影响：

- 当前阵形编号
- 队伍成员数量
- 实体碰撞箱宽度
- `formationSpacingMultiplier` 配置值

因此：

- 体型大的实体会自然占用更大间距
- 调整间距倍率后，可以让编队更紧凑或更分散

## 客户端可视化

主手拿着权杖时，客户端会渲染指挥辅助框线：

- 焦点单位或队伍成员高亮框
- 当前攻击目标红框
- 当前移动目标青框
- 视线指向单位/方块高亮
- 路径点连线与标记

这部分主要用于 RTS 式的实时指挥反馈。

## 配置项

项目当前注册的是 Forge `COMMON` 配置，核心项如下。

### `formation.spacingMultiplier`

类型：

- `double`

默认值：

- `1.0`

作用：

- 控制编队时实体之间的间距倍率

### `control.maxWaypoints`

类型：

- `int`

默认值：

- `6`

范围：

- `1` 到 `20`

作用：

- 控制路径点模式允许保存的最大路径点数量

### `control.scepterBlacklist`

类型：

- `List<String>`

作用：

- 指定哪些实体不能被 `统御权杖` 控制

格式示例：

```toml
scepterBlacklist = ["minecraft:wolf", "minecraft:cat"]
```

### `control.dominionWhitelist`

类型：

- `List<String>`

作用：

- 指定哪些实体可以被 `支配权杖` 控制

规则：

- 留空时：允许所有非可驯服普通 `Mob`
- 非空时：只允许白名单中的实体

格式示例：

```toml
dominionWhitelist = ["minecraft:zombie", "minecraft:skeleton"]
```

## 使用流程示例

一个常见流程如下：

1. 主手拿起一把权杖
2. 左键点击可控制单位，将它们加入队伍
3. 再次左键队伍成员，设置焦点单位
4. 潜行 + 左键切换为单选或阵形模式
5. 右键地面命令移动，或右键敌人命令攻击
6. 冲刺 + 右键连续记录路径点
7. 冲刺 + 左键一次性执行路径任务
8. 潜行 + 右键打开 GUI，调整阵形或收容/释放队伍

## 获取方式

当前项目中：

- 已注册创造标签页
- 已注册两把权杖物品
- 但没有提供配方 JSON

因此当前更适合：

- 创造模式直接拿取
- 通过 `/give` 获取
- 或后续自行补充配方 / 战利品表 / KubeJS 发放逻辑

示例：

```mcfunction
give @p scepterofdominion:scepter_of_dominion
give @p scepterofdominion:dominion_scepter
```

## 实现概要

项目当前主要由以下模块组成：

- `AbstractScepterItem`：权杖通用队伍、模式、命令、路径点逻辑
- `ScepterOfDominionItem`：驯服生物控制逻辑
- `DominionScepterItem`：未驯服普通 `Mob` 控制逻辑
- `StorageDimension`：收容与释放系统
- `ScepterScreen` / `ScepterMenu`：管理 GUI
- `ClientInputHandler` / `ClientEvents`：客户端输入和高亮渲染
- `Packet*`：模式切换、右键命令、GUI 动作、同步等网络包

## 注意事项

- 一只生物同一时间只能被一把权杖控制
- 队伍上限固定为 `6`
- `统御权杖` 面向有归属关系的实体，不是所有生物都能加
- `支配权杖` 默认只控制非可驯服普通 `Mob`
- 当前没有内置合成表
- 收容系统依赖自带存储维度；若维度资源缺失，收容会失败

## 开发与运行

### 运行客户端

```powershell
.\gradlew runClient
```

### 运行服务端

```powershell
.\gradlew runServer
```

### 构建 Jar

```powershell
.\gradlew build
```

构建产物通常位于：

```text
build/libs/
```

## License

项目配置中声明的许可证为 `MIT`。如后续仓库补充正式授权文件，请以实际 `LICENSE.txt` 或仓库声明为准。

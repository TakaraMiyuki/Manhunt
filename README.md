# 猎人游戏 Manhunt

Minecraft Java **26.2** / NeoForge **26.2** 猎人追杀玩法模组（**0.3.6-beta**）。

灵感来自 Dream 的猎人游戏：**逃生者**在躲避追杀的同时跑图攒里程、激活检查点并击杀末影龙获胜；**猎人**负责追杀所有逃生者。在原生**经典模式**之外，0.3.x 新增了玩法完全重构的**赏金猎人模式**。

> 详细数值与机制文档见 [docs/玩法与机制介绍.md](docs/玩法与机制介绍.md)。

---

## 两种游戏模式

| | 经典模式 | 赏金猎人模式 |
|---|---|---|
| 开局 | `/manhunt admin start ...` | `/manhunt admin start bounty ...` |
| 检查点 | 单链无限检查点，逐个解锁 | **2–5 个同时存在**（按人数），自由选择前往；总里程池达标后仅剩末地要塞 |
| 里程 | 个人里程（猎人 1/2 效率） | 个人里程（猎人 **0.7** 效率）+ **总里程池**（只增不减） |
| 死亡 | 逃生者死亡即淘汰 | 逃生者 **3 条命**：死亡失去 1/3 个人里程、原地复活（距死亡位置 300–500 格）、**60 秒罗盘赦免**；末地死亡直接淘汰 |
| 技能 | 激活检查点激活者抽卡 + 全员各抽一张 | 激活者超级抽奖；**总里程池每跨一档全体"技能三选一"**（最多 6 次+要塞彩卡） |
| 猎人量表 | 士气量表（1 伤=1 士气） | **赏金量表**（1 伤=3 赏金，击杀得里程÷3×排名倍率），按猎人数三套分档 |
| 特色 | 动态性能档位（4 档） | **头号赏金金色发光**、右侧赏金榜、超级疾跑（C 键）、专属动态性能 |

## 共通机制

- **检查点**：粒子圈标记，靠近自动激活；激活后全员奖励；末地要塞检查点含末影之眼 ×12
- **里程与资源抽奖**：每 200 里程触发一次；老虎机动画 → 领取模式（滚轮选择/中键标记/右键领取，未标记丢弃）；里程抽奖间相互覆盖，其余来源顺延排队
- **技能卡（联动 [SkillCards](https://github.com/TakaraMiyuki/SkillCards)）**：39 张主动/被动卡；主动卡收进 **9 格技能栏**（按住左 Alt 呼出，滚轮/数字键切换、右键释放、按住左键看详情，背包界面可调顺序）；被动卡自动进入 **Curios 6 格被动饰品栏**常驻生效；加权抽取（普通 54/稀有 30/黑卡 30/彩卡 4）且不可重复
- **罗盘**：猎人追踪罗盘（右键切换目标、偏航红脉冲、近距金脉冲）；逃生者检查点罗盘（赏金模式可右键切换检查点）
- **昼夜 7:3**：夜间时钟加速
- **经验条接管**：参与者量表占用经验条（猎人量表增长后 5 秒接管），经验获取/掉落禁用
- **免伤**：末影龙与末影人无法伤害猎人；参与者摔落保底 1 颗心；同阵营伤害上限 2 点（击退不变）
- **进食加速**：对局中进食/饮用 1.5 倍速

## 安装

- NeoForge 26.2 实例（服务端/客户端都要装），下载 [Releases](https://github.com/TakaraMiyuki/Manhunt/releases) 的 jar 放入 `mods/`
- 可选联动：[SkillCards 0.2.0-beta+](https://github.com/TakaraMiyuki/SkillCards/releases)（技能卡与被动饰品栏）、[Curios](https://www.curseforge.com/minecraft/mc-mods/curios)（被动饰品栏）、Crafting on a Stick（便携工作台自动装备）
- 依赖 NeoForge `[26.2.0.82,)`，在 26.2.0.88 实例上可正常加载

## 游戏流程

1. **开局**：`/manhunt admin team add hunter|runner <玩家>` 分队（或 `start [bounty] random <猎人数>`），然后 `start`。全员传送出生点，60 秒逃跑倒计时（猎人被定身）
2. **追逐**：猎人罗盘追杀；逃生者跑图激活检查点、攒里程触发抽奖与技能抽取
3. **末地**：里程/池达标 → 要塞检查点（彩卡+末影之眼×12）→ 开启传送门 → 击杀末影龙获胜
4. **胜负**：龙死逃生者胜；逃生者全部淘汰猎人胜（solo 模式不结算）

## 指令

管理类需 OP 权限 2，位于 `/manhunt admin ...`：

| 指令 | 说明 |
| --- | --- |
| `team add hunter\|runner <玩家>` / `team clear` | 队伍管理 |
| `start` / `start random <猎人数>` / `start solo` | 经典模式开局 |
| `start bounty` / `start bounty random <猎人数>` / `start bounty solo` | **赏金猎人模式**开局 |
| `stop` | 终止对局 |
| `debug status` | 模式/里程池/士气赏金/档位/检查点总览 |
| `debug mileage add\|set <n>` | 调试里程（赏金模式同步总里程池） |
| `debug morale add\|set <n>` / `debug bounty add\|set <n>` | 调试士气/赏金量表 |
| `debug draw` / `debug superdraw` / `debug skilldraw` | 手动资源/超级抽奖/技能三选一 |
| `debug card [common\|rare\|rainbow\|black]` | 直接获得技能卡 |
| `debug slot set <1-4>\|auto` / `debug nextcp stronghold` | 经典模式调试 |
| `debug unlock` / `debug tp <n>` / `debug compass <玩家>` | 检查点/罗盘调试 |
| `/manhunt status` | 所有玩家可查的简要状态 |

## 持久化与开发

- 对局状态保存在 `world/manhunt_state.json`（模式/里程/里程池/命数/赏金/检查点/技能栏），服务器重启后恢复（逃跑倒计时统一按追逐阶段恢复）
- 所有数值集中在 `src/main/java/com/example/manhunt/GameConfig.java`
- 构建插件 ModDevGradle 2.0.146 / Gradle 9.2.1 / JDK 25；编译期 NeoForge **26.2.0.82**（0.88 会触发 NFRT 重编译失败，运行依赖为宽区间不受影响）
- 编译期引用 `../SkillCards/build/libs/skillcards-0.2.0-beta.jar` 与 `libs/curios-neoforge-16.0.0+26.2.jar`（联动引用收敛在 `cards/SkillCardsBridge` 与 `compat/` 桥接类）

## 已知限制（测试版）

- 检查点标记为原版方块组合，未做防破坏保护
- 抽奖/技能栏动画为程序化原版风格（预留贴图位 `assets/manhunt/textures/gui/loot/`）
- 逃跑倒计时期间里程照常累计、抽奖照常触发

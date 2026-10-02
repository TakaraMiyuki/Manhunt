# 参与开发指南（CONTRIBUTING）

感谢你参与猎人游戏 Manhunt 的开发！请先阅读本文件，了解环境搭建与协作规则。

## 许可与贡献条款

本项目采用 **All Rights Reserved**（保留所有权利）许可，版权归 © TakaraMiyuki 所有。

> **提交即授权**：你向本仓库提交的任何代码、资源或文档（以 Issue、Pull Request 或其他形式），
> 均视为无条件将相关著作权授予项目所有者 TakaraMiyuki，由其按仓库 LICENSE（All Rights Reserved）
> 与后续版本自由使用、修改与再授权。请仅在理解并接受上述条款后提交贡献。

## 环境搭建

1. 安装 **JDK 25** 与 IntelliJ IDEA（导入 Gradle 项目即可，无需其他插件配置）
2. `git clone https://github.com/TakaraMiyuki/Manhunt.git` —— **克隆后即可直接编译**，
   联动依赖（SkillCards / Curios）的 jar 已收入 `libs/`，不再依赖兄弟仓库路径
3. 本地试玩/调试：将以下 jar 放入 `run/mods/`（`run/` 目录不入库）：
   - `curios-neoforge-16.0.0+26.2.jar`（被动饰品栏）
   - `crafting_on_a_stick`（便携工作台联动）
   - [SkillCards 0.2.0-beta](https://github.com/TakaraMiyuki/SkillCards/releases)（技能卡）
4. 启动：`./gradlew runClient`（客户端实机）/ `./gradlew runServer`（服务端冒烟）

## 修改规则

- **平衡数值只改一处**：`src/main/java/com/example/manhunt/GameConfig.java`
  （SkillCards 侧为 `CardConfig.java`），并同步更新 `docs/玩法与机制介绍.md`
- **兼容性红线**：不得让本仓库在编译期依赖兄弟仓库的源码路径——联动 jar 一律放 `libs/`；
  两侧接口变更后需同步替换 `libs/skillcards.jar`（Manhunt）与 `libs/manhunt.jar`（SkillCards）
- **经典模式回归**：所有赏金模式逻辑以 `ManhuntGame.isBounty()` 分支隔离，
  改动不得改变经典模式的既有行为
- **26.2 API 陷阱**：参考仓库提交历史与 `docs/`；数据包注册表 Holder 必须来自玩家实时
  registryAccess；经验同步必须更新 totalExperience 等（详见既有代码注释）

## 提交前自查

- [ ] `./gradlew build` 通过
- [ ] `./gradlew runServer` 启动无 ERROR/FATAL，核心指令可用（`manhunt admin debug status`）
- [ ] 改动涉及数值 → 文档已同步
- [ ] 提交信息使用中文一句话说明行为变化（参照 `git log` 风格）

## 协作流程

1. 从 `main` 拉出功能分支（`feat/xxx` 或 `fix/xxx`）
2. 开发并按自查清单验证
3. 发起 Pull Request 到 `main`，描述清楚改动动机与测试方式
4. 由仓库所有者审核合并；版本号变更与 GitHub Release 由所有者统一发布

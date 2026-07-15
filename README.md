# MoFish Tool

MoFish Tool 是一个运行在 IntelliJ IDEA 内的行情助手插件，用于在开发过程中查看自选行情、持仓收益和价格提醒，减少在 IDE 与行情页面之间切换。

- **自选行情**：支持股票、指数、基金、虚拟币和外汇。
- **持仓收益**：汇总股票、基金和虚拟币持仓收益。
- **状态栏展示**：轮播收益摘要和已选行情信息。
- **行情提醒**：价格或涨跌幅达到条件时通过 IDE 通知提醒。
- **在线五子棋**：支持邀请对局、自动匹配、战绩统计和透明悬浮棋盘。
- **数据兜底**：远程行情暂时不可用时使用静态或占位数据保持界面可用。

- **插件 ID**：`online.mofish.tool`
- **当前版本**：`1.0.5`
- **支持平台**：IntelliJ IDEA `2025.x+`
- **主要语言**：Kotlin
- **UI 技术**：IntelliJ Swing/JB UI
- **构建系统**：Gradle Kotlin DSL + IntelliJ Platform Gradle Plugin

## 快速开始

**运行插件（沙箱 IDE）**

```bash
./gradlew runIde
```

**运行测试**

```bash
./gradlew test
```

**验证插件兼容性**

```bash
./gradlew verifyPlugin
```

**打包插件**

```bash
./gradlew buildPlugin
```

构建产物位于 `build/distributions/`。

## 功能现状

插件当前内置五个行情模块和一个可选的五子棋模块：

- mofish股票
- mofish指数
- mofish基金
- mofish虚拟币
- mofish外汇
- mofish五子棋（默认隐藏，可在设置中开启）

核心能力包括：

- 自选标的添加、删除、搜索建议和股票分组管理
- 股票、指数、基金、虚拟币、外汇行情查看
- 卡片视图与表格视图切换，股票表格列可配置
- 股票、基金、虚拟币持仓配置和收益计算
- 价格与涨跌幅提醒，通过 IDE 通知触发
- 工具窗口模块显隐配置
- 五子棋在线用户、邀请对局、自动匹配、棋盘落子、认输和战绩统计
- 五子棋昵称与玩家身份持久化，以及可配置透明度的置顶悬浮棋盘
- 状态栏行情与收益轮播
- 股票与基金趋势详情页，通过 IDE 自定义 Web 编辑器打开
- 股票、指数、虚拟币自动刷新；基金和外汇保留手动刷新
- 行情数据失败时按模块回落到静态或占位数据，避免整个 UI 空白

`NEWS` 刷新模块只保留为旧配置兼容项，不再作为可见模块展示或刷新。

## 架构主线

项目的主链路可以按“数据源 -> 服务状态 -> UI 渲染”理解：

1. `MoFishHttpClient` 封装 OkHttp、JSON 解析和 HTML 解析。
2. 各市场 provider/client 拉取并解析远程行情数据。
3. `RemoteMoFishDataSource` 汇总远程数据，并在单个模块失败时回落到 `StaticMoFishDataSource`。
4. `MoFishProjectService` 维护项目级工作区状态、缓存命中信息、当前视图和刷新事件。
5. `MoFishWatchlistService` 聚合项目状态、设置、内存缓存、自动刷新调度器状态，并负责触发提醒检查。
6. `MoFishToolWindowPanel` 和各模块面板订阅聚合状态并渲染工具窗口。
7. `MoFishStatusBarWidget` 基于同一份聚合状态展示收益和行情轮播。

五子棋模块通过 WebSocket 与独立服务通信；服务端负责在线用户、匹配和对局状态，并持久化玩家战绩。

自动刷新分为两层：`MoFishRefreshSchedulerService` 负责注册项目并按最短启用间隔唤醒；`MoFishWatchlistService` 再根据模块配置、刷新窗口和各模块间隔决定实际刷新哪些模块。

## 项目结构

```text
.
├── docs/
│   └── tree_file.json             项目目录说明索引
├── gradle/
│   ├── wrapper/                   Gradle Wrapper
│   └── libs.versions.toml         依赖版本目录
├── server/gomoku-server/          五子棋 WebSocket 服务、管理页和 Docker 部署配置
├── src/main/kotlin/online/mofish/tool/
│   ├── actions/                   打开mofish窗口的 IDE Action
│   ├── data/                      行情数据源、HTTP 客户端和各市场 provider
│   ├── domain/                    行情、持仓、提醒、收益等领域模型
│   ├── services/                  项目状态、缓存、刷新、提醒、收益计算服务
│   ├── settings/                  设置页、持仓/提醒编辑弹窗和持久化状态
│   ├── state/                     UI 聚合状态、缓存状态和事件模型
│   └── ui/                        工具窗口、状态栏、搜索弹窗和 Web 编辑器
├── src/main/resources/
│   ├── META-INF/plugin.xml        插件声明和扩展注册
│   └── icons/                     插件和工具窗口图标
├── src/test/kotlin/               数据解析和静态数据源测试
├── build.gradle.kts               Gradle 构建脚本
├── gradle.properties              项目属性
└── settings.gradle.kts            Gradle 项目与仓库配置
```

## 数据来源

- 股票、指数行情：腾讯、新浪
- 股票分时、K 线和部分详情数据：腾讯、东方财富
- 基金行情和基金搜索：东方财富
- 虚拟币行情和搜索：CoinGecko、Binance
- 外汇牌价：中国银行

网络请求统一通过 `MoFishHttpClient` 封装。远程数据不可用时，数据源会保留工作区结构并用静态样例或占位数据兜底。

## 设置与持久化

应用级设置由 `MoFishSettingsService` 持久化到 `mofish.xml`，主要包括：

- 自选股票、指数、基金、虚拟币
- 股票分组和分组归属
- 持仓和提醒规则
- 工具窗口可见模块
- 股票表格列配置
- 状态栏显示内容和轮播间隔
- 各自动刷新模块的启用状态、间隔和时间窗口
- 五子棋模块显隐、玩家昵称、玩家身份和悬浮棋盘配置

注意：设置页中的某个控件不一定等同于运行时能力本身。调整设置项时需要同时检查设置 UI、持久化状态、服务层消费者和工具窗口/状态栏使用点。

## 测试

当前测试主要覆盖：

- 静态数据源的默认和空自选行为
- 股票搜索、分时、K 线、资讯 payload 解析
- Binance 虚拟币 ticker payload 解析
- 设置状态序列化与兼容性

常用验证命令：

```bash
./gradlew test
```

## 发布

```bash
./gradlew publishPlugin
```

发布前需配置 JetBrains Marketplace Token，并按目标版本执行 `verifyPlugin`。

## 许可与隐私

- License: [MIT](LICENSE)
- EULA: [EULA.md](EULA.md)
- Privacy Policy: [PRIVACY.md](PRIVACY.md)

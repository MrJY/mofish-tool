# Mofish-tool

摸鱼工具是一个基于 IntelliJ Platform 的 IDE 插件，使用 Kotlin 与 Swing/JB UI 构建。插件在 IDE 内提供“摸鱼工具”工具窗口和状态栏收益组件，内置多个独立行情模块：

- 摸鱼股票
- 摸鱼指数
- 摸鱼基金
- 摸鱼虚拟币
- 摸鱼外汇

各模块在界面和产品概念上独立展示，底层共享行情拉取、缓存、自动刷新、持仓收益计算、提醒通知和设置持久化能力。

- **插件 ID**：`online.mofish.tool`
- **版本**：`1.0.0-SNAPSHOT`
- **支持平台**：IntelliJ IDEA `2025.3+`
- **主要语言**：Kotlin
- **UI 技术**：IntelliJ Swing/JB UI

## 快速开始

**运行插件（沙箱模式）**

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

**打包构建**

```bash
./gradlew buildPlugin
```

构建产物位于 `build/distributions/`。

## 项目结构

```text
.
├── .run/                          IntelliJ IDEA 运行/测试/验证配置
├── docs/
│   └── tree_file.json             项目目录说明索引
├── gradle/
│   ├── wrapper/                   Gradle Wrapper
│   └── libs.versions.toml         依赖版本目录
├── src/main/kotlin/online/mofish/tool/
│   ├── actions/                   打开摸鱼工具窗口的 IDE Action
│   ├── data/                      行情数据源、HTTP 客户端和各市场 provider
│   ├── domain/                    行情、持仓、提醒、收益等领域模型
│   ├── services/                  项目状态、缓存、刷新、提醒、收益计算服务
│   ├── settings/                  设置页、持仓/提醒编辑弹窗和持久化状态
│   ├── state/                     UI 聚合状态、缓存状态和事件模型
│   └── ui/                        工具窗口、状态栏和通用选择弹窗
├── src/main/resources/
│   ├── META-INF/plugin.xml        插件声明和扩展注册
│   └── icons/mofishToolWindow.svg 工具窗口图标
├── build.gradle.kts               Gradle 构建脚本
├── gradle.properties              项目属性
└── settings.gradle.kts            Gradle 项目与仓库配置
```

## 核心能力

- 摸鱼股票、摸鱼指数、摸鱼基金、摸鱼虚拟币、摸鱼外汇行情查看
- 卡片视图与表格视图切换
- 自选标的添加、删除和搜索建议
- 持仓配置与收益计算
- 价格和涨跌幅提醒
- 自动刷新窗口与刷新间隔设置
- 工具窗口与状态栏收益展示

## 数据来源

- 基金行情：东方财富
- A 股/港股/美股行情：腾讯、新浪
- 虚拟币行情：CoinGecko
- 外汇牌价：中国银行

网络请求统一通过 `MoFishHttpClient` 封装，远程数据不可用时会回落到静态占位数据，保证 UI 仍可展示。

## 发布

```bash
./gradlew publishPlugin
```

发布前需配置 JetBrains Marketplace Token，并按目标版本执行 `verifyPlugin`。

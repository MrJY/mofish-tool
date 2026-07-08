# Changelog

All notable changes to MoFish Tool are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project follows semantic
versioning.

## Unreleased

- Moved the Gomoku UUID display to a dedicated settings section and avoided regenerating UUIDs on restart.
- Persisted the last Gomoku nickname and restored it when opening the Gomoku tab.
- Added a Gomoku settings toggle to show or hide the Gomoku tab, disabled by default.
- Fixed disabled tool window modules reappearing when all module toggles are turned off.
- Improved Gomoku board sizing so it adapts down to 240px before scrolling, with a softer grid style.
- Switched the Gomoku Docker Compose data mount from a named volume to local `./data`.
- 将五子棋 UUID 展示移到设置页独立模块，并避免重启时重新生成 UUID。
- 保存五子棋上次使用的昵称，并在打开五子棋标签页时自动回填。
- 新增五子棋标签页显示开关，默认关闭。
- 修复工具窗口模块全部关闭后反而恢复显示默认模块的问题。
- 优化五子棋棋盘尺寸，缩小到 240px 后再滚动，并降低棋盘线视觉存在感。
- 将五子棋 Docker Compose 数据挂载从命名卷改为本地 `./data` 目录。

## 1.0.6 - 2026-07-08

- Added a `mofish五子棋` tab with nickname registration, UUID-based identity, online users, invitations, automatic matching, board play, and resign support.
- Added persistent Gomoku player UUID settings and validation.
- Added a Python Gomoku WebSocket server with SQLite-backed player records, win/loss/game statistics, health checks, a simple admin page, and Docker Compose deployment.
- Switched the default Gomoku server endpoint to `wss://demo.mrjy.online/gomoku`.
- Improved Gomoku connection failure messages with detailed error and response information.
- 新增 `mofish五子棋` 标签页，支持昵称注册、UUID 身份、在线用户、邀请对局、自动匹配、棋盘落子和认输。
- 新增五子棋玩家 UUID 设置持久化和长度校验。
- 新增 Python 五子棋 WebSocket 服务端，使用 SQLite 持久化玩家战绩，并提供健康检查、简单管理页面和 Docker Compose 部署。
- 默认五子棋连接地址切换为 `wss://demo.mrjy.online/gomoku`。
- 优化五子棋连接失败提示，展示更具体的异常和响应信息。

## 1.0.5 - 2026-07-07

- Added add/remove support for the foreign exchange watchlist with searchable currency selection.
- Kept foreign exchange rows in user-added watchlist order.
- Added high, low, and open prices to stock card view.
- Removed the left-side `0%` label from stock card intraday charts while keeping the zero axis.
- 新增外汇自选的搜索添加和删除能力。
- 外汇列表按用户添加顺序展示。
- 股票卡片视图新增最高、最低、开盘价展示。
- 股票卡片分时图保留零轴，但不再在左侧显示 `0%` 标签。

## 1.0.4 - 2026-07-03

- Added searchable convertible bond support in the stock module.
- Fixed direct-input market inference for Shanghai and Shenzhen convertible bond codes.
- Kept convertible bond details on basic quote, intraday, and K-line views instead of loading stock-only enhanced data.
- 新增股票模块搜索并添加可转债的能力。
- 修复沪深可转债代码直接输入时的市场前缀推断。
- 可转债详情页保留基础行情、分时和 K 线展示，不再加载股票专属增强信息。

## 1.0.3 - 2026-07-01

- Fixed local settings persistence so watchlists, holdings, reminders, and display preferences survive IDE restarts.
- Added a fallback periodic save for mofish settings after local changes.
- Adjusted stock card intraday charts to draw against the previous-close zero axis while keeping dynamic scaling.
- 修复本地设置持久化问题，确保自选列表、持仓、提醒和展示偏好在 IDE 重启后保留。
- 新增 mofish 设置变更后的定时兜底保存。
- 调整股票卡片分时图，以昨收零轴为基准绘制，同时保留动态缩放。

## 1.0.2 - 2026-06-26

- Lowered the compatibility baseline to IntelliJ IDEA 2025.x.
- Kept table refreshes from scrolling the current table view to the selected row.
- Fixed the stock group assignment popup position so it opens near the selected row.
- 将兼容基线调整为 IntelliJ IDEA 2025.x。
- 修复表格刷新后自动滚动到选中行的问题。
- 修复股票移动分组弹窗位置不正确的问题。

## 1.0.1 - 2026-06-26

- Simplified the user-facing display name to `mofish` across plugin metadata, tool window labels,
  settings, documentation, and runtime text.
- 将用户可见名称统一简化为 `mofish`，覆盖插件元信息、工具窗口、设置页、文档和运行时文案。

## 1.0.0 - 2026-05-15

- Initial stable release.
- Added quote modules for stocks, market indexes, funds, cryptocurrencies, and foreign exchange rates.
- Added watchlists with searchable assets, grouped stocks, and configurable card/table views.
- Added portfolio holding configuration and profit calculation for stocks, funds, and cryptocurrencies.
- Added price and percentage-change reminders through IDE notifications.
- Added automatic refresh settings, per-module refresh windows, and status bar market/profit rotation.
- Added local settings persistence for watchlists, holdings, reminders, refresh preferences, visible modules, table
  columns, and status bar content.
- 首个正式版本。
- 新增股票、指数、基金、虚拟币和外汇行情模块。
- 新增可搜索自选列表、股票分组，以及卡片/表格视图配置。
- 新增股票、基金、虚拟币持仓配置和收益计算。
- 新增价格和涨跌幅提醒，通过 IDE 通知触发。
- 新增自动刷新设置、按模块刷新时间窗口，以及状态栏行情/收益轮播。
- 新增自选列表、持仓、提醒、刷新偏好、可见模块、表格列和状态栏内容的本地设置持久化。

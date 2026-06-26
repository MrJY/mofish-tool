# Changelog

All notable changes to MoFish Tool are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project follows semantic
versioning.

## Unreleased

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

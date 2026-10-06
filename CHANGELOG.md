# 更新日志 / Changelog

本项目所有值得记录的变更都会写在这里。

格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [1.4] - 2026-10-06

### 新增
- **应用图标**：新增方形图标 `ic_launcher` 与圆形图标 `ic_launcher_round`（xxhdpi / xxxhdpi 两套密度），并在清单中声明 `android:icon` / `android:roundIcon`。

### 优化
- **Rapfi 引擎更新**：更换 `pbrain-rapfi` 原生引擎为新版构建，并同步更新 `config.toml`。
- 移除已废弃的旧模型文件 `model210901.bin`，并清理其在 `EngineManager` 与 `config.toml` 中的引用。

### 变更
- 版本号升至 1.4（versionCode 5）。

## [1.3] - 2026-10-05

### 新增
- **全新 UI 主题体系**：引入 `colors.xml` 统一色彩规范与 `UiTheme` 工具类，全局系统栏（状态栏 / 导航栏）随主题自适应。
- **卡片式对话框 `UiDialog`**：以统一的卡片样式重构应用内所有弹窗，替换原生 `AlertDialog`，交互与视觉更一致。
- 新增 v21 ripple 触摸反馈资源（按钮、列表项、分段控件等）。

### 变更
- 应用内所有弹窗统一为 `UiDialog`，移除零散的 `AlertDialog` 调用。
- 「关于」页改用主题色与统一系统栏处理，版本信息同步至 1.3。
- 按钮文案精简，「无禁手」规则提示落到横竖屏两套布局。
- 版本号升至 1.3（versionCode 4）。

## [1.2] - 2026-10-04

### 新增
- **📷 图片识谱（拍照识别）**：支持直接拍摄棋盘照片，或从相册选择图片，自动识别盘面落子并一键导入为棋谱。识别过程完全在本地完成，照片不上传、不联网。

### 修复
- 修复部分已知 bug，提升打谱与分析过程中的稳定性。

### 变更
- 版本号升至 1.2（versionCode 3）。

## [1.1.0] - 2026-10-03

### 新增
- 新增「关于」页：应用信息、版本号、作者与引擎说明。

### 变更
- 版本号升至 1.1.0（versionCode 2）。

## [1.0.0] - 2026-10-02

### 新增
- 首个公开版本：15x15 棋盘、无禁手 / 连珠禁手规则。
- 双 AI 引擎（KataGo + Rapfi），全本地推理，无需联网。
- 标准 SGF (FF[4]) 棋谱读写。
- 对局自动保存与记录管理。

[1.4]: https://github.com/1812245401/Gomoku/releases/tag/v1.4
[1.3]: https://github.com/1812245401/Gomoku/releases/tag/v1.3
[1.2]: https://github.com/1812245401/Gomoku/releases/tag/v1.2
[1.1.0]: https://github.com/1812245401/Gomoku/releases/tag/v1.1.0
[1.0.0]: https://github.com/1812245401/Gomoku/releases/tag/v1.0.0

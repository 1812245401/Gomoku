# 更新日志 / Changelog

本项目所有值得记录的变更都会写在这里。
格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

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

[1.2]: https://github.com/1812245401/Gomoku/releases/tag/v1.2
[1.1.0]: https://github.com/1812245401/Gomoku/releases/tag/v1.1.0
[1.0.0]: https://github.com/1812245401/Gomoku/releases/tag/v1.0.0

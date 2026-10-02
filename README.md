# Gomoku · 五子棋

Android 五子棋（连珠）分析与打谱应用。AI 引擎全本地运行，无需联网。

## 截图

| KataGo 局面分析 | Rapfi 最佳点 | 导入棋谱 |
|:---:|:---:|:---:|
| ![KataGo 局面分析](https://raw.githubusercontent.com/1812245401/Gomoku/main/screenshots/01_katago_analysis.jpg) | ![Rapfi 最佳点](https://raw.githubusercontent.com/1812245401/Gomoku/main/screenshots/02_rapfi_best_move.jpg) | ![导入棋谱](https://raw.githubusercontent.com/1812245401/Gomoku/main/screenshots/03_import_sgf.jpg) |

| 保存棋谱 | 横屏棋盘 | |
|:---:|:---:|:---:|
| ![保存棋谱](https://raw.githubusercontent.com/1812245401/Gomoku/main/screenshots/04_save_record.jpg) | ![横屏棋盘](https://raw.githubusercontent.com/1812245401/Gomoku/main/screenshots/05_landscape_board.jpg) | |

## 功能

- 15x15 棋盘，支持无禁手 / 连珠禁手规则
- 接入 Rapfi 与 KataGo 两种 AI 引擎（本地 ONNX / 原生库）
- 局面分析：显示当前执子、引擎最佳点提示、KataGo 分析点
- 棋谱 SGF (FF[4]) 导入 / 导出，支持从文件导入与粘贴文本导入
- 对局棋谱保存、命名与记录管理
- 横竖屏双布局适配
- 「关于」页

## 目录结构

```
Gomoku/
├── app/
│   ├── src/main/java/com/gomoku/   # 源码
│   ├── src/main/res/               # 资源
│   └── src/main/assets/            # 引擎模型与权重
├── build.gradle
├── gradle.properties
├── settings.gradle
├── screenshots/     # 应用截图
├── README.md
└── CHANGELOG.md
```

## 构建

```bash
./gradlew assembleDebug
```

注：`.gitignore` 已忽略 `local.properties`，请自行配置 SDK 路径。

## 版本

当前版本 **v1.1.0**（versionCode 2）。详见 [CHANGELOG.md](CHANGELOG.md)。

## 许可

本项目仅供学习交流使用。

## 作者

[1812245401](https://github.com/1812245401)

# Gomoku · 五子棋

Android 五子棋（连珠）分析与打谱应用。AI 引擎全本地运行，无需联网。

## 亮点

- **📷 拍照识别，一键成谱** — 对着棋盘拍一张照片（或从相册选图），自动识别盘面落子并生成标准棋谱。识别全程在手机本地完成，**照片不出手机、不上传、不联网**。
- **🧠 智子同款 24b 权重** — 内置与「智子」平台同款的 KataGo **24b** 神经网络（INT8 量化），配合 Rapfi 算杀引擎，全程**纯 CPU 离线推理**，无需 GPU、无需网络。

## 截图

| KataGo 局面分析 | Rapfi 最佳点 | 导入棋谱 |
|:---:|:---:|:---:|
| ![KataGo 局面分析](https://raw.githubusercontent.com/1812245401/Gomoku/main/screenshots/01_katago.jpg) | ![Rapfi 最佳点](https://raw.githubusercontent.com/1812245401/Gomoku/main/screenshots/02_rapfi.jpg) | ![导入棋谱](https://raw.githubusercontent.com/1812245401/Gomoku/main/screenshots/03_import.jpg) |

| 保存棋谱 | 横屏棋盘 | |
|:---:|:---:|:---:|
| ![保存棋谱](https://raw.githubusercontent.com/1812245401/Gomoku/main/screenshots/04_save_record.jpg) | ![横屏棋盘](https://raw.githubusercontent.com/1812245401/Gomoku/main/screenshots/05_landscape.jpg) | |

## 功能

- 15x15 棋盘，支持无禁手 / 连珠禁手规则
- 接入 Rapfi 与 KataGo 两种 AI 引擎（本地 ONNX / 原生库）
- 局面分析：显示当前执子、引擎最佳点提示、KataGo 分析点
- 📷 图片识谱（拍照识别）：拍棋盘照片 / 从相册选图 → 自动识别棋子 → 一键导入为棋谱（纯本地，不联网）
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
├── gradlew / gradlew.bat   # Gradle Wrapper
├── gradle/wrapper/         # Wrapper 配置与 jar
├── screenshots/            # 应用截图
├── README.md
└── CHANGELOG.md
```

## 构建

```bash
./gradlew assembleDebug
```

注：`.gitignore` 已忽略 `local.properties`，请自行配置 SDK 路径。

## 版本

当前版本 **v1.3**（versionCode 4）。详见 [CHANGELOG.md](CHANGELOG.md)。

## 许可

本项目基于 [MIT License](LICENSE) 开源。

- 开源协议：[MIT](LICENSE)
- 隐私政策：[PRIVACY.md](PRIVACY.md)（本应用不收集任何个人数据）

## 作者

[1812245401](https://github.com/1812245401)

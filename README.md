# Gomoku

Android 五子棋（连珠）分析与打谱应用。基于 AIDE 手机端开发。

## 功能

- 15x15 棋盘，支持无禁手 / 连珠禁手规则
- 接入 Rapfi 与 KataGo 两种 AI 引擎（本地 ONNX / 原生库）
- 棋谱 SGF (FF[4]) 导入 / 导出，兼容主流打谱软件
- 对局自动保存、棋谱记录管理（SharedPreferences 持久化）
- 横竖屏双布局适配

## 目录结构

```
app/src/main/java/com/gomoku/
  MainActivity.java     主界面与交互
  BoardView.java        棋盘绘制与落子
  EngineManager.java    AI 引擎调度
  SgfIO.java            SGF 解析/生成
  GameRecord.java       棋谱数据模型
  RecordManager.java    记录持久化
app/src/main/assets/    引擎配置与模型文件（约 99MB）
  config.toml / engine.cfg     引擎配置
  model210901.bin              默认模型
  *.bin.lz4                    压缩权重
  b24.int8.onnx / libonnxruntime.so / pbrain-rapfi / katago  引擎二进制
```

## 构建

Gradle + Android Gradle Plugin 7.0.2，compileSdk 33，minSdk 19。

```bash
./gradlew assembleDebug
```

> 注：`.gitignore` 已忽略 `local.properties`，请自行配置 SDK 路径。

## 许可

仅供学习交流。

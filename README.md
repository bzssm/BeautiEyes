# BeautiEyes

一款帮助儿童保护视力的 Android TV 应用。家长通过手机在局域网发送信号，TV 立即从后台抢占前台并全屏循环播放预置的护眼提示视频（例如提醒孩子停下当前活动、做眼保健操、远眺休息等），起到强制打断、引导护眼的作用。遥控器返回键可手动关闭。

---

## 功能特性

- **局域网信号触发**：内嵌 HTTP 服务器，手机浏览器访问 `http://<TV-IP>:8080` 即可看到控制页，点击"播放"按钮下发信号
- **强制抢占前台**：收到信号后，无论 TV 当前在看什么内容，都会全屏弹出播放护眼视频
- **全屏循环播放**：基于 ExoPlayer（Media3），循环播放 `res/raw/video.mp4`，遥控器返回键关闭
- **前台服务保活**：`Foreground Service` 常驻，保证信号长期可达
- **开机自启**：设备重启后自动恢复监听，无需家长手动打开
- **状态可视化**：主界面显示本机 IP、端口以及信号服务运行状态
- **扩展友好**：信号源接口化，未来可新增 MQTT 等外网信号源（支持家长不在家时远程提醒）

---

## 技术栈

| 模块 | 选型 |
| --- | --- |
| 语言 | Kotlin |
| UI | Jetpack Compose + Compose for TV (`androidx.tv`) |
| 视频播放 | AndroidX Media3 / ExoPlayer |
| 内嵌 HTTP 服务器 | NanoHTTPD |
| 后台保活 | Foreground Service |
| 构建 | Gradle (Kotlin DSL)，AGP 9.1.1，Kotlin 2.2.10 |
| SDK | minSdk 29，targetSdk 33，compileSdk 36 |

---

## 项目结构

```
app/src/main/
├── java/com/roy/beautieyes/
│   ├── MainActivity.kt              // 主界面：IP / 状态 / 悬浮窗授权引导
│   ├── VideoPlayerActivity.kt       // 全屏循环播放 Activity
│   ├── signal/
│   │   ├── SignalSource.kt          // 统一信号源接口 + 状态枚举
│   │   └── HttpSignalSource.kt      // NanoHTTPD 实现
│   ├── service/
│   │   └── SignalService.kt         // 前台服务，串联信号源与播放
│   └── receiver/
│       └── BootReceiver.kt          // 开机自启
├── assets/
│   └── control.html                 // 手机端控制页
└── res/
    └── raw/video.mp4                // 预置护眼视频
```

更详细的任务拆分与实现要点见 `docs/plan.md`、`docs/codingplan.md`。

---

## 构建与安装

前置要求：JDK 11、Android SDK（含 API 36）、已连接 TV 设备或模拟器。

```bash
# Debug 构建
./gradlew :app:assembleDebug

# Release 构建（当前未开启 minify）
./gradlew :app:assembleRelease

# 安装到已连接设备
./gradlew :app:installDebug

# Lint
./gradlew :app:lint

# 清理
./gradlew clean
```

> **代理注意**：`gradle.properties` 里预留了 `https.proxyHost=127.0.0.1:7897`。无代理环境下请先注释掉这两行，否则依赖下载会失败。

---

## 使用流程

1. TV 上安装并打开 BeautiEyes，首次使用请**授予悬浮窗权限**（主界面有"去授权"按钮引导）
2. 主界面显示控制地址，例如 `http://192.168.1.23:8080`
3. 家长手机连接同一局域网 WiFi，浏览器访问该地址，看到大按钮"播放"
4. 点击"播放"，TV 端立即全屏播放护眼视频（循环）
5. 遥控器按 **返回键** 关闭播放，回到之前的内容

---

## 替换护眼视频

直接替换 `app/src/main/res/raw/video.mp4` 为你想要的视频，然后重新构建安装即可。文件名必须保持为 `video.mp4`（代码里通过 `R.raw.video` 引用）。

---

## 权限说明

| 权限 | 用途 |
| --- | --- |
| `INTERNET` | 启动内嵌 HTTP 服务器，接收局域网信号 |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_CONNECTED_DEVICE` | 前台服务常驻监听信号 |
| `RECEIVE_BOOT_COMPLETED` | 开机自启监听服务 |
| `USE_FULL_SCREEN_INTENT` | 后台场景下通过 Full-Screen Intent 强制弹出播放页 |
| `SYSTEM_ALERT_WINDOW`（悬浮窗） | Android 10+ 限制后台直接启动 Activity，需用悬浮窗权限作为兜底 |

抢占前台的策略：`SignalService` 收到信号后会同时调用 `startActivity` 并发送一条 `CATEGORY_ALARM` + `PRIORITY_MAX` + `setFullScreenIntent` 的通知；`VideoPlayerActivity` 声明了 `showOnLockScreen="true"` 与 `turnScreenOn="true"`，保证锁屏/息屏状态也能顺利拉起。

---

## 后续规划

- **MQTT 信号源**：新增 `MqttSignalSource` 实现 `SignalSource` 接口，支持家长在外网远程触发。服务端可选用 Mochi-MQTT，客户端建议使用 HiveMQ MQTT Client（Eclipse Paho Android Service 已停止维护）。Topic 约定 `beautieyes/play`，QoS 1。
- 主界面启动/停止服务按钮
- 多段护眼视频随机播放、播放时长限制

---

## 许可

待定。
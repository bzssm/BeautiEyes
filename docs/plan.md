# BeautiEyes 开发计划

## 项目目标

Android TV app，通过信号源接收指令，收到信号后从后台抢占焦点，全屏循环播放本地预置视频。遥控器可手动关闭。

## 技术选型

- **语言/UI**：Kotlin + Jetpack Compose for TV
- **视频播放**：ExoPlayer（Media3）
- **内嵌 HTTP 服务器**：NanoHTTPD（轻量，单个文件即可集成）
- **后台保活**：Foreground Service

## 核心设计：信号源接口

```kotlin
// 所有信号源的统一接口
interface SignalSource {
    fun start(onSignal: (SignalCommand) -> Unit)
    fun stop()
    fun getStatus(): SignalSourceStatus
}
```

**已实现**：`HttpSignalSource` — 内嵌 HTTP 服务器，局域网访问控制
**预留扩展**：`MqttSignalSource` — 后续接入 MQTT（Mochi-MQTT + Paho 客户端）

## 开发任务（按顺序）

### 阶段一：依赖与权限

**任务 1：引入依赖与权限**
- 添加 NanoHTTPD 依赖
- 添加 Media3 ExoPlayer 依赖
- 添加权限（INTERNET、FOREGROUND_SERVICE、RECEIVE_BOOT_COMPLETED）

### 阶段二：信号源

**任务 2：定义 SignalSource 接口**
- 定义 `SignalSource` 接口和 `SignalCommand` 数据类
- 定义 `SignalSourceStatus`（运行中/已停止/错误）

**任务 3：实现 HttpSignalSource**
- 基于 NanoHTTPD 在指定端口（默认 8080）启动 HTTP 服务
- `GET /` — 返回控制网页（一个"播放"按钮）
- `POST /play` — 触发播放信号
- `GET /status` — 返回 app 当前状态

### 阶段三：视频播放

**任务 4：视频播放页面**
- 创建 `VideoPlayerActivity`（全屏、无状态栏）
- 使用 ExoPlayer 播放 `res/raw/` 下的预置视频
- 循环播放模式
- 遥控器返回键关闭页面

### 阶段四：前台服务与串联

**任务 5：前台服务**
- 创建 `SignalService`（Foreground Service）
- 启动 `HttpSignalSource` 监听信号
- 收到信号后用 `FLAG_ACTIVITY_NEW_TASK` 拉起 `VideoPlayerActivity`
- 常驻通知显示服务运行中

**任务 6：开机自启动**
- 创建 `BootReceiver`，开机后自动启动 `SignalService`

### 阶段五：主界面

**任务 7：主界面**
- 显示本机 IP 地址和端口（方便用户知道访问地址）
- 显示信号源状态（运行中/已停止）
- 启动/停止服务按钮

## 文件结构（预计新增）

```
app/src/main/java/com/roy/beautieyes/
├── MainActivity.kt                   // 主界面，显示 IP 和状态
├── VideoPlayerActivity.kt            // 全屏视频播放
├── signal/
│   ├── SignalSource.kt               // 接口定义
│   └── HttpSignalSource.kt           // HTTP 服务器实现
├── service/
│   └── SignalService.kt              // 前台服务
└── receiver/
    └── BootReceiver.kt               // 开机自启动
app/src/main/res/raw/
└── video.mp4                         // 预置视频文件
```

## 使用流程

1. TV 上打开 BeautiEyes → 显示 `192.168.x.x:8080`
2. 手机浏览器访问该地址 → 看到控制页面
3. 点击"播放"按钮 → TV 全屏播放视频
4. 遥控器按返回键 → 关闭播放，回到之前的界面

## 后续扩展（MQTT）

当需要外网控制时，新增 `MqttSignalSource` 实现 `SignalSource` 接口：
- 依赖：Eclipse Paho Android
- 服务端：Mochi-MQTT 部署到公网
- Topic：`beautieyes/play`，QoS 1
- 在 `SignalService` 中替换或并行启动多个信号源即可
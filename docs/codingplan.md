# BeautiEyes Coding Plan

基于 `docs/plan.md` 的详细编码计划，每个任务列出具体的文件改动和代码要点。

---

## 任务 1：引入依赖与权限

### 修改文件
1. `gradle/libs.versions.toml`
2. `app/build.gradle.kts`
3. `app/src/main/AndroidManifest.xml`

### 具体改动

**libs.versions.toml** — 新增版本和依赖：
```toml
[versions]
nanohttpd = "2.3.1"
media3 = "1.3.1"

[libraries]
nanohttpd = { group = "org.nanohttpd", name = "nanohttpd", version.ref = "nanohttpd" }
media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
media3-ui = { group = "androidx.media3", name = "media3-ui", version.ref = "media3" }
```

**app/build.gradle.kts** — dependencies 块新增：
```kotlin
implementation(libs.nanohttpd)
implementation(libs.media3.exoplayer)
implementation(libs.media3.ui)
```

**AndroidManifest.xml** — 新增权限：
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
```

---

## 任务 2：定义 SignalSource 接口

### 新增文件
1. `app/src/main/java/com/roy/beautieyes/signal/SignalSource.kt`

### 代码要点

```kotlin
package com.roy.beautieyes.signal

// 信号指令
data class SignalCommand(
    val action: String  // "play", 后续可扩展 "stop" 等
)

// 信号源状态
enum class SignalSourceStatus {
    RUNNING, STOPPED, ERROR
}

// 信号源接口 — 所有信号源（HTTP、MQTT 等）实现此接口
interface SignalSource {
    fun start(onSignal: (SignalCommand) -> Unit)
    fun stop()
    fun getStatus(): SignalSourceStatus
}
```

---

## 任务 3：实现 HttpSignalSource

### 新增文件
1. `app/src/main/java/com/roy/beautieyes/signal/HttpSignalSource.kt`
2. `app/src/main/assets/control.html`

### 代码要点

**HttpSignalSource.kt**：
- 构造签名 `HttpSignalSource(context: Context, port: Int = 8080)`，需要 `Context` 读取 `assets/control.html`
- 继承 `NanoHTTPD(port)` 并实现 `SignalSource` 接口
- 路由处理：
  - `GET /` → 返回 `control.html`（控制页面）
  - `POST /play` → 调用 `onSignal(SignalCommand("play"))`，返回 JSON `{"ok": true}`
  - `GET /status` → 返回当前状态 JSON
- `start(onSignal)` 中调用 `NanoHTTPD.start(SOCKET_READ_TIMEOUT, false)`；`stop()` 中调用 `super.stop()` 并清空回调
- 异常处理：`IOException`（端口占用等）时状态设为 ERROR

**control.html**：
- 简洁的单页面，一个大按钮"播放"
- 点击按钮 `fetch('/play', {method: 'POST'})` 
- 显示操作结果（成功/失败）
- 适配 TV 浏览器和手机浏览器的大字体样式

---

## 任务 4：视频播放页面

### 新增文件
1. `app/src/main/java/com/roy/beautieyes/VideoPlayerActivity.kt`

### 修改文件
2. `app/src/main/AndroidManifest.xml`

### 预置资源
3. `app/src/main/res/raw/video.mp4`（需手动放入一个测试视频）

### 代码要点

**VideoPlayerActivity.kt**：
- 继承 `ComponentActivity`
- `onCreate` 中：
  - 设置全屏（隐藏系统 UI）：`WindowInsetsControllerCompat` 隐藏 status bar 和 navigation bar
  - 初始化 ExoPlayer，设置 `repeatMode = Player.REPEAT_MODE_ONE`（循环播放）
  - 数据源使用 `RawResourceDataSource` 读取 `R.raw.video`
  - 使用 Compose 的 `AndroidView` 嵌入 `PlayerView`
- `onDestroy` 中释放 ExoPlayer
- `onBackPressed` / `onKeyDown` 监听遥控器返回键 → `finish()`

**AndroidManifest.xml** — 注册 Activity：
```xml
<activity
    android:name=".VideoPlayerActivity"
    android:theme="@android:style/Theme.NoTitleBar.Fullscreen"
    android:launchMode="singleTask" />
```

---

## 任务 5：前台服务

### 新增文件
1. `app/src/main/java/com/roy/beautieyes/service/SignalService.kt`

### 修改文件
2. `app/src/main/AndroidManifest.xml`

### 代码要点

**SignalService.kt**：
- 继承 `Service`，实现前台服务
- `onCreate` 中：
  - 创建两个 NotificationChannel：
    - `beautieyes_signal`（IMPORTANCE_LOW）— 前台服务常驻通知
    - `beautieyes_alert`（IMPORTANCE_HIGH）— Full-Screen Intent 使用
  - 创建 `HttpSignalSource(this, DEFAULT_PORT)`
  - 调用 `signalSource.start { command -> handleCommand(command) }`
  - 调用 `startForeground()` 绑定常驻通知
- `onStartCommand` 返回 `START_STICKY`（系统杀掉后自动重启）
- `handleCommand(command)` 中，`action == "play"` 时走 `launchVideoPlayer()`：
  ```kotlin
  val intent = Intent(this, VideoPlayerActivity::class.java).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
  }
  // 始终尝试直接启动
  startActivity(intent)
  // 后台时额外发 Full-Screen Intent 通知作为兜底（Android 10+ 后台启 Activity 受限）
  if (!isAppInForeground()) {
      val pendingIntent = PendingIntent.getActivity(
          this, 0, intent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )
      val notification = Notification.Builder(this, ALERT_CHANNEL_ID)
          .setFullScreenIntent(pendingIntent, true)
          .setCategory(Notification.CATEGORY_ALARM)
          .setPriority(Notification.PRIORITY_MAX)
          // ... title/text/icon
          .build()
      notificationManager.notify(ALERT_NOTIFICATION_ID, notification)
  }
  ```
- `onDestroy` 中调用 `signalSource.stop()`
- 通过 `LocalBinder` 暴露 `getSignalSourceStatus()` 给 `MainActivity`

**AndroidManifest.xml** — 注册 Service：
```xml
<service
    android:name=".service.SignalService"
    android:foregroundServiceType="connectedDevice" />
```

**VideoPlayerActivity 需配合的清单属性**（与后台弹播放配套）：
```xml
<activity
    android:name=".VideoPlayerActivity"
    android:launchMode="singleTask"
    android:showOnLockScreen="true"
    android:turnScreenOn="true"
    android:theme="@android:style/Theme.NoTitleBar.Fullscreen" />
```

---

## 任务 6：开机自启动

### 新增文件
1. `app/src/main/java/com/roy/beautieyes/receiver/BootReceiver.kt`

### 修改文件
2. `app/src/main/AndroidManifest.xml`

### 代码要点

**BootReceiver.kt**：
```kotlin
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, SignalService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
```

**AndroidManifest.xml**：
```xml
<receiver
    android:name=".receiver.BootReceiver"
    android:enabled="true"
    android:exported="false">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```

---

## 任务 7：主界面

### 修改文件
1. `app/src/main/java/com/roy/beautieyes/MainActivity.kt`

### 代码要点

- 绑定 `SignalService`，通过 `LocalBinder.getService().getSignalSourceStatus()` 获取信号源状态
- 获取本机 WiFi IP 地址（遍历 `NetworkInterface`，取非 loopback 的 IPv4 地址）
- 悬浮窗权限引导：
  - `checkOverlayPermission()` 使用 `Settings.canDrawOverlays(this)` 检查
  - 未授权时 UI 显示警告文案 + "去授权"按钮，点击跳 `ACTION_MANAGE_OVERLAY_PERMISSION`
  - `onResume` 中重新检查权限（从设置页返回时更新 UI）
- Compose UI 显示：
  - 大字显示访问地址：`http://192.168.x.x:8080`
  - 信号源状态指示（运行中 🟢 / 已停止 🔴 / 异常 🔴）
  - 未授权悬浮窗时的警告与授权入口
- `onCreate` 中 `startForegroundService(SignalService)` 拉起服务；`onStart`/`onStop` 中 `bindService`/`unbindService`
- 备注：启动/停止服务按钮未实现，若需要可后续迭代

---

## 执行顺序总结

```
任务1（依赖权限） → 任务2（接口定义） → 任务3（HTTP实现） → 任务4（视频播放） → 任务5（前台服务） → 任务6（开机自启） → 任务7（主界面）
```

每个任务完成后可以单独验证：
- 任务 1-3 完成后：可以写单元测试验证 HTTP 服务是否正常响应
- 任务 4 完成后：可以直接启动 Activity 测试视频播放
- 任务 5 完成后：完整流程可跑通（浏览器点按钮 → TV 播放视频）
- 任务 6-7 是体验优化
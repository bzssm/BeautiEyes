package com.roy.beautieyes

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.roy.beautieyes.service.SignalService
import com.roy.beautieyes.signal.SignalSourceStatus
import com.roy.beautieyes.ui.theme.BeautiEyesTheme
import java.net.NetworkInterface

class MainActivity : ComponentActivity() {

    private var signalService: SignalService? = null
    private var bound = mutableStateOf(false)
    private var serviceStatus = mutableStateOf(SignalSourceStatus.STOPPED)
    private var hasOverlayPermission = mutableStateOf(false)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SignalService.LocalBinder
            signalService = binder.getService()
            bound.value = true
            serviceStatus.value = signalService?.getSignalSourceStatus() ?: SignalSourceStatus.STOPPED
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            signalService = null
            bound.value = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 检查悬浮窗权限
        checkOverlayPermission()

        // 启动前台服务
        val serviceIntent = Intent(this, SignalService::class.java)
        startForegroundService(serviceIntent)

        setContent {
            BeautiEyesTheme {
                MainScreen(
                    ip = getLocalIpAddress(),
                    port = SignalService.DEFAULT_PORT,
                    status = serviceStatus.value,
                    hasOverlayPermission = hasOverlayPermission.value,
                    onRequestPermission = { requestOverlayPermission() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 从设置页返回后重新检查权限
        checkOverlayPermission()
    }

    private fun checkOverlayPermission() {
        hasOverlayPermission.value = Settings.canDrawOverlays(this)
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, SignalService::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (bound.value) {
            unbindService(connection)
            bound.value = false
        }
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address.hostAddress?.contains('.') == true) {
                        return address.hostAddress ?: "未知"
                    }
                }
            }
        } catch (_: Exception) {}
        return "未知"
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MainScreen(
    ip: String,
    port: Int,
    status: SignalSourceStatus,
    hasOverlayPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RectangleShape
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "BeautiEyes",
                fontSize = 48.sp,
                color = Color(0xFFE94560)
            )

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "控制地址",
                fontSize = 24.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "http://$ip:$port",
                fontSize = 36.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(40.dp))

            val statusText = when (status) {
                SignalSourceStatus.RUNNING -> "服务运行中"
                SignalSourceStatus.STOPPED -> "服务已停止"
                SignalSourceStatus.ERROR -> "服务异常"
            }
            val statusColor = when (status) {
                SignalSourceStatus.RUNNING -> Color(0xFF4ECCA3)
                SignalSourceStatus.STOPPED -> Color.Gray
                SignalSourceStatus.ERROR -> Color(0xFFE94560)
            }

            Text(
                text = statusText,
                fontSize = 28.sp,
                color = statusColor
            )

            // 悬浮窗权限提示
            if (!hasOverlayPermission) {
                Spacer(modifier = Modifier.height(40.dp))

                Text(
                    text = "⚠ 未授权悬浮窗权限，无法从后台弹出播放",
                    fontSize = 20.sp,
                    color = Color(0xFFE94560)
                )

                Spacer(modifier = Modifier.height(16.dp))

                androidx.tv.material3.Button(onClick = { onRequestPermission() }) {
                    Text("去授权", fontSize = 20.sp)
                }
            }
        }
    }
}

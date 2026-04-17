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

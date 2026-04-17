package com.roy.beautieyes.signal

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.IOException

/**
 * 基于内嵌 HTTP 服务器的信号源实现。
 * 在指定端口启动 HTTP 服务，提供控制网页和 API。
 */
class HttpSignalSource(
    private val context: Context,
    private val port: Int = 8080
) : NanoHTTPD(port), SignalSource {

    private var onSignal: ((SignalCommand) -> Unit)? = null
    private var status: SignalSourceStatus = SignalSourceStatus.STOPPED

    override fun start(onSignal: (SignalCommand) -> Unit) {
        this.onSignal = onSignal
        try {
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            status = SignalSourceStatus.RUNNING
        } catch (e: IOException) {
            status = SignalSourceStatus.ERROR
        }
    }

    override fun stop() {
        super.stop()
        status = SignalSourceStatus.STOPPED
        onSignal = null
    }

    override fun getStatus(): SignalSourceStatus = status

    override fun serve(session: IHTTPSession): Response {
        return when {
            session.method == Method.GET && session.uri == "/" -> serveControlPage()
            session.method == Method.POST && session.uri == "/play" -> handlePlay()
            session.method == Method.GET && session.uri == "/status" -> serveStatus()
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
        }
    }

    private fun serveControlPage(): Response {
        val html = context.assets.open("control.html").bufferedReader().use { it.readText() }
        return newFixedLengthResponse(Response.Status.OK, "text/html", html)
    }

    private fun handlePlay(): Response {
        Log.d("BeautiEyes", "HTTP /play received")
        onSignal?.invoke(SignalCommand("play"))
        return newFixedLengthResponse(Response.Status.OK, "application/json", """{"ok": true}""")
    }

    private fun serveStatus(): Response {
        val json = """{"status": "${status.name}"}"""
        return newFixedLengthResponse(Response.Status.OK, "application/json", json)
    }
}

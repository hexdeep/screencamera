package com.xzh.hexdeep.manager

import android.content.Context
import com.xzh.hexdeep.App
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI
import java.nio.ByteBuffer
import java.util.Timer
import kotlin.concurrent.schedule

object WebsocketManager {

    // =========================
    // 状态 & 锁
    // =========================
    private val lock = Any()

    @Volatile
    private var client: WebSocketClient? = null

    @Volatile
    private var running = false

    @Volatile
    private var isConnecting = false

    @Volatile
    private var closing = false   // ⭐ 关键：防止 send / close 并发

    private var pingTimer: Timer? = null
    private var healthTimer: Timer? = null

    private lateinit var serverUrl: String

    private const val HEARTBEAT_INTERVAL = 8000L
    private const val PONG_TIMEOUT = 10000L

    @Volatile
    private var lastPongTime = 0L

    // =========================
    // 初始化
    // =========================
    fun init(context: Context, serverUrl: String) {
        synchronized(lock) {
            if (running) return
            this.serverUrl = serverUrl
            running = true
        }
        connect()
    }

    // =========================
    // 建立连接（防重入）
    // =========================
    private fun connect() {
        synchronized(lock) {
            if (!running || isConnecting /*|| closing*/) return
            if (client?.isOpen == true) return
            isConnecting = true
        }

        try {
            val uri = URI("$serverUrl?device_id=${App.currentDeviceId}")

            val newClient = object : WebSocketClient(uri) {

                override fun onOpen(handshakedata: ServerHandshake?) {
                    synchronized(lock) {
                        client = this
                        closing = false
                        isConnecting = false
                        lastPongTime = System.currentTimeMillis()
                    }
                    startPing()
                    startHealthCheck()
                }

                override fun onMessage(message: String?) {
                    if (message != null) handleMessage(message)
                }

                override fun onMessage(bytes: ByteBuffer?) {}

                override fun onWebsocketPong(
                    conn: org.java_websocket.WebSocket?,
                    f: org.java_websocket.framing.Framedata?
                ) {
                    lastPongTime = System.currentTimeMillis()
                }

                override fun onClose(code: Int, reason: String?, remote: Boolean) {
                    cleanupAfterClose()
                    if (running) tryReconnect()
                }

                override fun onError(ex: Exception?) {
                    cleanupAfterClose()
                    if (running) tryReconnect()
                }
            }

            newClient.connect()

        } catch (e: Exception) {
            synchronized(lock) {
                isConnecting = false
            }
            tryReconnect()
        }
    }

    // =========================
    // 自动重连
    // =========================
    private fun tryReconnect() {
        synchronized(lock) {
            if (!running || isConnecting /*|| closing*/) return
        }

        Timer(true).schedule(3000) {
            synchronized(lock) {
                if (!running || isConnecting /*|| closing*/) return@schedule
            }
            connect()
        }
    }

    // =========================
    // Ping
    // =========================
    private fun startPing() {
        stopPing()
        pingTimer = Timer(true)
        pingTimer?.schedule(HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL) {
            val cli = synchronized(lock) { client }
            try {
                if (cli?.isOpen == true && !closing) {
                    println("send ping")
                    cli.sendPing()
                }
            } catch (_: Exception) {
                safeClose()
            }
        }
    }

    private fun stopPing() {
        pingTimer?.cancel()
        pingTimer = null
    }

    // =========================
    // 健康检测
    // =========================
    private fun startHealthCheck() {
        stopHealthCheck()
        healthTimer = Timer(true)
        healthTimer?.schedule(3000, 3000) {
            val timeout = System.currentTimeMillis() - lastPongTime > PONG_TIMEOUT
            if (timeout) {
                safeClose()
            }
        }
    }

    private fun stopHealthCheck() {
        healthTimer?.cancel()
        healthTimer = null
    }

    // =========================
    // 安全关闭（只执行一次）
    // =========================
    private fun safeClose() {
        var needReconnect = false
        synchronized(lock) {
            if (closing) return
            closing = true

            stopPing()
            stopHealthCheck()

            try {
                client?.close()
            } catch (_: Exception) {}

            client = null
            isConnecting = false
            needReconnect = running
        }

        if (needReconnect) {
            tryReconnect()
        }
    }

    private fun cleanupAfterClose() {
        synchronized(lock) {
            client = null
            closing = false
            isConnecting = false
        }
    }

    // =========================
    // 消息处理
    // =========================
    private fun handleMessage(message: String) {
        try {
            val obj = JSONObject(message)
            val toDevice = obj.getString("to_device_id")
            if (toDevice != App.currentDeviceId) return

            WebRTCManager.handleSignal(
                from = obj.getString("from_device_id"),
                type = obj.getString("message_type"),
                content = obj.getString("content")
            )
        } catch (_: Exception) {}
    }

    private fun signalReconnectIfNeeded() {
        synchronized(lock) {
            if (!running) return
            if (closing) return
            if (isConnecting) return
        }

        // 异步触发，避免递归 / 锁问题
        tryReconnect()
    }

    // =========================
    // 发送（串行 + 安全）
    // =========================
    fun send(msg: String): Boolean {
        val cli: WebSocketClient

        synchronized(lock) {
            if (closing) return false.also { signalReconnectIfNeeded() }
            cli = client ?: return false.also { signalReconnectIfNeeded() }
            if (!cli.isOpen) return false.also { signalReconnectIfNeeded() }
        }

        return try {
            cli.send(msg)
            true
        } catch (_: Exception) {
            safeClose()
            false
        }
    }

    fun isConnected(): Boolean {
        return client?.isOpen == true && !closing
    }

    // =========================
    // 停止
    // =========================
    fun stop() {
        synchronized(lock) {
            running = false
        }
        safeClose()
    }
}

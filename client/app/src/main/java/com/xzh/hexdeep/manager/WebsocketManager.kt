package com.xzh.hexdeep.manager

import android.content.Context
import android.util.Log
import com.xzh.hexdeep.App
import kotlinx.coroutines.*
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI
import java.nio.ByteBuffer

object WebsocketManager {

    private const val TAG = "WebsocketManager"

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
    private var closing = false

    // 协程作用域（所有定时任务统一管理）
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var pingJob: Job? = null
    private var healthJob: Job? = null
    private var reconnectJob: Job? = null

    private lateinit var serverUrl: String

    private const val HEARTBEAT_INTERVAL = 8000L
    private const val PONG_TIMEOUT = 10000L

    // 指数退避参数
    private const val RECONNECT_BASE_DELAY = 1000L   // 初始重连延迟 1s
    private const val RECONNECT_MAX_DELAY  = 30000L  // 最大重连延迟 30s

    @Volatile
    private var reconnectAttempts = 0

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
            if (!running || isConnecting) return
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
                        reconnectAttempts = 0   // 连接成功，重置退避计数
                        lastPongTime = System.currentTimeMillis()
                    }
                    Log.d(TAG, "WebSocket 已连接")
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
                    Log.d(TAG, "收到 Pong")
                }

                override fun onClose(code: Int, reason: String?, remote: Boolean) {
                    Log.w(TAG, "WebSocket 关闭: code=$code, reason=$reason, remote=$remote")
                    cleanupAfterClose()
                    if (running) scheduleReconnect()
                }

                override fun onError(ex: Exception?) {
                    Log.e(TAG, "WebSocket 错误: ${ex?.message}")
                    cleanupAfterClose()
                    if (running) scheduleReconnect()
                }
            }

            newClient.connect()

        } catch (e: Exception) {
            Log.e(TAG, "建立连接异常: ${e.message}")
            synchronized(lock) { isConnecting = false }
            scheduleReconnect()
        }
    }

    // =========================
    // 指数退避重连
    // =========================
    private fun scheduleReconnect() {
        synchronized(lock) {
            if (!running || isConnecting) return
            // 取消上一个待执行的重连任务，避免重复堆积
            reconnectJob?.cancel()
        }

        val delay = minOf(
            RECONNECT_BASE_DELAY * (1L shl reconnectAttempts.coerceAtMost(5)),
            RECONNECT_MAX_DELAY
        )
        reconnectAttempts++

        Log.d(TAG, "将在 ${delay}ms 后重连（第 $reconnectAttempts 次尝试）")

        reconnectJob = scope.launch {
            delay(delay)
            synchronized(lock) {
                if (!running || isConnecting) return@launch
            }
            connect()
        }
    }

    // =========================
    // Ping（协程版，替代 Timer）
    // =========================
    private fun startPing() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL)
                val cli = synchronized(lock) { client }
                try {
                    if (cli?.isOpen == true && !closing) {
                        Log.d(TAG, "发送 Ping")
                        cli.sendPing()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "发送 Ping 失败: ${e.message}")
                    safeClose()
                }
            }
        }
    }

    private fun stopPing() {
        pingJob?.cancel()
        pingJob = null
    }

    // =========================
    // 健康检测（协程版，替代 Timer）
    // =========================
    private fun startHealthCheck() {
        healthJob?.cancel()
        healthJob = scope.launch {
            while (isActive) {
                delay(3000)
                val timeout = System.currentTimeMillis() - lastPongTime > PONG_TIMEOUT
                if (timeout) {
                    Log.w(TAG, "Pong 超时，主动关闭并重连")
                    safeClose()
                }
            }
        }
    }

    private fun stopHealthCheck() {
        healthJob?.cancel()
        healthJob = null
    }

    // =========================
    // 安全关闭（只执行一次）
    // =========================
    private fun safeClose() {
        val clientToClose: WebSocketClient?
        var needReconnect = false

        // 先在锁内取出引用，避免长时间持锁
        synchronized(lock) {
            if (closing) return
            closing = true
            clientToClose = client
            client = null
            isConnecting = false
            needReconnect = running
        }

        // 在锁外停止协程任务（避免死锁）
        stopPing()
        stopHealthCheck()

        // 在锁外关闭连接（close 可能阻塞，不应持锁）
        try {
            clientToClose?.close()
        } catch (_: Exception) {}

        if (needReconnect) {
            scheduleReconnect()
        }
    }

    // onClose / onError 回调时的清理（已由底层关闭，无需再 close）
    private fun cleanupAfterClose() {
        synchronized(lock) {
            client = null
            closing = false
            isConnecting = false
        }
        // 停止定时协程，防止旧任务继续运行
        stopPing()
        stopHealthCheck()
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
        } catch (e: Exception) {
            Log.e(TAG, "消息处理失败: ${e.message}")
        }
    }

    // =========================
    // 发送（线程安全）
    // =========================
    fun send(msg: String): Boolean {
        val cli: WebSocketClient

        synchronized(lock) {
            if (closing) {
                scheduleReconnect()
                return false
            }
            cli = client ?: run {
                scheduleReconnect()
                return false
            }
            if (!cli.isOpen) {
                scheduleReconnect()
                return false
            }
        }

        return try {
            cli.send(msg)
            true
        } catch (e: Exception) {
            Log.e(TAG, "发送消息失败: ${e.message}")
            safeClose()
            false
        }
    }

    fun isConnected(): Boolean {
        synchronized(lock) {
            return client?.isOpen == true && !closing
        }
    }

    // =========================
    // 停止
    // =========================
    fun stop() {
        synchronized(lock) {
            running = false
        }
        reconnectJob?.cancel()
        safeClose()
    }
}

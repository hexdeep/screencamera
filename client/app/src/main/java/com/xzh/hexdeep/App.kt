package com.xzh.hexdeep

import android.app.Application
import android.provider.Settings
import com.xzh.hexdeep.manager.WebRTCManager
import com.xzh.hexdeep.manager.WebsocketManager
import com.xzh.hexdeep.store.DeviceIdStore
import java.util.*

class App : Application() {
    companion object {
        lateinit var instance: App
            private set

        lateinit var currentDeviceId: String
            private set

        val iceServers: List<org.webrtc.PeerConnection.IceServer> by lazy {
            listOf(
                org.webrtc.PeerConnection.IceServer.builder(listOf("stun:43.138.176.9:3480"))
                    .setUsername("root")
                    .setPassword("wbs007007")
                    .createIceServer(),
                org.webrtc.PeerConnection.IceServer.builder(listOf("turn:43.138.176.9:3480"))
                    .setUsername("root")
                    .setPassword("wbs007007")
                    .createIceServer()
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 全局唯一 DeviceId（与 WebSocketManager 获取方式一致）
        currentDeviceId = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: UUID.randomUUID().toString()

        DeviceIdStore.init();

        // ⭐ 初始化 WebSocketManager（只初始化一次）
        WebsocketManager.init(
            context = applicationContext,
            serverUrl = "ws://43.138.176.9:8880/ws"
        )

        WebRTCManager.init(
            context = this,
            deviceIds = DeviceIdStore.getAll()  // 需要连接的设备
        )
    }
}

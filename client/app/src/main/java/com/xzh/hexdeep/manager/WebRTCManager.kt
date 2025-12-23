package com.xzh.hexdeep.manager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xzh.hexdeep.App
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.webrtc.*
import org.json.JSONObject
import java.nio.ByteBuffer
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object WebRTCManager {

    private val lock = ReentrantLock()

    private lateinit var appContext: Context

    private val peerConnectionFactory: PeerConnectionFactory by lazy { buildFactory() }

    private val globalEglBase: EglBase by lazy { EglBase.create() }

    private val coordinateBuffer: ByteBuffer = ByteBuffer.allocate(16)

    data class PeerConnectionWrapper(
        val id: String,
        val pc: PeerConnection,
        val commandChannel: DataChannel,
        val screenShotChannel : DataChannel,
        var screenshotHandler: android.os.Handler? = null,
        var screenshotRunnable: Runnable? = null
    ){
        var thumbnail: Bitmap? by mutableStateOf(null)
        var thumbnailWidth: Int? by mutableStateOf(null)
        var thumbnailHeight: Int? by mutableStateOf(null)
        var screenRecordWidth: Int? by mutableStateOf(null)       // 录制分辨率宽度
        var screenRecordHeight: Int? by mutableStateOf(null)      // 录制分辨率高度
        var devicePhysicalWidth: Int? by mutableStateOf(null)     // 设备物理分辨率宽度
        var devicePhysicalHeight: Int? by mutableStateOf(null)     // 设备物理分辨率高度
        var remoteVideoTrack: VideoTrack? by mutableStateOf(null) // 远端视频轨道
        var remoteAudioTrack: AudioTrack? by mutableStateOf(null) // 远端音频轨道
        var videoRenderer: SurfaceViewRenderer? by mutableStateOf(null)
    }


    object WebRTCConstants {
        const val CLIENT_START_SCREEN_RECORD = 1
        const val CLIENT_STOP_SCREEN_RECORD = 2
        const val CLIENT_SCREEN_SIZE                 = 3
        const val CLIENT_SCREENSHOT                 = 5
        const val CLIENT_START_AUDIO_RECORD         = 9
        const val CLIENT_STOP_AUDIO_RECORD          = 10

        const val DEVICE_INFO_MESSAGE = 1000
    }

    enum class MouseEventType(val value: Int) {
        MOUSE_DOWN(0),
        MOUSE_UP(1),
        MOUSE_MOVE(2),
        MOUSE_ROLLDOWN(3),
        MOUSE_ROLLUP(4)
    }

    private val _devicesFlow =
        MutableStateFlow<List<PeerConnectionWrapper>>(emptyList())

    val devicesFlow: StateFlow<List<PeerConnectionWrapper>> = _devicesFlow
    private val peerConnections = mutableMapOf<String, PeerConnectionWrapper>()  // key = remoteDeviceId

    fun init(context: Context,deviceIds: List<String>) {
        appContext = context.applicationContext

        // 创建到所有设备的 PeerConnection
        deviceIds.forEach { deviceId ->
            createPeerConnection(deviceId)
        }
    }

    fun getEglBaseContext(): EglBase.Context = globalEglBase.eglBaseContext

    // -----------------------------
    // 创建 PeerConnectionFactory
    // -----------------------------
    private fun buildFactory(): PeerConnectionFactory {
        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(appContext)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)

        val encoderFactory = DefaultVideoEncoderFactory(
            globalEglBase.eglBaseContext,
            true,
            true
        )
        val decoderFactory = DefaultVideoDecoderFactory(globalEglBase.eglBaseContext)

        return PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    // -----------------------------
    // 创建 PeerConnection
    // -----------------------------
    private fun createPeerConnection(remoteId: String) = lock.withLock {
        val rtcConfig = PeerConnection.RTCConfiguration(App.iceServers)
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN

        val observer = object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate ?: return
                sendIceCandidate(remoteId, candidate)
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {

            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onTrack(transceiver: RtpTransceiver?) {
                handleOnTrack(remoteId,transceiver)
            }
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {}
            override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}
            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
            override fun onDataChannel(dc: DataChannel?) {

            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onRenegotiationNeeded() {
                createOffer(remoteId)
            }
        }

        val pc = peerConnectionFactory.createPeerConnection(rtcConfig, observer)
            ?: return
        pc.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV))
        pc.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV))

        // 创建本地 command DataChannel
        val commandChannel = pc.createDataChannel("command", DataChannel.Init())
        commandChannel.registerObserver(CommandDataChannelObserver("command") { msg ->
            handleCommandMessage(remoteId,msg)
        })

        // 创建本地 screenshot DataChannel
        val screenShotChannel = pc.createDataChannel("screenShot", DataChannel.Init())
        screenShotChannel.registerObserver(CommandDataChannelObserver("screenShot") { msg ->
            handleScreenShot(remoteId,msg)
        })

        val handler = android.os.Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                screenShot(remoteId)
                handler.postDelayed(this, 5000) // 每 5 秒执行一次
            }
        }
        handler.postDelayed(runnable,1000)

        peerConnections[remoteId] = PeerConnectionWrapper(remoteId,pc, commandChannel,screenShotChannel, screenshotHandler = handler, screenshotRunnable = runnable)
        // 初次创建 → 主动发 Offer
        createOffer(remoteId)
    }

    // -----------------------------
    // 创建 Offer
    // -----------------------------
    private fun createOffer(remoteId: String) {
        val wrapper = peerConnections[remoteId] ?: return
        val pc = wrapper.pc

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }

        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                if (sdp != null) {
                    pc.setLocalDescription(object : SdpObserverAdapter() {}, sdp)
                    sendOffer(remoteId, sdp)
                }
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(msg: String?) {}
            override fun onSetFailure(msg: String?) {}
        }, constraints)
    }

    // -----------------------------
    // 发送 Offer（通过 WebsocketManager）
    // -----------------------------
    private fun sendOffer(remoteId: String, sdp: SessionDescription) {
        val offerJson = JSONObject()
        offerJson.put("type", sdp.type.canonicalForm())
        offerJson.put("sdp", sdp.description)

        val iceServersJson = JSONArray()
        App.iceServers.forEach { server ->
            val urlsArray = JSONArray()
            server.urls.forEach { urlsArray.put(it) }
            val serverJson = JSONObject().apply {
                put("urls", urlsArray)
                server.username?.let { put("username", it) }
                server.password?.let { put("credential", it) }
            }
            iceServersJson.put(serverJson)
        }

        val json = JSONObject().apply {
            put("offer", offerJson)
            put("ice_servers", iceServersJson)
        }

        sendSignal(
            to = remoteId,
            type = "offer",
            content = json.toString()
        )
    }

    // -----------------------------
    // 发送 ICE Candidate
    // -----------------------------
    private fun sendIceCandidate(remoteId: String, c: IceCandidate) {
        val obj = JSONObject()
        obj.put("sdpMid", c.sdpMid)
        obj.put("sdpMLineIndex", c.sdpMLineIndex)
        obj.put("candidate", c.sdp)

        sendSignal(
            to = remoteId,
            type = "ice",
            content = obj.toString()
        )
    }

    // -----------------------------
    // 发送通用信令（封装消息体）
    // -----------------------------
    private fun sendSignal(to: String, type: String, content: String) {
        val wrapper = JSONObject()
        wrapper.put("from_device_id", App.currentDeviceId)
        wrapper.put("to_device_id", to)
        wrapper.put("message_type", type)
        wrapper.put("content", content)

        WebsocketManager.send(wrapper.toString())
    }

    fun handleSignal(from: String, type: String, content: String) {
        val wrapper = peerConnections[from] ?: return
        val pc = wrapper.pc

        when (type) {

            "answer" -> {
                val obj = JSONObject(content)
                val sdp = SessionDescription(
                    SessionDescription.Type.ANSWER,
                    obj.getString("sdp")
                )

                pc.setRemoteDescription(object : SdpObserverAdapter() {
                    override fun onSetSuccess() {
                        println("[WebRTC] 已设置 Answer")
                    }
                }, sdp)
            }

            "ice" -> {
                val obj = JSONObject(content)
                val ice = IceCandidate(
                    obj.getString("sdpMid"),
                    obj.getInt("sdpMLineIndex"),
                    obj.getString("candidate")
                )
                pc.addIceCandidate(ice)
                println("[WebRTC] 收到并添加 ICE")
            }

            "camera_answer" -> {
                CameraStreamManager.setRemoteDescription(JSONObject(content))
            }

            "set_camera_stream" -> {
                CameraStreamManager.start(from)
            }
        }
    }


    fun releasePeerConnection(remoteId: String) = lock.withLock {
        val wrapper = peerConnections[remoteId] ?: return

        // 关闭 PeerConnection
        wrapper.commandChannel.close()
        wrapper.screenShotChannel.close()
        wrapper.pc.close()

        wrapper.screenshotRunnable?.let {
            wrapper.screenshotHandler?.removeCallbacks(it)
        }


        println("[WebRTCManager] 已释放 $remoteId 的 PeerConnection 和定时任务")
    }

    fun release() {
        peerConnections.values.forEach { releasePeerConnection(it.id) }
        peerConnections.clear()
    }

    private fun handleCommandMessage(remoteId: String, msg: String) {
        try {
            println("分辨率信息$remoteId")
            val json = JSONObject(msg)
            val messageType = json.optInt("messageType")
            if (messageType == WebRTCConstants.DEVICE_INFO_MESSAGE) {

                val wrapper = peerConnections[remoteId] ?: return
                wrapper.screenRecordWidth = json.optInt("screenRecordWidth")
                wrapper.screenRecordHeight = json.optInt("screenRecordHeight")
                wrapper.devicePhysicalWidth = json.optInt("devicePhysicalWidth")
                wrapper.devicePhysicalHeight = json.optInt("devicePhysicalHeight")

                println("[WebRTC] 更新 $remoteId 分辨率信息: " +
                        "screen=${wrapper.screenRecordWidth}x${wrapper.screenRecordHeight}, " +
                        "device=${wrapper.devicePhysicalWidth}x${wrapper.devicePhysicalHeight}")

            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleScreenShot(remoteId: String, msg: String){
        val json = JSONObject(msg)

        val wrapper = peerConnections[remoteId] ?: return
        val width = json.optInt("width")
        val height = json.optInt("height")
        val base64Data = json.optString("data")

        if (base64Data.isNullOrEmpty()) return

        val imageBytes = android.util.Base64.decode(
            base64Data,
            android.util.Base64.DEFAULT
        )

        val bitmap = BitmapFactory.decodeByteArray(
            imageBytes,
            0,
            imageBytes.size
        )

        if (bitmap != null) {
//            _devicesFlow.value = _devicesFlow.value.map {
//                if (it.id == remoteId) {
//                    it.copy(
//                        thumbnail = bitmap,
//                        thumbnailWidth = width,
//                        thumbnailHeight = height
//                    )
//                } else it
//            }

            wrapper.thumbnail = bitmap
            wrapper.thumbnailWidth = width
            wrapper.thumbnailHeight = height
            //println("[WebRTC] 收到截图 $remoteId ${width}x${height}")
        }
    }

    private fun handleOnTrack(remoteId: String, transceiver: RtpTransceiver?) {
        transceiver ?: return
        val track = transceiver.receiver.track()
        val wrapper = peerConnections[remoteId] ?: return


        if (track != null) {
            when (track.kind()) {
                "video" -> {
                    val videoTrack = track as VideoTrack
                    wrapper.remoteVideoTrack = videoTrack
                    videoTrack.setEnabled(true)
                    wrapper.videoRenderer?.let { videoTrack.addSink(it) }
                }
                "audio" -> {
                    val audioTrack = track as AudioTrack
                    wrapper.remoteAudioTrack = audioTrack
                    audioTrack.setEnabled(true)
                }
            }
        }
    }

    fun bindRemoteVideoRenderer(remoteId: String, renderer: SurfaceViewRenderer) {
        val wrapper = peerConnections[remoteId] ?: return
        wrapper.videoRenderer?.let {  wrapper.remoteVideoTrack?.removeSink(it) }
        wrapper.videoRenderer = renderer
        wrapper.remoteVideoTrack?.addSink(renderer)
    }

    private fun updateDevices() {
        _devicesFlow.value = peerConnections.values.toList()
    }

    /**
     * 通知远端启动录屏
     */
    fun startScreenRecord(
        remoteId: String,
        enableAudio: Boolean = false,
        maxSize: Int = 1000,
        frameRate: Int = 30,
        videoBitRate: Int = 2000000,
        iFrameInterval: Int = 1,
        mode: Int = 2,
        usePPSAndSPS: Boolean = true
    ) {
        withOpenCommandChannel(remoteId, { channel ->

            // 构建 JSON 消息
            val json = JSONObject().apply {
                put("id", WebRTCConstants.CLIENT_START_SCREEN_RECORD)
                put("mode", mode)
                put("eventType", "startScreenRecord")
                put("maxSize", maxSize)
                put("frameRate", frameRate)
                put("videoBitRate", videoBitRate)
                put("iFrameInterval", iFrameInterval)
                put("isUsePPSAndSPS", usePPSAndSPS)
            }

            // 发送给远端
            channel.send(DataChannel.Buffer(ByteBuffer.wrap(json.toString().toByteArray()), false))
            println("[WebRTCManager] 发送 startScreenRecord 指令给 $remoteId: $json")

            if (enableAudio) {
                // 模拟 setTimeout 500ms
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    startAudioRecord(remoteId)
                }, 500)
            }
        })
    }

    fun stopScreenRecord(remoteId: String){
        withOpenCommandChannel(remoteId, { channel ->

            val json = JSONObject().apply {
                put("id", WebRTCConstants.CLIENT_STOP_SCREEN_RECORD)
                put("eventType", "stop")
            }

            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                stopAudioRecord(remoteId)
            }, 500)

            // 发送给远端
            channel.send(DataChannel.Buffer(ByteBuffer.wrap(json.toString().toByteArray()), false))
            println("[WebRTCManager] 发送 stopScreenRecord 指令给 $remoteId: $json")
        })
    }

    /**
     * 发送鼠标/触摸坐标到远端
     *
     * @param remoteId 远端设备 ID
     * @param eventType 事件类型枚举，如 MouseEventType.MOUSE_DOWN
     * @param x X 坐标（对应设备物理分辨率）
     * @param y Y 坐标（对应设备物理分辨率）
     * @param pointerId 指针 ID，可固定为 0
     */
    fun sendCoordinates(
        remoteId: String,
        eventType: MouseEventType,
        x: Int,
        y: Int,
        pointerId: Int = 0
    ) {
        withOpenCommandChannel(remoteId, { channel ->

            coordinateBuffer.clear() // 重置缓冲区
            coordinateBuffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
            coordinateBuffer.putInt(eventType.value)
            coordinateBuffer.putInt(x)
            coordinateBuffer.putInt(y)
            coordinateBuffer.putInt(pointerId)
            coordinateBuffer.flip() // 切换为读模式

            channel.send(DataChannel.Buffer(coordinateBuffer, true))
            println("[WebRTCManager] 发送坐标事件给 $remoteId: event=${eventType.name}, x=$x, y=$y, pointerId=$pointerId")
        })
    }

    private fun startAudioRecord(
        remoteId: String,
        audioBitRate: Int = 32000,
        sampleRate: Int = 48000
    ) {
        withOpenCommandChannel(remoteId, { channel ->

            val json = JSONObject().apply {
                put("id", WebRTCConstants.CLIENT_START_AUDIO_RECORD)
                put("eventType", "start")
                put("audioBitRate", audioBitRate)
                put("sampleRate", sampleRate)
            }

            channel.send(DataChannel.Buffer(ByteBuffer.wrap(json.toString().toByteArray()), false))
            println("[WebRTCManager] 发送 startAudioRecord 指令给 $remoteId: $json")
        })
    }

    private fun stopAudioRecord(
        remoteId: String
    ) {
        withOpenCommandChannel(remoteId,{channel->
            val json = JSONObject().apply {
                put("id", WebRTCConstants.CLIENT_STOP_AUDIO_RECORD)
                put("eventType", "stop")
            }

            channel.send(DataChannel.Buffer(ByteBuffer.wrap(json.toString().toByteArray()), false))
            println("[WebRTCManager] 发送 stopAudioRecord 指令给 $remoteId: $json")
        })
    }

    private fun screenShot(remoteId: String){
        withOpenCommandChannel(remoteId, { channel ->

            val json = JSONObject().apply {
                put("id", WebRTCConstants.CLIENT_SCREENSHOT)
            }

            channel.send(DataChannel.Buffer(ByteBuffer.wrap(json.toString().toByteArray()), false))
        })
    }

    private inline fun withOpenCommandChannel(
        remoteId: String,
        action: (DataChannel) -> Unit
    ) {
        val wrapper = peerConnections[remoteId]
        if (wrapper == null) {
            println("[WebRTCManager] 设备 $remoteId 未连接")
            return
        }

        val channel = wrapper.commandChannel
        if (channel.state() != DataChannel.State.OPEN) {
            println("[WebRTCManager] 设备 $remoteId CommandChannel 未打开")
            return
        }

        action(channel)
    }

    fun refreshPeerConnection(remoteId: String) {
        val wrapper = peerConnections[remoteId] ?: run {
            println("[WebRTCManager] 设备 $remoteId 未连接，无法刷新")
            return
        }

        println("[WebRTCManager] 刷新 PeerConnection: $remoteId")

        // 关闭现有连接
        releasePeerConnection(remoteId)
        //peerConnections.remove(remoteId)

        // 重新创建连接
        createPeerConnection(remoteId)
    }

    /**
     * 刷新所有 PeerConnection
     */
    fun refreshAllPeerConnections() {
        val deviceIds = peerConnections.keys.toList() // 避免边遍历边修改
        println("[WebRTCManager] 刷新所有 PeerConnection, count=${deviceIds.size}")
        deviceIds.forEach { remoteId ->
            refreshPeerConnection(remoteId)
        }
        updateDevices()
    }

    fun addDevice(deviceId: String) {
        lock.withLock {
            if (peerConnections.containsKey(deviceId)) {
                println("[WebRTCManager] 设备 $deviceId 已存在，不重复创建")
                return
            }

            createPeerConnection(deviceId)

            // 将新设备插入列表头部
            val newWrapper = peerConnections[deviceId]!!
            _devicesFlow.value = listOf(newWrapper) + _devicesFlow.value.filter { it.id != deviceId }

            println("[WebRTCManager] 动态添加设备 $deviceId 到列表头部")
        }
    }
}

open class SdpObserverAdapter : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}

open class CommandDataChannelObserver(
    private val label: String,
    private val onMessageCallback: ((String) -> Unit)? = null,
) : DataChannel.Observer {

    override fun onBufferedAmountChange(previousAmount: Long) {
        // 可选：处理缓存变化
    }

    override fun onStateChange() {
        println("[WebRTC] DataChannel $label state: ${stateString()}")
    }

    override fun onMessage(buffer: DataChannel.Buffer?) {
        buffer?.let {
            val bytes = ByteArray(it.data.remaining())
            it.data.get(bytes)
            val msg = String(bytes)
            onMessageCallback?.invoke(msg)
        }
    }

    private fun stateString(): String {
        return when (label) {
            else -> "UNKNOWN"
        }
    }
}

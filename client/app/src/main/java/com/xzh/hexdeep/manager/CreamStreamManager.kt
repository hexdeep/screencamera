package com.xzh.hexdeep.manager

import android.app.Activity
import android.content.Context
import android.util.Log
import com.xzh.hexdeep.App
import org.json.JSONArray
import org.webrtc.*
import org.json.JSONObject
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object CameraStreamManager {

    private val lock = ReentrantLock()
    private var isRunning = false
    private var currentRemoteId: String? = null

    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null
    private var localMediaStream: MediaStream? = null

    private var appContext: Context? = null

    // ⭐ 新增：用于前后摄切换
    private var cameraCapturer: CameraVideoCapturer? = null
    private var isFrontCamera = false

    private val globalEglBase by lazy { EglBase.create() }
    var originalBrightness: Float? = null

    fun setStream(
        remoteId: String,
        context: Context,
        videoEnable: Boolean,
        audioEnable: Boolean
    ) = lock.withLock {
        stop()

        this.appContext = context.applicationContext
        this.currentRemoteId = remoteId

        sendSetStream(remoteId, videoEnable, audioEnable)
    }

    /**
     * 启动音视频采集 & WebRTC
     */
    fun start(remoteId: String, useFrontCamera: Boolean = false) = lock.withLock {
        if (isRunning) return
        isRunning = true
        if (currentRemoteId != remoteId) return

        val ctx = appContext ?: return

        val factory = buildFactory(ctx)
        createLocalTracks(factory, ctx, useFrontCamera)

        val rtcConfig = PeerConnection.RTCConfiguration(App.iceServers)
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN

        var pc: PeerConnection? = null
        pc = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {}

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                println("[Camera WebRTC] $remoteId connection: $newState")
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {
                if (newState == PeerConnection.IceGatheringState.COMPLETE) {
                    val localSdp = peerConnection?.localDescription ?: return
                    sendOffer(remoteId, localSdp)
                }
            }
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(dc: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
            override fun onTrack(transceiver: RtpTransceiver?) {}
        }) ?: throw RuntimeException("无法创建 PeerConnection")

        peerConnection = pc

        localVideoTrack?.let {
            pc.addTransceiver(
                it,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY)
            )
        }
        localAudioTrack?.let {
            pc.addTransceiver(
                it,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY)
            )
        }

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    pc.setLocalDescription(object : SdpObserverAdapter() {}, it)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(msg: String?) {}
            override fun onSetFailure(msg: String?) {}
        }, constraints)
    }

    /**
     * ⭐ 新增：切换前 / 后置摄像头（不重建 WebRTC）
     */
    fun switchCamera() = lock.withLock {
        val capturer = cameraCapturer ?: return
        capturer.switchCamera(null)
        isFrontCamera = !isFrontCamera
    }

    /**
     * 停止采集 & 清理资源
     */
    fun stop() = lock.withLock {
        if (!isRunning) return
        isRunning = false
        currentRemoteId?.let { stopWebrtc(it) }
        currentRemoteId = null

        cameraCapturer?.stopCapture()
        cameraCapturer?.dispose()
        cameraCapturer = null

        peerConnection?.close()
        peerConnection = null

        localAudioTrack = null
        localVideoTrack = null
        localMediaStream?.dispose()
        localMediaStream = null

    }

    private fun buildFactory(context: Context): PeerConnectionFactory {
        val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)

        val encoderFactory = DefaultVideoEncoderFactory(globalEglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(globalEglBase.eglBaseContext)

        return PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    private fun createLocalTracks(
        factory: PeerConnectionFactory,
        context: Context,
        useFrontCamera: Boolean
    ) {
        val videoSource = factory.createVideoSource(false)
        val audioSource = factory.createAudioSource(MediaConstraints())
        localAudioTrack = factory.createAudioTrack("audio0", audioSource)
        localVideoTrack = factory.createVideoTrack("video0", videoSource)

        val enumerator = Camera2Enumerator(context)

        val deviceName = enumerator.deviceNames.firstOrNull { name ->
            if (useFrontCamera) enumerator.isFrontFacing(name)
            else enumerator.isBackFacing(name)
        } ?: return

        val capturer =
            enumerator.createCapturer(deviceName, null) as CameraVideoCapturer
        cameraCapturer = capturer
        isFrontCamera = useFrontCamera

        val surfaceHelper =
            SurfaceTextureHelper.create("CaptureThread", globalEglBase.eglBaseContext)
        capturer.initialize(surfaceHelper, context, videoSource.capturerObserver)
        capturer.startCapture(1280, 720, 60)

        localMediaStream = factory.createLocalMediaStream("localStream")
        localMediaStream?.addTrack(localAudioTrack)
        localMediaStream?.addTrack(localVideoTrack)
    }

    private fun sendOffer(remoteId: String, localSdp: SessionDescription) {
        val offerJson = JSONObject().apply {
            put("type", localSdp.type.canonicalForm())
            put("sdp", filterSrflx(localSdp.description))
        }

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

        val wrapper = JSONObject().apply {
            put("from_device_id", App.currentDeviceId)
            put("to_device_id", remoteId)
            put("message_type", "camera_offer")
            put("content", json.toString())
        }

        WebsocketManager.send(wrapper.toString())
    }

    private fun sendSetStream(remoteId: String, videoEnable: Boolean, audioEnable: Boolean) {
        val json = JSONObject().apply {
            put("video_enable", videoEnable)
            put("audio_enable", audioEnable)
        }

        val wrapper = JSONObject().apply {
            put("from_device_id", App.currentDeviceId)
            put("to_device_id", remoteId)
            put("message_type", "set_camera_stream")
            put("content", json.toString())
        }

        WebsocketManager.send(wrapper.toString())
    }

    fun setRemoteDescription(remoteSdpJson: JSONObject) {
        val typeStr = remoteSdpJson.optString("type")
        val sdpStr = remoteSdpJson.optString("sdp")
        if (typeStr.lowercase() != "answer" || sdpStr.isEmpty()) return

        val sessionDescription =
            SessionDescription(SessionDescription.Type.ANSWER, sdpStr)

        peerConnection?.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                Log.d("CameraStreamManager", "Remote SDP set success")
            }
            override fun onSetFailure(error: String?) {
                Log.e("CameraStreamManager", "Set remote SDP failed: $error")
            }
        }, sessionDescription)
    }

    fun updateVideoRotation(remoteId: String, videoRotation: Int, videoMirror: Boolean) {
        val json = JSONObject().apply {
            put("video_rotation", videoRotation)
            put("video_mirror", videoMirror)
        }

        val wrapper = JSONObject().apply {
            put("from_device_id", App.currentDeviceId)
            put("to_device_id", remoteId)
            put("message_type", "update_video_rotation")
            put("content", json.toString())
        }

        WebsocketManager.send(wrapper.toString())
    }

    fun stopWebrtc(remoteId: String) {
        val wrapper = JSONObject().apply {
            put("from_device_id", App.currentDeviceId)
            put("to_device_id", remoteId)
            put("message_type", "update_video_rotation")
            put("content", "camera_stop")
        }

        WebsocketManager.send(wrapper.toString())
    }

    private fun filterSrflx(sdp: String, disableHost: Boolean = false): String {
        val lines = sdp.split("\r\n")
        return lines.filter { line ->
            if (!line.startsWith("a=candidate:")) return@filter true
            if (line.contains("typ srflx")) return@filter false
            if (disableHost && line.contains("typ host")) return@filter false
            true
        }.joinToString("\r\n")
    }

    fun setMaxBrightness(activity: Activity?){
        activity?.let { act ->
            try {
                val lp = act.window.attributes
                // 保存原亮度
                originalBrightness = lp.screenBrightness.takeIf { it >= 0f }
                // 设置最大亮度
                lp.screenBrightness = 1.0f
                act.window.attributes = lp
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun restoreBrightness(activity: Activity?) {
        activity?.let { act ->
            try {
                val lp = act.window.attributes
                lp.screenBrightness = originalBrightness?.takeIf { it >= 0f } ?: -1f
                act.window.attributes = lp
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

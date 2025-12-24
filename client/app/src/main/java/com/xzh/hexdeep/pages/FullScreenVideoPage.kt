package com.xzh.hexdeep.pages

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FlipCameraIos
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.xzh.hexdeep.manager.CameraStreamManager
import com.xzh.hexdeep.manager.WebRTCManager
import com.xzh.hexdeep.widgets.CameraMicSheetContent
import com.xzh.hexdeep.widgets.IconFont
import kotlinx.coroutines.launch
import org.webrtc.SurfaceViewRenderer
import kotlin.math.min

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun FullScreenVideo(
    device: WebRTCManager.PeerConnectionWrapper,
    onExit: () -> Unit,
    onToggleAudio: (() -> Unit)? = null,
    isAudioEnabled: Boolean = true
) {
    fun Context.findActivity(): Activity? {
        var ctx: Context? = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    val context = LocalContext.current
    val activity = remember { context.findActivity() }
    val eglBaseContext = WebRTCManager.getEglBaseContext()
    val rightBarWidth = 30.dp

    val videoOriginalWidth =
        device.screenRecordWidth ?: device.devicePhysicalWidth ?: 720
    val videoOriginalHeight =
        device.screenRecordHeight ?: device.devicePhysicalHeight ?: 1280
    val shouldLandscape = videoOriginalWidth > videoOriginalHeight

    // 横竖屏切换
    LaunchedEffect(shouldLandscape) {
        activity?.requestedOrientation =
            if (shouldLandscape)
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    // 页面退出恢复默认方向
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val sheetState = rememberBottomSheetState(initialValue = BottomSheetValue.Collapsed)
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    val scope = rememberCoroutineScope() // 协程作用域，用于 onClick 展开 BottomSheet

    var rotation by remember { mutableIntStateOf(0) }      // 0, 90, 180, 270
    var mirror by remember { mutableStateOf(false) }     // 是否镜像

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->

            val cameraGranted = result[Manifest.permission.CAMERA] == true
            val audioGranted = result[Manifest.permission.RECORD_AUDIO] == true

            if (cameraGranted) {
                CameraStreamManager.setStream(
                    device.id,
                    context,
                    videoEnable = true,
                    audioEnable = false
                )
                CameraStreamManager.setMaxBrightness(activity)
            }
        }

    val exitPage = {
        activity?.requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        WebRTCManager.stopScreenRecord(device.id)
        CameraStreamManager.stop()
        CameraStreamManager.restoreBrightness(activity)
        onExit()
    }

    var importCamera by remember { mutableStateOf(true) }
    var importMic by remember { mutableStateOf(false) }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 0.dp,
        sheetContent = {
            CameraMicSheetContent(
                onDisconnect = {
                    CameraStreamManager.stop()
                    CameraStreamManager.restoreBrightness(activity)
                    scope.launch { sheetState.collapse() }
                },
                onConnect = {
                    val needPermissions = mutableListOf<String>()

                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        needPermissions.add(Manifest.permission.CAMERA)
                    }

                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        needPermissions.add(Manifest.permission.RECORD_AUDIO)
                    }

                    if (needPermissions.isEmpty()) {
                        CameraStreamManager.setStream(
                            device.id,
                            context,
                            videoEnable = importCamera,
                            audioEnable = importMic
                        )
                        CameraStreamManager.setMaxBrightness(activity)
                    } else {
                        permissionLauncher.launch(needPermissions.toTypedArray())
                    }

                    scope.launch { sheetState.collapse() }
                },
                onImportCameraCheckedChange = { importCamera = it },
                onImportMicCheckedChange = { importMic = it },
                importCameraChecked = importCamera,
                importMicChecked = importMic,
            )
        },
        sheetBackgroundColor = MaterialTheme.colors.primary,
        sheetElevation = 8.dp
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.onPrimary)
                .padding(
                    WindowInsets
                        .statusBars
                        .only(WindowInsetsSides.Top)
                        .asPaddingValues()
                )
        ) {
            val configuration = LocalConfiguration.current
            val density = LocalDensity.current

            val navBarInsets = ViewCompat.getRootWindowInsets(LocalView.current)
            val navBarRightDp = with(density) {
                (navBarInsets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.right ?: 0).toDp()
            }

            val rightPaddingDp = if (shouldLandscape) navBarRightDp else 0.dp

            val screenWidthDp = configuration.screenWidthDp.dp
            val screenHeightDp = configuration.screenHeightDp.dp
            val statusBarHeightDp = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
            val availableHeightDp = screenHeightDp - statusBarHeightDp

            val screenWidthPx = with(density) { screenWidthDp.toPx() }
            val availableHeightPx = with(density) { availableHeightDp.toPx() }
            val rightBarWidthPx = with(density) { rightBarWidth.toPx() }

            val maxWidthPx = screenWidthPx - rightBarWidthPx

            val scale = min(
                maxWidthPx / videoOriginalWidth,
                availableHeightPx / videoOriginalHeight
            )

            val videoWidthDp = with(density) { (videoOriginalWidth * scale).toDp() }
            val videoHeightDp = with(density) { (videoOriginalHeight * scale).toDp() }

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            SurfaceViewRenderer(ctx).apply {
                                init(eglBaseContext, null)
                                setEnableHardwareScaler(true)
                                setMirror(false)

                                setOnTouchListener { view, event ->
                                    val videoWidth = view.width
                                    val videoHeight = view.height
                                    val relativeX = (event.x * (device.devicePhysicalWidth ?: 1280) / videoWidth).toInt()
                                    val relativeY = (event.y * (device.devicePhysicalHeight ?: 720) / videoHeight).toInt()

                                    when (event.actionMasked) {
                                        MotionEvent.ACTION_DOWN ->
                                            WebRTCManager.sendCoordinates(device.id, WebRTCManager.MouseEventType.MOUSE_DOWN, relativeX, relativeY, 0)
                                        MotionEvent.ACTION_MOVE ->
                                            WebRTCManager.sendCoordinates(device.id, WebRTCManager.MouseEventType.MOUSE_MOVE, relativeX, relativeY, 0)
                                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                            WebRTCManager.sendCoordinates(device.id, WebRTCManager.MouseEventType.MOUSE_UP, relativeX, relativeY, 0)
                                            view.performClick()
                                        }
                                        MotionEvent.ACTION_SCROLL -> {
                                            val deltaY = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                                            if (deltaY > 0)
                                                WebRTCManager.sendCoordinates(device.id, WebRTCManager.MouseEventType.MOUSE_ROLLUP, relativeX, relativeY, 0)
                                            else
                                                WebRTCManager.sendCoordinates(device.id, WebRTCManager.MouseEventType.MOUSE_ROLLDOWN, relativeX, relativeY, 0)
                                        }
                                    }
                                    true
                                }

                                WebRTCManager.bindRemoteVideoRenderer(device.id, this)
                            }
                        },
                        modifier = Modifier
                            .width(videoWidthDp)
                            .height(videoHeightDp),
                        update = { renderer ->
                            WebRTCManager.bindRemoteVideoRenderer(device.id, renderer)
                        }
                    )
                }

                val iconSize = 24f

                Column(
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .height(videoHeightDp)
                        .width(rightBarWidth)
                ) {
                    IconButton(onClick = { exitPage() }, modifier = Modifier.padding(0.dp).size(iconSize.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = null, tint = MaterialTheme.colors.surface)
                    }
                    IconButton(onClick = { scope.launch { if (sheetState.isCollapsed) sheetState.expand() else sheetState.collapse() } }, modifier = Modifier.padding(0.dp).size(iconSize.dp)) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = "Camera", tint = MaterialTheme.colors.surface)
                    }
                    IconButton(onClick = { CameraStreamManager.switchCamera() }, modifier = Modifier.padding(0.dp).size(iconSize.dp)) {
                        Icon(Icons.Filled.FlipCameraIos, contentDescription = "Camera", tint = MaterialTheme.colors.surface)
                    }
                    IconButton(onClick = { mirror = !mirror; CameraStreamManager.updateVideoRotation(device.id, rotation, mirror) }, modifier = Modifier.padding(0.dp).size(iconSize.dp)) {
                        Icon(Icons.Filled.Flip, contentDescription = "Camera", tint = MaterialTheme.colors.surface)
                    }
                    listOf(0, 90, 180, 270).forEach { deg ->
                        IconButton(onClick = { rotation = deg; CameraStreamManager.updateVideoRotation(device.id, rotation, mirror) }, modifier = Modifier.padding(0.dp).size(iconSize.dp)) {
                            Text(
                                text = when (deg) {
                                    0 -> IconFont.ROTATE_0_DEGREE
                                    90 -> IconFont.ROTATE_90_DEGREE
                                    180 -> IconFont.ROTATE_180_DEGREE
                                    270 -> IconFont.ROTATE_270_DEGREE
                                    else -> ""
                                },
                                fontSize = iconSize.sp,
                                fontFamily = IconFont.FontFamily,
                                color = MaterialTheme.colors.surface
                            )
                        }
                    }
                }

                if (shouldLandscape && rightPaddingDp > 0.dp) {
                    Spacer(modifier = Modifier.width(rightPaddingDp))
                }
            }
        }
    }

    LaunchedEffect(device.id) {
        WebRTCManager.startScreenRecord(device.id, enableAudio = true)
    }

    BackHandler { exitPage() }
}

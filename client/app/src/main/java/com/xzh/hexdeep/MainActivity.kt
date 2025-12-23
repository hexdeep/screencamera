package com.xzh.hexdeep

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.xzh.hexdeep.manager.WebRTCManager
import com.xzh.hexdeep.pages.AddDevicePage
import com.xzh.hexdeep.pages.DeviceSettingPage
import com.xzh.hexdeep.pages.FullScreenVideo
import com.xzh.hexdeep.ui.theme.HexDeepTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            HexDeepTheme {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = "main"
                ) {

                    // 主界面：有 TopBar + BottomNavigation
                    composable("main") {
                        MainScreen(navController)   // ← 传给 MainScreen
                    }

                    // 添加设备页面（独立布局）
                    composable("add_device") {
                        AddDevicePage(
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable("device_full/{deviceId}") { backStackEntry ->
                        val deviceId = backStackEntry.arguments?.getString("deviceId") ?: return@composable

                        val device = WebRTCManager.devicesFlow.collectAsState().value
                            .find { it.id == deviceId }

                        device?.let {
                            FullScreenVideo(
                                device = it,
                                onExit = { navController.popBackStack() }
                            )
                        }
                    }

                    composable("device_setting/{deviceId}") {backStackEntry ->
                        val deviceId = backStackEntry.arguments?.getString("deviceId") ?: return@composable

                        val device = WebRTCManager.devicesFlow.collectAsState().value
                            .find { it.id == deviceId }

                        device?.let {
                            DeviceSettingPage(
                                deviceId = device.id,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}

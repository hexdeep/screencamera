package com.xzh.hexdeep

import androidx.compose.foundation.background
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.*
import androidx.navigation.compose.rememberNavController
import com.xzh.hexdeep.manager.WebRTCManager
import com.xzh.hexdeep.pages.DevicePage
import com.xzh.hexdeep.pages.MyPage

@Composable
fun MainScreen(navController: NavController) {
    val innerNavController = rememberNavController()
    val navBackStackEntry by innerNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var gridColumns by remember { mutableIntStateOf(3) }


    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colors.primary) // TopBar 背景颜色
            ) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(
                            WindowInsets.statusBars
                                .asPaddingValues()
                                .calculateTopPadding() // ✅ 获取状态栏高度
                        )
                )
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    elevation = 0.dp,
                    actions = {
                        // ---- 添加设备按钮 ----
                        IconButton(onClick = {
                            navController.navigate("add_device")
                        }) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_device))
                        }

                        // ---- 刷新按钮 ----
                        IconButton(onClick = {
                            WebRTCManager.refreshAllPeerConnections()
                        }) {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
                        }

                        var menuExpanded by remember { mutableStateOf(false) } // 菜单显示状态

                        Box{
                            // 右上角语言图标按钮
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Filled.GridView, contentDescription = stringResource(R.string.grid_menu))
                            }

                            // 下拉菜单
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                offset = DpOffset(0.dp, 5.dp)
                            ) {
                                Column(
                                    modifier = Modifier.width(120.dp)   // ← 设置菜单宽度
                                ) {
                                    DropdownMenuItem(
                                        modifier = Modifier.height(32.dp),
                                        onClick = {
                                            menuExpanded = false
                                            gridColumns = 2;
                                        }
                                    ) {
                                        Text(stringResource(R.string.grid_4))
                                    }

                                    DropdownMenuItem(
                                        modifier = Modifier.height(32.dp),
                                        onClick = {
                                            menuExpanded = false
                                            gridColumns = 3;
                                        }
                                    ) {
                                        Text(stringResource(R.string.grid_9))
                                    }

                                    DropdownMenuItem(
                                        modifier = Modifier.height(32.dp),
                                        onClick = {
                                            menuExpanded = false
                                            gridColumns = 4;
                                        }
                                    ) {
                                        Text(stringResource(R.string.grid_16))
                                    }
                                }
                            }
                        }

                    }
                )
            }

        },
        bottomBar = {
            Column {
                Modifier
                    .background(MaterialTheme.colors.primary)
                BottomNavigation {
                    BottomNavigationItem(
                        selected = currentRoute == "device",
                        onClick = { innerNavController.navigate("device"){launchSingleTop = true} },
                        label = { Text(stringResource(R.string.device)) },
                        icon = { Icon(Icons.Filled.Devices, contentDescription = stringResource(R.string.device)) },
                        selectedContentColor = MaterialTheme.colors.secondary,
                        unselectedContentColor = MaterialTheme.colors.onSurface
                    )
                    BottomNavigationItem(
                        selected = currentRoute == "my",
                        onClick = { innerNavController.navigate("my"){launchSingleTop = true} },
                        label = { Text(stringResource(R.string.my)) },
                        icon = { Icon(Icons.Filled.Person, contentDescription = stringResource(R.string.my)) },
                        selectedContentColor = MaterialTheme.colors.secondary,
                        unselectedContentColor = MaterialTheme.colors.onSurface
                    )
                }
                Spacer(
                    modifier = Modifier.height(
                        WindowInsets.navigationBars
                            .asPaddingValues()
                            .calculateBottomPadding()
                    )
                )
            }

        }
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            NavHost(innerNavController, startDestination = "device") {
                composable("device") {
                    val devices by WebRTCManager.devicesFlow.collectAsState()

                    DevicePage(
                        devices = devices,
                        columns = gridColumns,
                        onDeviceClick = { device ->
                            navController.navigate("device_full/${device.id}")
                        }
                    )
                }

                composable("my") { MyPage() }
            }
        }
    }
}

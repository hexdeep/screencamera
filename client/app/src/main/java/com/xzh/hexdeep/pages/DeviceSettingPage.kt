package com.xzh.hexdeep.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.FirstBaseline
import com.xzh.hexdeep.manager.WebRTCManager
import com.xzh.hexdeep.store.DeviceIdStore

@Composable
fun DeviceSettingPage(
    deviceId: String,
    onBack: () -> Unit
) {
    val device = remember(deviceId) {
        DeviceIdStore.get(deviceId)
    }

    if (device == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    var remark by remember { mutableStateOf(device.remark) }

    val isSaveEnabled = remark.isNotBlank() && remark != device.remark

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier.background(MaterialTheme.colors.primary)
            ) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(
                            WindowInsets.statusBars
                                .asPaddingValues()
                                .calculateTopPadding()
                        )
                )
                TopAppBar(
                    title = { Text("设备设置") },
                    elevation = 0.dp,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    }
                )
            }
        }
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.primary)
                .padding(inner)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {

                /** -------- Device ID（只读） -------- */
                UnderlineInputReadOnly(
                    title = "设备ID",
                    value = device.id
                )

                /** -------- Remark -------- */
                UnderlineInput(
                    title = "备注",
                    value = remark,
                    placeholder = "请输入备注",
                    onValueChange = { if (it.length <= 10) remark = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    /** 删除（危险操作） */
                    OutlinedButton(
                        onClick = {
                            WebRTCManager.removeDevice(deviceId)
                            DeviceIdStore.remove(deviceId)
                            onBack()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.Red
                        )
                    ) {
                        Text("删除")
                    }

                    /** 保存（主按钮） */
                    Button(
                        onClick = {
                            DeviceIdStore.updateRemark(deviceId, remark)
                            onBack()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = isSaveEnabled,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (isSaveEnabled)
                                MaterialTheme.colors.secondary
                            else
                                MaterialTheme.colors.onSurface.copy(alpha = 0.3f)
                        )
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}

@Composable
fun UnderlineInputReadOnly(
    title: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier
                .width(60.dp)
                .padding(top = 12.dp)
                .alignBy(FirstBaseline)
        )

        SelectionContainer {
            TextField(
                value = value,
                onValueChange = {},
                enabled = false,
                modifier = Modifier.alignBy(FirstBaseline),
                colors = TextFieldDefaults.textFieldColors(
                    backgroundColor = MaterialTheme.colors.primary,
                    cursorColor = MaterialTheme.colors.secondary,
                    focusedIndicatorColor = MaterialTheme.colors.background,
                    unfocusedIndicatorColor = MaterialTheme.colors.background,
                    textColor = MaterialTheme.colors.onSurface
                )
            )
        }
    }
}


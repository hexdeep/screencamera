package com.xzh.hexdeep.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

@Composable
fun CameraMicSheetContent(
    onDisconnect: () -> Unit,
    onConnect: () -> Unit,
    onImportCameraCheckedChange: (Boolean) -> Unit,
    onImportMicCheckedChange: (Boolean) -> Unit,
    importCameraChecked: Boolean,
    importMicChecked: Boolean
) {
    val density = LocalDensity.current
    val navBarHeightDp: Dp = with(density) {
        val insets = ViewCompat.getRootWindowInsets(LocalView.current)
        (insets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0).toDp()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(bottom = navBarHeightDp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = importCameraChecked,
                    onCheckedChange = onImportCameraCheckedChange,
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colors.secondary)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "导入摄像头", color = MaterialTheme.colors.onPrimary)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = importMicChecked,
                    onCheckedChange = onImportMicCheckedChange,
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colors.secondary)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "导入麦克风", color = MaterialTheme.colors.onPrimary)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = onDisconnect,
                colors = ButtonDefaults.buttonColors(backgroundColor = Color.Gray)
            ) {
                Text("断开", color = MaterialTheme.colors.onPrimary)
            }

            Button(
                onClick = onConnect,
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.secondary)
            ) {
                Text("连接", color = MaterialTheme.colors.onPrimary)
            }
        }
    }
}
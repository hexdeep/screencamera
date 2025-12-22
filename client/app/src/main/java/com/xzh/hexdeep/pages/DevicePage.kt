package com.xzh.hexdeep.pages

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xzh.hexdeep.manager.WebRTCManager
import com.xzh.hexdeep.widgets.DeviceCard

@Composable
fun DevicePage(
    devices: List<WebRTCManager.PeerConnectionWrapper>,
    columns: Int = 2,
    onDeviceClick: (WebRTCManager.PeerConnectionWrapper) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            count = devices.size,
            key = { index -> devices[index].id },  // 唯一 key
            itemContent = { index ->
                val device = devices[index]

                // 使用 aspectRatio 固定宽高比 4:3
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(9f / 16f) // 固定宽高比
                ) {
                    DeviceCard(
                        device,
                        onClick = { onDeviceClick(device) }
                    )
                }
            }
        )
    }
}

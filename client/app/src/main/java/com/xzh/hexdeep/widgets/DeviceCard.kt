package com.xzh.hexdeep.widgets

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xzh.hexdeep.manager.WebRTCManager
import com.xzh.hexdeep.store.DeviceIdStore


@Composable
fun DeviceCard(
    device: WebRTCManager.PeerConnectionWrapper,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier,
        elevation = 4.dp,
        shape = RoundedCornerShape(4.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Gray)
                .clickable { onClick?.invoke() }
        ) {

            val overlayHeight = maxHeight * 0.12f
            val fontSize = (overlayHeight.value * 0.45f).sp
            val iconSize = overlayHeight * 0.6f
            val horizontalPadding = overlayHeight * 0.2f

            val processedBitmap: Bitmap? = device.thumbnail?.let { bitmap ->
                val w = device.thumbnailWidth ?: bitmap.width
                val h = device.thumbnailHeight ?: bitmap.height
                val needRotate = w > h
                rotateScaleToAspectBitmap(
                    src = bitmap,
                    degrees = if (needRotate) 90f else 0f,
                    targetAspect = 9f / 16f,
                    maxSize = 1080
                )
            }

            processedBitmap?.asImageBitmap()?.let {
                Image(
                    bitmap = it,
                    contentDescription = device.id,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Medium
                )
            } ?: Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("无画面", color = Color.White, fontSize = 14.sp)
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(overlayHeight)
                    .background(Color(0x88000000))
                    .padding(horizontal = horizontalPadding),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {

                    Text(
                        text = DeviceIdStore.getDeviceItemById(device.id)?.remark
                            ?: device.id.take(6),
                        color = MaterialTheme.colors.surface,
                        fontSize = fontSize,
                        lineHeight = fontSize,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "设置",
                        tint = MaterialTheme.colors.surface,
                        modifier = Modifier
                            .size(iconSize)
                            .clickable {
                                println("点击了设备 ${device.id} 的设置按钮")
                            }
                    )
                }
            }
        }
    }
}

// 将这个函数放在 DeviceCard 外部
fun rotateScaleToAspectBitmap(
    src: Bitmap,
    degrees: Float,
    targetAspect: Float,
    maxSize: Int = 1080
): Bitmap {
    // 1️⃣ 先旋转
    val rotateMatrix = Matrix().apply {
        if (degrees != 0f) postRotate(degrees)
    }

    val rotated = if (degrees != 0f) {
        Bitmap.createBitmap(src, 0, 0, src.width, src.height, rotateMatrix, false)
    } else {
        src
    }

    // 2️⃣ 计算目标宽高（不裁剪）
    val srcW = rotated.width
    val srcH = rotated.height

    var targetW: Int
    var targetH: Int

    if (srcW.toFloat() / srcH > targetAspect) {
        // 原图偏宽 → 拉高
        targetW = srcW
        targetH = (srcW / targetAspect).toInt()
    } else {
        // 原图偏高 → 拉宽
        targetH = srcH
        targetW = (srcH * targetAspect).toInt()
    }

    // 3️⃣ 限制最大尺寸（只缩小，不放大）
    val maxSide = maxOf(targetW, targetH)
    if (maxSide > maxSize) {
        val scale = maxSize.toFloat() / maxSide
        targetW = (targetW * scale).toInt()
        targetH = (targetH * scale).toInt()
    }

    // 4️⃣ 直接拉伸到目标尺寸（允许变形）
    return Bitmap.createScaledBitmap(
        rotated,
        targetW,
        targetH,
        true
    )
}

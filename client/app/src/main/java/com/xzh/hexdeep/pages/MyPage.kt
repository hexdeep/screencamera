package com.xzh.hexdeep.pages

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.xzh.hexdeep.BuildConfig

@Composable
fun MyPage() {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val projectUrl = "https://github.com/hexdeep/screencamera.git"

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("远程接入例子展示，本示例App开源，支持H5、小程序、安卓App，获取源码请联系官方客服")

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "项目地址：$projectUrl",
                modifier = Modifier.clickable {
                    clipboardManager.setText(AnnotatedString(projectUrl))
                    Toast.makeText(context, "项目地址已复制", Toast.LENGTH_SHORT).show()
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text("版本号：${BuildConfig.VERSION_NAME}")
        }
    }
}
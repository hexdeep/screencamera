package com.xzh.hexdeep.pages

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.Text
import androidx.compose.ui.unit.dp
import com.xzh.hexdeep.BuildConfig

@Composable
fun MyPage() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        //
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("远程接入例子展示，本示例app开源，支持H5、小程序、安卓App，获取源码请联系官方客服")

            Spacer(modifier = Modifier.height(8.dp))

            Text("版本号：${BuildConfig.VERSION_NAME}")
        }
    }
}

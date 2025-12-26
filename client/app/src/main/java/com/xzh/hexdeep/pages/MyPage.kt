package com.xzh.hexdeep.pages

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.Text

@Composable
fun MyPage() {
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("远程接入例子展示，本示例app开源，支持H5、小程序、安卓app，获取源码请联系官方客服")
    }
}

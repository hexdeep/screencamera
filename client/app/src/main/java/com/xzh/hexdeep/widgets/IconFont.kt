package com.xzh.hexdeep.widgets

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.xzh.hexdeep.R

/**
 * IconFont 常量类
 * 放置自定义字体图标及对应 Unicode
 */
object IconFont {
    // 1️⃣ 定义字体
    val FontFamily = FontFamily(
        Font(R.font.iconfont)
    )

    const val ROTATE_0_DEGREE = "\uE89A"       // 旋转0度
    const val ROTATE_90_DEGREE = "\uE73E"      // 旋转90度
    const val ROTATE_180_DEGREE = "\uE5D5"          // 至180-01
    const val ROTATE_270_DEGREE = "\uE65D"   // 270度旋转-16
    const val MIRROR = "\uE7AE"   // 镜像
}

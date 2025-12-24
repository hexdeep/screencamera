package com.xzh.hexdeep.manager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.xzh.hexdeep.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

object CheckForUpdateManager {

    private const val APK_URL = "https://download.hexdeep.com/screen_mirror/hexdeep.apk"
    private const val OSS_HEADER = "x-oss-meta-git-commit-id"

    private val client = OkHttpClient()

    /**
     * 检查更新
     */
    fun checkForUpdate(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder()
                    .url(APK_URL)
                    .head() // 只请求 Header，不下载文件
                    .build()

                client.newCall(request).execute().use { response ->
                    val remoteCommitId = response.header(OSS_HEADER)

                    if (!remoteCommitId.isNullOrEmpty() && remoteCommitId != BuildConfig.GIT_COMMIT_ID) {
                        // 发现新版本
                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(
                                context,
                                "发现新版本，正在打开下载页面...",
                                Toast.LENGTH_LONG
                            ).show()
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(APK_URL))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(
                        context,
                        "检查更新失败: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}

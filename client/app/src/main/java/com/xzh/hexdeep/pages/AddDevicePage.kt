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
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xzh.hexdeep.R
import com.xzh.hexdeep.manager.WebRTCManager
import com.xzh.hexdeep.store.DeviceIdStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

@Composable
fun AddDevicePage(onBack: () -> Unit) {

    var deviceId by remember { mutableStateOf("") }
    var remark by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isSaveEnabled = deviceId.isNotBlank() && remark.isNotBlank()
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colors.primary)
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
                    title = { Text(stringResource(R.string.add_device)) },
                    elevation = 0.dp,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.arrow_back)
                            )
                        }
                    }
                )
            }
        }
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()                  // 占满整个屏幕
                .background(MaterialTheme.colors.primary)  // 背景色
                .padding(inner)
        ){
            Column(
                modifier = Modifier
                    .padding(inner)
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {

                // ---- 设备 ID ----
                UnderlineInput(
                    title = stringResource(R.string.device_id),
                    value = deviceId,
                    placeholder = stringResource(R.string.device_id_placeholder),
                    onValueChange = { deviceId = it }
                )

                //Spacer(modifier = Modifier.height(24.dp))

                // ---- 备注 ----
                UnderlineInput(
                    title = stringResource(R.string.remark),
                    value = remark,
                    placeholder = stringResource(R.string.remark_placeholder),
                    onValueChange = { if (it.length <= 10) remark = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        coroutineScope.launch {
                            errorMessage = null
                            isLoading = true
                            val result = saveDeviceSuspend(deviceId, remark)
                            isLoading = false
                            if (result.first) {
                                DeviceIdStore.add(deviceId, remark)
                                WebRTCManager.addDevice(deviceId)
                                onBack()
                            } else {
                                errorMessage = result.second
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isSaveEnabled && !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (isSaveEnabled) MaterialTheme.colors.secondary else MaterialTheme.colors.onSurface.copy(alpha = 0.3f)
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colors.onPrimary,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("保存设备")
                    }
                }


                errorMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    SelectionContainer {
                        Text(
                            text = msg,
                            color = MaterialTheme.colors.error
                        )
                    }
                }
            }
        }


    }
}

@Composable
fun UnderlineInput(
    title: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {

        Text(
            text = title,
            modifier = Modifier
                .width(60.dp)        // 左侧标题宽度，可自行调整
                .padding(top = 12.dp)
                .alignBy (FirstBaseline)
        )

        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .alignBy (FirstBaseline),
            placeholder = { Text(placeholder) },
            singleLine = false,
            colors = TextFieldDefaults.textFieldColors(
                backgroundColor = MaterialTheme.colors.primary,
                cursorColor = MaterialTheme.colors.secondary,
                focusedIndicatorColor = MaterialTheme.colors.background,
                unfocusedIndicatorColor = MaterialTheme.colors.background,
                disabledIndicatorColor = MaterialTheme.colors.background,
                textColor = MaterialTheme.colors.onSurface
            ),
        )
    }
}

suspend fun saveDeviceSuspend(deviceId: String, remark: String): Pair<Boolean, String?> {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val url = "http://43.138.176.9:8880/device_online?id=$deviceId"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext false to "返回为空"

            val json = JSONObject(body)
            val code = json.optInt("code", -1)
            val err = json.optString("err", "未知错误")

            if (code == 200) {
                true to null
            } else {
                false to err
            }
        } catch (e: Exception) {
            false to (e.message ?: "网络请求异常")
        }
    }
}

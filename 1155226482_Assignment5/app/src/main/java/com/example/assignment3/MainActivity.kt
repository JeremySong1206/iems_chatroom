package com.example.assignment3
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Bundle
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import android.util.Log
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import android.Manifest
import android.content.ContentValues.TAG
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.messaging.FirebaseMessaging

@Throws(IOException::class)
private fun readStream(input: InputStream): String? {
    val reader = BufferedReader(InputStreamReader(input))
    var line: String? = ""
    var text: String? = ""
    while ((reader.readLine().also { line = it }) != null) {
        text += line
    }
    input.close()
    return text
}

@Serializable
data class ApiResponse(val data: List<Room>, val status: String)

@Serializable
data class Room(val id: Int, val name: String)

class MainActivity : ComponentActivity() {
    var currentChatroomId: Int? = null
    companion object {
        private const val TAG = "MainActivity"
        private const val NOTIFICATION_PERMISSION_CODE = 100
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        currentChatroomId = null
        // 初始化FCM
        initializeFCM()

        // 请求通知权限
        requestNotificationPermission()

        setContent {
            RoomList()
        }
    }

    private fun initializeFCM() {
        // 获取FCM Token
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "获取FCM Token失败", task.exception)
                return@addOnCompleteListener
            }

            val token = task.result
            sendTokenToServer(token)
            Log.d(TAG, "FCM Token: $token")
        }

        // 订阅通用主题
        FirebaseMessaging.getInstance().subscribeToTopic("all")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "订阅主题成功")
                } else {
                    Log.e(TAG, "订阅主题失败")
                }
            }
    }
    private fun sendTokenToServer(token: String) {
        Log.i(TAG, "发送 token 到服务器...")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://10.0.2.2:55722/submit_push_token")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "application/json")
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                val jsonData = """
                    {
                        "user_id": "1155226482_2",
                        "token": "$token"
                    }
                """.trimIndent()

                Log.i(TAG, "发送数据到服务器: $jsonData")

                conn.outputStream.use { os ->
                    val input = jsonData.toByteArray(charset("UTF-8"))
                    os.write(input, 0, input.size)
                }

                val responseCode = conn.responseCode
                val responseBody = if (responseCode == HttpURLConnection.HTTP_OK) {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } else {
                    conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "无错误详情"
                }

                Log.i(TAG, "服务器响应码: $responseCode")
                Log.i(TAG, "服务器响应内容: $responseBody")

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    Log.i(TAG, "Token 提交成功")
                } else {
                    Log.e(TAG, "Token 提交失败: $responseCode, 错误: $responseBody")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Token 提交过程发生异常", e)
                e.printStackTrace()
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_CODE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            NOTIFICATION_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "通知权限已授权")
                } else {
                    Log.w(TAG, "通知权限被拒绝")
                }
            }
        }
    }
}

            /*
// The code below is related to create notification channel for later use
            val channel = NotificationChannel("MyNotification","MyNotification",
                NotificationManager.IMPORTANCE_DEFAULT)
            val manager = getSystemService(NotificationManager::class.java) as
                    NotificationManager;
            manager.createNotificationChannel(channel)


             */

@Composable
fun RoomList() {
    var rooms by remember { mutableStateOf<List<Room>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current

    LaunchedEffect(Unit) {
        try {
            rooms = getRooms()
        } catch (e: Exception) {
            err = e.message
            Log.e("RoomList", "Error", e)
        } finally {
            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("≡ IEMS5722", modifier = Modifier.weight(1f))
        }

        when {
            loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(50.dp).align(Alignment.CenterHorizontally)
                )
            }
            err != null -> {
                Text(
                    "Error: $err",
                    modifier = Modifier.padding(16.dp)
                )
            }
            else -> {
                LazyColumn {
                    items(rooms) { room ->
                        Column {
                            Text(
                                room.name,
                                modifier = Modifier.clickable {
                                        val intent = Intent(ctx, ChatActivity::class.java).apply {
                                            putExtra("ROOM_ID", room.id)
                                            putExtra("ROOM_NAME", room.name)
                                        }
                                        ctx.startActivity(intent)
                                    }.fillMaxWidth()
                                    .padding(16.dp)
                            )
                            Divider()
                        }
                    }
                }
            }
        }
    }
}

suspend fun getRooms(): List<Room> = withContext(Dispatchers.IO) {
    try {
        val result = GET("http://10.0.2.2:55722/get_chatrooms/")
        Log.d("GetRooms", "Received response: $result")
        val resp = Json.decodeFromString<ApiResponse>(result)
        if (resp.status == "OK") {
            resp.data
        } else {
            throw Exception("API Error")
        }
    } catch (e: Exception) {
        Log.e("GetRooms", "Error", e)
        throw e
    }
}

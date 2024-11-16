package com.example.assignment3

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class MyFirebaseMessagingService : FirebaseMessagingService() {
    private val TAG = "FCMService"
    private val CHANNEL_ID = "ChatNotifications"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Chat Notifications",
                importance
            ).apply {
                description = "聊天室消息通知"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "Token刷新: $token")
        sendTokenToServer(token)
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
                        "user_id": "1155226482_1",
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



    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "收到消息: ${remoteMessage.from}")

        val chatroomId = remoteMessage.data["chatroom_id"]?.toIntOrNull()
        val chatroomName = remoteMessage.data["chatroom_name"] ?: "新消息"
        val messageContent = remoteMessage.data["message"] ?: ""
        val currentChatroomId = (application as? MainActivity)?.currentChatroomId

        // Only show notification if:
        // 1. App is in background AND
        // 2. User is in the same chatroom that the message was sent to
        if (!isAppForeground() && currentChatroomId == chatroomId) {
            showNotification(chatroomName, messageContent)
        }
    }

    private fun isAppForeground(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false
        val packageName = packageName
        for (appProcess in appProcesses) {
            if (appProcess.importance ==
                android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                appProcess.processName == packageName) {
                return true
            }
        }
        return false
    }

    private fun showNotification(chatroomName: String, message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(chatroomName)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
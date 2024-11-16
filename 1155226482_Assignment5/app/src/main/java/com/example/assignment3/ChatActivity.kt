package com.example.assignment3
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.HttpResponse
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.client.HttpClient
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.client.methods.HttpGet
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.impl.client.HttpClientBuilder
import com.google.firebase.messaging.FirebaseMessaging
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale




private val json = Json { ignoreUnknownKeys = true }

fun GET(url: String): String {
    var inputStream: InputStream? = null
    var result = ""
    try {
        val httpclient: HttpClient = HttpClientBuilder.create().build()
        val httpResponse: HttpResponse = httpclient.execute(HttpGet(url))
        inputStream = httpResponse.entity.content
        if (inputStream != null) result =
            convertInputStreamToString(inputStream).toString()
        else result = "Did not work!"
    } catch (e: Exception) {
        Log.d("InputStream", e.localizedMessage)
    }
    return result
}

@Throws(IOException::class)
private fun convertInputStreamToString(inputStream: InputStream): String {
    val bufferedReader = BufferedReader(InputStreamReader(inputStream))
    var line: String? = ""
    var result = StringBuilder()
    while (bufferedReader.readLine().also { line = it } != null) {
        result.append(line)
    }
    inputStream.close()
    return result.toString()
}



fun POST(url: String, postRequestModel: PostRequestModel): String {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.doOutput = true
    conn.doInput = true
    conn.readTimeout = 15000
    conn.connectTimeout = 15000
    conn.setRequestProperty("Content-Type", "application/json")
    conn.setRequestProperty("Accept", "application/json")

    val message = json.encodeToString(PostRequestModel.serializer(), postRequestModel)
    Log.d("POST", "Sending JSON: $message")
    val os = conn.outputStream
    val writer = BufferedWriter(OutputStreamWriter(os, "UTF-8"))
    writer.write(message)
    writer.flush()
    writer.close()
    os.close()

    return if (conn.responseCode == HttpURLConnection.HTTP_OK) {
        val inputStream = BufferedReader(InputStreamReader(conn.inputStream))
        val sb = StringBuilder()
        var line: String?
        while (inputStream.readLine().also { line = it } != null) {
            sb.append(line)
        }
        inputStream.close()
        sb.toString()
    } else {
        "ERROR"
    }
}

class ChatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val chatroomId = intent.getIntExtra("ROOM_ID", -1)
        val chatroomName = intent.getStringExtra("ROOM_NAME") ?: "Chatroom"

        (application as? MainActivity)?.currentChatroomId = chatroomId

        setContent {
            MaterialTheme {
                ChatRoom(chatroomId, chatroomName)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Reset current chatroom ID when leaving the chatroom
        (application as? MainActivity)?.currentChatroomId = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoom(roomId: Int, roomName: String) {
    val context = LocalContext.current
    var msgs by remember { mutableStateOf<List<Message>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var msgText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        getMessages(roomId) { newMsgs ->
            msgs = newMsgs
            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(roomName, fontSize = 20.sp) },
            navigationIcon = {
                IconButton(onClick = {
                    context.startActivity(Intent(context, MainActivity::class.java))
                }) {
                    Text("←", fontSize = 24.sp, textAlign = TextAlign.Center)
                }
            },
            actions = {
                IconButton(onClick = {
                    loading = true
                    CoroutineScope(Dispatchers.Main).launch {
                        getMessages(roomId) { newMsgs ->
                            msgs = newMsgs
                            loading = false
                        }
                    }
                }) {
                    Text("↻", fontSize = 24.sp, textAlign = TextAlign.Center)
                }
            }
        )

        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(msgs.reversed()) { msg ->
                    MessageItem(msg)

                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = msgText,
                onValueChange = { msgText = it },
                placeholder = { Text("Enter message:") },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                if (msgText.isNotBlank()) {
                    CoroutineScope(Dispatchers.Main).launch {
                        val newMsg = Message(
                            message = msgText,
                            name = "SONG Jiaming",
                            message_time = getCurrentTime(),
                            user_id = 1155226482
                        )
                        val messageToSend = msgText
                        msgText = ""
                        msgs = msgs + newMsg
                        val success = sendMessage(roomId, "1155226482", "SONG Jiaming", messageToSend)
                        if (success) {
                            getMessages(roomId) { newMsgs ->
                                msgs = newMsgs
                            }
                        }
                    }
                }
            }) {
                Text("Send")
            }
        }
    }
}

fun getCurrentTime(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date())
}


@Composable
fun MessageItem(message: Message) {
    val isUserMessage = message.name == "SONG Jiaming"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp),
        horizontalAlignment = if (isUserMessage) Alignment.End else Alignment.Start
    ) {
        Text(
            text = message.name,
            style = MaterialTheme.typography.bodySmall,
            color = if (isUserMessage) Color.Blue else Color.Gray
        )

        Box(
            modifier = Modifier
                .background(
                    color = if (isUserMessage) Color(0xFFB2EBF2) else Color(0xFFFFCDD2),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(8.dp)
        ) {
            Column {
                Text(text = message.message)
                Text(
                    text = message.message_time,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

suspend fun getMessages(chatroomId: Int, onResult: (List<Message>) -> Unit) {
    withContext(Dispatchers.IO) {
        try {
            val result = GET("http://10.0.2.2:55722/get_messages?chatroom_id=$chatroomId")
            val messageResponse = json.decodeFromString<MessageResponse>(result)
            if (messageResponse.status == "OK") {
                withContext(Dispatchers.Main) {
                    onResult(messageResponse.data.messages)
                }
            } else {
                throw Exception("API returned non-OK status")
            }
        } catch (e: Exception) {
            Log.e("getMessages", "Error getting messages", e)
            withContext(Dispatchers.Main) {
                onResult(emptyList())
            }
        }
    }
}

suspend fun sendMessage(chatroomId: Int, userId: String, name: String, message: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val postRequestModel = PostRequestModel(
                chatroom_id = chatroomId,
                user_id = userId,
                name = name,
                message = message
            )

            // 发送消息
            val result = POST("http://10.0.2.2:55722/send_message", postRequestModel)
            val response = json.decodeFromString<StatusResponse>(result)

            response.status == "OK"
        } catch (e: Exception) {
            Log.e("SendMessage", "发送消息失败: ${e.message}")
            false
        }
    }
}

@Serializable
data class MessageResponse(val data: MessageData, val status: String)

@Serializable
data class MessageData(val messages: List<Message>)

@Serializable
data class Message(
    val message: String,
    val name: String,
    val message_time: String,
    val user_id: Int
)

@Serializable
data class PostRequestModel(
    val chatroom_id: Int,  
    val user_id: String,
    val name: String,
    val message: String
)
@Serializable
data class StatusResponse(val status: String)
package com.whatesp

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

private const val PREFS_NAME = "whatesp_prefs"
private const val KEY_DEVICE_UUID = "device_uuid"
private const val KEY_ACCESS_TOKEN = "access_token"
private const val KEY_EXPIRES_AT = "expires_at"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                var currentScreen by remember { mutableStateOf(AppScreen.Main) }
                var selectedChat by remember { mutableStateOf<ChatSummary?>(null) }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (currentScreen) {
                        AppScreen.Main -> {
                            MainScreen(
                                context = this,
                                onOpenChat = { chat ->
                                    selectedChat = chat
                                    currentScreen = AppScreen.Chat
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }

                        AppScreen.Chat -> {
                            val chat = selectedChat

                            if (chat != null) {
                                ChatScreen(
                                    context = this,
                                    chat = chat,
                                    onBack = { currentScreen = AppScreen.Main },
                                    modifier = Modifier.padding(innerPadding)
                                )
                            } else {
                                MainScreen(
                                    context = this,
                                    onOpenChat = { selected ->
                                        selectedChat = selected
                                        currentScreen = AppScreen.Chat
                                    },
                                    modifier = Modifier.padding(innerPadding)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainScreen(
    context: Context,
    onOpenChat: (ChatSummary) -> Unit,
    modifier: Modifier = Modifier
) {
    var username by remember { mutableStateOf("demo") }
    var credential by remember { mutableStateOf("WESP_shared_dev_2026!") }
    var deviceName by remember { mutableStateOf("Android Emulator") }
    var statusMessage by remember { mutableStateOf("Pendiente de iniciar sesion") }
    var meMessage by remember { mutableStateOf("Resultado de /me pendiente") }
    var chatsMessage by remember { mutableStateOf("Resultado de /chats pendiente") }
    var chats by remember { mutableStateOf<List<ChatSummary>>(emptyList()) }
    var logoutMessage by remember { mutableStateOf("Resultado de logout pendiente") }
    var isLoading by remember { mutableStateOf(false) }
    var isMeLoading by remember { mutableStateOf(false) }
    var isChatsLoading by remember { mutableStateOf(false) }
    var isLogoutLoading by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "WhatsESP",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Login Android",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Usuario") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = TextFieldDefaults.colors()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = credential,
            onValueChange = { credential = it },
            label = { Text("Credential") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = TextFieldDefaults.colors()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = deviceName,
            onValueChange = { deviceName = it },
            label = { Text("Nombre del dispositivo") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = TextFieldDefaults.colors()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                isLoading = true
                statusMessage = "Iniciando sesion..."

                CoroutineScope(Dispatchers.IO).launch {
                    val result = loginRequest(
                        context = context,
                        username = username,
                        credential = credential,
                        deviceName = deviceName
                    )

                    withContext(Dispatchers.Main) {
                        isLoading = false

                        if (result.isSuccess) {
                            val loginResult = result.getOrNull()

                            if (loginResult != null) {
                                saveToken(
                                    context = context,
                                    accessToken = loginResult.accessToken,
                                    expiresAt = loginResult.expiresAt
                                )

                                statusMessage =
                                    "Login OK. Token guardado.\nExpira: ${loginResult.expiresAt}"
                            } else {
                                statusMessage = "Error inesperado: respuesta vacia"
                            }
                        } else {
                            statusMessage =
                                result.exceptionOrNull()?.message ?: "Error desconocido"
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Text("Iniciar sesion")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                isMeLoading = true
                meMessage = "Consultando /me..."

                CoroutineScope(Dispatchers.IO).launch {
                    val result = meRequest(context = context)

                    withContext(Dispatchers.Main) {
                        isMeLoading = false
                        meMessage = result.getOrElse { exception ->
                            exception.message ?: "Error desconocido"
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isMeLoading
        ) {
            Text("Probar /me")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                isChatsLoading = true
                chatsMessage = "Consultando /chats..."

                CoroutineScope(Dispatchers.IO).launch {
                    val result = chatsRequest(context = context)

                    withContext(Dispatchers.Main) {
                        isChatsLoading = false
                        if (result.isSuccess) {
                            chats = result.getOrDefault(emptyList())
                            chatsMessage = if (chats.isEmpty()) {
                                "GET /chats OK: no hay chats."
                            } else {
                                "GET /chats OK: pulsa un chat para abrirlo."
                            }
                        } else {
                            chats = emptyList()
                            chatsMessage = result.exceptionOrNull()?.message ?: "Error desconocido"
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isChatsLoading
        ) {
            Text("Listar chats")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (chats.isNotEmpty()) {
            Text(
                text = "Chats disponibles",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            chats.forEach { chat ->
                Button(
                    onClick = { onOpenChat(chat) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${chat.otherUsername} (Chat ${chat.chatId})\n${chat.lastMessagePreview}"
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                isLogoutLoading = true
                logoutMessage = "Cerrando sesion..."

                CoroutineScope(Dispatchers.IO).launch {
                    val result = logoutRequest(context = context)

                    withContext(Dispatchers.Main) {
                        isLogoutLoading = false
                        logoutMessage = result.getOrElse { exception ->
                            exception.message ?: "Error desconocido"
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLogoutLoading
        ) {
            Text("Cerrar sesion")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
            text = statusMessage,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isMeLoading) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
            text = meMessage,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isChatsLoading) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
            text = chatsMessage,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isLogoutLoading) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
            text = logoutMessage,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ChatScreen(
    context: Context,
    chat: ChatSummary,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chatTitle = "${chat.otherUsername} (Chat ${chat.chatId})"
    var messagesMessage by remember { mutableStateOf("") }
    var chatMessages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var newMessageContent by remember { mutableStateOf("") }
    var sendMessageResult by remember { mutableStateOf("") }
    var isMessagesLoading by remember { mutableStateOf(false) }
    var isSendMessageLoading by remember { mutableStateOf(false) }
    val messagesListState = rememberLazyListState()

    fun loadMessages() {
        isMessagesLoading = true
        messagesMessage = ""

        CoroutineScope(Dispatchers.IO).launch {
            val result = messagesRequest(
                context = context,
                chatId = chat.chatId
            )

            withContext(Dispatchers.Main) {
                isMessagesLoading = false
                if (result.isSuccess) {
                    chatMessages = result.getOrDefault(emptyList())
                    messagesMessage = ""
                } else {
                    messagesMessage =
                        result.exceptionOrNull()?.message ?: "Error desconocido"
                }
            }
        }
    }

    LaunchedEffect(chat.chatId) {
        loadMessages()
    }

    LaunchedEffect(chat.chatId, chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            messagesListState.animateScrollToItem(chatMessages.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = chat.otherUsername,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Chat ${chat.chatId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(onClick = onBack) {
                    Text("Volver")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        HorizontalDivider()

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            state = messagesListState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isMessagesLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Cargando mensajes...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (messagesMessage.isNotBlank()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = messagesMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            } else if (chatMessages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Todavia no hay mensajes en este chat.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(chatMessages) { message ->
                    MessageItem(message = message)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = newMessageContent,
                onValueChange = { newMessageContent = it },
                label = { Text("Mensaje") },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(),
                enabled = !isSendMessageLoading
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    val contentToSend = newMessageContent.trim()

                    if (contentToSend.isBlank()) {
                        sendMessageResult = "El contenido del mensaje no puede estar vacio."
                    } else {
                        isSendMessageLoading = true
                        sendMessageResult = ""

                        CoroutineScope(Dispatchers.IO).launch {
                            val result = sendMessageRequest(
                                context = context,
                                chatId = chat.chatId,
                                content = contentToSend
                            )

                            withContext(Dispatchers.Main) {
                                isSendMessageLoading = false
                                sendMessageResult = result.exceptionOrNull()?.message ?: ""

                                if (result.isSuccess) {
                                    newMessageContent = ""
                                    loadMessages()
                                }
                            }
                        }
                    }
                },
                enabled = !isSendMessageLoading
            ) {
                Text(if (isSendMessageLoading) "..." else "Enviar")
            }
        }

        if (sendMessageResult.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = sendMessageResult,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

private enum class AppScreen {
    Main,
    Chat
}

private data class LoginResult(
    val accessToken: String,
    val expiresAt: String
)

private data class ChatSummary(
    val chatId: Int,
    val otherUsername: String,
    val lastMessagePreview: String
)

private data class ChatMessage(
    val content: String,
    val createdAt: String,
    val senderUserId: Int
)

@Composable
private fun MessageItem(message: ChatMessage) {
    val isSentByCurrentUser = message.senderUserId == 1

    val bubbleColor = if (isSentByCurrentUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = if (isSentByCurrentUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val bubbleShape = if (isSentByCurrentUser) {
        RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = if (isSentByCurrentUser) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(
                    color = bubbleColor,
                    shape = bubbleShape
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = message.createdAt,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor
                )
            }
        }
    }
}

private fun getOrCreateDeviceUuid(context: Context): String {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val existingUuid = prefs.getString(KEY_DEVICE_UUID, null)

    if (!existingUuid.isNullOrBlank()) {
        return existingUuid
    }

    val newUuid = UUID.randomUUID().toString()

    prefs.edit()
        .putString(KEY_DEVICE_UUID, newUuid)
        .apply()

    return newUuid
}

private fun saveToken(context: Context, accessToken: String, expiresAt: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    prefs.edit()
        .putString(KEY_ACCESS_TOKEN, accessToken)
        .putString(KEY_EXPIRES_AT, expiresAt)
        .apply()
}

private fun getSavedToken(context: Context): String? {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(KEY_ACCESS_TOKEN, null)
}

private fun clearToken(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    prefs.edit()
        .remove(KEY_ACCESS_TOKEN)
        .remove(KEY_EXPIRES_AT)
        .apply()
}

private fun loginRequest(
    context: Context,
    username: String,
    credential: String,
    deviceName: String
): Result<LoginResult> {
    return try {
        val deviceUuid = getOrCreateDeviceUuid(context)

        val url = URL("http://10.0.2.2:8000/auth/login")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5000
            readTimeout = 5000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }

        val jsonBody = JSONObject().apply {
            put("username", username)
            put("credential", credential)
            put("device_name", deviceName)
            put("device_uuid", deviceUuid)
        }

        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(jsonBody.toString())
            writer.flush()
        }

        val responseCode = connection.responseCode

        val responseText = if (responseCode in 200..299) {
            connection.inputStream.bufferedReader().use(BufferedReader::readText)
        } else {
            connection.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                ?: "Error HTTP $responseCode"
        }

        if (responseCode in 200..299) {
            val jsonResponse = JSONObject(responseText)

            val accessToken = jsonResponse.getString("access_token")
            val expiresAt = jsonResponse.getString("expires_at")

            Result.success(
                LoginResult(
                    accessToken = accessToken,
                    expiresAt = expiresAt
                )
            )
        } else {
            Result.failure(Exception("HTTP $responseCode: $responseText"))
        }
    } catch (e: Exception) {
        Result.failure(Exception("Fallo en login: ${e.message}", e))
    }
}

private fun meRequest(context: Context): Result<String> {
    return try {
        val accessToken = getSavedToken(context)

        if (accessToken.isNullOrBlank()) {
            return Result.failure(Exception("No hay token guardado. Inicia sesion primero."))
        }

        val url = URL("http://10.0.2.2:8000/me")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
            setRequestProperty("Authorization", "Bearer $accessToken")
        }

        val responseCode = connection.responseCode

        val responseText = if (responseCode in 200..299) {
            connection.inputStream.bufferedReader().use(BufferedReader::readText)
        } else {
            connection.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                ?: "Error HTTP $responseCode"
        }

        if (responseCode in 200..299) {
            Result.success("GET /me OK:\n$responseText")
        } else {
            Result.failure(Exception("HTTP $responseCode: $responseText"))
        }
    } catch (e: Exception) {
        Result.failure(Exception("Fallo en /me: ${e.message}", e))
    }
}

private fun chatsRequest(context: Context): Result<List<ChatSummary>> {
    return try {
        val accessToken = getSavedToken(context)

        if (accessToken.isNullOrBlank()) {
            return Result.failure(Exception("No hay token guardado. Inicia sesion primero."))
        }

        val url = URL("http://10.0.2.2:8000/chats")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
            setRequestProperty("Authorization", "Bearer $accessToken")
        }

        val responseCode = connection.responseCode

        val responseText = if (responseCode in 200..299) {
            connection.inputStream.bufferedReader().use(BufferedReader::readText)
        } else {
            connection.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                ?: "Error HTTP $responseCode"
        }

        if (responseCode in 200..299) {
            try {
                val trimmedResponse = responseText.trim()

                val chatsArray = when {
                    trimmedResponse.startsWith("{") -> {
                        val jsonResponse = JSONObject(trimmedResponse)
                        jsonResponse.getJSONArray("value")
                    }
                    trimmedResponse.startsWith("[") -> {
                        JSONArray(trimmedResponse)
                    }
                    else -> {
                        return Result.failure(
                            Exception("Respuesta inesperada de /chats:\n$responseText")
                        )
                    }
                }

                val chats = mutableListOf<ChatSummary>()

                for (index in 0 until chatsArray.length()) {
                    val chat = chatsArray.getJSONObject(index)
                    val chatId = chat.optInt("chat_id")
                    val otherUsername = chat.optString("other_username", "Sin usuario")
                    val lastMessagePreview =
                        chat.optString("last_message_preview", "Sin mensajes")

                    chats.add(
                        ChatSummary(
                            chatId = chatId,
                            otherUsername = otherUsername,
                            lastMessagePreview = lastMessagePreview
                        )
                    )
                }

                Result.success(chats)
            } catch (e: Exception) {
                Result.failure(Exception("Error parseando /chats: ${e.message}\n$responseText"))
            }
        } else {
            Result.failure(Exception("HTTP $responseCode: $responseText"))
        }
    } catch (e: Exception) {
        Result.failure(Exception("Fallo en /chats: ${e.message}", e))
    }
}

private fun messagesRequest(context: Context, chatId: Int): Result<List<ChatMessage>> {
    return try {
        val accessToken = getSavedToken(context)

        if (accessToken.isNullOrBlank()) {
            return Result.failure(Exception("No hay token guardado. Inicia sesion primero."))
        }

        val url = URL("http://10.0.2.2:8000/chats/$chatId/messages")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
            setRequestProperty("Authorization", "Bearer $accessToken")
        }

        val responseCode = connection.responseCode

        val responseText = if (responseCode in 200..299) {
            connection.inputStream.bufferedReader().use(BufferedReader::readText)
        } else {
            connection.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                ?: "Error HTTP $responseCode"
        }

        if (responseCode in 200..299) {
            try {
                val jsonResponse = JSONObject(responseText)
                val messagesArray = jsonResponse.getJSONArray("items")
                val messages = mutableListOf<ChatMessage>()

                for (index in 0 until messagesArray.length()) {
                    val message = messagesArray.getJSONObject(index)
                    val content = message.optString("content", "Sin contenido")
                    val createdAt = message.optString("created_at", "Sin fecha")
                    val senderUserId = message.optInt("sender_user_id", 0)

                    messages.add(
                        ChatMessage(
                            content = content,
                            createdAt = createdAt,
                            senderUserId = senderUserId
                        )
                    )
                }

                Result.success(messages)
            } catch (e: Exception) {
                Result.failure(
                    Exception("Error parseando /chats/$chatId/messages: ${e.message}\n$responseText")
                )
            }
        } else {
            Result.failure(Exception("HTTP $responseCode: $responseText"))
        }
    } catch (e: Exception) {
        Result.failure(Exception("Fallo en /chats/$chatId/messages: ${e.message}", e))
    }
}

private fun sendMessageRequest(context: Context, chatId: Int, content: String): Result<String> {
    return try {
        val accessToken = getSavedToken(context)

        if (accessToken.isNullOrBlank()) {
            return Result.failure(Exception("No hay token guardado. Inicia sesion primero."))
        }

        val url = URL("http://10.0.2.2:8000/chats/$chatId/messages")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5000
            readTimeout = 5000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }

        val jsonBody = JSONObject().apply {
            put("content", content)
        }

        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(jsonBody.toString())
            writer.flush()
        }

        val responseCode = connection.responseCode

        val responseText = if (responseCode in 200..299) {
            connection.inputStream.bufferedReader().use(BufferedReader::readText)
        } else {
            connection.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                ?: "Error HTTP $responseCode"
        }

        if (responseCode in 200..299) {
            try {
                val jsonResponse = JSONObject(responseText)
                val messageContent = jsonResponse.optString("content", "Sin contenido")
                val createdAt = jsonResponse.optString("created_at", "Sin fecha")

                Result.success(
                    "Mensaje enviado correctamente." +
                            "\nContenido: $messageContent" +
                            "\nFecha: $createdAt"
                )
            } catch (e: Exception) {
                Result.failure(
                    Exception("Error parseando envio de mensaje: ${e.message}\n$responseText")
                )
            }
        } else {
            Result.failure(Exception("HTTP $responseCode: $responseText"))
        }
    } catch (e: Exception) {
        Result.failure(Exception("Fallo enviando mensaje: ${e.message}", e))
    }
}

private fun logoutRequest(context: Context): Result<String> {
    return try {
        val accessToken = getSavedToken(context)

        if (accessToken.isNullOrBlank()) {
            return Result.failure(Exception("No hay token guardado. No hay sesion que cerrar."))
        }

        val url = URL("http://10.0.2.2:8000/auth/logout")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5000
            readTimeout = 5000
            setRequestProperty("Authorization", "Bearer $accessToken")
        }

        val responseCode = connection.responseCode

        val responseText = if (responseCode in 200..299) {
            connection.inputStream.bufferedReader().use(BufferedReader::readText)
        } else {
            connection.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                ?: "Error HTTP $responseCode"
        }

        if (responseCode in 200..299) {
            clearToken(context)
            Result.success("Logout OK. Sesion cerrada.\n$responseText")
        } else {
            Result.failure(Exception("HTTP $responseCode: $responseText"))
        }
    } catch (e: Exception) {
        Result.failure(Exception("Fallo en logout: ${e.message}", e))
    }
}
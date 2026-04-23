package com.whatesp

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
                                onForceBackToMain = {
                                    selectedChat = null
                                    currentScreen = AppScreen.Main
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
                                    onSessionExpired = {
                                        clearToken(this)
                                        selectedChat = null
                                        currentScreen = AppScreen.Main
                                    },
                                    modifier = Modifier.padding(innerPadding)
                                )
                            } else {
                                MainScreen(
                                    context = this,
                                    onOpenChat = { selected ->
                                        selectedChat = selected
                                        currentScreen = AppScreen.Chat
                                    },
                                    onForceBackToMain = {
                                        selectedChat = null
                                        currentScreen = AppScreen.Main
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
    onForceBackToMain: () -> Unit,
    modifier: Modifier = Modifier
) {
    var username by remember { mutableStateOf("demo") }
    var credential by remember { mutableStateOf("WESP_shared_dev_2026!") }
    var deviceName by remember { mutableStateOf("Android Emulator") }

    var statusMessage by remember { mutableStateOf("Comprobando sesion guardada...") }
    var chatsMessage by remember { mutableStateOf("Resultado de /chats pendiente") }
    var emergencyMessage by remember { mutableStateOf("Resultado de emergencia pendiente") }
    var logoutMessage by remember { mutableStateOf("Resultado de logout pendiente") }
    var chats by remember { mutableStateOf<List<ChatSummary>>(emptyList()) }

    var latestEmergencyAlert by remember { mutableStateOf<EmergencyEvent?>(null) }
    var lastSeenEmergencyId by remember { mutableLongStateOf(0L) }
    var dismissedEmergencyId by remember { mutableLongStateOf(0L) }

    var isAuthenticated by remember { mutableStateOf(false) }
    var isCheckingSession by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var isChatsLoading by remember { mutableStateOf(false) }
    var isEmergencyLoading by remember { mutableStateOf(false) }
    var isLogoutLoading by remember { mutableStateOf(false) }

    fun resetProtectedUi() {
        chats = emptyList()
        chatsMessage = "Resultado de /chats pendiente"
        emergencyMessage = "Resultado de emergencia pendiente"
        logoutMessage = "Resultado de logout pendiente"
        latestEmergencyAlert = null
        onForceBackToMain()
    }

    fun invalidateLocalSession(message: String) {
        clearToken(context)
        isAuthenticated = false
        statusMessage = message
        resetProtectedUi()
    }

    fun handleProtectedFailure(errorMessage: String) {
        if (isAuthErrorMessage(errorMessage)) {
            invalidateLocalSession("Sesion no valida o caducada. Inicia sesion de nuevo.")
        }
    }

    fun loadChats() {
        isChatsLoading = true
        chatsMessage = "Consultando /chats..."

        CoroutineScope(Dispatchers.IO).launch {
            val result = chatsRequest(context = context)

            withContext(Dispatchers.Main) {
                isChatsLoading = false
                if (result.isSuccess) {
                    chats = result.getOrDefault(emptyList())
                    chatsMessage = if (chats.isEmpty()) {
                        "No hay chats disponibles."
                    } else {
                        "Chats cargados correctamente."
                    }
                } else {
                    chats = emptyList()
                    val errorMessage =
                        result.exceptionOrNull()?.message ?: "Error desconocido"
                    chatsMessage = errorMessage
                    handleProtectedFailure(errorMessage)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = restoreSessionRequest(context)

            withContext(Dispatchers.Main) {
                isCheckingSession = false

                if (result.isSuccess) {
                    val session = result.getOrNull()

                    if (session != null) {
                        isAuthenticated = true
                        statusMessage =
                            "Sesion restaurada correctamente.\nUsuario: ${session.username}\nDispositivo: ${session.deviceName}\nExpira: ${session.expiresAt}"
                        loadChats()
                    } else {
                        invalidateLocalSession("No hay sesion valida. Inicia sesion primero.")
                    }
                } else {
                    invalidateLocalSession("No hay sesion valida. Inicia sesion primero.")
                }
            }
        }
    }

    LaunchedEffect(isAuthenticated) {
        if (!isAuthenticated) return@LaunchedEffect

        while (isAuthenticated) {
            val result = withContext(Dispatchers.IO) {
                latestEmergencyRequest(context)
            }

            if (result.isSuccess) {
                val emergency = result.getOrNull()

                if (emergency != null) {
                    if (lastSeenEmergencyId == 0L) {
                        lastSeenEmergencyId = emergency.id
                    } else if (emergency.id != lastSeenEmergencyId) {
                        lastSeenEmergencyId = emergency.id

                        if (emergency.id != dismissedEmergencyId) {
                            latestEmergencyAlert = emergency
                        }
                    }
                }
            } else {
                val errorMessage = result.exceptionOrNull()?.message ?: ""
                if (isAuthErrorMessage(errorMessage)) {
                    invalidateLocalSession("Sesion no valida o caducada. Inicia sesion de nuevo.")
                }
            }

            delay(5000)
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        if (isAuthenticated) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .padding(bottom = 110.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "WhatsESP",
                            style = MaterialTheme.typography.headlineMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Tus chats",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Button(
                        onClick = {
                            isLogoutLoading = true
                            logoutMessage = "Cerrando sesion..."

                            CoroutineScope(Dispatchers.IO).launch {
                                val result = logoutRequest(context = context)

                                withContext(Dispatchers.Main) {
                                    isLogoutLoading = false

                                    if (result.isSuccess) {
                                        isAuthenticated = false
                                        resetProtectedUi()
                                        logoutMessage = result.getOrDefault("Logout OK")
                                        statusMessage = "Sesion cerrada correctamente."
                                    } else {
                                        val errorMessage =
                                            result.exceptionOrNull()?.message ?: "Error desconocido"
                                        logoutMessage = errorMessage
                                        handleProtectedFailure(errorMessage)
                                    }
                                }
                            }
                        },
                        enabled = !isLogoutLoading && !isCheckingSession,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Cerrar sesión")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (latestEmergencyAlert != null) {
                    EmergencyAlertCard(
                        emergency = latestEmergencyAlert!!,
                        onDismiss = {
                            dismissedEmergencyId = latestEmergencyAlert?.id ?: 0L
                            latestEmergencyAlert = null
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (isChatsLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (chats.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = chatsMessage,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(chats) { chat ->
                            ChatListItem(
                                chat = chat,
                                onClick = { onOpenChat(chat) }
                            )
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .padding(bottom = 110.dp)
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

                                        isAuthenticated = true
                                        statusMessage =
                                            "Login OK. Sesion iniciada.\nExpira: ${loginResult.expiresAt}"
                                        loadChats()
                                    } else {
                                        isAuthenticated = false
                                        statusMessage = "Error inesperado: respuesta vacia"
                                    }
                                } else {
                                    isAuthenticated = false
                                    statusMessage =
                                        result.exceptionOrNull()?.message ?: "Error desconocido"
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && !isCheckingSession
                ) {
                    Text("Iniciar sesion")
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isCheckingSession || isLoading) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        EmergencyBottomButton(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            enabled = !isEmergencyLoading
        ) {
            isEmergencyLoading = true
            emergencyMessage = "Enviando evento de emergencia..."

            CoroutineScope(Dispatchers.IO).launch {
                val result = emergencyRequest(
                    context = context,
                    latitude = 41.6561,
                    longitude = -0.8773,
                    note = "Prueba emergencia desde Android"
                )

                withContext(Dispatchers.Main) {
                    isEmergencyLoading = false

                    if (result.isSuccess) {
                        emergencyMessage =
                            result.getOrDefault("Emergencia enviada correctamente.")
                    } else {
                        val errorMessage =
                            result.exceptionOrNull()?.message ?: "Error desconocido"
                        emergencyMessage = errorMessage
                        handleProtectedFailure(errorMessage)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatListItem(
    chat: ChatSummary,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Text(
                text = chat.otherUsername,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Chat ${chat.chatId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = chat.lastMessagePreview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.95f)
            )
        }
    }
}

@Composable
private fun EmergencyBottomButton(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(84.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFD93025),
            contentColor = Color.White
        )
    ) {
        Text(
            text = "SOS",
            fontSize = 18.sp
        )
    }
}

@Composable
private fun EmergencyAlertCard(
    emergency: EmergencyEvent,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFB3261E)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "ALERTA DE EMERGENCIA",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Evento: ${emergency.id}",
                color = Color.White
            )
            Text(
                text = "Usuario: ${emergency.userId}",
                color = Color.White
            )
            Text(
                text = "Dispositivo: ${emergency.deviceId}",
                color = Color.White
            )
            Text(
                text = "Latitud: ${emergency.latitude}",
                color = Color.White
            )
            Text(
                text = "Longitud: ${emergency.longitude}",
                color = Color.White
            )
            Text(
                text = "Nota: ${emergency.note}",
                color = Color.White
            )
            Text(
                text = "Fecha: ${emergency.createdAt}",
                color = Color.White
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(onClick = onDismiss) {
                Text("Cerrar alerta")
            }
        }
    }
}

@Composable
private fun ChatScreen(
    context: Context,
    chat: ChatSummary,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
    modifier: Modifier = Modifier
) {
    var messagesMessage by remember { mutableStateOf("") }
    var chatMessages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var newMessageContent by remember { mutableStateOf("") }
    var sendMessageResult by remember { mutableStateOf("") }
    var emergencyMessage by remember { mutableStateOf("") }
    var isMessagesLoading by remember { mutableStateOf(false) }
    var isSendMessageLoading by remember { mutableStateOf(false) }
    var isEmergencyLoading by remember { mutableStateOf(false) }
    val messagesListState = rememberLazyListState()

    fun expireSessionAndReturn() {
        clearToken(context)
        onSessionExpired()
    }

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
                    val errorMessage =
                        result.exceptionOrNull()?.message ?: "Error desconocido"
                    messagesMessage = errorMessage

                    if (isAuthErrorMessage(errorMessage)) {
                        expireSessionAndReturn()
                    }
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

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .padding(bottom = 110.dp)
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newMessageContent,
                    onValueChange = { newMessageContent = it },
                    placeholder = { Text("Mensaje...") },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = TextFieldDefaults.colors(),
                    enabled = !isSendMessageLoading,
                    singleLine = true
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

                                    if (result.isSuccess) {
                                        newMessageContent = ""
                                        sendMessageResult = ""
                                        loadMessages()
                                    } else {
                                        val errorMessage =
                                            result.exceptionOrNull()?.message
                                                ?: "Error desconocido"
                                        sendMessageResult = errorMessage

                                        if (isAuthErrorMessage(errorMessage)) {
                                            expireSessionAndReturn()
                                        }
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

            if (emergencyMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = emergencyMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        EmergencyBottomButton(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            enabled = !isEmergencyLoading
        ) {
            isEmergencyLoading = true
            emergencyMessage = "Enviando evento de emergencia..."

            CoroutineScope(Dispatchers.IO).launch {
                val result = emergencyRequest(
                    context = context,
                    latitude = 41.6561,
                    longitude = -0.8773,
                    note = "Prueba emergencia desde Android"
                )

                withContext(Dispatchers.Main) {
                    isEmergencyLoading = false

                    if (result.isSuccess) {
                        emergencyMessage =
                            result.getOrDefault("Emergencia enviada correctamente.")
                    } else {
                        val errorMessage =
                            result.exceptionOrNull()?.message ?: "Error desconocido"
                        emergencyMessage = errorMessage

                        if (isAuthErrorMessage(errorMessage)) {
                            expireSessionAndReturn()
                        }
                    }
                }
            }
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

private data class SessionInfo(
    val userId: Int,
    val username: String,
    val deviceUuid: String,
    val deviceName: String,
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

private data class EmergencyEvent(
    val id: Long,
    val userId: Int,
    val deviceId: Int,
    val latitude: String,
    val longitude: String,
    val note: String,
    val createdAt: String
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = formatMessageTime(message.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.65f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

private fun formatMessageTime(createdAt: String): String {
    return try {
        if (createdAt.length >= 16) {
            createdAt.substring(11, 16)
        } else {
            createdAt
        }
    } catch (e: Exception) {
        createdAt
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

private fun isAuthErrorMessage(message: String?): Boolean {
    if (message.isNullOrBlank()) return false

    val normalized = message.lowercase()

    return normalized.contains("http 401") ||
            normalized.contains("missing bearer token") ||
            normalized.contains("token revoked") ||
            normalized.contains("token expired") ||
            normalized.contains("invalid authorization header")
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

private fun restoreSessionRequest(context: Context): Result<SessionInfo> {
    return try {
        val accessToken = getSavedToken(context)

        if (accessToken.isNullOrBlank()) {
            return Result.failure(Exception("No hay token guardado."))
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
            val jsonResponse = JSONObject(responseText)

            Result.success(
                SessionInfo(
                    userId = jsonResponse.optInt("user_id", 0),
                    username = jsonResponse.optString("username", "Sin usuario"),
                    deviceUuid = jsonResponse.optString("device_uuid", "Sin device_uuid"),
                    deviceName = jsonResponse.optString("device_name", "Sin nombre"),
                    expiresAt = jsonResponse.optString("expires_at", "Sin fecha")
                )
            )
        } else {
            clearToken(context)
            Result.failure(Exception("HTTP $responseCode: $responseText"))
        }
    } catch (e: Exception) {
        clearToken(context)
        Result.failure(Exception("Fallo restaurando sesion: ${e.message}", e))
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

private fun emergencyRequest(
    context: Context,
    latitude: Double,
    longitude: Double,
    note: String
): Result<String> {
    return try {
        val accessToken = getSavedToken(context)

        if (accessToken.isNullOrBlank()) {
            return Result.failure(Exception("No hay token guardado. Inicia sesion primero."))
        }

        val url = URL("http://10.0.2.2:8000/emergency")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5000
            readTimeout = 5000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }

        val jsonBody = JSONObject().apply {
            put("latitude", latitude)
            put("longitude", longitude)
            put("note", note)
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
                val eventId = jsonResponse.optInt("event_id", 0)
                val createdAt = jsonResponse.optString("created_at", "Sin fecha")
                val savedLatitude = jsonResponse.optString("latitude", latitude.toString())
                val savedLongitude = jsonResponse.optString("longitude", longitude.toString())

                Result.success(
                    "Emergencia enviada correctamente." +
                            "\nEvento: $eventId" +
                            "\nLatitud: $savedLatitude" +
                            "\nLongitud: $savedLongitude" +
                            "\nFecha: $createdAt"
                )
            } catch (e: Exception) {
                Result.failure(
                    Exception("Error parseando /emergency: ${e.message}\n$responseText")
                )
            }
        } else {
            Result.failure(Exception("HTTP $responseCode: $responseText"))
        }
    } catch (e: Exception) {
        Result.failure(Exception("Fallo en /emergency: ${e.message}", e))
    }
}

private fun latestEmergencyRequest(context: Context): Result<EmergencyEvent> {
    return try {
        val accessToken = getSavedToken(context)

        if (accessToken.isNullOrBlank()) {
            return Result.failure(Exception("No hay token guardado. Inicia sesion primero."))
        }

        val url = URL("http://10.0.2.2:8000/emergency/latest")
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
            val jsonResponse = JSONObject(responseText)

            Result.success(
                EmergencyEvent(
                    id = jsonResponse.optLong("id", 0L),
                    userId = jsonResponse.optInt("user_id", 0),
                    deviceId = jsonResponse.optInt("device_id", 0),
                    latitude = jsonResponse.optString("latitude", "Sin latitud"),
                    longitude = jsonResponse.optString("longitude", "Sin longitud"),
                    note = jsonResponse.optString("note", "Sin nota"),
                    createdAt = jsonResponse.optString("created_at", "Sin fecha")
                )
            )
        } else {
            Result.failure(Exception("HTTP $responseCode: $responseText"))
        }
    } catch (e: Exception) {
        Result.failure(Exception("Fallo en /emergency/latest: ${e.message}", e))
    }
}

private fun logoutRequest(context: Context): Result<String> {
    return try {
        val accessToken = getSavedToken(context)

        if (accessToken.isNullOrBlank()) {
            clearToken(context)
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
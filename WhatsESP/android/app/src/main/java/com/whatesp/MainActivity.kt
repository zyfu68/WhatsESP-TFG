package com.whatesp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    StatusScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun StatusScreen(modifier: Modifier = Modifier) {
    var statusText by remember { mutableStateOf("Pulsa el botón para probar /status") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "WhatsESP — Comprobación Backend",
            style = MaterialTheme.typography.titleMedium
        )

        Button(
            enabled = !isLoading,
            onClick = {
                scope.launch {
                    isLoading = true
                    statusText = "Consultando backend…"
                    statusText = fetchStatus("http://10.0.2.2:8000/status")
                    isLoading = false
                }
            }
        ) {
            Text(if (isLoading) "Consultando…" else "Probar /status")
        }

        Text(text = statusText)
    }
}

suspend fun fetchStatus(url: String): String = withContext(Dispatchers.IO) {
    var conn: HttpURLConnection? = null
    try {
        conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
        }

        val code = conn.responseCode

        // Ojo: errorStream puede ser null
        val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)

        val body = stream.bufferedReader().use { it.readText() }

        "HTTP $code\n$body"
    } catch (e: Exception) {
        "ERROR: ${e.javaClass.simpleName}: ${e.message}"
    } finally {
        conn?.disconnect()
    }
}
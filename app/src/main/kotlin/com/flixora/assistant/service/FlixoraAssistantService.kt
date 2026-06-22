package com.flixora.assistant.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.flixora.assistant.R
import com.flixora.assistant.audio.AudioHandler
import com.flixora.assistant.network.FlixoraWebSocketClient
import com.flixora.assistant.tools.ToolExecutionEngine
import kotlinx.coroutines.*

class FlixoraAssistantService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var audioHandler: AudioHandler
    private lateinit var webSocketClient: FlixoraWebSocketClient
    private lateinit var toolExecutionEngine: ToolExecutionEngine

    companion object {
        const val CHANNEL_ID = "FlixoraAssistantChannel"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification(), 
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else 0
        )

        toolExecutionEngine = ToolExecutionEngine(this)
        audioHandler = AudioHandler(this)
        webSocketClient = FlixoraWebSocketClient(
            serverUrl = "ws://10.0.2.2:3000", // Android Emulator loopback to host
            onAudioReceived = { audioData ->
                audioHandler.playAudio(audioData)
            },
            onInterrupted = {
                audioHandler.stopPlayback()
            },
            onToolCall = { toolId, name, args ->
                serviceScope.launch {
                    val result = toolExecutionEngine.execute(name, args)
                    webSocketClient.sendToolResponse(toolId, name, result)
                }
            }
        )

        startAssistant()
    }

    private fun startAssistant() {
        serviceScope.launch {
            webSocketClient.connect()
            audioHandler.startContinuousCapture { audioChunk ->
                // Send audio to WebSocket
                webSocketClient.sendAudio(audioChunk)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Flixora Assistant Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Flixora Assistant Active")
            .setContentText("Listening and ready to help.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now) // Placeholder
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        audioHandler.stop()
        webSocketClient.disconnect()
    }
}

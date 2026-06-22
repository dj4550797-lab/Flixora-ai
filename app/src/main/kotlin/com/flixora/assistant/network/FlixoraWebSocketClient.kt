package com.flixora.assistant.network

import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class FlixoraWebSocketClient(
    private val serverUrl: String,
    private val onAudioReceived: (String) -> Unit,
    private val onInterrupted: () -> Unit,
    private val onToolCall: (String, String, JSONObject) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    
    private var webSocket: WebSocket? = null

    fun connect() {
        val request = Request.Builder().url(serverUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = JSONObject(text)
                when (json.optString("type")) {
                    "audio" -> onAudioReceived(json.getString("data"))
                    "interrupted" -> onInterrupted()
                    "tool_call" -> {
                        val call = json.getJSONObject("data")
                        onToolCall(
                            call.getString("id"),
                            call.getString("name"),
                            call.getJSONObject("args")
                        )
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                println("WebSocket failure: ${t.message}")
                // Attempt reconnect
                reconnect()
            }
        })
    }

    private fun reconnect() {
        Thread.sleep(5000)
        connect()
    }

    fun sendAudio(base64Data: String) {
        val msg = JSONObject().apply {
            put("type", "audio")
            put("data", base64Data)
        }
        webSocket?.send(msg.toString())
    }

    fun sendToolResponse(callId: String, name: String, result: JSONObject) {
        val response = JSONObject().apply {
            put("type", "tool_response")
            put("data", JSONObject().apply {
                put("functionResponses", listOf(
                    JSONObject().apply {
                        put("id", callId)
                        put("name", name)
                        put("response", result)
                    }
                ))
            })
        }
        webSocket?.send(response.toString())
    }

    fun disconnect() {
        webSocket?.close(1000, "App closed")
    }
}

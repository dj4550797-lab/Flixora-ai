package com.flixora.assistant.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.util.Base64
import kotlinx.coroutines.*
import java.util.concurrent.LinkedBlockingQueue

class AudioHandler(private val context: Context) {

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isRecording = false
    private val audioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val playbackQueue = LinkedBlockingQueue<ByteArray>()
    private var isPlaying = false

    @SuppressLint("MissingPermission")
    fun startContinuousCapture(onAudioData: (String) -> Unit) {
        val bufferSize = AudioRecord.getMinBufferSize(
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        isRecording = true
        audioRecord?.startRecording()

        audioScope.launch {
            val audioData = ShortArray(bufferSize)
            while (isRecording) {
                val read = audioRecord?.read(audioData, 0, bufferSize) ?: 0
                if (read > 0) {
                    val pcmData = shortArrayToByteArray(audioData.copyOfRange(0, read))
                    val base64 = Base64.encodeToString(pcmData, Base64.NO_WRAP)
                    onAudioData(base64)
                }
            }
        }
        
        startPlaybackLoop()
    }

    private fun startPlaybackLoop() {
        val bufferSize = AudioTrack.getMinBufferSize(
            24000,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(24000)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()
        isPlaying = true

        audioScope.launch {
            while (isPlaying) {
                val chunk = playbackQueue.take()
                audioTrack?.write(chunk, 0, chunk.size)
            }
        }
    }

    fun playAudio(base64Data: String) {
        val pcmData = Base64.decode(base64Data, Base64.DEFAULT)
        playbackQueue.add(pcmData)
    }

    fun stopPlayback() {
        playbackQueue.clear()
        audioTrack?.flush()
    }

    fun stop() {
        isRecording = false
        isPlaying = false
        audioRecord?.stop()
        audioRecord?.release()
        audioTrack?.stop()
        audioTrack?.release()
        audioScope.cancel()
    }

    private fun shortArrayToByteArray(shorts: ShortArray): ByteArray {
        val bytes = ByteArray(shorts.size * 2)
        for (i in shorts.indices) {
            bytes[i * 2] = (shorts[i].toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = ((shorts[i].toInt() shr 8) and 0xFF).toByte()
        }
        return bytes
    }
}

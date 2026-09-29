package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Real-time Bidirectional Voice Conversation Client using Gemini Live API (gemini-3.8-live / WebSockets).
 * Supports low-latency audio streaming, live transcriptions, interruption, and tools.
 */
class GeminiLiveClient(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "GeminiLiveClient"
        const val LIVE_MODEL = "gemini-3.8-live"
        private const val HOST = "generativelanguage.googleapis.com"
        private const val WEBSOCKET_PATH = "/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var audioTrack: AudioTrack? = null

    private val _connectionState = MutableStateFlow(LiveConnectionState.DISCONNECTED)
    val connectionState: StateFlow<LiveConnectionState> = _connectionState.asStateFlow()

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val _userTranscript = MutableStateFlow("")
    val userTranscript: StateFlow<String> = _userTranscript.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    enum class LiveConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    init {
        setupAudioOutput()
    }

    private fun setupAudioOutput() {
        try {
            val sampleRate = 24000 // Gemini Live standard output sample rate
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack init failed: ${e.message}")
        }
    }

    fun startLiveSession(
        systemInstruction: String = "You are Frank, the private on-device Super Admin. You have real-time voice conversations with the user. You speak naturally, concisely, and helpfully."
    ) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            _connectionState.value = LiveConnectionState.ERROR
            _liveTranscript.value = "Gemini API key is required for Live Voice Conversations. Please configure it in the Secrets panel."
            return
        }

        _connectionState.value = LiveConnectionState.CONNECTING
        _liveTranscript.value = "Connecting to Gemini 3.8 Live API..."

        val url = "wss://$HOST$WEBSOCKET_PATH?key=$apiKey"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Gemini Live WebSocket opened.")
                _connectionState.value = LiveConnectionState.CONNECTED
                _liveTranscript.value = "Connected. Speak to Frank in real-time..."

                // Send Initial Setup Message as specified by Gemini Live protocol
                val setupMessage = JSONObject().apply {
                    put("setup", JSONObject().apply {
                        put("model", "models/$LIVE_MODEL")
                        put("generationConfig", JSONObject().apply {
                            put("responseModalities", JSONArray().apply {
                                put("AUDIO")
                            })
                            put("speechConfig", JSONObject().apply {
                                put("voiceConfig", JSONObject().apply {
                                    put("prebuiltVoiceConfig", JSONObject().apply {
                                        put("voiceName", "Puck")
                                    })
                                })
                            })
                        })
                        put("systemInstruction", JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", systemInstruction)
                                })
                            })
                        })
                    })
                }
                webSocket.send(setupMessage.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleServerAudioBytes(bytes.toByteArray())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code / $reason")
                _connectionState.value = LiveConnectionState.DISCONNECTED
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                _connectionState.value = LiveConnectionState.ERROR
                _liveTranscript.value = "Connection error: ${t.message ?: "Live session ended."}"
            }
        })
    }

    private fun handleServerMessage(text: String) {
        try {
            val json = JSONObject(text)

            // Check serverContent
            if (json.has("serverContent")) {
                val serverContent = json.getJSONObject("serverContent")
                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            if (part.has("text")) {
                                val transcriptPart = part.getString("text")
                                _liveTranscript.value = (_liveTranscript.value + " " + transcriptPart).trim()
                            }
                            if (part.has("inlineData")) {
                                val inlineData = part.getJSONObject("inlineData")
                                val base64Audio = inlineData.optString("data")
                                if (base64Audio.isNotEmpty()) {
                                    val audioBytes = Base64.decode(base64Audio, Base64.DEFAULT)
                                    playAudioPcm(audioBytes)
                                }
                            }
                        }
                    }
                }

                if (serverContent.optBoolean("turnComplete", false)) {
                    _isSpeaking.value = false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing server message: ${e.message}")
        }
    }

    private fun handleServerAudioBytes(rawPcm: ByteArray) {
        playAudioPcm(rawPcm)
    }

    private fun playAudioPcm(pcmData: ByteArray) {
        try {
            _isSpeaking.value = true
            audioTrack?.write(pcmData, 0, pcmData.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio PCM: ${e.message}")
        }
    }

    /**
     * Send user text into the active Live API session
     */
    fun sendTextMessage(message: String) {
        if (_connectionState.value != LiveConnectionState.CONNECTED || webSocket == null) {
            return
        }

        try {
            val clientContent = JSONObject().apply {
                put("clientContent", JSONObject().apply {
                    put("turns", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", message)
                                })
                            })
                        })
                    })
                    put("turnComplete", true)
                })
            }
            webSocket?.send(clientContent.toString())
            _userTranscript.value = message
        } catch (e: Exception) {
            Log.e(TAG, "Error sending text to Live API: ${e.message}")
        }
    }

    /**
     * Send real-time microphone PCM chunks (16kHz Mono 16-bit) to Gemini Live API
     */
    fun sendAudioChunk(pcmChunk: ByteArray) {
        if (_connectionState.value != LiveConnectionState.CONNECTED || webSocket == null) {
            return
        }

        try {
            val base64Data = Base64.encodeToString(pcmChunk, Base64.NO_WRAP)
            val realtimeInput = JSONObject().apply {
                put("realtimeInput", JSONObject().apply {
                    put("mediaChunks", JSONArray().apply {
                        put(JSONObject().apply {
                            put("mimeType", "audio/pcm;rate=16000")
                            put("data", base64Data)
                        })
                    })
                })
            }
            webSocket?.send(realtimeInput.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending audio chunk: ${e.message}")
        }
    }

    fun endLiveSession() {
        try {
            webSocket?.close(1000, "User ended session")
            webSocket = null
            audioTrack?.stop()
            audioTrack?.flush()
            _connectionState.value = LiveConnectionState.DISCONNECTED
            _isSpeaking.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Error ending session: ${e.message}")
        }
    }

    fun release() {
        endLiveSession()
        try {
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audio track: ${e.message}")
        }
    }
}

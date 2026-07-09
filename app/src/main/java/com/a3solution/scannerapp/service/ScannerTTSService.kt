package com.a3solution.scannerapp.service

import android.app.*
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import com.a3solution.scannerapp.MainActivity
import com.a3solution.scannerapp.R
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*
import java.util.*

class ScannerTTSService : Service(), TextToSpeech.OnInitListener {
    private val TAG = "ScannerTTSService"
    private val CHANNEL_ID = "ScannerTTSChannel"
    private val NOTIFICATION_ID = 101

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var extractionJob: Job? = null
    
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    private var currentUris: List<Uri> = emptyList()
    private var isPaused = false
    private var currentIndex = 0
    private var returnScreen: String? = null
    private var docId: Int = -1

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_PAUSE_RESUME = "ACTION_PAUSE_RESUME"
        const val ACTION_SPEAK_TEXT = "ACTION_SPEAK_TEXT"
        const val ACTION_GET_STATUS = "ACTION_GET_STATUS"
        const val EXTRA_URIS = "EXTRA_URIS"
        const val EXTRA_TEXT = "EXTRA_TEXT"
        const val EXTRA_RETURN_SCREEN = "EXTRA_RETURN_SCREEN"
        const val EXTRA_DOC_ID = "EXTRA_DOC_ID"
        
        const val ACTION_STATUS_UPDATE = "com.a3solution.scannerapp.TTS_STATUS_UPDATE"
        const val EXTRA_IS_SPEAKING = "EXTRA_IS_SPEAKING"
    }

    private fun sendStatusBroadcast(isSpeaking: Boolean) {
        val intent = Intent(ACTION_STATUS_UPDATE).apply {
            putExtra(EXTRA_IS_SPEAKING, isSpeaking)
            `package` = packageName
        }
        sendBroadcast(intent)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            isTtsInitialized = true
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    updateNotification("Speaking page ${currentIndex + 1} of ${currentUris.size}")
                    sendStatusBroadcast(true)
                }
                override fun onDone(utteranceId: String?) {
                    if (!isPaused) {
                        currentIndex++
                        if (currentIndex >= currentUris.size) {
                            stopSpeech()
                        } else {
                            processNextPage()
                        }
                    }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "TTS Error on $utteranceId")
                    sendStatusBroadcast(false)
                }
            })
        } else {
            Log.e(TAG, "TTS Init Failed")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                @Suppress("DEPRECATION")
                val uris = intent.getParcelableArrayListExtra<Uri>(EXTRA_URIS)
                returnScreen = intent.getStringExtra(EXTRA_RETURN_SCREEN)
                docId = intent.getIntExtra(EXTRA_DOC_ID, -1)
                
                if (uris != null) {
                    currentUris = uris
                    currentIndex = 0
                    isPaused = false
                    startForeground(NOTIFICATION_ID, createNotification("Starting extraction..."))
                    processNextPage()
                }
            }
            ACTION_PAUSE_RESUME -> {
                isPaused = !isPaused
                if (isPaused) {
                    tts?.stop()
                    updateNotification("Speech Paused")
                } else {
                    processNextPage()
                }
            }
            ACTION_STOP -> {
                stopSpeech()
            }
            ACTION_GET_STATUS -> {
                sendStatusBroadcast(tts?.isSpeaking == true)
            }
            ACTION_SPEAK_TEXT -> {
                val text = intent.getStringExtra(EXTRA_TEXT)
                if (text != null && text.isNotBlank()) {
                    isPaused = false
                    startForeground(NOTIFICATION_ID, createNotification("Speaking extracted text..."))
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ScannerAppTTS")
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun processNextPage() {
        if (currentIndex >= currentUris.size) {
            stopSpeech()
            return
        }

        if (isPaused) return

        extractionJob?.cancel()
        extractionJob = serviceScope.launch {
            try {
                val uri = currentUris[currentIndex]
                val image = InputImage.fromFilePath(this@ScannerTTSService, uri)
                val result = withContext(Dispatchers.IO) {
                    com.google.android.gms.tasks.Tasks.await(textRecognizer.process(image))
                }
                val pageText = result.text
                if (pageText.isNotBlank()) {
                    tts?.speak(pageText, TextToSpeech.QUEUE_FLUSH, null, "page_$currentIndex")
                } else {
                    currentIndex++
                    processNextPage()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Extraction error", e)
                currentIndex++
                processNextPage()
            }
        }
    }

    private fun stopSpeech() {
        tts?.stop()
        extractionJob?.cancel()
        sendStatusBroadcast(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Scanner Speech Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_RETURN_SCREEN, returnScreen)
            putExtra(EXTRA_DOC_ID, docId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val pauseIntent = Intent(this, ScannerTTSService::class.java).apply { action = ACTION_PAUSE_RESUME }
        val pausePendingIntent = PendingIntent.getService(this, 1, pauseIntent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, ScannerTTSService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 2, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Scanner App Speaking")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_speaker)
            .setContentIntent(pendingIntent)
            .addAction(if (isPaused) R.drawable.ic_speaker else R.drawable.ic_stop, if (isPaused) "Resume" else "Pause", pausePendingIntent)
            .addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        tts?.shutdown()
        textRecognizer.close()
    }
}

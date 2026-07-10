package com.a3solution.scannerapp

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.foundation.clickable
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import android.content.Context
import androidx.core.content.FileProvider
import java.io.File
import coil.compose.AsyncImage
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Checkbox
import androidx.compose.ui.res.stringResource
import com.a3solution.scannerapp.data.AppDatabase
import com.a3solution.scannerapp.data.ScannedDocument
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import com.a3solution.scannerapp.ui.theme.ScannerAppTheme
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import android.speech.tts.TextToSpeech
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.ClipData
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.Color as AndroidColor
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.tasks.await
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.RequestOptions
import com.google.ai.client.generativeai.type.content
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material3.TextField
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt
import com.a3solution.scannerapp.service.ScannerTTSService

class MainActivity : ComponentActivity() {
    private val TAG = "ScannerApp_Main"
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null

    // REPLACE_WITH_YOUR_GEMINI_API_KEY
    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = "AIzaSyA68Q6blUU9vG2sKlGgtWQrBtYo6A-8FJI",
        requestOptions = RequestOptions(apiVersion = "v1beta"),
    )

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Extracted Text", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, getString(R.string.text_copied_toast), Toast.LENGTH_SHORT).show()
    }

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.getDefault(), "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    private fun getDocumentSize(doc: ScannedDocument): Long {
        var totalSize = 0L
        val uris = doc.imageUris.split(",").filter { it.isNotEmpty() }.map { Uri.parse(it) }
        for (uri in uris) {
            try {
                contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                    totalSize += it.length
                }
            } catch (e: Exception) {
                // Ignore errors for individual files
            }
        }
        return totalSize
    }

    private suspend fun compressImages(uris: List<Uri>): List<Uri> = withContext(Dispatchers.IO) {
        val compressedUris = mutableListOf<Uri>()
        for (uri in uris) {
            try {
                val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                if (bitmap != null) {
                    val file = File(cacheDir, "compressed_${System.currentTimeMillis()}_${compressedUris.size}.jpg")
                    java.io.FileOutputStream(file).use { out ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 60, out)
                    }
                    compressedUris.add(Uri.fromFile(file))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Compression error", e)
            }
        }
        compressedUris
    }

    private suspend fun applyWatermark(uris: List<Uri>, watermarkText: String, normalizedPosition: Offset, rotation: Float, isTiled: Boolean, scaleFactor: Float, watermarkColor: Int): List<Uri> = withContext(Dispatchers.IO) {
        val watermarkedUris = mutableListOf<Uri>()
        for (uri in uris) {
            try {
                val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                if (bitmap != null) {
                    val mutableBitmap = bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
                    val canvas = Canvas(mutableBitmap)
                    val paint = Paint().apply {
                        color = watermarkColor
                        alpha = if (isTiled) 60 else 100
                        textSize = (mutableBitmap.height / (if (isTiled) 30 else 20)).toFloat() * scaleFactor
                        isAntiAlias = true
                        textAlign = Paint.Align.CENTER
                    }
                    
                    if (isTiled) {
                        val stepX = mutableBitmap.width / 3f
                        val stepY = mutableBitmap.height / 5f
                        for (i in 0..3) {
                            for (j in 0..5) {
                                val x = i * stepX
                                val y = j * stepY
                                canvas.save()
                                canvas.rotate(rotation, x, y)
                                canvas.drawText(watermarkText, x, y, paint)
                                canvas.restore()
                            }
                        }
                    } else {
                        val x = mutableBitmap.width * normalizedPosition.x
                        val y = mutableBitmap.height * normalizedPosition.y
                        
                        canvas.save()
                        canvas.rotate(rotation, x, y)
                        canvas.drawText(watermarkText, x, y, paint)
                        canvas.restore()
                    }

                    val file = File(cacheDir, "watermarked_${System.currentTimeMillis()}_${watermarkedUris.size}.jpg")
                    java.io.FileOutputStream(file).use { out ->
                        mutableBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    watermarkedUris.add(Uri.fromFile(file))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Watermark error", e)
            }
        }
        watermarkedUris
    }

    private suspend fun generatePdfFromImages(uris: List<Uri>): Uri? = withContext(Dispatchers.IO) {
        val pdfDocument = PdfDocument()
        try {
            for ((index, uri) in uris.withIndex()) {
                val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                if (bitmap != null) {
                    val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    pdfDocument.finishPage(page)
                }
            }
            val file = File(cacheDir, "generated_${System.currentTimeMillis()}.pdf")
            java.io.FileOutputStream(file).use { out ->
                pdfDocument.writeTo(out)
            }
            Uri.fromFile(file)
        } catch (e: Exception) {
            Log.e(TAG, "PDF generation error", e)
            null
        } finally {
            pdfDocument.close()
        }
    }

    private suspend fun extractTextWithAi(uris: List<Uri>): String? {
        if (uris.isEmpty()) return null
        
        return try {
            val bitmaps = uris.map { uri ->
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            }.filterNotNull()

            if (bitmaps.isEmpty()) return null

            val inputContent = content {
                bitmaps.forEach { image(it) }
                text("Extract all text from these images accurately. If it is handwritten, decipher it carefully. Use the surrounding context to fix any obvious mistakes. Return ONLY the extracted text.")
            }

            val response = generativeModel.generateContent(inputContent)
            response.text
        } catch (e: Exception) {
            Log.e(TAG, "AI Visual Extraction Error", e)
            null
        }
    }

    enum class Screen { HOME, HISTORY, DETAILS }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.SUCCESS) {
                Log.e(TAG, "TTS Initialization failed!")
            }
        }

        val database = AppDatabase.getDatabase(this)
        val documentDao = database.documentDao()

        enableEdgeToEdge()
        setContent {
            ScannerAppTheme {
                var currentScreen by remember { mutableStateOf(Screen.HOME) }
                var selectedDocument by remember { mutableStateOf<ScannedDocument?>(null) }
                var showExitDialog by remember { mutableStateOf(false) }
                var documentToDelete by remember { mutableStateOf<ScannedDocument?>(null) }
                var neverAskDeleteAgain by remember { mutableStateOf(false) }
                var lastBackPressTime by remember { mutableLongStateOf(0L) }
                val coroutineScope = rememberCoroutineScope()

                var scannedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
                var pdfUri by remember { mutableStateOf<Uri?>(null) }
                var isSpeaking by remember { mutableStateOf(false) }
                var isListening by remember { mutableStateOf(false) }
                var isAiProcessing by remember { mutableStateOf(false) }
                var isCompressing by remember { mutableStateOf(false) }
                var isWatermarking by remember { mutableStateOf(false) }
                var aiEditedText by remember { mutableStateOf<String?>(null) }
                var showWatermarkDialog by remember { mutableStateOf(false) }
                var watermarkInput by remember { mutableStateOf("") }
                
                // Handle navigation from notification intent
                LaunchedEffect(intent) {
                    val returnScreen = intent.getStringExtra(ScannerTTSService.EXTRA_RETURN_SCREEN)
                    val returnDocId = intent.getIntExtra(ScannerTTSService.EXTRA_DOC_ID, -1)
                    
                    if (returnScreen == Screen.DETAILS.name && returnDocId != -1) {
                        documentDao.getAllDocuments().collectLatest { docs ->
                            val doc = docs.find { it.id == returnDocId }
                            if (doc != null) {
                                selectedDocument = doc
                                currentScreen = Screen.DETAILS
                            }
                        }
                    } else if (returnScreen == Screen.HOME.name) {
                        currentScreen = Screen.HOME
                        @Suppress("DEPRECATION")
                        val uris = intent.getParcelableArrayListExtra<Uri>(ScannerTTSService.EXTRA_URIS)
                        if (uris != null) {
                            scannedUris = uris
                        }
                    }
                }

                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted: Boolean ->
                    if (!isGranted) {
                        Toast.makeText(this@MainActivity, "Notifications are required to speak in background.", Toast.LENGTH_SHORT).show()
                    }
                }

                val requestPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted: Boolean ->
                    if (isGranted) {
                        Toast.makeText(this@MainActivity, "Permission granted! Try again.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Permission denied for voice commands.", Toast.LENGTH_SHORT).show()
                    }
                }

                val textRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

                DisposableEffect(Unit) {
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent?) {
                            if (intent?.action == ScannerTTSService.ACTION_STATUS_UPDATE) {
                                isSpeaking = intent.getBooleanExtra(ScannerTTSService.EXTRA_IS_SPEAKING, false)
                            }
                        }
                    }
                    val filter = android.content.IntentFilter(ScannerTTSService.ACTION_STATUS_UPDATE)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
                    } else {
                        @Suppress("UnspecifiedRegisterReceiverFlag")
                        registerReceiver(receiver, filter)
                    }

                    // Request initial status when activity opens
                    val statusIntent = Intent(this@MainActivity, ScannerTTSService::class.java).apply {
                        action = "ACTION_GET_STATUS"
                    }
                    startService(statusIntent)

                    onDispose {
                        unregisterReceiver(receiver)
                        tts?.stop()
                        tts?.shutdown()
                    }
                }

                fun startSpeechService(uris: List<Uri>, docId: Int = -1) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            return
                        }
                    }
                    val intent = Intent(this@MainActivity, ScannerTTSService::class.java).apply {
                        action = ScannerTTSService.ACTION_START
                        putParcelableArrayListExtra(ScannerTTSService.EXTRA_URIS, ArrayList(uris))
                        putExtra(ScannerTTSService.EXTRA_RETURN_SCREEN, currentScreen.name)
                        putExtra(ScannerTTSService.EXTRA_DOC_ID, docId)
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                    isSpeaking = true
                }

                fun stopSpeechService() {
                    val intent = Intent(this@MainActivity, ScannerTTSService::class.java).apply {
                        action = ScannerTTSService.ACTION_STOP
                    }
                    startService(intent)
                    isSpeaking = false
                }

                fun speakText(text: String) {
                    if (text.isBlank()) return
                    val intent = Intent(this@MainActivity, ScannerTTSService::class.java).apply {
                        action = ScannerTTSService.ACTION_SPEAK_TEXT
                        putExtra(ScannerTTSService.EXTRA_TEXT, text)
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                    isSpeaking = true
                }

                fun startVoiceCommand(onCommandReceived: (String) -> Unit) {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.listening_for_command))
                    }

                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this@MainActivity).apply {
                        setRecognitionListener(object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) { isListening = true }
                            override fun onBeginningOfSpeech() {}
                            override fun onRmsChanged(rmsdB: Float) {}
                            override fun onBufferReceived(buffer: ByteArray?) {}
                            override fun onEndOfSpeech() { isListening = false }
                            override fun onError(error: Int) {
                                isListening = false
                                Log.e(TAG, "Speech Error: $error")
                                Toast.makeText(this@MainActivity, getString(R.string.voice_not_recognized), Toast.LENGTH_SHORT).show()
                            }
                            override fun onResults(results: Bundle?) {
                                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                if (!matches.isNullOrEmpty()) {
                                    onCommandReceived(matches[0])
                                }
                            }
                            override fun onPartialResults(partialResults: Bundle?) {}
                            override fun onEvent(eventType: Int, params: Bundle?) {}
                        })
                        startListening(intent)
                    }
                }

                suspend fun applyAiEdit(originalText: String, command: String) {
                    if (originalText.isBlank()) return
                    isAiProcessing = true
                    try {
                        val prompt = "Original Text: $originalText\n\nUser Command: $command\n\nApply the command to the original text and return ONLY the edited text."
                        val response = generativeModel.generateContent(prompt)
                        val edited = response.text
                        if (!edited.isNullOrBlank()) {
                            aiEditedText = edited
                            speakText(edited)
                        } else {
                            Toast.makeText(this@MainActivity, getString(R.string.ai_edit_error), Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "AI Error", e)
                        Toast.makeText(this@MainActivity, "AI Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
                        isAiProcessing = false
                    }
                }

                suspend fun extractAndSpeak(uris: List<Uri>, onExtracted: (suspend (String) -> Unit)? = null) {
                    if (uris.isEmpty()) {
                        Toast.makeText(this@MainActivity, getString(R.string.no_text_found), Toast.LENGTH_SHORT).show()
                        return
                    }

                    if (onExtracted != null) {
                        Toast.makeText(this@MainActivity, getString(R.string.extracting_text), Toast.LENGTH_SHORT).show()
                        val fullText = StringBuilder()
                        try {
                            for (uri in uris) {
                                val image = InputImage.fromFilePath(this@MainActivity, uri)
                                val result = textRecognizer.process(image).await()
                                fullText.append(result.text).append("\n")
                            }
                            val extracted = fullText.toString()
                            if (extracted.isNotBlank()) {
                                onExtracted(extracted)
                            } else {
                                Toast.makeText(this@MainActivity, getString(R.string.no_text_found), Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error extracting text", e)
                            Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        startSpeechService(uris)
                    }
                }
                val options = remember {
                    GmsDocumentScannerOptions.Builder()
                        .setScannerMode(SCANNER_MODE_FULL)
                        .setResultFormats(RESULT_FORMAT_JPEG, RESULT_FORMAT_PDF)
                        .setGalleryImportAllowed(true)
                        .build()
                }
                
                val scanner = remember { GmsDocumentScanning.getClient(options) }
                
                BackHandler {
                    if (currentScreen == Screen.DETAILS) {
                        currentScreen = Screen.HISTORY
                    } else if (currentScreen == Screen.HISTORY) {
                        currentScreen = Screen.HOME
                    } else {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastBackPressTime < 2000) {
                            finish()
                        } else {
                            lastBackPressTime = currentTime
                            showExitDialog = true
                        }
                    }
                }

                if (showExitDialog) {
                    AlertDialog(
                        onDismissRequest = { showExitDialog = false },
                        title = { Text(stringResource(R.string.exit_app_title)) },
                        text = { Text(
                            text = stringResource(R.string.exit_app_message),
                            fontSize = 16.sp) },
                        confirmButton = {
                            TextButton(onClick = { finish() }) {
                                Text(stringResource(R.string.exit_button))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showExitDialog = false }) {
                                Text(stringResource(R.string.cancel_button))
                            }
                        }
                    )
                }

                if (showWatermarkDialog) {
                    var dragOffset by remember { mutableStateOf(Offset.Zero) }
                    var containerSize by remember { mutableStateOf(IntSize.Zero) }
                    var textSize by remember { mutableStateOf(IntSize.Zero) }
                    var rotation by remember { mutableFloatStateOf(-45f) }
                    var scaleFactor by remember { mutableFloatStateOf(1f) }
                    var isTiled by remember { mutableStateOf(false) }
                    var selectedColor by remember { mutableStateOf(Color.Gray) }
                    var showColorPicker by remember { mutableStateOf(false) }
                    
                    val mainColors = listOf(
                        Color.Red, Color.Green, Color.Blue, Color.Yellow, 
                        Color.Cyan, Color.Magenta, Color.Gray
                    )
                    
                    val previewUri = if (currentScreen == Screen.HOME) scannedUris.firstOrNull() else {
                        selectedDocument?.imageUris?.split(",")?.firstOrNull { it.isNotEmpty() }?.let { Uri.parse(it) }
                    }

                    AlertDialog(
                        onDismissRequest = { showWatermarkDialog = false },
                        title = { Text("Watermark") },
                        text = {
                            Column {
                                TextField(
                                    value = watermarkInput,
                                    onValueChange = { watermarkInput = it },
                                    label = { Text("Watermark Text") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically, 
                                        modifier = Modifier.weight(1f).clickable { isTiled = !isTiled }
                                    ) {
                                        Checkbox(checked = isTiled, onCheckedChange = { isTiled = it })
                                        Text("Tile across document", style = MaterialTheme.typography.bodyMedium)
                                    }
                                    
                                    // Color Box
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(selectedColor, MaterialTheme.shapes.small)
                                            .clickable { showColorPicker = !showColorPicker }
                                    )
                                }
                                
                                if (showColorPicker) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        mainColors.forEach { color ->
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .background(color, androidx.compose.foundation.shape.CircleShape)
                                                    .clickable {
                                                        selectedColor = color
                                                        showColorPicker = false
                                                    }
                                            )
                                        }
                                    }
                                } else {
                                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                        Text("Rotation: ${rotation.roundToInt()}°", style = MaterialTheme.typography.labelSmall)
                                    }
                                    
                                    Slider(
                                        value = rotation,
                                        onValueChange = { rotation = it },
                                        valueRange = -180f..180f,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                        Text("Size: ${(scaleFactor * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall)
                                    }

                                    Slider(
                                        value = scaleFactor,
                                        onValueChange = { scaleFactor = it },
                                        valueRange = 0.5f..3f,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
                                if (previewUri != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(300.dp)
                                            .background(Color.Black.copy(alpha = 0.05f))
                                            .onGloballyPositioned { containerSize = it.size },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = previewUri,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )
                                        if (watermarkInput.isNotBlank()) {
                                            val previewColor = selectedColor.copy(alpha = if (isTiled) 0.4f else 0.8f)
                                            if (isTiled) {
                                                Column(modifier = Modifier.fillMaxSize().graphicsLayer(rotationZ = rotation), verticalArrangement = Arrangement.SpaceEvenly) {
                                                    repeat(5) {
                                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                                            repeat(3) {
                                                                Text(
                                                                    text = watermarkInput, 
                                                                    color = previewColor, 
                                                                    fontSize = (10 * scaleFactor).sp
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            } else {
                                                Text(
                                                    text = watermarkInput,
                                                    color = previewColor,
                                                    fontSize = (20 * scaleFactor).sp,
                                                    modifier = Modifier
                                                        .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
                                                        .onGloballyPositioned { textSize = it.size }
                                                        .graphicsLayer(rotationZ = rotation)
                                                        .pointerInput(Unit) {
                                                            detectDragGestures { change, dragAmount ->
                                                                change.consume()
                                                                dragOffset += dragAmount
                                                            }
                                                        }
                                                        .padding(8.dp)
                                                )
                                            }
                                        }
                                    }
                                    if (!isTiled) {
                                        Text("Drag the text to reposition", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                if (watermarkInput.isNotBlank()) {
                                    val urisToWatermark = if (currentScreen == Screen.HOME) scannedUris else {
                                        selectedDocument?.imageUris?.split(",")?.filter { it.isNotEmpty() }?.map { Uri.parse(it) } ?: emptyList()
                                    }
                                    
                                    // Calculate center point normalized
                                    val actualCenterX = containerSize.width / 2f + dragOffset.x
                                    val actualCenterY = containerSize.height / 2f + dragOffset.y
                                    
                                    val normalizedX = if (containerSize.width > 0) actualCenterX / containerSize.width else 0.5f
                                    val normalizedY = if (containerSize.height > 0) actualCenterY / containerSize.height else 0.5f
                                    
                                    val colorInt = android.graphics.Color.argb(
                                        255,
                                        (selectedColor.red * 255).toInt(),
                                        (selectedColor.green * 255).toInt(),
                                        (selectedColor.blue * 255).toInt()
                                    )

                                    coroutineScope.launch {
                                        Toast.makeText(this@MainActivity, "Applying watermark...", Toast.LENGTH_SHORT).show()
                                        isWatermarking = true
                                        val newUris = applyWatermark(urisToWatermark, watermarkInput, Offset(normalizedX, normalizedY), rotation, isTiled, scaleFactor, colorInt)
                                        if (newUris.isNotEmpty()) {
                                            val newPdfUri = generatePdfFromImages(newUris)
                                            if (currentScreen == Screen.HOME) {
                                                scannedUris = newUris
                                                pdfUri = newPdfUri
                                            } else {
                                                selectedDocument?.let { doc ->
                                                    val newDoc = doc.copy(
                                                        id = 0, // Generate new ID for Room
                                                        name = "${doc.name} (Watermarked)",
                                                        timestamp = System.currentTimeMillis(),
                                                        imageUris = newUris.joinToString(",") { it.toString() },
                                                        pdfUri = newPdfUri?.toString()
                                                    )
                                                    documentDao.insertDocument(newDoc)
                                                    Toast.makeText(this@MainActivity, "New watermarked copy created in history", Toast.LENGTH_SHORT).show()
                                                    currentScreen = Screen.HISTORY
                                                }
                                            }
                                        }
                                        isWatermarking = false
                                        showWatermarkDialog = false
                                        watermarkInput = ""
                                    }
                                }
                            }) {
                                Text("Apply")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showWatermarkDialog = false }) {
                                Text(stringResource(R.string.cancel_button))
                            }
                        }
                    )
                }

                if (documentToDelete != null) {
                    var isChecked by remember { mutableStateOf(false) }
                    AlertDialog(
                        onDismissRequest = { documentToDelete = null },
                        title = { Text(stringResource(R.string.delete_confirm_title)) },
                        text = {
                            Column {
                                Text(stringResource(R.string.delete_confirm_message))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 8.dp).clickable { isChecked = !isChecked }
                                ) {
                                    Checkbox(checked = isChecked, onCheckedChange = { isChecked = it })
                                    Text(stringResource(R.string.never_ask_again))
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                documentToDelete?.let { doc ->
                                    coroutineScope.launch {
                                        documentDao.deleteDocument(doc)
                                        if (isChecked) neverAskDeleteAgain = true
                                        documentToDelete = null
                                    }
                                }
                            }) {
                                Text(stringResource(R.string.delete_button))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { documentToDelete = null }) {
                                Text(stringResource(R.string.cancel_button))
                            }
                        }
                    )
                }

                val scannerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartIntentSenderForResult(),
                ) { result ->
                    if (result.resultCode == RESULT_OK) {
                        val gmsResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                        gmsResult?.let { res ->
                            scannedUris = res.pages?.map { it.imageUri } ?: emptyList()
                            pdfUri = res.pdf?.uri
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        @OptIn(ExperimentalMaterial3Api::class)
                        TopAppBar(
                            title = {
                                Text(
                                    when (currentScreen) {
                                        Screen.HOME -> stringResource(R.string.home_screen_title)
                                        Screen.HISTORY -> stringResource(R.string.history_screen_title)
                                        Screen.DETAILS -> stringResource(R.string.details_screen_title)
                                    }
                                )
                            },
                            navigationIcon = {
                                if (currentScreen == Screen.DETAILS) {
                                    IconButton(onClick = { currentScreen = Screen.HISTORY }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_desc))
                                    }
                                }
                            }
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Home, contentDescription = stringResource(R.string.home_nav_label)) },
                                label = { Text(stringResource(R.string.home_nav_label)) },
                                selected = currentScreen == Screen.HOME,
                                onClick = { currentScreen = Screen.HOME }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.history_nav_label)) },
                                label = { Text(stringResource(R.string.history_nav_label)) },
                                selected = currentScreen == Screen.HISTORY,
                                onClick = { currentScreen = Screen.HISTORY }
                            )
                        }
                    }
                ) { innerPadding ->
                    when (currentScreen) {
                        Screen.HOME -> {
                            HomeScreen(
                                modifier = Modifier.padding(innerPadding),
                                scannedUris = scannedUris,
                                pdfUri = pdfUri,
                                isSpeaking = isSpeaking,
                                onScanClick = {
                                    scanner.getStartScanIntent(this@MainActivity)
                                        .addOnSuccessListener { intentSender ->
                                            scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                                        }
                                },
                                onSaveClick = {
                                    coroutineScope.launch {
                                        val doc = ScannedDocument(
                                            name = "Scan ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}",
                                            timestamp = System.currentTimeMillis(),
                                            pdfUri = pdfUri?.toString(),
                                            imageUris = scannedUris.joinToString(",") { it.toString() }
                                        )
                                        documentDao.insertDocument(doc)
                                        scannedUris = emptyList()
                                        pdfUri = null
                                        Toast.makeText(this@MainActivity, getString(R.string.saved_to_history_toast), Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onShareImages = { shareImages(it) },
                                onSharePdf = { sharePdf(it) },
                                onPrintPdf = { printPdf(it) },
                                onSpeakClick = {
                                    if (isSpeaking) {
                                        stopSpeechService()
                                    } else {
                                        startSpeechService(scannedUris)
                                    }
                                },
                                onViewTextClick = {
                                    coroutineScope.launch {
                                        extractAndSpeak(scannedUris) { text ->
                                            aiEditedText = text
                                        }
                                    }
                                },
                                onAiEditClick = {
                                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        startVoiceCommand { command ->
                                            coroutineScope.launch {
                                                extractAndSpeak(scannedUris) { text ->
                                                    applyAiEdit(text, command)
                                                }
                                            }
                                        }
                                    } else {
                                        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                onAiVisualScanClick = {
                                    coroutineScope.launch {
                                        isAiProcessing = true
                                        val text = extractTextWithAi(scannedUris)
                                        if (text != null) {
                                            aiEditedText = text
                                        } else {
                                            Toast.makeText(this@MainActivity, "AI Scan failed", Toast.LENGTH_SHORT).show()
                                        }
                                        isAiProcessing = false
                                    }
                                },
                                onCompressClick = {
                                    coroutineScope.launch {
                                        isCompressing = true
                                        val newUris = compressImages(scannedUris)
                                        if (newUris.isNotEmpty()) {
                                            scannedUris = newUris
                                            Toast.makeText(this@MainActivity, "Images compressed", Toast.LENGTH_SHORT).show()
                                        }
                                        isCompressing = false
                                    }
                                },
                                isListening = isListening,
                                isAiProcessing = isAiProcessing,
                                isCompressing = isCompressing,
                                isWatermarking = isWatermarking,
                                aiEditedText = aiEditedText,
                                onAiEditedTextChange = { aiEditedText = it },
                                onClearAiEdit = { aiEditedText = null },
                                onWatermarkClick = { showWatermarkDialog = true }
                            )
                        }
                        Screen.HISTORY -> {
                            val documents by documentDao.getAllDocuments().collectAsState(initial = emptyList())
                            HistoryScreen(
                                modifier = Modifier.padding(innerPadding),
                                documents = documents,
                                onItemClick = {
                                    selectedDocument = it
                                    currentScreen = Screen.DETAILS
                                },
                                onDeleteClick = {
                                    if (neverAskDeleteAgain) {
                                        coroutineScope.launch { documentDao.deleteDocument(it) }
                                    } else {
                                        documentToDelete = it
                                    }
                                }
                            )
                        }
                        Screen.DETAILS -> {
                            selectedDocument?.let { doc ->
                                DocumentDetailsScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    document = doc,
                                    isSpeaking = isSpeaking,
                                    onShareImages = { shareImages(it) },
                                    onSharePdf = { sharePdf(it) },
                                    onPrintPdf = { printPdf(it) },
                                    onSpeakClick = { uris ->
                                        if (isSpeaking) {
                                            stopSpeechService()
                                        } else {
                                            startSpeechService(uris, doc.id)
                                        }
                                    },
                                    onViewTextClick = { uris ->
                                        coroutineScope.launch {
                                            extractAndSpeak(uris) { text ->
                                                aiEditedText = text
                                            }
                                        }
                                    },
                                    onAiEditClick = { uris ->
                                        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                            startVoiceCommand { command ->
                                                coroutineScope.launch {
                                                    extractAndSpeak(uris) { text ->
                                                        applyAiEdit(text, command)
                                                    }
                                                }
                                            }
                                        } else {
                                            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    },
                                    onAiVisualScanClick = { uris ->
                                        coroutineScope.launch {
                                            isAiProcessing = true
                                            val text = extractTextWithAi(uris)
                                            if (text != null) {
                                                aiEditedText = text
                                            } else {
                                                Toast.makeText(this@MainActivity, "AI Scan failed", Toast.LENGTH_SHORT).show()
                                            }
                                            isAiProcessing = false
                                        }
                                    },
                                    onCompressClick = { uris ->
                                        coroutineScope.launch {
                                            isCompressing = true
                                            val newUris = compressImages(uris)
                                            if (newUris.isNotEmpty()) {
                                                val updatedDoc = doc.copy(imageUris = newUris.joinToString(",") { it.toString() })
                                                documentDao.insertDocument(updatedDoc)
                                                selectedDocument = updatedDoc
                                                Toast.makeText(this@MainActivity, "Document compressed and updated", Toast.LENGTH_SHORT).show()
                                            }
                                            isCompressing = false
                                        }
                                    },
                                    onSaveClick = { text ->
                                        coroutineScope.launch {
                                            // Handle saving logic for details screen if needed, 
                                            // or show a toast that it's already in history
                                            Toast.makeText(this@MainActivity, "Document already in history", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    isListening = isListening,
                                    isAiProcessing = isAiProcessing,
                                    isCompressing = isCompressing,
                                    isWatermarking = isWatermarking,
                                    aiEditedText = aiEditedText,
                                    onAiEditedTextChange = { aiEditedText = it },
                                    onClearAiEdit = { aiEditedText = null },
                                    onWatermarkClick = { showWatermarkDialog = true }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun HomeScreen(
        modifier: Modifier,
        scannedUris: List<Uri>,
        pdfUri: Uri?,
        isSpeaking: Boolean,
        isListening: Boolean,
        isAiProcessing: Boolean,
        isCompressing: Boolean,
        isWatermarking: Boolean,
        aiEditedText: String?,
        onAiEditedTextChange: (String) -> Unit,
        onScanClick: () -> Unit,
        onSaveClick: () -> Unit,
        onShareImages: (List<Uri>) -> Unit,
        onSharePdf: (Uri) -> Unit,
        onPrintPdf: (Uri) -> Unit,
        onSpeakClick: () -> Unit,
        onViewTextClick: () -> Unit,
        onAiEditClick: () -> Unit,
        onAiVisualScanClick: () -> Unit,
        onCompressClick: () -> Unit,
        onClearAiEdit: () -> Unit,
        onWatermarkClick: () -> Unit
    ) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (aiEditedText != null) {
                Card(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.extracted_text_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = { copyToClipboard(aiEditedText) }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = onClearAiEdit) {
                                Icon(Icons.Default.Delete, contentDescription = "Clear", modifier = Modifier.size(20.dp))
                            }
                        }
                        TextField(
                            value = aiEditedText,
                            onValueChange = onAiEditedTextChange,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            colors = androidx.compose.material3.TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                    }
                }
            }

            Button(onClick = onScanClick, modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.scan_document_button))
            }

            if (scannedUris.isNotEmpty()) {
                var scale by remember { mutableStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }
                val state = rememberTransformableState { zoomChange, offsetChange, _ ->
                    scale = (scale * zoomChange).coerceIn(1f, 5f)
                    val newOffset = offset + offsetChange
                    // Only allow panning if zoomed in
                    if (scale > 1f) {
                        offset = newOffset
                    } else {
                        offset = Offset.Zero
                    }
                }

                AnimatedVisibility(
                    visible = scale <= 1.1f,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                ) {
                    Column {
                        // Row 1: Primary Actions (Share, Print, Save)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { onShareImages(scannedUris) }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_share_image),
                                    contentDescription = stringResource(R.string.share_images_desc),
                                    modifier = Modifier.size(40.dp),
                                    tint = Color.Unspecified
                                )
                            }
                            pdfUri?.let { uri ->
                                IconButton(onClick = { onSharePdf(uri) }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_share_pdf),
                                        contentDescription = stringResource(R.string.share_pdf_desc),
                                        modifier = Modifier.size(40.dp),
                                        tint = Color.Unspecified
                                    )
                                }
                                IconButton(onClick = { onPrintPdf(uri) }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_printer),
                                        contentDescription = stringResource(R.string.print_pdf_desc),
                                        modifier = Modifier.size(40.dp),
                                        tint = Color.Unspecified
                                    )
                                }
                            }
                            IconButton(onClick = onSaveClick) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_save),
                                    contentDescription = stringResource(R.string.save_document_desc),
                                    modifier = Modifier.size(40.dp),
                                    tint = Color.Unspecified
                                )
                            }
                        }

                        // Row 2: Secondary/AI Actions
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onSpeakClick) {
                                Icon(
                                    painter = painterResource(id = if (isSpeaking) R.drawable.ic_stop else R.drawable.ic_speaker),
                                    contentDescription = stringResource(R.string.speak_desc),
                                    modifier = Modifier.size(40.dp),
                                    tint = Color.Unspecified
                                )
                            }
                            IconButton(onClick = onViewTextClick) {
                                Icon(
                                    Icons.Filled.ContentCopy,
                                    contentDescription = stringResource(R.string.view_text_desc),
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = onAiEditClick) {
                                if (isAiProcessing || isListening) {
                                    CircularProgressIndicator(modifier = Modifier.size(28.dp), color = if (isListening) Color.Red else MaterialTheme.colorScheme.primary)
                                } else {
                                    Icon(
                                        Icons.Default.AutoFixHigh,
                                        contentDescription = stringResource(R.string.ai_edit_desc),
                                        modifier = Modifier.size(40.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            IconButton(onClick = onAiVisualScanClick) {
                                if (isAiProcessing) {
                                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                } else {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = stringResource(R.string.ai_visual_scan_desc),
                                        modifier = Modifier.size(40.dp),
                                        tint = Color(0xFF673AB7) // Deep Purple
                                    )
                                }
                            }
                            IconButton(onClick = onCompressClick) {
                                if (isCompressing) {
                                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                } else {
                                    Icon(
                                        Icons.Default.Compress,
                                        contentDescription = "Compress",
                                        modifier = Modifier.size(40.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            IconButton(onClick = onWatermarkClick) {
                                if (isWatermarking) {
                                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                } else {
                                    Icon(
                                        painter = painterResource(id = R.drawable.watermark_scanner),
                                        contentDescription = "Watermark",
                                        modifier = Modifier.size(40.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                        .transformable(state = state)
                ) {
                    items(scannedUris) { uri ->
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().height(300.dp).padding(8.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun HistoryScreen(
        modifier: Modifier,
        documents: List<ScannedDocument>,
        onItemClick: (ScannedDocument) -> Unit,
        onDeleteClick: (ScannedDocument) -> Unit
    ) {
        if (documents.isEmpty()) {
            Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.no_saved_documents), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(modifier = modifier.fillMaxSize().padding(8.dp)) {
                items(documents) { doc ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onItemClick(doc) },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            val firstUri = remember(doc.imageUris) {
                                doc.imageUris.split(",").firstOrNull { it.isNotEmpty() }?.let { Uri.parse(it) }
                            }
                            
                            AsyncImage(
                                model = firstUri,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(50.dp)
                                    .padding(end = 12.dp),
                                contentScale = ContentScale.Crop
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = doc.name, style = MaterialTheme.typography.titleMedium)
                                val formattedDate = remember(doc.timestamp) {
                                    SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(doc.timestamp))
                                }
                                val fileSize = remember(doc) {
                                    formatFileSize(getDocumentSize(doc))
                                }
                                Text(
                                    text = "$formattedDate • $fileSize",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            IconButton(onClick = { onDeleteClick(doc) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_desc), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun DocumentDetailsScreen(
        modifier: Modifier,
        document: ScannedDocument,
        isSpeaking: Boolean,
        isListening: Boolean,
        isAiProcessing: Boolean,
        isCompressing: Boolean,
        isWatermarking: Boolean,
        aiEditedText: String?,
        onAiEditedTextChange: (String) -> Unit,
        onShareImages: (List<Uri>) -> Unit,
        onSharePdf: (Uri) -> Unit,
        onPrintPdf: (Uri) -> Unit,
        onSpeakClick: (List<Uri>) -> Unit,
        onViewTextClick: (List<Uri>) -> Unit,
        onAiEditClick: (List<Uri>) -> Unit,
        onAiVisualScanClick: (List<Uri>) -> Unit,
        onCompressClick: (List<Uri>) -> Unit,
        onSaveClick: (String?) -> Unit,
        onClearAiEdit: () -> Unit,
        onWatermarkClick: () -> Unit
    ) {
        val imageUris = remember(document) { document.imageUris.split(",").filter { it.isNotEmpty() }.map { Uri.parse(it) } }
        val pdfUri = remember(document) { document.pdfUri?.let { Uri.parse(it) } }

        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val state = rememberTransformableState { zoomChange, offsetChange, _ ->
            scale = (scale * zoomChange).coerceIn(1f, 5f)
            val newOffset = offset + offsetChange
            if (scale > 1f) {
                offset = newOffset
            } else {
                offset = Offset.Zero
            }
        }

        Column(modifier = modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (aiEditedText != null) {
                Card(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.extracted_text_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = { copyToClipboard(aiEditedText) }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = onClearAiEdit) {
                                Icon(Icons.Default.Delete, contentDescription = "Clear", modifier = Modifier.size(20.dp))
                            }
                        }
                        TextField(
                            value = aiEditedText,
                            onValueChange = onAiEditedTextChange,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            colors = androidx.compose.material3.TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                    }
                }
            }
            
            AnimatedVisibility(
                visible = scale <= 1.1f,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
            ) {
                Column {
                    // Row 1: Primary Actions
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onShareImages(imageUris) }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_share_image),
                                contentDescription = stringResource(R.string.share_images_desc),
                                modifier = Modifier.size(40.dp),
                                tint = androidx.compose.ui.graphics.Color.Unspecified
                            )
                        }
                        pdfUri?.let { uri ->
                            IconButton(onClick = { onSharePdf(uri) }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_share_pdf),
                                    contentDescription = stringResource(R.string.share_pdf_desc),
                                    modifier = Modifier.size(40.dp),
                                    tint = androidx.compose.ui.graphics.Color.Unspecified
                                )
                            }
                            IconButton(onClick = { onPrintPdf(uri) }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_printer),
                                    contentDescription = stringResource(R.string.print_pdf_desc),
                                    modifier = Modifier.size(40.dp),
                                    tint = androidx.compose.ui.graphics.Color.Unspecified
                                )
                            }
                        }
                        IconButton(onClick = { onSaveClick(aiEditedText) }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_save),
                                contentDescription = stringResource(R.string.save_document_desc),
                                modifier = Modifier.size(40.dp),
                                tint = androidx.compose.ui.graphics.Color.Unspecified
                            )
                        }
                    }

                    // Row 2: Processing & AI
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onSpeakClick(imageUris) }) {
                            Icon(
                                painter = painterResource(id = if (isSpeaking) R.drawable.ic_stop else R.drawable.ic_speaker),
                                contentDescription = stringResource(R.string.speak_desc),
                                modifier = Modifier.size(40.dp),
                                tint = Color.Unspecified
                            )
                        }
                        IconButton(onClick = { onViewTextClick(imageUris) }) {
                            Icon(
                                Icons.Filled.ContentCopy,
                                contentDescription = stringResource(R.string.view_text_desc),
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { onAiEditClick(imageUris) }) {
                            if (isAiProcessing || isListening) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp), color = if (isListening) Color.Red else MaterialTheme.colorScheme.primary)
                            } else {
                                Icon(
                                    Icons.Default.AutoFixHigh,
                                    contentDescription = stringResource(R.string.ai_edit_desc),
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(onClick = { onAiVisualScanClick(imageUris) }) {
                            if (isAiProcessing) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            } else {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = stringResource(R.string.ai_visual_scan_desc),
                                    modifier = Modifier.size(40.dp),
                                    tint = Color(0xFF673AB7)
                                )
                            }
                        }
                        IconButton(onClick = { onCompressClick(imageUris) }) {
                            if (isCompressing) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            } else {
                                Icon(
                                    Icons.Default.Compress,
                                    contentDescription = "Compress",
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(onClick = onWatermarkClick) {
                            if (isWatermarking) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            } else {
                                Icon(
                                    painter = painterResource(id = R.drawable.watermark_scanner),
                                    contentDescription = "Watermark",
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
                    .transformable(state = state)
            ) {
                items(imageUris) { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(400.dp).padding(8.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }

    private fun shareImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        Log.d(TAG, "Sharing ${uris.size} images")
        val contentUris = uris.map { getContentUri(it) }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/jpeg"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(contentUris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share Images"))
    }

    private fun sharePdf(uri: Uri) {
        Log.d(TAG, "Sharing PDF: $uri")
        val contentUri = getContentUri(uri)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share PDF"))
    }

    private fun getContentUri(uri: Uri): Uri {
        return if (uri.scheme == "file") {
            FileProvider.getUriForFile(this, "${packageName}.fileprovider", File(uri.path!!))
        } else {
            uri
        }
    }

    private fun printPdf(uri: Uri) {
        Log.d(TAG, "Printing PDF: $uri")
        val printManager = getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager
        val jobName = "${getString(R.string.app_name)} Document"

        try {
            val adapter = object : android.print.PrintDocumentAdapter() {
                override fun onWrite(
                    pages: Array<out android.print.PageRange>?,
                    destination: android.os.ParcelFileDescriptor?,
                    cancellationSignal: android.os.CancellationSignal?,
                    callback: WriteResultCallback?
                ) {
                    Log.d(TAG, "PrintAdapter.onWrite")
                    var input: java.io.InputStream? = null
                    var output: java.io.OutputStream? = null
                    try {
                        input = contentResolver.openInputStream(uri)
                        output = java.io.FileOutputStream(destination?.fileDescriptor)
                        input?.copyTo(output)
                        Log.d(TAG, "Print document written successfully")
                        callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error writing print document: ${e.message}", e)
                        callback?.onWriteFailed(e.message)
                    } finally {
                        try {
                            input?.close()
                            output?.close()
                        } catch (e: java.io.IOException) {
                            // Ignore
                        }
                    }
                }

                override fun onLayout(
                    oldAttributes: android.print.PrintAttributes?,
                    newAttributes: android.print.PrintAttributes?,
                    cancellationSignal: android.os.CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: Bundle?
                ) {
                    Log.d(TAG, "PrintAdapter.onLayout")
                    if (cancellationSignal?.isCanceled == true) {
                        Log.d(TAG, "Print layout cancelled")
                        callback?.onLayoutCancelled()
                        return
                    }
                    val info = android.print.PrintDocumentInfo.Builder(jobName)
                        .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .build()
                    callback?.onLayoutFinished(info, true)
                }
            }
            printManager.print(jobName, adapter, null)
        } catch (e: Exception) {
            Log.e(TAG, "Print initiation failed: ${e.message}", e)
            Toast.makeText(this, "Print failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

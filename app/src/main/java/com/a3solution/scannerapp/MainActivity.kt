package com.a3solution.scannerapp

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.a3solution.scannerapp.data.AppDatabase
import com.a3solution.scannerapp.data.ScannedDocument
import com.a3solution.scannerapp.service.ScannerTTSService
import com.a3solution.scannerapp.ui.screens.DocumentDetailsScreen
import com.a3solution.scannerapp.ui.screens.HistoryScreen
import com.a3solution.scannerapp.ui.screens.HomeScreen
import com.a3solution.scannerapp.ui.theme.ScannerAppTheme
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.RequestOptions
import com.google.ai.client.generativeai.type.content
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val TAG = "ScannerApp_Main"
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null

    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = "AIzaSyA68Q6blUU9vG2sKlGgtWQrBtYo6A-8FJI",
        requestOptions = RequestOptions(apiVersion = "v1beta"),
    )

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(getString(R.string.extracted_text_title), text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, getString(R.string.text_copied_toast), Toast.LENGTH_SHORT).show()
    }

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return getString(R.string.zero_bytes)
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.getDefault(), "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    private fun getDocumentSize(doc: ScannedDocument): Long {
        var totalSize = 0L
        val uris = doc.imageUris.split(",").filter { it.isNotEmpty() }.map { Uri.parse(it) }
        for (uri in uris) {
            try {
                if (uri.scheme == "file") {
                    val file = File(uri.path ?: "")
                    if (file.exists()) {
                        totalSize += file.length()
                    }
                } else {
                    contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                        totalSize += it.length
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting size for $uri", e)
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
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, out)
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
                    val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
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
                        mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
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
                text(getString(R.string.ai_prompt_extract))
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
            if (status != TextToSpeech.SUCCESS) Log.e(TAG, "TTS Initialization failed!")
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

                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted: Boolean ->
                    if (!isGranted) Toast.makeText(this@MainActivity, getString(R.string.notifications_required_toast), Toast.LENGTH_SHORT).show()
                }

                val requestPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted: Boolean ->
                    if (isGranted) Toast.makeText(this@MainActivity, getString(R.string.permission_granted_toast), Toast.LENGTH_SHORT).show()
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
                    val filter = IntentFilter(ScannerTTSService.ACTION_STATUS_UPDATE)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
                    } else {
                        @Suppress("UnspecifiedRegisterReceiverFlag")
                        registerReceiver(receiver, filter)
                    }
                    val statusIntent = Intent(this@MainActivity, ScannerTTSService::class.java).apply { action = ScannerTTSService.ACTION_GET_STATUS }
                    startService(statusIntent)
                    onDispose { unregisterReceiver(receiver); tts?.stop(); tts?.shutdown() }
                }

                LaunchedEffect(intent) {
                    val returnScreen = intent.getStringExtra(ScannerTTSService.EXTRA_RETURN_SCREEN)
                    val returnDocId = intent.getIntExtra(ScannerTTSService.EXTRA_DOC_ID, -1)
                    if (returnScreen == Screen.DETAILS.name && returnDocId != -1) {
                        documentDao.getAllDocuments().collectLatest { docs ->
                            val doc = docs.find { it.id == returnDocId }
                            if (doc != null) { selectedDocument = doc; currentScreen = Screen.DETAILS }
                        }
                    } else if (returnScreen == Screen.HOME.name) {
                        currentScreen = Screen.HOME
                        @Suppress("DEPRECATION")
                        val uris = intent.getParcelableArrayListExtra<Uri>(ScannerTTSService.EXTRA_URIS)
                        if (uris != null) scannedUris = uris
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
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
                    isSpeaking = true
                }

                fun stopSpeechService() {
                    val intent = Intent(this@MainActivity, ScannerTTSService::class.java).apply { action = ScannerTTSService.ACTION_STOP }
                    startService(intent)
                    isSpeaking = false
                }

                fun speakText(text: String) {
                    if (text.isBlank()) return
                    val intent = Intent(this@MainActivity, ScannerTTSService::class.java).apply {
                        action = ScannerTTSService.ACTION_SPEAK_TEXT
                        putExtra(ScannerTTSService.EXTRA_TEXT, text)
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
                    isSpeaking = true
                }

                fun startVoiceCommand(onCommandReceived: (String) -> Unit) {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.listening_for_command))
                    }
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this@MainActivity).apply {
                        setRecognitionListener(object : RecognitionListener {
                            override fun onReadyForSpeech(p: Bundle?) { isListening = true }
                            override fun onBeginningOfSpeech() {}
                            override fun onRmsChanged(r: Float) {}
                            override fun onBufferReceived(b: ByteArray?) {}
                            override fun onEndOfSpeech() { isListening = false }
                            override fun onError(e: Int) { isListening = false; Toast.makeText(this@MainActivity, getString(R.string.voice_not_recognized), Toast.LENGTH_SHORT).show() }
                            override fun onResults(r: Bundle?) {
                                val m = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                if (!m.isNullOrEmpty()) onCommandReceived(m[0])
                            }
                            override fun onPartialResults(p: Bundle?) {}
                            override fun onEvent(ev: Int, p: Bundle?) {}
                        })
                        startListening(intent)
                    }
                }

                suspend fun applyAiEdit(originalText: String, command: String) {
                    if (originalText.isBlank()) return
                    isAiProcessing = true
                    try {
                        val prompt = getString(R.string.ai_prompt_edit, originalText, command)
                        val response = generativeModel.generateContent(prompt)
                        val edited = response.text
                        if (!edited.isNullOrBlank()) { aiEditedText = edited; speakText(edited) }
                        else Toast.makeText(this@MainActivity, getString(R.string.ai_edit_error), Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e(TAG, "AI Error", e); Toast.makeText(this@MainActivity, getString(R.string.ai_error_format, e.message), Toast.LENGTH_SHORT).show()
                    } finally { isAiProcessing = false }
                }

                suspend fun extractAndSpeak(uris: List<Uri>, onExtracted: (suspend (String) -> Unit)? = null) {
                    if (uris.isEmpty()) { Toast.makeText(this@MainActivity, getString(R.string.no_text_found), Toast.LENGTH_SHORT).show(); return }
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
                            if (extracted.isNotBlank()) onExtracted(extracted) else Toast.makeText(this@MainActivity, getString(R.string.no_text_found), Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) { Log.e(TAG, "Error extracting text", e); Toast.makeText(this@MainActivity, getString(R.string.ai_error_format, e.message), Toast.LENGTH_SHORT).show() }
                    } else { 
                        Toast.makeText(this@MainActivity, getString(R.string.starting_speech_toast), Toast.LENGTH_SHORT).show()
                        startSpeechService(uris) 
                    }
                }

                val options = remember { GmsDocumentScannerOptions.Builder().setScannerMode(SCANNER_MODE_FULL).setResultFormats(RESULT_FORMAT_JPEG, RESULT_FORMAT_PDF).setGalleryImportAllowed(true).build() }
                val scanner = remember { GmsDocumentScanning.getClient(options) }
                val scannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
                    if (result.resultCode == RESULT_OK) {
                        val gmsResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                        gmsResult?.let { res ->
                            scannedUris = res.pages?.map { it.imageUri } ?: emptyList()
                            pdfUri = res.pdf?.uri
                        }
                    }
                }

                BackHandler {
                    if (currentScreen == Screen.DETAILS) currentScreen = Screen.HISTORY
                    else if (currentScreen == Screen.HISTORY) currentScreen = Screen.HOME
                    else {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastBackPressTime < 2000) finish()
                        else { lastBackPressTime = currentTime; showExitDialog = true }
                    }
                }

                if (showExitDialog) {
                    AlertDialog(onDismissRequest = { showExitDialog = false }, title = { Text(stringResource(R.string.exit_app_title)) }, text = { Text(text = stringResource(R.string.exit_app_message), fontSize = 16.sp) }, confirmButton = { TextButton(onClick = { finish() }) { Text(stringResource(R.string.exit_button)) } }, dismissButton = { TextButton(onClick = { showExitDialog = false }) { Text(stringResource(R.string.cancel_button)) } })
                }

                if (showWatermarkDialog) {
                    var dragOffset by remember { mutableStateOf(Offset.Zero) }
                    var containerSize by remember { mutableStateOf(IntSize.Zero) }
                    var rotation by remember { mutableFloatStateOf(-45f) }
                    var scaleFactor by remember { mutableFloatStateOf(1f) }
                    var isTiled by remember { mutableStateOf(false) }
                    var selectedColor by remember { mutableStateOf(Color.Gray) }
                    var showColorPicker by remember { mutableStateOf(false) }
                    val mainColors = listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Cyan, Color.Magenta, Color.Gray)
                    val previewUri = if (currentScreen == Screen.HOME) scannedUris.firstOrNull() else selectedDocument?.imageUris?.split(",")?.firstOrNull { it.isNotEmpty() }?.let { Uri.parse(it) }

                    AlertDialog(
                        onDismissRequest = { showWatermarkDialog = false },
                        title = { Text(stringResource(R.string.watermark_dialog_title)) },
                        text = {
                            Column {
                                TextField(value = watermarkInput, onValueChange = { watermarkInput = it }, label = { Text(stringResource(R.string.watermark_input_label)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).clickable { isTiled = !isTiled }) {
                                        Checkbox(checked = isTiled, onCheckedChange = { isTiled = it })
                                        Text(stringResource(R.string.tile_watermark_label), style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Box(modifier = Modifier.size(32.dp).background(selectedColor, MaterialTheme.shapes.small).clickable { showColorPicker = !showColorPicker })
                                }
                                if (showColorPicker) {
                                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                        mainColors.forEach { color -> Box(modifier = Modifier.size(32.dp).background(color, CircleShape).clickable { selectedColor = color; showColorPicker = false }) }
                                    }
                                } else {
                                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(stringResource(R.string.rotation_label, rotation.roundToInt()), style = MaterialTheme.typography.labelSmall) }
                                    Slider(value = rotation, onValueChange = { rotation = it }, valueRange = -180f..180f, modifier = Modifier.fillMaxWidth())
                                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) { Text(stringResource(R.string.size_label, (scaleFactor * 100).roundToInt()), style = MaterialTheme.typography.labelSmall) }
                                    Slider(value = scaleFactor, onValueChange = { scaleFactor = it }, valueRange = 0.5f..3f, modifier = Modifier.fillMaxWidth())
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                if (previewUri != null) {
                                    Box(modifier = Modifier.fillMaxWidth().height(300.dp).background(Color.Black.copy(alpha = 0.05f)).onGloballyPositioned { containerSize = it.size }, contentAlignment = Alignment.Center) {
                                        AsyncImage(model = previewUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                                        if (watermarkInput.isNotBlank()) {
                                            val previewColor = selectedColor.copy(alpha = if (isTiled) 0.4f else 0.8f)
                                            if (isTiled) {
                                                Column(modifier = Modifier.fillMaxSize().graphicsLayer(rotationZ = rotation), verticalArrangement = Arrangement.SpaceEvenly) {
                                                    repeat(5) { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { repeat(3) { Text(text = watermarkInput, color = previewColor, fontSize = (10 * scaleFactor).sp) } } }
                                                }
                                            } else {
                                                @Suppress("DEPRECATION")
                                                Text(text = watermarkInput, color = previewColor, fontSize = (20 * scaleFactor).sp, modifier = Modifier.offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }.graphicsLayer(rotationZ = rotation).pointerInput(Unit) { detectDragGestures { change, dragAmount -> change.consume(); dragOffset += dragAmount } }.padding(8.dp))
                                            }
                                        }
                                    }
                                    if (!isTiled) Text(stringResource(R.string.drag_reposition_hint), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                if (watermarkInput.isNotBlank()) {
                                    val urisToWatermark = if (currentScreen == Screen.HOME) scannedUris else selectedDocument?.imageUris?.split(",")?.filter { it.isNotEmpty() }?.map { Uri.parse(it) } ?: emptyList()
                                    val centerX = containerSize.width / 2f + dragOffset.x; val centerY = containerSize.height / 2f + dragOffset.y
                                    val nx = if (containerSize.width > 0) centerX / containerSize.width else 0.5f; val ny = if (containerSize.height > 0) centerY / containerSize.height else 0.5f
                                    val colorInt = android.graphics.Color.argb(255, (selectedColor.red * 255).toInt(), (selectedColor.green * 255).toInt(), (selectedColor.blue * 255).toInt())
                                    coroutineScope.launch {
                                        Toast.makeText(this@MainActivity, getString(R.string.applying_watermark_toast), Toast.LENGTH_SHORT).show(); isWatermarking = true
                                        val newUris = applyWatermark(urisToWatermark, watermarkInput, Offset(nx, ny), rotation, isTiled, scaleFactor, colorInt)
                                        if (newUris.isNotEmpty()) {
                                            val newPdfUri = generatePdfFromImages(newUris)
                                            if (currentScreen == Screen.HOME) { scannedUris = newUris; pdfUri = newPdfUri }
                                            else {
                                                selectedDocument?.let { doc ->
                                                    val newDoc = doc.copy(id = 0, name = "${doc.name} (Watermarked)", timestamp = System.currentTimeMillis(), imageUris = newUris.joinToString(",") { it.toString() }, pdfUri = newPdfUri?.toString())
                                                    documentDao.insertDocument(newDoc); Toast.makeText(this@MainActivity, getString(R.string.new_watermarked_copy_toast), Toast.LENGTH_SHORT).show(); currentScreen = Screen.HISTORY
                                                }
                                            }
                                        }
                                        isWatermarking = false; showWatermarkDialog = false; watermarkInput = ""
                                    }
                                }
                            }) { Text(stringResource(R.string.apply_button)) }
                        },
                        dismissButton = { TextButton(onClick = { showWatermarkDialog = false }) { Text(stringResource(R.string.cancel_button)) } }
                    )
                }

                if (documentToDelete != null) {
                    var isChecked by remember { mutableStateOf(false) }
                    AlertDialog(onDismissRequest = { documentToDelete = null }, title = { Text(stringResource(R.string.delete_confirm_title)) }, text = { Column { Text(stringResource(R.string.delete_confirm_message)); Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp).clickable { isChecked = !isChecked }) { Checkbox(checked = isChecked, onCheckedChange = { isChecked = it }); Text(stringResource(R.string.never_ask_again)) } } }, confirmButton = { TextButton(onClick = { documentToDelete?.let { doc -> coroutineScope.launch { documentDao.deleteDocument(doc); if (isChecked) neverAskDeleteAgain = true; documentToDelete = null } } }) { Text(stringResource(R.string.delete_button)) } }, dismissButton = { TextButton(onClick = { documentToDelete = null }) { Text(stringResource(R.string.cancel_button)) } })
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        @OptIn(ExperimentalMaterial3Api::class)
                        TopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (currentScreen != Screen.DETAILS) {
                                        Box(modifier = Modifier.size(40.dp).background(Color(0xFFFFFFFF), shape = MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) {
                                            AsyncImage(model = R.mipmap.ic_launcher, contentDescription = null, modifier = Modifier.size(36.dp))
                                        }
                                        @Suppress("DEPRECATION")
                                        Spacer(modifier = Modifier.size(12.dp))
                                        Text(stringResource(R.string.app_branding_name), style = MaterialTheme.typography.titleLarge)
                                    } else {
                                        Text(stringResource(R.string.details_screen_title))
                                    }
                                }
                            },
                            navigationIcon = {
                                if (currentScreen == Screen.DETAILS) {
                                    IconButton(onClick = { currentScreen = Screen.HISTORY }) { 
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack, 
                                            contentDescription = stringResource(R.string.back_desc),
                                            tint = colorResource(R.color.white)
                                        ) 
                                    }
                                }
                            },
                            actions = {
                                if (currentScreen != Screen.DETAILS) {
                                    // Custom Pill Toggle for Scan/History
                                    Surface(
                                        color = Color(0xFFF1F4F9),
                                        shape = RoundedCornerShape(40.dp),
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val isScanActive = currentScreen == Screen.HOME
                                            val isHistoryActive = currentScreen == Screen.HISTORY

                                            // Scan Toggle
                                            Surface(
                                                onClick = { currentScreen = Screen.HOME },
                                                color = if (isScanActive) colorResource(R.color.primary) else Color.Transparent,
                                                shape = RoundedCornerShape(20.dp)
                                            ) {
                                                Text(
                                                    text = "Scan",
                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = if (isScanActive) Color.White else colorResource(R.color.text_primary),
                                                    fontWeight = if (isScanActive) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }

                                            // History Toggle
                                            Surface(
                                                onClick = { currentScreen = Screen.HISTORY },
                                                color = if (isHistoryActive) colorResource(R.color.primary) else Color.Transparent,
                                                shape = RoundedCornerShape(20.dp)
                                            ) {
                                                Text(
                                                    text = "History",
                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = if (isHistoryActive) Color.White else colorResource(R.color.text_primary),
                                                    fontWeight = if (isHistoryActive) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    when (currentScreen) {
                        Screen.HOME -> HomeScreen(
                            modifier = Modifier.padding(innerPadding),
                            scannedUris = scannedUris,
                            pdfUri = pdfUri,
                            isSpeaking = isSpeaking,
                            isListening = isListening,
                            isAiProcessing = isAiProcessing,
                            isCompressing = isCompressing,
                            isWatermarking = isWatermarking,
                            aiEditedText = aiEditedText,
                            onAiEditedTextChange = { aiEditedText = it },
                            onScanClick = { scanner.getStartScanIntent(this@MainActivity).addOnSuccessListener { scannerLauncher.launch(IntentSenderRequest.Builder(it).build()) } },
                            onSaveClick = {
                                coroutineScope.launch {
                                    val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                                    val doc = ScannedDocument(name = getString(R.string.scan_name_format, formattedDate), timestamp = System.currentTimeMillis(), pdfUri = pdfUri?.toString(), imageUris = scannedUris.joinToString(",") { it.toString() })
                                    documentDao.insertDocument(doc); scannedUris = emptyList(); pdfUri = null; Toast.makeText(this@MainActivity, getString(R.string.saved_to_history_toast), Toast.LENGTH_SHORT).show()
                                }
                            },
                            onShareImages = { shareImages(it) },
                            onSharePdf = { sharePdf(it) },
                            onPrintPdf = { printPdf(it) },
                            onSpeakClick = { if (isSpeaking) stopSpeechService() else startSpeechService(scannedUris) },
                            onViewTextClick = { coroutineScope.launch { extractAndSpeak(scannedUris) { aiEditedText = it } } },
                            onAiEditClick = { if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startVoiceCommand { cmd -> coroutineScope.launch { extractAndSpeak(scannedUris) { applyAiEdit(it, cmd) } } } else requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            onAiVisualScanClick = { coroutineScope.launch { isAiProcessing = true; val text = extractTextWithAi(scannedUris); if (text != null) aiEditedText = text else Toast.makeText(this@MainActivity, getString(R.string.ai_scan_failed), Toast.LENGTH_SHORT).show(); isAiProcessing = false } },
                            onCompressClick = { coroutineScope.launch { isCompressing = true; val newUris = compressImages(scannedUris); if (newUris.isNotEmpty()) scannedUris = newUris; isCompressing = false } },
                            onClearAiEdit = { aiEditedText = null },
                            onWatermarkClick = { showWatermarkDialog = true },
                            copyToClipboard = ::copyToClipboard
                        )
                        Screen.HISTORY -> {
                            val docs by documentDao.getAllDocuments().collectAsState(initial = emptyList())
                            HistoryScreen(
                                modifier = Modifier.padding(innerPadding),
                                documents = docs,
                                onItemClick = { selectedDocument = it; currentScreen = Screen.DETAILS },
                                onDeleteClick = { if (neverAskDeleteAgain) coroutineScope.launch { documentDao.deleteDocument(it) } else documentToDelete = it },
                                onRenameClick = { doc, newName ->
                                    coroutineScope.launch {
                                        documentDao.insertDocument(doc.copy(name = newName))
                                        Toast.makeText(this@MainActivity, getString(R.string.document_renamed_toast), Toast.LENGTH_SHORT).show()
                                    }
                                },
                                getDocumentSize = ::getDocumentSize,
                                formatFileSize = ::formatFileSize
                            )
                        }
                        Screen.DETAILS -> selectedDocument?.let { doc ->
                            DocumentDetailsScreen(
                                modifier = Modifier.padding(innerPadding),
                                document = doc,
                                isSpeaking = isSpeaking,
                                isListening = isListening,
                                isAiProcessing = isAiProcessing,
                                isCompressing = isCompressing,
                                isWatermarking = isWatermarking,
                                aiEditedText = aiEditedText,
                                onAiEditedTextChange = { aiEditedText = it },
                                onShareImages = { shareImages(it) },
                                onSharePdf = { sharePdf(it) },
                                onPrintPdf = { printPdf(it) },
                                onSpeakClick = { uris -> if (isSpeaking) stopSpeechService() else startSpeechService(uris, doc.id) },
                                onViewTextClick = { uris -> coroutineScope.launch { extractAndSpeak(uris) { aiEditedText = it } } },
                                onAiEditClick = { uris -> if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startVoiceCommand { cmd -> coroutineScope.launch { extractAndSpeak(uris) { applyAiEdit(it, cmd) } } } else requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                                onAiVisualScanClick = { uris -> coroutineScope.launch { isAiProcessing = true; val text = extractTextWithAi(uris); if (text != null) aiEditedText = text else Toast.makeText(this@MainActivity, getString(R.string.ai_scan_failed), Toast.LENGTH_SHORT).show(); isAiProcessing = false } },
                                onCompressClick = { uris -> coroutineScope.launch { isCompressing = true; val newUris = compressImages(uris); if (newUris.isNotEmpty()) { val ud = doc.copy(imageUris = newUris.joinToString(",") { it.toString() }); documentDao.insertDocument(ud); selectedDocument = ud }; isCompressing = false } },
                                onSaveClick = { Toast.makeText(this@MainActivity, getString(R.string.doc_already_in_history), Toast.LENGTH_SHORT).show() },
                                onClearAiEdit = { aiEditedText = null },
                                onWatermarkClick = { showWatermarkDialog = true },
                                copyToClipboard = ::copyToClipboard
                            )
                        }
                    }
                }
            }
        }
    }

    private fun shareImages(uris: List<Uri>) { if (uris.isEmpty()) return; val contentUris = uris.map { getContentUri(it) }; val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply { type = "image/jpeg"; putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(contentUris)); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }; startActivity(Intent.createChooser(intent, getString(R.string.share_images_chooser))) }
    private fun sharePdf(uri: Uri) { val contentUri = getContentUri(uri); val intent = Intent(Intent.ACTION_SEND).apply { type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, contentUri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }; startActivity(Intent.createChooser(intent, getString(R.string.share_pdf_chooser))) }
    private fun getContentUri(uri: Uri): Uri { return if (uri.scheme == "file") FileProvider.getUriForFile(this, "${packageName}.fileprovider", File(uri.path!!)) else uri }
    private fun printPdf(uri: Uri) { val pm = getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager; val jn = "${getString(R.string.app_name)} Document"; try { val adapter = object : android.print.PrintDocumentAdapter() { override fun onWrite(pages: Array<out android.print.PageRange>?, dest: android.os.ParcelFileDescriptor?, cs: android.os.CancellationSignal?, cb: WriteResultCallback?) { var i: java.io.InputStream? = null; var o: java.io.OutputStream? = null; try { i = contentResolver.openInputStream(uri); o = java.io.FileOutputStream(dest?.fileDescriptor); i?.copyTo(o); cb?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES)) } catch (e: Exception) { cb?.onWriteFailed(e.message) } finally { try { i?.close(); o?.close() } catch (e: java.io.IOException) {} } } override fun onLayout(oa: android.print.PrintAttributes?, na: android.print.PrintAttributes?, cs: android.os.CancellationSignal?, cb: LayoutResultCallback?, ex: Bundle?) { if (cs?.isCanceled == true) { cb?.onLayoutCancelled(); return }; val info = android.print.PrintDocumentInfo.Builder(jn).setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(); cb?.onLayoutFinished(info, true) } }; pm.print(jn, adapter, null) } catch (e: Exception) { Toast.makeText(this, getString(R.string.print_error_format, e.message), Toast.LENGTH_SHORT).show() } }
}

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
import android.content.ClipboardManager
import android.content.ClipData
import android.graphics.BitmapFactory
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.tasks.await
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.RequestOptions
import com.google.ai.client.generativeai.type.content
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextField
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.graphics.graphicsLayer

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
                var aiEditedText by remember { mutableStateOf<String?>(null) }

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
                    val listener = object : android.speech.tts.UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) { isSpeaking = false }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) { isSpeaking = false }
                    }
                    tts?.setOnUtteranceProgressListener(listener)
                    onDispose {
                        tts?.stop()
                        tts?.shutdown()
                    }
                }

                fun speakText(text: String) {
                    if (text.isBlank()) return
                    isSpeaking = true
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ScannerAppTTS")
                }

                fun stopSpeaking() {
                    tts?.stop()
                    isSpeaking = false
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
                            if (onExtracted != null) {
                                onExtracted(extracted)
                            } else {
                                speakText(extracted)
                            }
                        } else {
                            Toast.makeText(this@MainActivity, getString(R.string.no_text_found), Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error extracting text", e)
                        Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
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
                                        stopSpeaking()
                                    } else {
                                        coroutineScope.launch { extractAndSpeak(scannedUris) }
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
                                aiEditedText = aiEditedText,
                                onAiEditedTextChange = { aiEditedText = it },
                                onClearAiEdit = { aiEditedText = null }
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
                                            stopSpeaking()
                                        } else {
                                            coroutineScope.launch { extractAndSpeak(uris) }
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
                                    aiEditedText = aiEditedText,
                                    onAiEditedTextChange = { aiEditedText = it },
                                    onClearAiEdit = { aiEditedText = null }
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
        onClearAiEdit: () -> Unit
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
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
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
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onSaveClick) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_save),
                                    contentDescription = stringResource(R.string.save_document_desc),
                                    modifier = Modifier.size(40.dp),
                                    tint = Color.Unspecified
                                )
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
        onClearAiEdit: () -> Unit
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
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
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
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onSaveClick(aiEditedText) }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_save),
                                contentDescription = stringResource(R.string.save_document_desc),
                                modifier = Modifier.size(40.dp),
                                tint = androidx.compose.ui.graphics.Color.Unspecified
                            )
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

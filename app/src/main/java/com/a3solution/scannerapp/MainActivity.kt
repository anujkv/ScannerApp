package com.a3solution.scannerapp

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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Print
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import android.content.Context
import androidx.compose.foundation.layout.size
import androidx.core.content.FileProvider
import java.io.File
import coil.compose.AsyncImage
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Checkbox
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import com.a3solution.scannerapp.data.AppDatabase
import com.a3solution.scannerapp.data.ScannedDocument
import kotlinx.coroutines.launch
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

class MainActivity : ComponentActivity() {
    private val TAG = "ScannerApp_Main"

    enum class Screen { HOME, HISTORY, DETAILS }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back_desc))
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
                                icon = { Icon(Icons.Default.List, contentDescription = stringResource(R.string.history_nav_label)) },
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
                                onPrintPdf = { printPdf(it) }
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
                                    onShareImages = { shareImages(it) },
                                    onSharePdf = { sharePdf(it) },
                                    onPrintPdf = { printPdf(it) }
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
        onScanClick: () -> Unit,
        onSaveClick: () -> Unit,
        onShareImages: (List<Uri>) -> Unit,
        onSharePdf: (Uri) -> Unit,
        onPrintPdf: (Uri) -> Unit
    ) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(onClick = onScanClick, modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.scan_document_button))
            }

            if (scannedUris.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onShareImages(scannedUris) }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_share_image),
                            contentDescription = stringResource(R.string.share_images_desc),
                            modifier = Modifier.size(48.dp),
                            tint = androidx.compose.ui.graphics.Color.Unspecified
                        )
                    }
                    pdfUri?.let { uri ->
                        IconButton(onClick = { onSharePdf(uri) }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_share_pdf),
                                contentDescription = stringResource(R.string.share_pdf_desc),
                                modifier = Modifier.size(48.dp),
                                tint = androidx.compose.ui.graphics.Color.Unspecified
                            )
                        }
                        IconButton(onClick = { onPrintPdf(uri) }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_printer),
                                contentDescription = stringResource(R.string.print_pdf_desc),
                                modifier = Modifier.size(48.dp),
                                tint = androidx.compose.ui.graphics.Color.Unspecified
                            )
                        }
                    }
                    IconButton(onClick = onSaveClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_save),
                            contentDescription = stringResource(R.string.save_document_desc),
                            modifier = Modifier.size(48.dp),
                            tint = androidx.compose.ui.graphics.Color.Unspecified
                        )
                    }
                }

                LazyColumn(modifier = Modifier.fillMaxWidth()) {
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
                                Text(
                                    text = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(doc.timestamp)),
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
        onShareImages: (List<Uri>) -> Unit,
        onSharePdf: (Uri) -> Unit,
        onPrintPdf: (Uri) -> Unit
    ) {
        val imageUris = remember(document) { document.imageUris.split(",").filter { it.isNotEmpty() }.map { Uri.parse(it) } }
        val pdfUri = remember(document) { document.pdfUri?.let { Uri.parse(it) } }

        Column(modifier = modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onShareImages(imageUris) }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_share_image),
                        contentDescription = stringResource(R.string.share_images_desc),
                        modifier = Modifier.size(48.dp),
                        tint = androidx.compose.ui.graphics.Color.Unspecified
                    )
                }
                pdfUri?.let { uri ->
                    IconButton(onClick = { onSharePdf(uri) }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_share_pdf),
                            contentDescription = stringResource(R.string.share_pdf_desc),
                            modifier = Modifier.size(48.dp),
                            tint = androidx.compose.ui.graphics.Color.Unspecified
                        )
                    }
                    IconButton(onClick = { onPrintPdf(uri) }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_printer),
                            contentDescription = stringResource(R.string.print_pdf_desc),
                            modifier = Modifier.size(48.dp),
                            tint = androidx.compose.ui.graphics.Color.Unspecified
                        )
                    }
                }
            }

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
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

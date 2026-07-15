package com.a3solution.scannerapp.ui.screens

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.tooling.preview.Preview
import com.a3solution.scannerapp.R
import com.a3solution.scannerapp.data.ScannedDocument
import com.a3solution.scannerapp.ui.components.ActionCard
import java.text.SimpleDateFormat
import java.util.*

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
    onWatermarkClick: () -> Unit,
    copyToClipboard: (String) -> Unit
) {
    val imageUris = remember(document) { document.imageUris.split(",").filter { it.isNotEmpty() }.map { Uri.parse(it) } }
    val pdfUri = remember(document) { document.pdfUri?.let { Uri.parse(it) } }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { z, o, _ ->
        scale = (scale * z).coerceIn(1f, 5f)
        val no = offset + o
        offset = if (scale > 1f) no else Offset.Zero
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(Color(0xFFF7F9FC)),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = document.name,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 38.sp,
                    color = colorResource(R.color.text_primary)
                )
                val formattedDate = remember(document.timestamp) { 
                    SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault()).format(Date(document.timestamp)) 
                }
                Text(text = formattedDate, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().height(450.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = MaterialTheme.shapes.large,
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
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
                                modifier = Modifier.fillMaxWidth().height(450.dp).padding(8.dp),
                                contentScale = ContentScale.Fit,
                                error = painterResource(id = R.drawable.ic_launcher_foreground),
                                placeholder = painterResource(id = R.drawable.ic_launcher_foreground)
                            )
                        }
                    }
                }
            }
        }

        item { Text(stringResource(R.string.actions_header), style = MaterialTheme.typography.labelLarge, color = colorResource(R.color.text_primary), fontWeight = FontWeight.Bold) }

        item {
            AnimatedVisibility(
                visible = scale <= 1.1f,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionCard(modifier = Modifier.weight(1f), icon = Icons.Default.Image, title = stringResource(R.string.share_image_title), subtitle = stringResource(R.string.share_image_subtitle), onClick = { onShareImages(imageUris) })
                        ActionCard(modifier = Modifier.weight(1f), icon = Icons.Default.PictureAsPdf, title = stringResource(R.string.share_pdf_title), subtitle = stringResource(R.string.share_pdf_subtitle), onClick = { pdfUri?.let { onSharePdf(it) } })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionCard(modifier = Modifier.weight(1f), icon = Icons.AutoMirrored.Filled.VolumeUp, title = stringResource(R.string.tts_title), subtitle = stringResource(R.string.tts_subtitle), onClick = { onSpeakClick(imageUris) }, isLoading = isSpeaking)
                        ActionCard(modifier = Modifier.weight(1f), icon = painterResource(id = R.drawable.watermark_scanner), title = stringResource(R.string.watermark_title), subtitle = stringResource(R.string.watermark_subtitle), onClick = onWatermarkClick, isLoading = isWatermarking)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionCard(modifier = Modifier.weight(1f), icon = Icons.Default.Compress, title = stringResource(R.string.compress_title), subtitle = stringResource(R.string.compress_subtitle), onClick = { onCompressClick(imageUris) }, isLoading = isCompressing)
                        ActionCard(modifier = Modifier.weight(1f), icon = Icons.Default.AutoFixHigh, title = stringResource(R.string.ai_edit_title), subtitle = stringResource(R.string.ai_edit_subtitle), onClick = { onAiEditClick(imageUris) }, isLoading = isListening)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionCard(modifier = Modifier.weight(1f), icon = painterResource(R.drawable.ic_launcher_foreground), title = stringResource(R.string.ai_visual_scan_title), subtitle = stringResource(R.string.ai_visual_scan_subtitle), onClick = { onAiVisualScanClick(imageUris) }, isLoading = isAiProcessing)
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        if (aiEditedText != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, Color(0xFFE0E0E0))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.extracted_text_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            IconButton(onClick = { copyToClipboard(aiEditedText) }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.copy_button), modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = onClearAiEdit) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.clear_button), modifier = Modifier.size(20.dp))
                            }
                        }
                        TextField(
                            value = aiEditedText,
                            onValueChange = onAiEditedTextChange,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DocumentDetailsScreenPreview() {
    val mockDoc = ScannedDocument(
        id = 1,
        name = "Sample Document",
        timestamp = System.currentTimeMillis(),
        pdfUri = null,
        imageUris = ""
    )
    MaterialTheme {
        DocumentDetailsScreen(
            modifier = Modifier,
            document = mockDoc,
            isSpeaking = false,
            isListening = false,
            isAiProcessing = false,
            isCompressing = false,
            isWatermarking = false,
            aiEditedText = "Sample extracted text from the document.",
            onAiEditedTextChange = {},
            onShareImages = {},
            onSharePdf = {},
            onPrintPdf = {},
            onSpeakClick = {},
            onViewTextClick = {},
            onAiEditClick = {},
            onAiVisualScanClick = {},
            onCompressClick = {},
            onSaveClick = {},
            onClearAiEdit = {},
            onWatermarkClick = {},
            copyToClipboard = {}
        )
    }
}


package com.a3solution.scannerapp.ui.screens

import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.TextSnippet
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
import com.a3solution.scannerapp.R
import com.a3solution.scannerapp.ui.components.ActionCard

import androidx.compose.ui.tooling.preview.Preview

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
    onWatermarkClick: () -> Unit,
    onGalleryClick: () -> Unit,
    copyToClipboard: (String) -> Unit
) {
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
                Surface(color = Color(0xFFF1F4F9), shape = CircleShape, modifier = Modifier.padding(bottom = 12.dp)) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = R.mipmap.ic_launcher, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.ocr_badge_label), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                }
                @Suppress("DEPRECATION")
                Text(stringResource(R.string.home_headline), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, lineHeight = 38.sp, color = colorResource(R.color.text_primary))
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.home_description), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth().height(350.dp), colors = CardDefaults.cardColors(containerColor = Color.White), shape = MaterialTheme.shapes.large, elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (scannedUris.isEmpty()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(modifier = Modifier.size(64.dp).background(Color(0xFF1d3b60), shape = MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) { Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp)) }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(stringResource(R.string.position_frame_hint), fontWeight = FontWeight.Bold, color = Color.DarkGray)
                            Text(stringResource(R.string.edge_detection_hint), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(onClick = onGalleryClick,
                                    colors = ButtonDefaults
                                        .buttonColors(containerColor = Color.White, contentColor = colorResource(R.color.text_primary)),
                                    border = BorderStroke(1.dp, colorResource(R.color.light_primary))) {
                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.gallery_label), color = colorResource(R.color.text_primary)) }
                                Button(onClick = onScanClick, colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.primary))) { Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(8.dp)); Text(text = stringResource(R.string.scan_document_button), color = Color(0xFFFFFFFF) ) }
                            }
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y).transformable(state = state)) {
                            items(scannedUris) { uri -> AsyncImage(model = uri, contentDescription = null, modifier = Modifier.fillMaxWidth().height(400.dp).padding(8.dp), contentScale = ContentScale.Fit) }
                        }
                    }
                }
            }
        }
        item { Text(stringResource(R.string.actions_header), style = MaterialTheme.typography.labelLarge, color = colorResource(R.color.text_primary), fontWeight = FontWeight.Bold) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionCard(modifier = Modifier.weight(1f), icon = Icons.Default.Image, title = stringResource(R.string.share_image_title), subtitle = stringResource(R.string.share_image_subtitle), onClick = { onShareImages(scannedUris) })
                    ActionCard(modifier = Modifier.weight(1f), icon = Icons.Default.PictureAsPdf, title = stringResource(R.string.share_pdf_title), subtitle = stringResource(R.string.share_pdf_subtitle), onClick = { pdfUri?.let { onSharePdf(it) } })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionCard(modifier = Modifier.weight(1f), icon = Icons.Default.Print, title = stringResource(R.string.print_title), subtitle = stringResource(R.string.print_subtitle), onClick = { pdfUri?.let { onPrintPdf(it) } })
                    ActionCard(modifier = Modifier.weight(1f), icon = Icons.Default.Save, title = stringResource(R.string.save_title), subtitle = stringResource(R.string.save_subtitle), onClick = onSaveClick)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionCard(modifier = Modifier.weight(1f), icon = Icons.AutoMirrored.Filled.VolumeUp, title = stringResource(R.string.tts_title), subtitle = stringResource(R.string.tts_subtitle), onClick = onSpeakClick, isLoading = isSpeaking)
                    ActionCard(modifier = Modifier.weight(1f), icon = Icons.AutoMirrored.Filled.TextSnippet, title = stringResource(R.string.extracted_text_title), subtitle = stringResource(R.string.extract_text_subtitle), onClick = onViewTextClick, isLoading = isAiProcessing)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionCard(modifier = Modifier.weight(1f), icon = Icons.AutoMirrored.Filled.CompareArrows, title = stringResource(R.string.compare_title), subtitle = stringResource(R.string.compare_subtitle), onClick = { /* TODO */ })
                    ActionCard(modifier = Modifier.weight(1f), icon = painterResource(id = R.drawable.watermark_scanner), title = stringResource(R.string.watermark_title), subtitle = stringResource(R.string.watermark_subtitle), onClick = onWatermarkClick, isLoading = isWatermarking)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionCard(modifier = Modifier.weight(1f), icon = Icons.Default.AutoFixHigh, title = stringResource(R.string.ai_edit_title), subtitle = stringResource(R.string.ai_edit_subtitle), onClick = onAiEditClick, isLoading = isListening)
                    ActionCard(modifier = Modifier.weight(1f), icon = painterResource(R.drawable.ic_launcher_foreground), title = stringResource(R.string.ai_visual_scan_title), subtitle = stringResource(R.string.ai_visual_scan_subtitle), onClick = onAiVisualScanClick, isLoading = isAiProcessing)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionCard(modifier = Modifier.weight(1f), icon = Icons.Default.Compress, title = stringResource(R.string.compress_title), subtitle = stringResource(R.string.compress_subtitle), onClick = onCompressClick, isLoading = isCompressing)
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
        if (aiEditedText != null) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, Color(0xFFE0E0E0))) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.extracted_text_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text(stringResource(R.string.ocr_result_hint), style = MaterialTheme.typography.labelSmall, color = Color.Gray) }
                        Spacer(modifier = Modifier.height(12.dp))
                        TextField(value = aiEditedText, onValueChange = onAiEditedTextChange, modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 300.dp).background(Color(0xFFF8FAFB), shape = MaterialTheme.shapes.medium), textStyle = MaterialTheme.typography.bodyMedium, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { 
                                copyToClipboard(aiEditedText)
                            }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F4F9), contentColor = Color.Black), shape = MaterialTheme.shapes.medium) { Text(stringResource(R.string.copy_button)) }
                            Button(onClick = onSpeakClick, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F4F9), contentColor = Color.Black), shape = MaterialTheme.shapes.medium) { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(4.dp)); Text(stringResource(R.string.read_aloud_button)) }
                            IconButton(onClick = onClearAiEdit) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.clear_button), tint = Color.Gray) }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    MaterialTheme {
        HomeScreen(
            modifier = Modifier,
            scannedUris = emptyList(),
            pdfUri = null,
            isSpeaking = false,
            isListening = false,
            isAiProcessing = false,
            isCompressing = false,
            isWatermarking = false,
            aiEditedText = null,
            onAiEditedTextChange = {},
            onScanClick = {},
            onSaveClick = {},
            onShareImages = {},
            onSharePdf = {},
            onPrintPdf = {},
            onSpeakClick = {},
            onViewTextClick = {},
            onAiEditClick = {},
            onAiVisualScanClick = {},
            onCompressClick = {},
            onClearAiEdit = {},
            onWatermarkClick = {},
            onGalleryClick = {},
            copyToClipboard = {}
        )
    }
}


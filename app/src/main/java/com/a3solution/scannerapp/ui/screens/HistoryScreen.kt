package com.a3solution.scannerapp.ui.screens

import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.a3solution.scannerapp.R
import androidx.compose.ui.tooling.preview.Preview
import com.a3solution.scannerapp.data.ScannedDocument
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(
    modifier: Modifier,
    documents: List<ScannedDocument>,
    onItemClick: (ScannedDocument) -> Unit,
    onDeleteClick: (ScannedDocument) -> Unit,
    onRenameClick: (ScannedDocument, String) -> Unit,
    getDocumentSize: (ScannedDocument) -> Long,
    formatFileSize: (Long) -> String
) {
    var searchQuery by remember { mutableStateOf("") }
    var documentToRename by remember { mutableStateOf<ScannedDocument?>(null) }
    var newName by remember { mutableStateOf("") }

    if (documentToRename != null) {
        AlertDialog(
            onDismissRequest = { documentToRename = null },
            title = { Text("Rename Document") },
            text = {
                TextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        documentToRename?.let { onRenameClick(it, newName) }
                        documentToRename = null
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { documentToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    val filteredDocuments = remember(documents, searchQuery) {
        documents.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }
    val totalSize = remember(documents) { documents.sumOf { getDocumentSize(it) } }
    val totalSizeStr = formatFileSize(totalSize)

    Column(modifier = modifier.fillMaxSize().background(Color(0xFFF7F9FC))) {
        // Header
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.history_screen_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = colorResource(R.color.text_primary)
            )
            Text(
                text = "${documents.size} saved documents · $totalSizeStr total",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Search Bar
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Color.White, shape = MaterialTheme.shapes.medium)
                    .border(1.dp, Color(0xFFE0E0E0), shape = MaterialTheme.shapes.medium),
                placeholder = { Text("Search documents...", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                singleLine = true
            )
        }

        if (filteredDocuments.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (searchQuery.isEmpty()) stringResource(R.string.no_saved_documents)
                    else "No matching documents found",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                items(filteredDocuments) { doc ->
                    HistoryItem(
                        doc, 
                        onItemClick, 
                        onDeleteClick, 
                        onRename = { 
                            documentToRename = it
                            newName = it.name
                        },
                        getDocumentSize, 
                        formatFileSize
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HistoryScreenPreview() {
    val mockDocs = listOf(
        ScannedDocument(1, "Invoice 001", System.currentTimeMillis(), null, ""),
        ScannedDocument(2, "Receipt - Starbucks", System.currentTimeMillis() - 3600000, null, "")
    )
    MaterialTheme {
        HistoryScreen(
            modifier = Modifier,
            documents = mockDocs,
            onItemClick = {},
            onDeleteClick = {},
            onRenameClick = { _, _ -> },
            getDocumentSize = { 1024L },
            formatFileSize = { "1.2 MB" }
        )
    }
}


@Composable
fun HistoryItem(
    doc: ScannedDocument,
    onClick: (ScannedDocument) -> Unit,
    onDelete: (ScannedDocument) -> Unit,
    onRename: (ScannedDocument) -> Unit,
    getDocumentSize: (ScannedDocument) -> Long,
    formatFileSize: (Long) -> String
) {
    val pageCount = remember(doc.imageUris) { doc.imageUris.split(",").count { it.isNotEmpty() } }
    val fileSize = remember(doc) { formatFileSize(getDocumentSize(doc)) }
    val firstUri = remember(doc.imageUris) { doc.imageUris.split(",").firstOrNull { it.isNotEmpty() }?.let { Uri.parse(it) } }
    val formattedDate = remember(doc.timestamp) { SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault()).format(Date(doc.timestamp)) }
    var showMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Image Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .clickable { onClick(doc) },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, Color(0xFFE0E0E0))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = firstUri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFF1F4F9))
                        .padding(16.dp),
                    contentScale = ContentScale.Fit,
                    error = painterResource(id = R.drawable.ic_launcher_foreground),
                    placeholder = painterResource(id = R.drawable.ic_launcher_foreground)
                )

                // Floating Action Pill
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp),
                    color = Color.White,
                    shape = CircleShape,
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.dp, Color(0xFFE0E0E0))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CropFree, contentDescription = null, modifier = Modifier.size(18.dp), tint = colorResource(R.color.primary))
                        Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(18.dp), tint = colorResource(R.color.primary))
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp), tint = colorResource(R.color.primary))
                        Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(18.dp), tint = colorResource(R.color.primary))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Metadata
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorResource(R.color.text_primary)
                )
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Color(0xFFE8EDF4),
                        shape = CircleShape
                    ) {
                        Text(
                            text = if (pageCount == 1) "1 page" else "$pageCount pages",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1d3b60)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = fileSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
            
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = Color.Black
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Preview") },
                        onClick = {
                            showMenu = false
                            onClick(doc)
                        },
                        leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = {
                            showMenu = false
                            onRename(doc)
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            showMenu = false
                            onDelete(doc)
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                    )
                }
            }
        }
    }
}




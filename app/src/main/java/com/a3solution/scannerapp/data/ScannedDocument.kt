package com.a3solution.scannerapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scanned_documents")
data class ScannedDocument(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val timestamp: Long,
    val pdfUri: String?,
    val imageUris: String // Comma-separated list of URIs
)

package com.metao.ai.domain.model

import android.net.Uri
import java.io.File

data class ModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val sourceUrl: Uri,
    val destinationFile: File,
    val sizeBytes: Long,
    val isDownloaded: Boolean = false,
    val isLoaded: Boolean = false
)

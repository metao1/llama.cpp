package com.metao.ai.domain.repository

import com.metao.ai.domain.model.FileItem
import com.metao.ai.domain.model.FileCategory
import com.metao.ai.domain.model.CategorizationResult
import com.metao.ai.domain.usecase.GenerateTextUseCase
import com.metao.ai.domain.usecase.MoveFileProgress
import kotlinx.coroutines.flow.Flow

interface FileRepository {
    
    /**
     * Scan a directory and return file items with metadata
     */
    suspend fun scanDirectory(
        directoryPath: String,
        includeSubdirectories: Boolean = false,
        maxFileSizeForContent: Long = 1024 * 1024
    ): Flow<List<FileItem>>
    
    /**
     * Extract metadata and content preview from a file
     */
    suspend fun getFileMetadata(filePath: String, maxContentSize: Long = 1024 * 1024): FileItem?
    
    /**
     * Categorize a single file using LLM
     */
    suspend fun categorizeFile(
        fileItem: FileItem,
        availableCategories: List<FileCategory>,
        generateTextUseCase: GenerateTextUseCase
    ): Flow<CategorizationResult>
    
    /**
     * Move files to their categorized folders
     */
    suspend fun moveFiles(
        results: List<CategorizationResult>,
        baseDirectory: String
    ): Flow<MoveFileProgress>
    
    /**
     * Create category folders if they don't exist
     */
    suspend fun createCategoryFolders(
        categories: List<FileCategory>,
        baseDirectory: String
    ): Boolean
    
    /**
     * Get available storage directories
     */
    suspend fun getAvailableDirectories(): List<String>
    
    /**
     * Check if directory is accessible
     */
    suspend fun isDirectoryAccessible(directoryPath: String): Boolean
}

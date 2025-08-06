package com.metao.ai.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface SettingsRepository {
    suspend fun setFileWatchingEnabled(enabled: Boolean)
    suspend fun isFileWatchingEnabled(): Boolean
    suspend fun addWatchedDirectory(path: String)
    suspend fun removeWatchedDirectory(path: String)
    suspend fun getWatchedDirectories(): List<String>
    suspend fun setAutoCategorizationEnabled(enabled: Boolean)
    suspend fun isAutoCategorizationEnabled(): Boolean
    fun getWatchedDirectoriesFlow(): Flow<List<String>>
    fun getFileWatchingEnabledFlow(): Flow<Boolean>
}

class SettingsRepositoryImpl(
    private val context: Context
) : SettingsRepository {
    
    companion object {
        private const val PREFS_NAME = "file_categorizer_settings"
        private const val KEY_FILE_WATCHING_ENABLED = "file_watching_enabled"
        private const val KEY_WATCHED_DIRECTORIES = "watched_directories"
        private const val KEY_AUTO_CATEGORIZATION_ENABLED = "auto_categorization_enabled"
        private const val DIRECTORY_SEPARATOR = "||"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _watchedDirectories = MutableStateFlow(getWatchedDirectoriesFromPrefs())
    private val _fileWatchingEnabled = MutableStateFlow(getFileWatchingEnabledFromPrefs())
    
    override suspend fun setFileWatchingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FILE_WATCHING_ENABLED, enabled).apply()
        _fileWatchingEnabled.value = enabled
    }
    
    override suspend fun isFileWatchingEnabled(): Boolean {
        return prefs.getBoolean(KEY_FILE_WATCHING_ENABLED, false)
    }
    
    override suspend fun addWatchedDirectory(path: String) {
        val currentDirs = getWatchedDirectories().toMutableSet()
        currentDirs.add(path)
        saveWatchedDirectories(currentDirs.toList())
    }
    
    override suspend fun removeWatchedDirectory(path: String) {
        val currentDirs = getWatchedDirectories().toMutableSet()
        currentDirs.remove(path)
        saveWatchedDirectories(currentDirs.toList())
    }
    
    override suspend fun getWatchedDirectories(): List<String> {
        return getWatchedDirectoriesFromPrefs()
    }
    
    override suspend fun setAutoCategorizationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CATEGORIZATION_ENABLED, enabled).apply()
    }
    
    override suspend fun isAutoCategorizationEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_CATEGORIZATION_ENABLED, true)
    }
    
    override fun getWatchedDirectoriesFlow(): Flow<List<String>> {
        return _watchedDirectories.asStateFlow()
    }
    
    override fun getFileWatchingEnabledFlow(): Flow<Boolean> {
        return _fileWatchingEnabled.asStateFlow()
    }
    
    private fun getWatchedDirectoriesFromPrefs(): List<String> {
        val directoriesString = prefs.getString(KEY_WATCHED_DIRECTORIES, "")
        return if (directoriesString.isNullOrEmpty()) {
            getDefaultWatchedDirectories()
        } else {
            directoriesString.split(DIRECTORY_SEPARATOR).filter { it.isNotEmpty() }
        }
    }
    
    private fun getFileWatchingEnabledFromPrefs(): Boolean {
        return prefs.getBoolean(KEY_FILE_WATCHING_ENABLED, false)
    }
    
    private fun saveWatchedDirectories(directories: List<String>) {
        val directoriesString = directories.joinToString(DIRECTORY_SEPARATOR)
        prefs.edit().putString(KEY_WATCHED_DIRECTORIES, directoriesString).apply()
        _watchedDirectories.value = directories
    }
    
    private fun getDefaultWatchedDirectories(): List<String> {
        return listOf(
            "/storage/emulated/0/Download",
            "/storage/emulated/0/Documents",
            "/storage/emulated/0/Pictures",
            "/storage/emulated/0/DCIM/Camera"
        ).filter { java.io.File(it).exists() }
    }
}

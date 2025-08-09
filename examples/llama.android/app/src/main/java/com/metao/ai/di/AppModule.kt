package com.metao.ai.di

import android.app.DownloadManager
import android.content.Context
import android.llama.cpp.LLamaAndroid
import com.metao.ai.data.database.ModelDatabase
import com.metao.ai.data.database.CategorizationDatabase
import com.metao.ai.data.repository.ModelDatabaseRepository
import com.metao.ai.data.repository.ModelRepositoryImpl
import com.metao.ai.data.repository.FileRepositoryImpl
import com.metao.ai.data.repository.CategorizationStateRepository
import com.metao.ai.data.repository.CategorizationStateRepositoryImpl
import com.metao.ai.data.repository.SettingsRepository
import com.metao.ai.data.repository.SettingsRepositoryImpl
import com.metao.ai.domain.manager.FileWatcherManager
import com.metao.ai.presentation.settings.FileWatcherSettingsViewModel
import com.metao.ai.domain.repository.ModelRepository
import com.metao.ai.domain.repository.FileRepository
import com.metao.ai.domain.manager.ModelStateManager
import com.metao.ai.domain.usecase.AddCustomModelUseCase
import com.metao.ai.domain.usecase.ClearMessagesUseCase
import com.metao.ai.domain.usecase.DownloadModelUseCase
import com.metao.ai.domain.usecase.GenerateTextUseCase
import com.metao.ai.domain.usecase.GetModelsUseCase
import com.metao.ai.domain.usecase.IsModelLoadedUseCase
import com.metao.ai.domain.usecase.LoadModelUseCase
import com.metao.ai.domain.usecase.ScanDirectoryUseCase
import com.metao.ai.domain.usecase.CategorizeFileUseCase
import com.metao.ai.domain.usecase.MoveFilesUseCase
import com.metao.ai.presentation.chat.ChatViewModel
import com.metao.ai.presentation.models.ModelsViewModel
import com.metao.ai.presentation.categorize.FileCategorizeViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {

    // System Services
    single<DownloadManager> {
        get<Context>().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }

    // Model State Manager
    single { ModelStateManager() }

    single {
        LLamaAndroid.instance()
    }

    // Database
    single { ModelDatabase.getDatabase(get()) }
    single { get<ModelDatabase>().modelDao() }
    single { ModelDatabaseRepository(get()) }

    // Categorization Database
    single { CategorizationDatabase.getDatabase(get()) }
    single { get<CategorizationDatabase>().categorizationDao() }
    single<CategorizationStateRepository> { CategorizationStateRepositoryImpl(get()) }

    // Settings and File Watching
    single<SettingsRepository> { SettingsRepositoryImpl(get()) }
    single { FileWatcherManager(get(), get()) }

    // Repository
    single<ModelRepository> {
        ModelRepositoryImpl(
            context = get(),
            downloadManager = get(),
            llamaAndroid = get(),
            databaseRepository = get()
        )
    }

    single<FileRepository> {
        FileRepositoryImpl(context = get())
    }

    // Use Cases
    single { GetModelsUseCase(get()) }
    single { AddCustomModelUseCase(get()) }
    single { DownloadModelUseCase(get()) }
    single { LoadModelUseCase(get()) }
    single { GenerateTextUseCase(get()) }
    single { IsModelLoadedUseCase(get()) }
    single { ClearMessagesUseCase(get()) }

    // File categorization use cases
    single { ScanDirectoryUseCase(get()) }
    single { CategorizeFileUseCase(get(), get()) }
    single { MoveFilesUseCase(get()) }

    // ViewModels
    viewModel { ChatViewModel(get(), get(), get(), get()) }
    viewModel { ModelsViewModel(get(), get(), get(), get(), get()) }
    viewModel { FileCategorizeViewModel(get(), get(), get(), get(), get()) }
    viewModel { FileWatcherSettingsViewModel(get(), get()) }
}

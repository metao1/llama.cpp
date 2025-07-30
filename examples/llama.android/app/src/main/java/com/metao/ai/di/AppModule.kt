package com.metao.ai.di

import android.app.DownloadManager
import android.content.Context
import android.llama.cpp.LLamaAndroid
import com.metao.ai.data.repository.ModelRepositoryImpl
import com.metao.ai.domain.repository.ModelRepository
import com.metao.ai.domain.manager.ModelStateManager
import com.metao.ai.domain.usecase.ClearMessagesUseCase
import com.metao.ai.domain.usecase.DownloadModelUseCase
import com.metao.ai.domain.usecase.GenerateTextUseCase
import com.metao.ai.domain.usecase.GetModelsUseCase
import com.metao.ai.domain.usecase.IsModelLoadedUseCase
import com.metao.ai.domain.usecase.LoadModelUseCase
import com.metao.ai.presentation.chat.ChatViewModel
import com.metao.ai.presentation.models.ModelsViewModel
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

    // Repository
    single<ModelRepository> {
        ModelRepositoryImpl(
            context = get(),
            downloadManager = get(),
            llamaAndroid = get()
        )
    }

    // Use Cases
    single { GetModelsUseCase(get()) }
    single { DownloadModelUseCase(get()) }
    single { LoadModelUseCase(get()) }
    single { GenerateTextUseCase(get()) }
    single { IsModelLoadedUseCase(get()) }
    single { ClearMessagesUseCase(get()) }

    // ViewModels
    viewModel { ChatViewModel(get(), get(), get(), get()) }
    viewModel { ModelsViewModel(get(), get(), get(), get()) }
}

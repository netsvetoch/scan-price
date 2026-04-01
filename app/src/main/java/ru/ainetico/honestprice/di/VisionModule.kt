package ru.ainetico.honestprice.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.ainetico.honestprice.analyzer.ImageAnalyzer
import ru.ainetico.honestprice.calculator.PriceCalculator
import ru.ainetico.honestprice.data.AppSettings
import ru.ainetico.honestprice.model.ModelDownloader
import ru.ainetico.honestprice.ocr.LocalVisionEngine
import ru.ainetico.honestprice.ocr.RemoteVisionClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VisionModule {

    @Provides
    @Singleton
    fun provideLocalVisionEngine(@ApplicationContext context: Context): LocalVisionEngine {
        return LocalVisionEngine(context)
    }

    @Provides
    @Singleton
    fun provideRemoteVisionClient(): RemoteVisionClient {
        return RemoteVisionClient()
    }

    @Provides
    @Singleton
    fun provideImageAnalyzer(
        localEngine: LocalVisionEngine,
        calculator: PriceCalculator,
        appSettings: AppSettings,
        remoteClient: RemoteVisionClient
    ): ImageAnalyzer {
        return ImageAnalyzer(localEngine, calculator, appSettings, remoteClient)
    }

    @Provides
    @Singleton
    fun provideModelDownloader(@ApplicationContext context: Context): ModelDownloader {
        return ModelDownloader(context)
    }
}

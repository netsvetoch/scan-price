package ru.ainetico.honestprice.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import ru.ainetico.honestprice.analyzer.ImageAnalyzer
import ru.ainetico.honestprice.calculator.PriceCalculator
import ru.ainetico.honestprice.data.AppSettings
import ru.ainetico.honestprice.model.ModelDownloader
import ru.ainetico.honestprice.ocr.LocalVisionEngine
import ru.ainetico.honestprice.ocr.RemoteVisionClient
import ru.ainetico.honestprice.ocr.VisionEngine
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
  @LocalEngine
  fun provideLocalEngine(engine: LocalVisionEngine): VisionEngine = engine

  @Provides
  @Singleton
  fun provideRemoteVisionClient(appSettings: AppSettings): RemoteVisionClient {
    return RemoteVisionClient(appSettings)
  }

  @Provides
  @Singleton
  @RemoteEngine
  fun provideRemoteEngine(client: RemoteVisionClient): VisionEngine = client

  @Provides
  @Singleton
  fun provideImageAnalyzer(
    @LocalEngine localEngine: VisionEngine,
    calculator: PriceCalculator,
    appSettings: AppSettings,
    @RemoteEngine remoteClient: VisionEngine
  ): ImageAnalyzer {
    return ImageAnalyzer(localEngine, calculator, appSettings, remoteClient)
  }

  @Provides
  @Singleton
  fun provideModelDownloader(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope
  ): ModelDownloader {
    return ModelDownloader(context, scope)
  }
}

package ru.ainetico.scanprice.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.ainetico.scanprice.data.AppSettings
import ru.ainetico.scanprice.data.ScanRepository
import ru.ainetico.scanprice.data.ScanRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

  @Suppress("unused")
  @Binds
  @Singleton
  abstract fun bindScanRepository(impl: ScanRepositoryImpl): ScanRepository

  companion object {
    @Provides
    @Singleton
    fun provideAppSettings(@ApplicationContext context: Context): AppSettings {
      return AppSettings(context)
    }
  }
}

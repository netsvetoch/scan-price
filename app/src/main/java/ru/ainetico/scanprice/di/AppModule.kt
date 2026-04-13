package ru.ainetico.scanprice.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ru.ainetico.scanprice.calculator.PriceCalculator
import ru.ainetico.scanprice.location.LocationProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

  @Provides
  fun providePriceCalculator(): PriceCalculator {
    return PriceCalculator()
  }

  @Provides
  @Singleton
  fun provideLocationProvider(@ApplicationContext context: Context): LocationProvider {
    return LocationProvider(context)
  }

  @Provides
  @Singleton
  @ApplicationScope
  fun provideApplicationScope(): CoroutineScope {
    return CoroutineScope(Dispatchers.IO + SupervisorJob())
  }
}

package ru.ainetico.honestprice.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.ainetico.honestprice.data.AppDatabase
import ru.ainetico.honestprice.data.ScanDao
import ru.ainetico.honestprice.data.StoreDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "honest_price.db"
        ).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
        .build()
    }

    @Provides
    fun provideScanDao(database: AppDatabase): ScanDao {
        return database.scanDao()
    }

    @Provides
    fun provideStoreDao(database: AppDatabase): StoreDao {
        return database.storeDao()
    }
}

package com.hasyame.marvelchampions.di

import android.content.Context
import androidx.room.Room
import com.hasyame.marvelchampions.data.db.MarvelChampionsDatabase
import com.hasyame.marvelchampions.data.db.dao.CampaignDao
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.hasyame.marvelchampions.data.db.dao.FavouriteDao
import com.hasyame.marvelchampions.data.db.dao.PausedGameDao
import com.hasyame.marvelchampions.data.db.dao.PlayDao
import com.hasyame.marvelchampions.data.db.dao.CardDao
import com.hasyame.marvelchampions.data.db.dao.ExcludedModularSetDao
import com.hasyame.marvelchampions.data.db.dao.ExcludedScenarioDao
import com.hasyame.marvelchampions.data.db.dao.OwnedPackDao
import com.hasyame.marvelchampions.data.db.dao.PackDao
import com.hasyame.marvelchampions.data.db.dao.RandomizerHistoryDao
import com.hasyame.marvelchampions.data.db.dao.SavedDeckDao
import com.hasyame.marvelchampions.data.marvelcdb.MarvelCdbApi
import com.hasyame.marvelchampions.data.marvelcdb.MarvelCdbUrls
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        // MarvelCDB adds fields over time; an unknown one must not break a sync.
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        // The full card dump is over 3 MB per locale, so the read timeout has
        // to be generous on a slow connection.
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        // Only a default. Localised requests pass an absolute URL, because
        // MarvelCDB serves translations from a locale subdomain.
        .baseUrl(MarvelCdbUrls.DEFAULT_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideMarvelCdbApi(retrofit: Retrofit): MarvelCdbApi =
        retrofit.create(MarvelCdbApi::class.java)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MarvelChampionsDatabase =
        Room.databaseBuilder(context, MarvelChampionsDatabase::class.java, MarvelChampionsDatabase.NAME)
            .build()

    @Provides
    fun provideCardDao(database: MarvelChampionsDatabase): CardDao = database.cardDao()

    @Provides
    fun providePackDao(database: MarvelChampionsDatabase): PackDao = database.packDao()

    @Provides
    fun provideOwnedPackDao(database: MarvelChampionsDatabase): OwnedPackDao =
        database.ownedPackDao()

    @Provides
    fun provideExcludedModularSetDao(database: MarvelChampionsDatabase): ExcludedModularSetDao =
        database.excludedModularSetDao()

    @Provides
    fun provideExcludedScenarioDao(database: MarvelChampionsDatabase): ExcludedScenarioDao =
        database.excludedScenarioDao()

    @Provides
    fun provideRandomizerHistoryDao(database: MarvelChampionsDatabase): RandomizerHistoryDao =
        database.randomizerHistoryDao()

    @Provides
    fun provideSavedDeckDao(database: MarvelChampionsDatabase): SavedDeckDao =
        database.savedDeckDao()

    @Provides
    fun provideCampaignDao(database: MarvelChampionsDatabase): CampaignDao =
        database.campaignDao()

    @Provides
    fun providePlayDao(database: MarvelChampionsDatabase): PlayDao =
        database.playDao()

    @Provides
    fun providePausedGameDao(database: MarvelChampionsDatabase): PausedGameDao =
        database.pausedGameDao()

    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext context: Context): ImageLoader =
        // The singleton the app already configured, with its disk cache, rather
        // than a second loader writing to a second cache.
        SingletonImageLoader.get(context)

    @Provides
    fun provideFavouriteDao(database: MarvelChampionsDatabase): FavouriteDao =
        database.favouriteDao()
}

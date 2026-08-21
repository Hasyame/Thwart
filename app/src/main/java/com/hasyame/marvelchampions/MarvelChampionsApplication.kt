package com.hasyame.marvelchampions

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.hasyame.marvelchampions.data.diagnostics.CrashLog
import com.hasyame.marvelchampions.data.db.dao.PausedGameDao
import com.hasyame.marvelchampions.data.db.dao.PlayDao
import com.hasyame.marvelchampions.data.photos.PhotoStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Provider

@HiltAndroidApp
class MarvelChampionsApplication :
    Application(),
    Configuration.Provider,
    SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        // First thing, so a crash during the rest of startup is still recorded.
        CrashLog.install(this)
        sweepPhotos()
    }

    /**
     * Throws away photographs no play and no paused game refers to any more.
     *
     * A game abandoned without being filed, or a paused game forgotten, leaves
     * its pictures behind and nothing else would ever remove them. Done once at
     * startup, off the main thread, and failure is not worth crashing over: the
     * only cost of skipping it is a few kilobytes until the next launch.
     */
    private fun sweepPhotos() {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val keep = (playDao.get().photoLists() + pausedGameDao.get().photoLists())
                    .flatMap { it.split(",") }
                    .filter { it.isNotBlank() }
                    .toSet()
                photoStore.get().deleteOrphans(keep)
            }
        }
    }

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var okHttpClient: Provider<OkHttpClient>

    // Providers rather than direct injection: the sweep is the only thing that
    // needs these, and it should not pull the database open during startup any
    // earlier than the app would have opened it anyway.
    @Inject
    lateinit var playDao: Provider<PlayDao>

    @Inject
    lateinit var pausedGameDao: Provider<PausedGameDao>

    @Inject
    lateinit var photoStore: Provider<PhotoStore>

    /**
     * WorkManager is configured on demand rather than by its default
     * initializer, so [com.hasyame.marvelchampions.data.sync.CardSyncWorker]
     * can be constructed by Hilt. The default initializer is removed in the
     * manifest.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    /**
     * Card images are fetched from MarvelCDB at runtime and cached on disk —
     * they are never committed to the repository. The network fetcher is wired
     * explicitly so images share the app's OkHttp client rather than a second
     * one with different timeouts.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient.get() }))
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("card_images"))
                    .maxSizeBytes(IMAGE_CACHE_BYTES)
                    .build()
            }
            .build()

    private companion object {
        const val IMAGE_CACHE_BYTES = 256L * 1024 * 1024
    }
}

package com.hasyame.marvelchampions.data.repository

import androidx.room.withTransaction
import com.hasyame.marvelchampions.data.db.MarvelChampionsDatabase
import com.hasyame.marvelchampions.data.db.entity.PackEntity
import com.hasyame.marvelchampions.data.db.entity.PackTranslationEntity
import com.hasyame.marvelchampions.data.db.toEntity
import com.hasyame.marvelchampions.data.marvelcdb.MarvelCdbApi
import com.hasyame.marvelchampions.data.marvelcdb.MarvelCdbUrls
import com.hasyame.marvelchampions.data.marvelcdb.dto.CardDto
import com.hasyame.marvelchampions.data.marvelcdb.dto.PackDto
import com.hasyame.marvelchampions.data.marvelcdb.dto.PackMetadataDto
import com.hasyame.marvelchampions.data.seed.CardSeedSource
import com.hasyame.marvelchampions.domain.model.CardLocale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** Progress of a card refresh, for the Settings screen. */
data class CardSyncProgress(
    val step: Step,
    val locale: CardLocale? = null,
) {
    enum class Step { PACKS, DOWNLOADING_CARDS, STORING_CARDS, DONE }
}

/**
 * Owns the card cache: populating it from the bundled seed, and refreshing it
 * from MarvelCDB on demand.
 */
@Singleton
class CardDataRepository @Inject constructor(
    private val api: MarvelCdbApi,
    private val database: MarvelChampionsDatabase,
    private val seed: CardSeedSource,
    private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun isEmpty(): Boolean = withContext(ioDispatcher) {
        database.packDao().countPacks() == 0
    }

    /**
     * Fills an empty database from `assets/seed/`. No network.
     *
     * Returns false when the build has no seed, which is a normal state — CI
     * builds that way — and means the user has to run a sync.
     */
    suspend fun seedIfEmpty(): Boolean = withContext(ioDispatcher) {
        if (!isEmpty()) {
            return@withContext true
        }
        val metadata = seed.readPackMetadata().packs.associateBy { it.code }
        var seeded = false
        for (locale in CardLocale.entries) {
            val packs = seed.readPacks(locale) ?: continue
            val cards = seed.readCards(locale) ?: continue
            if (locale == CardLocale.ENGLISH || !seeded) {
                storePacks(packs, metadata, locale)
            } else {
                addPackTranslations(packs, locale)
            }
            storeCards(cards, locale)
            seeded = true
        }
        seeded
    }

    /**
     * Puts the curated pack types and waves back over what is already stored.
     *
     * Cheap: sixty-odd targeted updates over a table that never grows much, and
     * it touches no card data, so it cannot disturb a sync or a download.
     *
     * Runs on every start rather than on a version change, because a version
     * number is one more thing to keep in step and this costs less than reading
     * it would. A pack the curated file does not mention is left exactly as
     * MarvelCDB described it.
     */
    suspend fun reapplyPackCuration(): Unit = withContext(ioDispatcher) {
        if (isEmpty()) {
            return@withContext
        }
        val dao = database.packDao()
        val stored = dao.getPacks().associateBy { it.code }
        // Only what actually differs, and all of it in one transaction.
        //
        // This runs before the first screen is shown, on every launch. Writing
        // all sixty-odd packs each time meant sixty-odd separate transactions
        // to change nothing, which is a cost paid at the moment the app is
        // least able to afford it. After the release that corrects a pack this
        // finds nothing to do and writes not at all.
        val corrections = seed.readPackMetadata().packs.filter { meta ->
            val pack = stored[meta.code] ?: return@filter false
            pack.type != meta.type ||
                pack.wave != meta.wave ||
                pack.waveInferred != meta.waveInferred ||
                pack.typeManual != meta.typeManual
        }
        if (corrections.isEmpty()) {
            return@withContext
        }
        database.withTransaction {
            corrections.forEach { meta ->
                dao.applyCuration(
                    code = meta.code,
                    type = meta.type,
                    wave = meta.wave,
                    waveInferred = meta.waveInferred,
                    typeManual = meta.typeManual,
                )
            }
        }
    }

    /**
     * Refreshes both locales from MarvelCDB.
     *
     * Each locale is replaced inside a single transaction, so cancelling mid
     * way — the Settings screen offers that — leaves the previous data intact
     * rather than a half-written database.
     */
    suspend fun refreshFromNetwork(
        onProgress: (CardSyncProgress) -> Unit = {},
    ): Unit = withContext(ioDispatcher) {
        val metadata = seed.readPackMetadata().packs.associateBy { it.code }

        onProgress(CardSyncProgress(CardSyncProgress.Step.PACKS))
        val packsByLocale = CardLocale.entries.associateWith { locale ->
            api.getPacksAt(MarvelCdbUrls.packs(locale))
        }
        coroutineContext.ensureActive()
        storePacks(
            packs = packsByLocale.getValue(CardLocale.ENGLISH),
            metadata = metadata,
            locale = CardLocale.ENGLISH,
        )
        packsByLocale.forEach { (locale, packs) ->
            if (locale != CardLocale.ENGLISH) {
                addPackTranslations(packs, locale)
            }
        }

        for (locale in CardLocale.entries) {
            onProgress(CardSyncProgress(CardSyncProgress.Step.DOWNLOADING_CARDS, locale))
            val cards = api.getAllCardsAt(MarvelCdbUrls.allCards(locale))
            coroutineContext.ensureActive()

            onProgress(CardSyncProgress(CardSyncProgress.Step.STORING_CARDS, locale))
            storeCards(cards, locale)
        }
        onProgress(CardSyncProgress(CardSyncProgress.Step.DONE))
    }

    private suspend fun storePacks(
        packs: List<PackDto>,
        metadata: Map<String, PackMetadataDto>,
        locale: CardLocale,
    ) {
        val entities = packs.map { pack ->
            val meta = metadata[pack.code]
            PackEntity(
                code = pack.code,
                marvelCdbId = pack.id,
                position = pack.position,
                available = pack.available,
                known = pack.known,
                total = pack.total,
                url = pack.url,
                // A pack MarvelCDB has added since the curated file was last
                // updated lands here. Wave 0 sorts it to the top of the
                // collection screen, where it is obvious it needs curating.
                type = meta?.type ?: UNKNOWN_PACK_TYPE,
                wave = meta?.wave ?: UNCURATED_WAVE,
                waveInferred = meta?.waveInferred ?: true,
                typeManual = meta?.typeManual ?: false,
            )
        }
        val translations = packs.map {
            PackTranslationEntity(packCode = it.code, locale = locale.code, name = it.name)
        }
        database.packDao().replaceAll(entities, translations)
    }

    private suspend fun addPackTranslations(packs: List<PackDto>, locale: CardLocale) {
        database.packDao().insertTranslations(
            packs.map {
                PackTranslationEntity(packCode = it.code, locale = locale.code, name = it.name)
            },
        )
    }

    private suspend fun storeCards(cards: List<CardDto>, locale: CardLocale) {
        database.cardDao().replaceLocale(locale.code, cards.map { it.toEntity(locale) })
    }

    private companion object {
        const val UNKNOWN_PACK_TYPE = "UNKNOWN"
        const val UNCURATED_WAVE = 0
    }
}

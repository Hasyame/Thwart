package com.hasyame.marvelchampions.data.sync

import com.hasyame.marvelchampions.data.db.dao.PackDao
import com.hasyame.marvelchampions.data.db.entity.PackEntity
import com.hasyame.marvelchampions.data.marvelcdb.MarvelCdbApi
import com.hasyame.marvelchampions.data.marvelcdb.dto.PackDto
import com.hasyame.marvelchampions.data.settings.AppPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** A pack MarvelCDB has that this device does not, or does not have all of. */
data class CardUpdate(
    val code: String,
    val name: String,
    /** True when the pack is entirely new here, rather than merely grown. */
    val isNewPack: Boolean,
)

/**
 * Asks MarvelCDB, on startup, whether there are cards this device has not got.
 *
 * The pack list is the cheap question: a few kilobytes, against several
 * megabytes for the cards themselves. So the app can afford to ask every time
 * it opens, and only offers the expensive download when the answer is yes.
 *
 * Nothing is downloaded here and nothing is written. A player who says no is
 * left exactly as they were.
 */
@Singleton
class CardUpdateChecker @Inject constructor(
    private val api: MarvelCdbApi,
    private val packDao: PackDao,
    private val preferences: AppPreferences,
    private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * What is missing, or nothing at all.
     *
     * Deliberately swallows failure. This runs unbidden while somebody is
     * opening the app to play a game: no network, a captive portal in a hotel,
     * or MarvelCDB being down are not things to interrupt them with. The
     * Settings screen still has the button that says what went wrong.
     */
    suspend fun check(): List<CardUpdate> = withContext(ioDispatcher) {
        runCatching {
            findUpdates(
                remote = api.getPacks(),
                local = packDao.getPacks(),
                dismissed = preferences.dismissedPacks.first(),
            )
        }.getOrDefault(emptyList())
    }

    /** Remembers a no, so the same answer is not asked for again every launch. */
    suspend fun dismiss(updates: List<CardUpdate>) {
        val already = preferences.dismissedPacks.first()
        preferences.setDismissedPacks(already + updates.map { it.code })
    }

    /** Forgets every no, so a fresh check offers everything again. */
    suspend fun clearDismissals() {
        preferences.setDismissedPacks(emptySet())
    }

    companion object {

        /**
         * The comparison, kept pure so it can be reasoned about and tested.
         *
         * `known` is compared against `known`, both sides having come from the
         * same MarvelCDB field, so a pack that has gained cards since the last
         * refresh shows up as well as one that did not exist here at all. A
         * count of rows in the database would not do: what is stored depends on
         * which locale is in use and whether encounter cards were included.
         */
        fun findUpdates(
            remote: List<PackDto>,
            local: List<PackEntity>,
            dismissed: Set<String>,
        ): List<CardUpdate> {
            val here = local.associateBy { it.code }
            return remote
                .filter { it.code !in dismissed }
                .mapNotNull { pack ->
                    val mine = here[pack.code]
                    when {
                        mine == null -> CardUpdate(pack.code, pack.name, isNewPack = true)
                        pack.known > mine.known ->
                            CardUpdate(pack.code, pack.name, isNewPack = false)

                        else -> null
                    }
                }
                .sortedBy { it.name }
        }
    }
}

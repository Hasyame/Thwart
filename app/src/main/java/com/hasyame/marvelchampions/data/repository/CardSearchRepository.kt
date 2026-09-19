package com.hasyame.marvelchampions.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import com.hasyame.marvelchampions.data.db.dao.CardDao
import com.hasyame.marvelchampions.data.db.dao.PackDao
import com.hasyame.marvelchampions.data.db.entity.CardEntity
import com.hasyame.marvelchampions.data.db.entity.PackEntity
import com.hasyame.marvelchampions.domain.model.CardFilter
import com.hasyame.marvelchampions.domain.model.CardLocale
import com.hasyame.marvelchampions.domain.search.CardQueryBuilder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** The values offered in the filter sheet, derived from what is in the database. */
data class CardFilterOptions(
    val typeCodes: List<String> = emptyList(),
    val factionCodes: List<String> = emptyList(),
    val traits: List<String> = emptyList(),
    /** Code to printed name, so the chips can be labelled in the card language. */
    val typeNames: Map<String, String> = emptyMap(),
    val factionNames: Map<String, String> = emptyMap(),
)

@Singleton
class CardSearchRepository @Inject constructor(
    private val cardDao: CardDao,
    private val packDao: PackDao,
    private val collectionRepository: CollectionRepository,
    private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * The pack a card came out of.
     *
     * A separate lookup because the thing worth showing — whether it is the
     * Core Set, a hero pack, a scenario pack — is not in the MarvelCDB API at
     * all. It comes from the curated pack metadata held on the pack row.
     */
    fun observeSearchChanges(): Flow<Int> = cardDao.observeSearchChanges(SimpleSQLiteQuery("SELECT 1"))

    fun observeFilterOptions(locale: CardLocale): Flow<CardFilterOptions> =
        cardDao.observeCatalogueChanges(SimpleSQLiteQuery("SELECT 1"))
            .map { filterOptions(locale) }.flowOn(ioDispatcher)

    suspend fun getPack(packCode: String): PackEntity? =
        withContext(ioDispatcher) { packDao.getPack(packCode) }

    /**
     * Searches name and card text with the filters applied.
     *
     * An empty filter is not an error: it returns the first page, so the list
     * shows cards before the user has typed anything.
     */
    suspend fun search(
        filter: CardFilter,
        locale: CardLocale,
        limit: Int = DEFAULT_LIMIT,
        offset: Int = 0,
    ): List<CardEntity> = withContext(ioDispatcher) {
        val owned = if (filter.ownedOnly) collectionRepository.getOwnedCodes() else emptySet()
        val query = CardQueryBuilder.build(
            filter = filter,
            locale = locale,
            ownedPackCodes = owned,
            limit = limit,
            offset = offset,
        )
        cardDao.queryCards(SimpleSQLiteQuery(query.sql, query.args.toTypedArray()))
    }

    suspend fun getCard(code: String, locale: CardLocale): CardEntity? =
        withContext(ioDispatcher) { cardDao.getCardPreferringLocale(code, locale.code) }

    fun observeCard(code: String, locale: CardLocale): Flow<CardEntity?> =
        cardDao.observeCard(code, locale.code)

    /** The other side of a double-sided card, or a hero's alter-ego. */
    suspend fun getLinkedCard(card: CardEntity, locale: CardLocale): CardEntity? =
        withContext(ioDispatcher) {
            card.linkedToCode?.let { cardDao.getCardPreferringLocale(it, locale.code) }
        }

    suspend fun countForLocale(locale: CardLocale): Int =
        withContext(ioDispatcher) { cardDao.countForLocale(locale.code) }

    suspend fun filterOptions(locale: CardLocale): CardFilterOptions =
        withContext(ioDispatcher) {
            CardFilterOptions(
                typeCodes = cardDao.distinctTypeCodes(locale.code),
                factionCodes = cardDao.distinctFactionCodes(locale.code),
                typeNames = cardDao.distinctTypeNames(locale.code)
                    .associate { it.code to it.name },
                factionNames = cardDao.distinctFactionNames(locale.code)
                    .associate { it.code to it.name },
                // Traits are stored as the printed string, "Avenger. Gamma.",
                // so the individual traits have to be split back out.
                traits = cardDao.distinctTraitStrings(locale.code)
                    .flatMap { it.split('.') }
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .sorted(),
            )
        }

    private companion object {
        const val DEFAULT_LIMIT = 200
    }
}

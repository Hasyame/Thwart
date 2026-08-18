package com.hasyame.marvelchampions.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SupportSQLiteQuery
import com.hasyame.marvelchampions.data.db.entity.CardEntity
import kotlinx.coroutines.flow.Flow

/**
 * SQLite's default bound-variable limit is 999 and each card binds well over a
 * hundred columns, so inserts are chunked well below it.
 */
private const val INSERT_CHUNK_SIZE = 200

/** A code and the word a player actually sees for it. */
data class CodeName(
    val code: String,
    val name: String,
)

/** A card set or hero, with its name in the requested locale. */
data class CardSetSummary(
    val code: String,
    val name: String?,
    val packCode: String,
)

@Dao
interface CardDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<CardEntity>)

    @Query("DELETE FROM cards WHERE locale = :locale")
    suspend fun deleteLocale(locale: String)

    /**
     * Replaces every card of one locale.
     *
     * Delete and insert happen in one SQLite transaction, so a sync that fails
     * or is cancelled part way leaves the previous data untouched rather than a
     * half-written database.
     */
    @Transaction
    suspend fun replaceLocale(locale: String, cards: List<CardEntity>) {
        deleteLocale(locale)
        cards.chunked(INSERT_CHUNK_SIZE).forEach { insertAll(it) }
    }

    @Query("SELECT COUNT(*) FROM cards WHERE locale = :locale")
    suspend fun countForLocale(locale: String): Int

    @Query("SELECT COUNT(*) FROM cards")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM cards WHERE code = :code AND locale = :locale")
    suspend fun getCard(code: String, locale: String): CardEntity?

    /**
     * The card in the requested language, or in whatever language it exists in.
     *
     * MarvelCDB has not translated everything — several packs are only partly
     * localised — and an exact-locale lookup returns nothing at all for those.
     * That made untranslated cards vanish from decks and lists rather than
     * merely appear in English, which is much the worse failure.
     */
    @Query(
        """
        SELECT * FROM cards
        WHERE code = :code
        ORDER BY CASE WHEN locale = :locale THEN 0 ELSE 1 END
        LIMIT 1
        """,
    )
    suspend fun getCardPreferringLocale(code: String, locale: String): CardEntity?

    /**
     * Every row for a set of codes, in every language they exist in.
     *
     * Resolving a campaign's card names one code at a time was around seventy
     * round trips per load, on the path that runs after every action. One query
     * returns them all and the caller picks the language it wants.
     */
    @Query("SELECT * FROM cards WHERE code IN (:codes)")
    suspend fun getCardsByCodes(codes: List<String>): List<CardEntity>

    @Query("SELECT * FROM cards WHERE code = :code AND locale = :locale")
    fun observeCard(code: String, locale: String): Flow<CardEntity?>

    @Query(
        """
        SELECT * FROM cards
        WHERE locale = :locale
        ORDER BY packCode, position
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun getPage(locale: String, limit: Int, offset: Int): List<CardEntity>

    /**
     * Full text search.
     *
     * [matchQuery] must already be normalised and turned into an FTS
     * expression by `SearchNormalizer.toPrefixMatchQuery`.
     */
    @Query(
        """
        SELECT cards.* FROM cards
        JOIN cards_fts ON cards_fts.rowid = cards.rowid
        WHERE cards_fts MATCH :matchQuery
          AND cards.locale = :locale
        ORDER BY cards.packCode, cards.position
        LIMIT :limit
        """,
    )
    suspend fun search(matchQuery: String, locale: String, limit: Int = 200): List<CardEntity>

    /**
     * Filtered card list. The statement comes from
     * [com.hasyame.marvelchampions.domain.search.CardQueryBuilder], which is
     * the only thing allowed to construct it.
     */
    @RawQuery
    suspend fun queryCards(query: SupportSQLiteQuery): List<CardEntity>

    /** Distinct values for the filter sheet, in the current locale. */
    /**
     * The villain and main scheme sides of one scenario, in printed order.
     *
     * Two rows exist for most scheme stages — 1A carries the setup text and 1B
     * the numbers — so the caller keeps the side that actually has a threat
     * limit on it. Ordered by set position, which is the order the cards are
     * stacked in the box.
     */
    @Query(
        """
        SELECT * FROM cards
        WHERE locale = :locale
          AND cardSetCode = :cardSetCode
          AND typeCode IN ('villain', 'main_scheme')
        ORDER BY setPosition
        """,
    )
    suspend fun getScenarioSides(cardSetCode: String, locale: String): List<CardEntity>

    @Query("SELECT DISTINCT typeCode FROM cards WHERE locale = :locale ORDER BY typeCode")
    suspend fun distinctTypeCodes(locale: String): List<String>

    @Query("SELECT DISTINCT factionCode FROM cards WHERE locale = :locale ORDER BY factionCode")
    suspend fun distinctFactionCodes(locale: String): List<String>

    /**
     * The printed name for each code, in one locale.
     *
     * The filter sheet used to label its chips with the codes themselves, so a
     * French player was offered "Player_side_scheme" and "Alter_ego". The cards
     * already carry a translated name for every one of them.
     */
    @Query(
        """
        SELECT DISTINCT typeCode AS code, typeName AS name
        FROM cards
        WHERE locale = :locale AND typeCode IS NOT NULL AND typeName IS NOT NULL
        """,
    )
    suspend fun distinctTypeNames(locale: String): List<CodeName>

    @Query(
        """
        SELECT DISTINCT factionCode AS code, factionName AS name
        FROM cards
        WHERE locale = :locale AND factionCode IS NOT NULL AND factionName IS NOT NULL
        """,
    )
    suspend fun distinctFactionNames(locale: String): List<CodeName>

    @Query(
        """
        SELECT DISTINCT traits FROM cards
        WHERE locale = :locale AND traits IS NOT NULL AND traits != ''
        """,
    )
    suspend fun distinctTraitStrings(locale: String): List<String>

    /** Every card of a pack, for the "cards I am missing" view later on. */
    @Query("SELECT * FROM cards WHERE packCode = :packCode AND locale = :locale ORDER BY position")
    suspend fun getPackCards(packCode: String, locale: String): List<CardEntity>

    /**
     * Distinct card sets of one kind (`villain`, `modular`, `hero`), with their
     * localised name and owning pack.
     *
     * This is what lets the randomiser build its pools without a second curated
     * file: which scenarios and modular sets exist is already in the card data.
     */
    @Query(
        """
        SELECT cardSetCode AS code,
               MIN(cardSetName) AS name,
               MIN(packCode) AS packCode
        FROM cards
        WHERE locale = :locale
          AND cardSetTypeNameCode = :setType
          AND cardSetCode IS NOT NULL
        GROUP BY cardSetCode
        ORDER BY name
        """,
    )
    suspend fun getCardSets(setType: String, locale: String): List<CardSetSummary>

    /**
     * Villain sets you can actually sit down and play.
     *
     * A scenario is a villain set that brings a main scheme. The four Wrecking
     * Crew villains and the Marauders are villain sets without one — they are
     * played inside somebody else's scenario — so drawing them offered
     * "Bulldozer" as though it were a scenario of its own.
     */
    @Query(
        """
        SELECT cardSetCode AS code,
               MIN(cardSetName) AS name,
               MIN(packCode) AS packCode
        FROM cards
        WHERE locale = :locale
          AND cardSetTypeNameCode = 'villain'
          AND cardSetCode IS NOT NULL
          AND cardSetCode IN (
            SELECT cardSetCode FROM cards
            WHERE locale = :locale AND typeCode = 'main_scheme'
          )
        GROUP BY cardSetCode
        ORDER BY name
        """,
    )
    suspend fun getPlayableScenarios(locale: String): List<CardSetSummary>

    /**
     * The leaders of a versus game — Captain Marvel, Iron Man, She-Hulk.
     *
     * Civil War and Synthezoid Smackdown are not played as a villain but as a
     * leader plus a side, and the card database models that with its own set
     * type. Nothing else in the game uses it, so a pack that has leaders is a
     * versus pack.
     */
    @Query(
        """
        SELECT cardSetCode AS code,
               MIN(cardSetName) AS name,
               MIN(packCode) AS packCode
        FROM cards
        WHERE locale = :locale AND cardSetTypeNameCode = 'leader' AND cardSetCode IS NOT NULL
        GROUP BY cardSetCode
        ORDER BY name
        """,
    )
    suspend fun getLeaders(locale: String): List<CardSetSummary>

    /** The sides of a versus game: Resistance and Registration. */
    @Query(
        """
        SELECT cardSetCode AS code,
               MIN(cardSetName) AS name,
               MIN(packCode) AS packCode
        FROM cards
        WHERE locale = :locale AND cardSetTypeNameCode = 'main_scheme' AND cardSetCode IS NOT NULL
        GROUP BY cardSetCode
        ORDER BY name
        """,
    )
    suspend fun getVersusSides(locale: String): List<CardSetSummary>

    /** Hero identities, which are cards rather than sets. */
    @Query(
        """
        SELECT cardSetCode AS code,
               MIN(name) AS name,
               MIN(packCode) AS packCode
        FROM cards
        WHERE locale = :locale
          AND typeCode = 'hero'
          AND cardSetCode IS NOT NULL
        GROUP BY cardSetCode
        ORDER BY name
        """,
    )
    suspend fun getHeroes(locale: String): List<CardSetSummary>

    /** Cards of a set, used to resolve a scenario's encounter sets. */
    @Query(
        """
        SELECT * FROM cards
        WHERE cardSetCode = :cardSetCode AND locale = :locale
        ORDER BY setPosition, position
        """,
    )
    suspend fun getCardSet(cardSetCode: String, locale: String): List<CardEntity>

    /**
     * Image paths for the packs a player owns, for pre-caching.
     *
     * Distinct is left to the caller: the same card has a row per language and
     * they share one image.
     */
    @Query("SELECT imageSrc FROM cards WHERE packCode IN (:packCodes) AND imageSrc IS NOT NULL")
    suspend fun getImageSources(packCodes: List<String>): List<String>
}

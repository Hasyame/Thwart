package com.hasyame.marvelchampions.data.repository

import android.content.Context
import com.hasyame.marvelchampions.core.util.runCatchingCancellable
import com.hasyame.marvelchampions.data.achievements.AchievementFacts
import com.hasyame.marvelchampions.data.db.dao.CampaignDao
import com.hasyame.marvelchampions.data.db.dao.CardDao
import com.hasyame.marvelchampions.data.db.dao.PackDao
import com.hasyame.marvelchampions.data.db.dao.PlayDao
import com.hasyame.marvelchampions.data.db.entity.PlayEntity
import com.hasyame.marvelchampions.data.seed.CardSeedSource
import com.hasyame.marvelchampions.data.seed.SetNameOverrides
import com.hasyame.marvelchampions.data.settings.AppPreferences
import com.hasyame.marvelchampions.domain.achievements.AchievementDefinitions
import com.hasyame.marvelchampions.domain.achievements.AchievementDerivation
import com.hasyame.marvelchampions.domain.achievements.AchievementState
import com.hasyame.marvelchampions.domain.achievements.Catalogue
import com.hasyame.marvelchampions.domain.achievements.DefinitionsFile
import com.hasyame.marvelchampions.domain.achievements.DeriveInput
import com.hasyame.marvelchampions.domain.achievements.HeroRef
import com.hasyame.marvelchampions.domain.achievements.PlayFact
import com.hasyame.marvelchampions.domain.achievements.RunFact
import com.hasyame.marvelchampions.domain.achievements.ScenarioRef
import com.hasyame.marvelchampions.domain.achievements.Unlock
import com.hasyame.marvelchampions.domain.model.CardLocale
import com.hasyame.marvelchampions.domain.play.FearNoEvil
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The achievements: the definitions file, the catalogue the grid ranges
 * over, and the state derived from the game history.
 *
 * Nothing here is stored. The state is a pure function of the plays, the
 * campaign runs and the collection (docs/spec/achievements in the web
 * repository, the shared specification), recomputed whenever any of them
 * changes; what this class adds is only the reading of this app's records
 * into the facts the derivation wants, and the names and pictures a screen
 * puts beside the codes.
 */
@Singleton
class AchievementRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val cardDao: CardDao,
    private val packDao: PackDao,
    private val playDao: PlayDao,
    private val campaignDao: CampaignDao,
    private val campaigns: CampaignRepository,
    private val collection: CollectionRepository,
    private val seeds: CardSeedSource,
    private val setNameOverrides: SetNameOverrides,
    private val fearNoEvil: FearNoEvilCatalog,
    private val preferences: AppPreferences,
    private val ioDispatcher: CoroutineDispatcher,
) {

    /** The definitions as bundled, or why they could not be loaded. */
    sealed interface Definitions {
        data class Loaded(val file: DefinitionsFile) : Definitions
        data class Refused(val reason: String) : Definitions
    }

    @Volatile
    private var definitionsCache: Definitions? = null

    /**
     * The bundled snapshot of the web's `achievements.json`, read once per
     * process. A file whose schema or difficulty scale this build does not
     * implement is refused, and the screen says so; the rest of the app
     * does not care.
     */
    suspend fun definitions(): Definitions = withContext(ioDispatcher) {
        definitionsCache ?: runCatchingCancellable {
            val text = context.assets.open(DEFINITIONS_ASSET).bufferedReader().use { it.readText() }
            Definitions.Loaded(AchievementDefinitions.parse(text))
        }.getOrElse { Definitions.Refused(it.message ?: it.javaClass.simpleName) }
            .also { definitionsCache = it }
    }

    /**
     * What the grid ranges over: every hero card and every scenario the
     * card data knows, each with its pack. Heroes are hero cards, one per
     * code; scenarios are the scenario-rules entries the randomiser draws
     * from, plus Fear No Evil's one-off keys, which are on no database and
     * come from its template. Built from the card data, never from the
     * plays: a scenario nobody has played is still a row to fill.
     */
    suspend fun catalogue(): Catalogue = withContext(ioDispatcher) { buildCatalogue() }

    private suspend fun buildCatalogue(): Catalogue {
        val heroes = cardDao.getHeroCards().map { HeroRef(it.code, it.packCode) }.sortedBy { it.code }
        val scenarios = LinkedHashMap<String, ScenarioRef>()
        runCatchingCancellable { seeds.readScenarioRules() }.getOrNull()?.scenarios?.forEach { rule ->
            scenarios.putIfAbsent(rule.code, ScenarioRef(rule.code, rule.packCode))
        }
        val fnePack = fearNoEvil.packCode() ?: FearNoEvil.TEMPLATE_ID
        fearNoEvil.scenarios(CardLocale.ENGLISH).forEach { scenario ->
            scenarios.putIfAbsent(scenario.code, ScenarioRef(scenario.code, fnePack))
        }
        return Catalogue(heroes, scenarios.values.sortedBy { it.key })
    }

    /** The state as of now, or null when the definitions are refused. */
    suspend fun state(): AchievementState? = input()?.let(AchievementDerivation::derive)

    /** The derivation's input as of now, or null when the definitions are refused. */
    suspend fun input(): DeriveInput? = withContext(ioDispatcher) {
        val definitions = (definitions() as? Definitions.Loaded)?.file ?: return@withContext null
        input(definitions, catalogue(), collection.getOwnedCodes(), playDao.getAllPlays())
    }

    /**
     * The derivation's input, kept current: read again when a play, a run
     * or the collection changes. A screen derives from it, and derives
     * again over a filtered set of facts for a filtered grid, which is why
     * the input rather than the state is what it watches. Null while the
     * definitions are refused.
     */
    fun observeInput(): Flow<DeriveInput?> = combine(
        playDao.observePlays(),
        campaignDao.observeAchievementChanges(androidx.sqlite.db.SimpleSQLiteQuery("SELECT 1")),
        collection.observeOwnedCodes(),
        cardDao.observeCatalogueChanges(androidx.sqlite.db.SimpleSQLiteQuery("SELECT 1")),
        preferences.cardLocale,
    ) { plays, _, owned, _, _ ->
        val definitions = (definitions() as? Definitions.Loaded)?.file ?: return@combine null
        input(definitions, catalogue(), owned, plays)
    }.flowOn(ioDispatcher)

    /** The state, kept current. */
    fun observeState(): Flow<AchievementState?> = observeInput().map { it?.let(AchievementDerivation::derive) }

    /** What a game just recorded unlocked, for the toast: [before] against now. */
    suspend fun unlockedSince(before: AchievementState?): List<Unlock> {
        val after = state() ?: return emptyList()
        return AchievementDerivation.newlyUnlocked(before, after)
    }

    private suspend fun input(
        definitions: DefinitionsFile,
        catalogue: Catalogue,
        owned: Set<String>,
        plays: List<PlayEntity>,
    ): DeriveInput {
        val folded = campaigns.foldedRuns().associateBy { it.entity.id }
        // Resolved ahead, once per campaign play, since a resolution reads
        // the card database and the derivation itself must stay pure.
        val campaignKeys = plays.filter { it.campaignRunId != null && it.deletedAt == null }
            .associate { it.id to campaignKeyOf(it, folded) }
        val resolver = AchievementFacts.CampaignResolver { play -> campaignKeys[play.id] }
        val facts: List<PlayFact> = plays.mapNotNull { AchievementFacts.factOf(it, resolver) }
        val runs: List<RunFact> = folded.values.map { run ->
            AchievementFacts.runFactOf(run.entity, lost = run.state?.campaignLost == true)
        }
        return DeriveInput(
            definitions = definitions.achievements,
            definitionsVersion = definitions.definitionsVersion,
            catalogue = catalogue,
            ownedPacks = owned,
            facts = facts,
            runs = runs,
        )
    }

    /**
     * A campaign play's scenario key through its run's template
     * (data-model.md §3.1): Fear No Evil keeps its scenario id under the
     * one-off prefix, so a job played in the campaign and on its own share
     * a row; every other box resolves to the card set of the villain the
     * template names for that run. Null when nothing resolves.
     */
    private suspend fun campaignKeyOf(play: PlayEntity, folded: Map<String, CampaignRepository.FoldedRun>): String? {
        val run = folded[play.campaignRunId ?: return null] ?: return null
        if (run.entity.templateId == FearNoEvil.TEMPLATE_ID) {
            return if (FearNoEvil.isFne(play.scenarioCode)) play.scenarioCode else FearNoEvil.codeOf(play.scenarioCode)
        }
        return campaigns.scenarioSetCode(run, play.scenarioCode)
    }

    // --- names and pictures ---------------------------------------------------------

    /** What a screen puts beside the codes: names, faces and packs, in the card language. */
    data class Names(
        val heroes: Map<String, String>,
        val heroFaces: Map<String, String?>,
        val scenarios: Map<String, String>,
        val packs: Map<String, String>,
        /** Packs in release order, for grouping the grid the way the shelf is laid out. */
        val packOrder: Map<String, Int>,
    )

    suspend fun names(): Names = names(preferences.currentCardLocale())

    suspend fun names(locale: CardLocale): Names = withContext(ioDispatcher) { buildNames(locale) }

    private suspend fun buildNames(locale: CardLocale): Names {
        val heroes = cardDao.getHeroCards(locale.code)
        val overrides = setNameOverrides.forLocale(locale)
        val sets = cardDao.getCardSets(VILLAIN_SET_TYPE, locale.code)
        val scenarioNames = LinkedHashMap<String, String>()
        sets.forEach { set ->
            scenarioNames[set.code] = overrides[set.code] ?: set.name ?: set.code
        }
        fearNoEvil.scenarios(locale).forEach { scenario -> scenarioNames[scenario.code] = scenario.name }
        return Names(
            heroes = heroes.associate { it.code to (it.localName ?: it.anyName ?: it.code) },
            heroFaces = heroes.associate { it.code to (it.localImage ?: it.anyImage) },
            scenarios = scenarioNames,
            packs = collection.packNames(locale),
            packOrder = packDao.getPacks().withIndex().associate { (index, pack) -> pack.code to index },
        )
    }

    /** The first villain of a scenario's set that has a picture, in the card language or English. */
    suspend fun scenarioFace(key: String): String? = scenarioFace(key, preferences.currentCardLocale())

    suspend fun scenarioFace(key: String, locale: CardLocale): String? = withContext(ioDispatcher) {
        if (FearNoEvil.isFne(key)) {
            return@withContext null
        }
        listOf(locale, CardLocale.ENGLISH).distinct().firstNotNullOfOrNull { l ->
            cardDao.getScenarioSides(key, l.code)
                .firstOrNull { it.typeCode in VILLAIN_TYPES && it.imageSrc != null }
                ?.imageSrc
        }
    }

    companion object {
        const val DEFINITIONS_ASSET = "achievements.json"
        private const val VILLAIN_SET_TYPE = "villain"
        private val VILLAIN_TYPES = setOf("villain", "leader")
    }
}

package com.hasyame.marvelchampions.ui.achievements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.data.repository.AchievementRepository
import com.hasyame.marvelchampions.domain.achievements.AchievementDerivation
import com.hasyame.marvelchampions.domain.achievements.AchievementDetails
import com.hasyame.marvelchampions.domain.achievements.PlayFact
import com.hasyame.marvelchampions.domain.achievements.TargetKind
import com.hasyame.marvelchampions.domain.achievements.AchievementState
import com.hasyame.marvelchampions.domain.achievements.AchievementStatusKind
import com.hasyame.marvelchampions.domain.achievements.CLASSIC_ASPECTS
import com.hasyame.marvelchampions.domain.achievements.Completion
import com.hasyame.marvelchampions.domain.achievements.DeriveInput
import com.hasyame.marvelchampions.domain.achievements.DifficultyLevel
import com.hasyame.marvelchampions.domain.achievements.Tally
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** What the grid is narrowed to. Display only: no filter changes an unlock. */
data class GridFilters(
    val heroPack: String? = null,
    val scenarioPack: String? = null,
    val aspect: String? = null,
    val minLevel: DifficultyLevel = DifficultyLevel.UNKNOWN,
    /** Credit every hero at the table rather than the owner's seat only. */
    val anySeat: Boolean = true,
    /** Show a lost game as played; off, a cell with no win looks never played. */
    val showLosses: Boolean = true,
    val everyHero: Boolean = false,
)

data class PackOption(val code: String, val name: String)

/** A column of the grid: a hero card. */
data class GridHero(val code: String, val name: String, val imageSrc: String?, val packCode: String, val packName: String)

/** A row of the grid: a scenario. */
data class GridScenario(val key: String, val name: String, val packCode: String, val packName: String, val imageSrc: String? = null)

enum class CellState { NEVER, PLAYED, WON }

data class GridCell(val state: CellState, val bestLevelWon: DifficultyLevel?, val attempts: Int, val wins: Int)

data class AchievementPlayEvidence(val id: String, val date: Long, val level: DifficultyLevel, val scenario: String, val heroes: String, val won: Boolean)
data class AchievementTargetRow(val key: String, val kind: TargetKind, val name: String, val pack: String?, val completedBy: AchievementPlayEvidence?)
data class AchievementDetail(val card: AchievementCard, val targets: List<AchievementTargetRow>?, val unlock: AchievementPlayEvidence?)
data class AlbumDetail(val hero: String, val scenario: String, val plays: List<AchievementPlayEvidence>)

data class AchievementsUiState(
    val loaded: Boolean = false,
    /** Why the definitions could not be read, when they could not. */
    val refused: String? = null,
    val owned: Completion = Completion(0, 0),
    val global: Completion = Completion(0, 0),
    val unlockedCount: Int = 0,
    val total: Int = 0,
    val filters: GridFilters = GridFilters(),
    val heroPacks: List<PackOption> = emptyList(),
    val scenarioPacks: List<PackOption> = emptyList(),
    val aspects: List<String> = CLASSIC_ASPECTS + "pool",
    val columns: List<GridHero> = emptyList(),
    val rows: List<GridScenario> = emptyList(),
    /** By "scenarioKey\u0000heroCode"; a missing key is a cell never played. */
    val cells: Map<String, GridCell> = emptyMap(),
    val cards: List<AchievementCard> = emptyList(),
    val recent: List<AchievementCard> = emptyList(),
    val detail: AchievementDetail? = null,
    val albumDetail: AlbumDetail? = null,
) {
    fun cell(scenarioKey: String, heroCode: String): GridCell? = cells[scenarioKey + "\u0000" + heroCode]
}

/**
 * The achievements page: the completion, the heroes × scenarios grid, and
 * the named achievements, all read from the derivation. The grid's filters
 * on aspect and difficulty derive again over the games they keep, the
 * same way the web does it, so a filtered grid is the same computation
 * over fewer facts rather than a second one.
 */
@HiltViewModel
class AchievementsViewModel @Inject constructor(
    private val repository: AchievementRepository,
    private val presenter: AchievementPresenter,
) : ViewModel() {

    private val filters = MutableStateFlow(GridFilters())
    private sealed interface Selection {
        data class Named(val id: String) : Selection
        data class Album(val hero: String, val scenario: String) : Selection
    }
    private val selection = MutableStateFlow<Selection?>(null)

    fun showAchievement(id: String) { selection.value = Selection.Named(id) }
    fun showCell(hero: String, scenario: String) { selection.value = Selection.Album(hero, scenario) }
    fun closeDetail() { selection.value = null }

    private data class Prepared(
        val input: DeriveInput,
        val state: AchievementState,
        val names: AchievementRepository.Names,
        val cards: List<AchievementCard>,
        val faces: Map<String, String?>,
    )

    private val prepared = repository.observeInput().map { input ->
        input?.let {
            val state = AchievementDerivation.derive(it)
            val names = repository.names()
            val cards = presenter.cards(state, it.definitions, it.catalogue, names)
            val faces = it.catalogue.scenarios.associate { scenario ->
                scenario.key to repository.scenarioFace(scenario.key)
            }
            Prepared(it, state, names, cards, faces)
        }
    }.flowOn(Dispatchers.Default)

    val uiState: StateFlow<AchievementsUiState> = combine(prepared, filters, selection) { prepared, filters, selection ->
        if (prepared == null) AchievementsUiState(loaded = true, refused = "", filters = filters)
        else build(prepared, filters, selection)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AchievementsUiState())

    fun setEveryHero(value: Boolean) = filters.update { it.copy(everyHero = value) }
    fun resetFilters() { filters.value = GridFilters() }

    fun setHeroPack(code: String?) = filters.update { it.copy(heroPack = code) }
    fun setScenarioPack(code: String?) = filters.update { it.copy(scenarioPack = code) }
    fun setAspect(aspect: String?) = filters.update { it.copy(aspect = aspect) }
    fun setMinLevel(level: DifficultyLevel) = filters.update { it.copy(minLevel = level) }
    fun setAnySeat(anySeat: Boolean) = filters.update { it.copy(anySeat = anySeat) }
    fun setShowLosses(show: Boolean) = filters.update { it.copy(showLosses = show) }

    private fun build(prepared: Prepared, filters: GridFilters, selection: Selection?): AchievementsUiState {
        val input = prepared.input
        val state = prepared.state
        val names = prepared.names
        val cards = prepared.cards
        val byId = cards.associateBy { it.id }
        fun evidence(fact: PlayFact) = AchievementPlayEvidence(fact.id, fact.playedAt, fact.level,
            names.scenarios[fact.scenarioKey] ?: fact.scenarioKey,
            fact.seats.joinToString(", ") { names.heroes[it.heroCode] ?: it.heroCode }, fact.won)
        val detail = (selection as? Selection.Named)?.let { selected ->
            val card = byId[selected.id] ?: return@let null
            val definition = input.definitions.firstOrNull { it.id == selected.id } ?: return@let null
            val unlockId = state.achievements.firstOrNull { it.id == selected.id }?.unlockedByPlayId
            AchievementDetail(card, AchievementDetails.targets(input, definition)?.map { target ->
                AchievementTargetRow(target.key, target.kind, when (target.kind) {
                    TargetKind.HERO -> names.heroes[target.key] ?: target.key
                    TargetKind.SCENARIO -> names.scenarios[target.key] ?: target.key
                    TargetKind.ASPECT -> target.key
                }, target.pack?.let { names.packs[it] ?: it }, target.completedBy?.let(::evidence))
            }, input.facts.firstOrNull { it.id == unlockId }?.let(::evidence))
        }
        val albumDetail = (selection as? Selection.Album)?.let { selected ->
            AlbumDetail(names.heroes[selected.hero] ?: selected.hero,
                names.scenarios[selected.scenario] ?: selected.scenario,
                input.facts.filter { fact -> fact.scenarioKey == selected.scenario &&
                    fact.seats.any { it.heroCode == selected.hero } }
                    .sortedWith(compareByDescending<PlayFact> { it.playedAt }.thenBy { it.id }).map(::evidence))
        }

        // The grid's state: the same derivation over the games the filters keep.
        val gridState = if (filters.aspect == null && filters.minLevel == DifficultyLevel.UNKNOWN) {
            state
        } else {
            AchievementDerivation.derive(
                input.copy(
                    facts = input.facts.filter { fact ->
                        fact.level.atLeast(filters.minLevel) && (
                            filters.aspect == null ||
                                if (filters.anySeat) {
                                    fact.seats.any { filters.aspect in it.aspects }
                                } else {
                                    fact.owner?.aspects?.contains(filters.aspect) == true
                                }
                            )
                    },
                ),
            )
        }

        val packName = { code: String -> names.packs[code] ?: code }
        val packOrder = { code: String -> names.packOrder[code] ?: Int.MAX_VALUE }
        val heroes = input.catalogue.heroes
            .map { GridHero(it.code, names.heroes[it.code] ?: it.code, names.heroFaces[it.code], it.packCode, packName(it.packCode)) }
            .sortedWith(compareBy<GridHero> { packOrder(it.packCode) }.thenBy { it.code })
        val scenarios = input.catalogue.scenarios
            .map { GridScenario(it.key, names.scenarios[it.key] ?: it.key, it.packCode, packName(it.packCode), prepared.faces[it.key]) }
            .sortedWith(compareBy<GridScenario> { packOrder(it.packCode) }.thenBy { it.name })

        val cells = HashMap<String, GridCell>()
        gridState.cells.forEach { cell ->
            val tally: Tally = if (filters.anySeat) cell.anySeat else cell.owner
            val cellState = when {
                tally.attempts == 0 -> CellState.NEVER
                tally.wins > 0 -> CellState.WON
                filters.showLosses -> CellState.PLAYED
                else -> CellState.NEVER
            }
            if (cellState != CellState.NEVER) {
                cells[cell.scenarioKey + "\u0000" + cell.heroCode] = GridCell(cellState, tally.bestLevelWon, tally.attempts, tally.wins)
            }
        }

        return AchievementsUiState(
            loaded = true,
            owned = state.completion.owned,
            global = state.completion.global,
            unlockedCount = cards.count { it.unlocked },
            total = cards.size,
            filters = filters,
            heroPacks = heroes.map { it.packCode }.distinct().map { PackOption(it, packName(it)) },
            scenarioPacks = scenarios.map { it.packCode }.distinct().map { PackOption(it, packName(it)) },
            columns = heroes.filter { (filters.everyHero || it.packCode in input.ownedPacks) &&
                (filters.heroPack == null || it.packCode == filters.heroPack) },
            rows = scenarios.filter { filters.scenarioPack == null || it.packCode == filters.scenarioPack },
            cells = cells,
            cards = cards,
            recent = state.recent.mapNotNull { byId[it.id] }.take(RECENT_SHOWN),
            detail = detail,
            albumDetail = albumDetail,
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val RECENT_SHOWN = 5
    }
}

/** The achievements a status counts as earned; the same test everywhere it is shown. */
fun AchievementState.unlockedCount(): Int = achievements.count { it.status == AchievementStatusKind.UNLOCKED }

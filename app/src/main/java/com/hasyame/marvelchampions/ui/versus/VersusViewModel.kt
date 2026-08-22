package com.hasyame.marvelchampions.ui.versus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.data.db.dao.CardDao
import com.hasyame.marvelchampions.data.repository.EncounterRepository
import com.hasyame.marvelchampions.data.settings.AppPreferences
import com.hasyame.marvelchampions.domain.play.Encounter
import com.hasyame.marvelchampions.domain.play.EncounterSetup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which of the two teams a piece of state belongs to. */
enum class Team { REGISTRATION, RESISTANCE }

/** A leader or a side, as the setup screen offers it. */
data class VersusOption(val code: String, val name: String, val packCode: String = "")

/** One team's board: the leader they face and the schemes it runs. */
data class VersusBoard(
    val leader: VersusOption? = null,
    val stageOne: VersusOption? = null,
    val stageTwo: VersusOption? = null,
    val heroes: Int = 1,
    val encounter: Encounter = Encounter.startOf(EncounterSetup()),
) {
    /** Nothing can be tracked until a leader and both schemes are named. */
    val isComplete: Boolean get() = leader != null && stageOne != null && stageTwo != null
}

/**
 * How a versus game ended.
 *
 * The rulebook gives three ways to win and a tie, and which one happened
 * decides nothing by itself: the game runs until both sides have had the same
 * number of phases, so a defeat on one board can still be matched on the other.
 */
enum class VersusOutcome { REGISTRATION_WON, RESISTANCE_WON, TIE }

data class VersusUiState(
    val phase: VersusPhase = VersusPhase.SETUP,
    val sides: Map<Team, VersusOption> = emptyMap(),
    /** Per team, because a leader only pairs with sides from its own pack. */
    val leaders: Map<Team, List<VersusOption>> = emptyMap(),
    val schemesOne: Map<Team, List<VersusOption>> = emptyMap(),
    val schemesTwo: Map<Team, List<VersusOption>> = emptyMap(),
    val boards: Map<Team, VersusBoard> = mapOf(
        Team.REGISTRATION to VersusBoard(),
        Team.RESISTANCE to VersusBoard(),
    ),
    val outcome: VersusOutcome? = null,
    val isLoading: Boolean = true,
) {
    val canStart: Boolean
        get() = boards.values.all { it.isComplete }
}

enum class VersusPhase { SETUP, PLAYING, RESULT }

/**
 * A competitive Civil War game, both boards on one device.
 *
 * Each team builds a scenario and hands it to the other, so what a team faces
 * is the enemy's leader and the enemy's schemes. Two boards are tracked side by
 * side because the interesting question in this mode is which side is further
 * along, and that is unanswerable while each half lives on a different phone.
 */
@HiltViewModel
class VersusViewModel @Inject constructor(
    private val encounterRepository: EncounterRepository,
    private val cardDao: CardDao,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val state = MutableStateFlow(VersusUiState())
    val uiState: StateFlow<VersusUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            val locale = preferences.currentCardLocale()
            val allLeaders = cardDao.getLeaders(locale.code)
                .map { VersusOption(it.code, it.name ?: it.code, it.packCode.orEmpty()) }
            val sides = cardDao.getVersusSides(locale.code)
                .map { VersusOption(it.code, it.name ?: it.code, it.packCode.orEmpty()) }

            // Two sides per pack, and a game is one pack's pair facing each
            // other. Grouped first because the query orders by name and both
            // packs name their sides the same way, so taking the first two
            // returned Registration twice out of different boxes.
            val chosen = sides.groupBy { it.packCode }
                .values
                .firstOrNull { it.size >= 2 }
                ?.take(2)
                .orEmpty()
            val bySide = chosen.associateWith { encounterRepository.versusSchemes(it.code) }

            state.value = VersusUiState(
                leaders = Team.entries.associateWith { team ->
                    val side = chosen.getOrNull(team.ordinal)
                    allLeaders.filter { it.packCode == side?.packCode }
                },
                sides = mapOf(
                    Team.REGISTRATION to (chosen.getOrNull(0) ?: VersusOption("", "")),
                    Team.RESISTANCE to (chosen.getOrNull(1) ?: VersusOption("", "")),
                ),
                schemesOne = Team.entries.associateWith { team ->
                    val side = chosen.getOrNull(team.ordinal)
                    bySide[side].orEmpty().filter { it.stage.endsWith("1B") }
                        .map { VersusOption(it.code, it.name) }
                },
                schemesTwo = Team.entries.associateWith { team ->
                    val side = chosen.getOrNull(team.ordinal)
                    bySide[side].orEmpty().filter { it.stage.endsWith("2B") }
                        .map { VersusOption(it.code, it.name) }
                },
                isLoading = false,
            )
        }
    }

    private fun updateBoard(team: Team, transform: VersusBoard.() -> VersusBoard) {
        state.value = state.value.copy(
            boards = state.value.boards + (team to state.value.boards.getValue(team).transform()),
        )
    }

    fun chooseLeader(team: Team, option: VersusOption) = updateBoard(team) { copy(leader = option) }

    fun chooseStageOne(team: Team, option: VersusOption) =
        updateBoard(team) { copy(stageOne = option) }

    fun chooseStageTwo(team: Team, option: VersusOption) =
        updateBoard(team) { copy(stageTwo = option) }

    fun setHeroes(team: Team, count: Int) =
        updateBoard(team) { copy(heroes = count.coerceIn(1, 4)) }

    /** Builds both boards and moves to the table. */
    fun start() {
        val current = state.value
        if (!current.canStart) {
            return
        }
        viewModelScope.launch {
            val built = current.boards.mapValues { (_, board) ->
                val setup = encounterRepository.versusBoard(
                    leaderCode = board.leader?.code.orEmpty(),
                    // Stage 1 then stage 2, the order the board advances in.
                    schemeCodes = listOfNotNull(
                        board.stageOne?.code,
                        board.stageTwo?.code,
                    ),
                    players = board.heroes,
                )
                board.copy(encounter = Encounter.startOf(setup))
            }
            state.value = state.value.copy(boards = built, phase = VersusPhase.PLAYING)
        }
    }

    private fun updateEncounter(team: Team, transform: Encounter.() -> Encounter) =
        updateBoard(team) { copy(encounter = encounter.transform()) }

    fun damageLeader(team: Team, amount: Int) = updateEncounter(team) { damaged(amount) }

    fun changeThreat(team: Team, amount: Int) = updateEncounter(team) { threatened(amount) }

    fun advanceLeader(team: Team) = updateEncounter(team) { villainAdvanced() }

    fun advanceScheme(team: Team) = updateEncounter(team) { schemeAdvanced() }

    /**
     * Ends the round for both boards at once.
     *
     * The rulebook keeps the two sides in step: a game does not end until both
     * have played the same number of phases. Ending each board separately would
     * let them drift, which is the one thing the tie rules exist to prevent.
     */
    fun endRound() {
        state.value = state.value.copy(
            boards = state.value.boards.mapValues { (_, board) ->
                board.copy(encounter = board.encounter.roundEnded())
            },
        )
    }

    fun declare(outcome: VersusOutcome) {
        state.value = state.value.copy(outcome = outcome, phase = VersusPhase.RESULT)
    }

    fun backToSetup() {
        state.value = state.value.copy(phase = VersusPhase.SETUP, outcome = null)
    }
}

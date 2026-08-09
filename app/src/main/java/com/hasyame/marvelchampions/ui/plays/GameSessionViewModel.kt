package com.hasyame.marvelchampions.ui.plays

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.data.db.entity.PlayEntity
import com.hasyame.marvelchampions.data.db.entity.PlayHero
import com.hasyame.marvelchampions.data.repository.PlayRecorded
import com.hasyame.marvelchampions.data.repository.PlayRepository
import com.hasyame.marvelchampions.data.repository.RandomizerNames
import com.hasyame.marvelchampions.data.repository.RandomizerRepository
import com.hasyame.marvelchampions.data.repository.SchemeBriefing
import com.hasyame.marvelchampions.data.settings.AppPreferences
import com.hasyame.marvelchampions.domain.campaign.engine.TimerState
import com.hasyame.marvelchampions.domain.randomizer.Difficulty
import com.hasyame.marvelchampions.domain.randomizer.RandomizerPools
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One player at the table: who they are and which aspect they brought. */
data class SessionHero(val heroCode: String, val aspect: String)

/** Which part of the screen is showing. */
enum class SessionPhase {
    /** Choosing the game. */
    SETUP,

    /**
     * Chosen, and being laid out on the table.
     *
     * What to fetch, and the setup printed on the scenario's own main scheme.
     * The clock is deliberately not running: putting a game out takes several
     * minutes, and counting them as play time made every game longer than it
     * was.
     */
    BRIEFING,

    /** Clock running. */
    PLAYING,
}

data class GameSessionUiState(
    val phase: SessionPhase = SessionPhase.SETUP,
    val pools: RandomizerPools = RandomizerPools(),
    val names: RandomizerNames = RandomizerNames(),
    val scenarioCode: String? = null,
    /**
     * Stored as the [Difficulty] name, lower case.
     *
     * The randomiser and this screen used different words for the same thing —
     * standard/expert/heroic here, STANDARD_I/EXPERT_II there — so the same
     * difficulty produced two rows in the statistics depending on which screen
     * logged it. One vocabulary, and it is the game's.
     */
    val difficulty: String = Difficulty.STANDARD_I.name.lowercase(),
    val heroes: List<SessionHero> = emptyList(),
    /** Modular sets shuffled into the encounter deck. */
    val modularSetCodes: List<String> = emptyList(),
    val timer: TimerState = TimerState(),
    /**
     * Who takes the first turn, as an index into [heroes]. Drawn when the game
     * starts and null in solo, where the question does not arise.
     */
    val firstPlayerIndex: Int? = null,
    val elapsedMillis: Long = 0,
    /** The scenario's own main scheme and the setup printed on it. */
    val briefing: SchemeBriefing = SchemeBriefing(),
    val isLoading: Boolean = true,
    /** True from the moment a result is tapped until it has been filed. */
    val isFinishing: Boolean = false,
) {
    /** A game needs somewhere to happen and someone to play it. */
    val canStart: Boolean get() = scenarioCode != null && heroes.isNotEmpty()
}

/**
 * A game the player sets up themselves, timed by the app.
 *
 * The timer is the point. Asking someone how long a game took after the fact
 * gets a guess, and a guess is a poor thing to build years of statistics on;
 * a clock that ran while they played does not need remembering.
 */
@HiltViewModel
class GameSessionViewModel @Inject constructor(
    private val randomizerRepository: RandomizerRepository,
    private val playRepository: PlayRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val state = MutableStateFlow(GameSessionUiState())
    val uiState: StateFlow<GameSessionUiState> = state.asStateFlow()

    private var prefilled = false

    private val finished = MutableStateFlow<PlayRecorded?>(null)
    val recorded: StateFlow<PlayRecorded?> = finished.asStateFlow()

    init {
        viewModelScope.launch {
            val locale = preferences.currentCardLocale()
            // Fetched into locals first, and the state read only once they are
            // all in hand. Written as `state.value.copy(pools = load(), ...)`
            // the receiver is read before the first suspension, so anything
            // written while the pools loaded was applied to a stale snapshot
            // and lost — which is what happened to the briefing, since one
            // small query finishes long before these two.
            val pools = randomizerRepository.loadPools(locale)
            val names = randomizerRepository.loadNames(locale)
            state.value = state.value.copy(
                pools = pools,
                names = names,
                isLoading = false,
            )
        }
    }

    /**
     * Fills the setup in from a randomiser draw.
     *
     * Applied once: a configuration change must not wipe choices the player
     * has changed since arriving.
     */
    fun prefill(
        scenarioCode: String?,
        difficulty: String?,
        heroes: String?,
        modularSets: String? = null,
        autoStart: Boolean = false,
    ) {
        if (prefilled) {
            return
        }
        prefilled = true

        val parsed = heroes.orEmpty().split(",")
            .mapNotNull { pair ->
                val parts = pair.split(":")
                if (parts.size == 2 && parts[0].isNotBlank()) {
                    SessionHero(parts[0], parts[1])
                } else {
                    null
                }
            }

        state.value = state.value.copy(
            scenarioCode = scenarioCode ?: state.value.scenarioCode,
            difficulty = difficulty ?: state.value.difficulty,
            heroes = parsed.ifEmpty { state.value.heroes },
            modularSetCodes = modularSets.orEmpty().split(",")
                .filter { it.isNotBlank() }
                .ifEmpty { state.value.modularSetCodes },
        )

        // A draw arrives complete, so there is nothing to choose: it goes
        // straight to the briefing, which is the part the player needs — what
        // to fetch out of the boxes and how the scenario is laid out.
        if (autoStart) {
            start()
        }
    }

    fun setScenario(code: String) {
        state.value = state.value.copy(scenarioCode = code)
    }

    fun setDifficulty(difficulty: String) {
        state.value = state.value.copy(difficulty = difficulty)
    }

    /** Modular sets are a free choice: some scenarios take one, some several. */
    fun toggleModularSet(code: String) {
        val current = state.value.modularSetCodes
        state.value = state.value.copy(
            modularSetCodes = if (code in current) current - code else current + code,
        )
    }

    fun addHero(heroCode: String, aspect: String) {
        state.value = state.value.copy(
            heroes = state.value.heroes + SessionHero(heroCode, aspect),
        )
    }

    fun removeHero(index: Int) {
        state.value = state.value.copy(
            heroes = state.value.heroes.filterIndexed { at, _ -> at != index },
        )
    }

    /**
     * Moves to the briefing: what to fetch, and the scenario's own setup.
     *
     * The clock does not start here. It starts when the player says the table
     * is ready, which is what [beginPlaying] is for.
     */
    fun start() {
        val current = state.value
        if (!current.canStart) {
            return
        }
        state.value = current.copy(phase = SessionPhase.BRIEFING)

        val scenario = current.scenarioCode ?: return
        viewModelScope.launch {
            val locale = preferences.currentCardLocale()
            val briefing = randomizerRepository.schemeBriefing(scenario, locale)
            state.value = state.value.copy(briefing = briefing)
        }
    }

    /**
     * The table is laid out. Starts the clock, and settles who goes first.
     *
     * The rules have the players decide that between them, which at a real
     * table is a pause and a shrug. Drawn here instead, once, and only when
     * there is more than one player.
     */
    fun beginPlaying() {
        val current = state.value
        if (current.phase != SessionPhase.BRIEFING) {
            return
        }
        state.value = current.copy(
            phase = SessionPhase.PLAYING,
            timer = TimerState().start(System.currentTimeMillis()),
            firstPlayerIndex = if (current.heroes.size > 1) current.heroes.indices.random() else null,
        )
    }

    /** Back to the choices, from the briefing. Nothing has been recorded yet. */
    fun backToSetup() {
        if (state.value.phase == SessionPhase.BRIEFING) {
            state.value = state.value.copy(phase = SessionPhase.SETUP)
        }
    }

    /** Called on a ticker while the clock runs, so the display keeps up. */
    fun tick() {
        val timer = state.value.timer
        if (!timer.isRunning) {
            return
        }
        state.value = state.value.copy(elapsedMillis = timer.elapsedAt(System.currentTimeMillis()))
    }

    /**
     * Corrects the clock.
     *
     * People forget to start it, or leave it running through dinner. The
     * campaign has always allowed this; the timed session did not, so a
     * mistimed game was recorded wrong with no way back. Blocked once the
     * result is in, because by then the duration has already been captured.
     */
    fun setElapsed(millis: Long) {
        if (state.value.isFinishing) {
            return
        }
        updateTimer { it.setElapsed(millis.coerceAtLeast(0L), System.currentTimeMillis()) }
    }

    fun pause() = updateTimer { it.pause(System.currentTimeMillis()) }

    fun resume() = updateTimer { it.start(System.currentTimeMillis()) }

    private fun updateTimer(transform: (TimerState) -> TimerState) {
        val next = transform(state.value.timer)
        state.value = state.value.copy(
            timer = next,
            elapsedMillis = next.elapsedAt(System.currentTimeMillis()),
        )
    }

    /**
     * Ends the game and files it. The measured time is used, not a typed one.
     *
     * Guarded, because it was not and the cost was severe. Saving and then
     * reporting to BoardGameGeek takes a network round trip, and during it the
     * screen looked unchanged — so a second tap did not feel like a second
     * result, it felt like the first had not registered. Every tap filed
     * another play and sent another entry to BGG.
     *
     * The clock now stops on the first tap, the elapsed time is captured there
     * and then, and every later call returns immediately.
     */
    fun finish(won: Boolean) {
        val current = state.value
        if (current.isFinishing) {
            return
        }

        val scenarioCode = current.scenarioCode ?: return
        val first = current.heroes.firstOrNull() ?: return
        // Captured once: a repeat tap must not record a longer game than played.
        val elapsed = current.timer.elapsedAt(System.currentTimeMillis())

        state.value = current.copy(
            isFinishing = true,
            timer = current.timer.pause(System.currentTimeMillis()),
            elapsedMillis = elapsed,
        )

        viewModelScope.launch {
            finished.value = playRepository.record(
                PlayEntity(
                    id = playRepository.newPlayId(),
                    playedAt = System.currentTimeMillis(),
                    scenarioCode = scenarioCode,
                    scenarioName = current.names.scenarios[scenarioCode] ?: scenarioCode,
                    difficulty = current.difficulty,
                    heroCode = first.heroCode,
                    heroName = current.names.heroes[first.heroCode] ?: first.heroCode,
                    aspects = current.heroes.map { it.aspect }.distinct().joinToString(", "),
                    otherHeroes = current.heroes.drop(1)
                        .joinToString(", ") { current.names.heroes[it.heroCode] ?: it.heroCode },
                    // Every seat, each hero with its own aspect. The fields
                    // above cannot express that, which is why the statistics
                    // used to credit the first player and nobody else.
                    roster = current.heroes.map {
                        PlayHero(
                            code = it.heroCode,
                            name = current.names.heroes[it.heroCode] ?: it.heroCode,
                            aspect = it.aspect,
                        )
                    },
                    players = current.heroes.size,
                    won = won,
                    elapsedMillis = elapsed,
                    // Which modulars were in play changes a scenario enough that
                    // a win rate without them is only half the story.
                    notes = current.modularSetCodes
                        .map { current.names.modularSets[it] ?: it }
                        .sorted()
                        .joinToString(", ")
                        .let { if (it.isBlank()) "" else "Modular sets: $it" },
                ),
            )
        }
    }

    /**
     * Throws the game away without recording it anywhere.
     *
     * A game gets abandoned, misconfigured, or started by accident, and there
     * has to be a way out that does not put a false row in the history and a
     * false play on BoardGameGeek. Nothing is written, so there is nothing to
     * undo afterwards.
     */
    fun discard() {
        state.value = state.value.copy(
            phase = SessionPhase.SETUP,
            timer = TimerState(),
            firstPlayerIndex = null,
            elapsedMillis = 0,
            isFinishing = false,
        )
    }

    /** Back to setup, keeping the choices so a rematch is one tap. */
    fun reset() {
        finished.value = null
        state.value = state.value.copy(
            phase = SessionPhase.SETUP,
            timer = TimerState(),
            firstPlayerIndex = null,
            elapsedMillis = 0,
            // Cleared, or a rematch could never be finished.
            isFinishing = false,
        )
    }

    fun dismissRecorded() {
        finished.value = null
    }
}

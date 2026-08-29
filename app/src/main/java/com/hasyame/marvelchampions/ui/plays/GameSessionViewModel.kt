package com.hasyame.marvelchampions.ui.plays

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.data.db.dao.PausedGameDao
import com.hasyame.marvelchampions.data.db.entity.PausedGameEntity
import com.hasyame.marvelchampions.data.db.entity.PausedPhase
import com.hasyame.marvelchampions.data.db.entity.PlayEntity
import com.hasyame.marvelchampions.data.db.entity.PlayHero
import com.hasyame.marvelchampions.data.db.entity.SavedDeckEntity
import com.hasyame.marvelchampions.data.db.entity.VillainStep
import com.hasyame.marvelchampions.data.photos.PhotoStore
import com.hasyame.marvelchampions.data.repository.DeckRepository
import com.hasyame.marvelchampions.data.repository.EncounterRepository
import com.hasyame.marvelchampions.data.repository.PlayRecorded
import com.hasyame.marvelchampions.data.repository.PlayRepository
import com.hasyame.marvelchampions.data.repository.RandomizerNames
import com.hasyame.marvelchampions.data.repository.RandomizerRepository
import com.hasyame.marvelchampions.data.repository.SchemeBriefing
import com.hasyame.marvelchampions.data.settings.AppPreferences
import com.hasyame.marvelchampions.domain.campaign.engine.TimerState
import com.hasyame.marvelchampions.domain.play.Encounter
import com.hasyame.marvelchampions.domain.play.EncounterProgress
import com.hasyame.marvelchampions.domain.play.EncounterSetup
import com.hasyame.marvelchampions.domain.randomizer.Difficulty
import com.hasyame.marvelchampions.domain.randomizer.RandomizerPools
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * One player at the table: who they are and which aspect they brought.
 *
 * [aspect] holds every aspect of the deck, comma separated, because a deck can
 * genuinely be two of them and the statistics already split this field on
 * commas. [deckName] is what the player recognises, so it is what the table
 * list shows; it is absent when a hero was picked directly rather than through
 * a deck, which is how a randomiser draw arrives.
 */
data class SessionHero(
    val heroCode: String,
    val aspect: String,
    val deckId: String? = null,
    val deckName: String? = null,
    /**
     * The hero's name as the deck states it.
     *
     * Not looked up: the name table is built from the cards the collection can
     * field, and a deck imported from MarvelCDB can name a hero that table has
     * never heard of. Without this the seat read "61001a" and the play was
     * filed under that.
     */
    val heroName: String? = null,
)
/**
 * What is being written down while a game is put away.
 *
 * Held apart from the session because none of it is play: it is the note a
 * table leaves itself, and it only exists between pressing the button and
 * saving.
 */
data class LongBreakDraft(
    val phase: PausedPhase = PausedPhase.PLAYER,
    val villainStep: VillainStep = VillainStep.PLACE_THREAT,
    /** Hit points left, by hero code, as typed. */
    val heroLives: Map<String, String> = emptyMap(),
    val villainLife: String = "",
    val villainStage: Int = 1,
)


/**
 * The hero's name: what the deck says, then what the cards say, then the code.
 *
 * The code is the last resort and should never be seen, but showing it beats
 * showing nothing when a pack has gone missing from the collection.
 */
fun SessionHero.displayName(names: Map<String, String>): String =
    heroName ?: names[heroCode] ?: heroCode

/**
 * Where the villain stood when the game was put away.
 *
 * The table wrote down the stage and the life left on it, so the damage is the
 * difference from that stage's printed total. Without a printed total to work
 * from, the life is carried as a manual figure instead, which is what the
 * tracker already does for a scenario it cannot read.
 */
private fun PausedGameEntity.progress(setup: EncounterSetup): EncounterProgress {
    val index = (villainStage - 1).coerceIn(0, (setup.villain.size - 1).coerceAtLeast(0))
    val printed = setup.villain.getOrNull(index)?.totalFor(setup.players)
    return EncounterProgress(
        villainIndex = index,
        damage = if (printed != null) (printed - villainLife).coerceAtLeast(0) else 0,
        manualVillainHealth = if (printed == null && villainLife > 0) villainLife else null,
    )
}

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
    /**
     * The Standard set that goes with [difficulty] when it is an Expert one.
     *
     * Null while the question is unanswered, which is why an Expert difficulty
     * with no Standard chosen cannot start: it is not a setup anybody can put
     * on a table.
     */
    val standardSet: String? = null,
    /** The player's own decks, which is what a seat is chosen from. */
    val decks: List<SavedDeckEntity> = emptyList(),
    /** Modular sets shuffled into the encounter deck. */
    val modularSetCodes: List<String> = emptyList(),
    val timer: TimerState = TimerState(),
    /**
     * Photographs of the table, by file name, taken while this game runs.
     *
     * Held here until the game is filed, because the play they belong to does
     * not exist until then. A game abandoned halfway leaves its pictures on
     * disk, which the orphan sweep clears.
     */
    val photos: List<String> = emptyList(),
    /** Non-null while the game is being put away for a long break. */
    val longBreak: LongBreakDraft? = null,
    /**
     * What was written down when this game was put away, when it is one being
     * picked back up.
     *
     * Kept so the briefing can print it: the table has to rebuild the board
     * from those notes, and they are the reason the pause was worth taking.
     */
    val resumedFrom: PausedGameEntity? = null,
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
    /** Whether this player asked for counters at all. A setting, not a mode. */
    val trackEncounter: Boolean = false,
    /** The scenario's printed numbers and where the counters stand. */
    val encounter: Encounter = Encounter(),
    /**
     * Whether the screen is held awake for this game.
     *
     * On for every game the tracker runs in, because that is what a tracker is
     * for, but escapable from the play screen without leaving the game — a
     * phone that must not stay lit is a real situation and nobody should have
     * to go to Settings mid-game for it. Not remembered between games on
     * purpose: the reason it gets turned off is usually about tonight.
     */
    val keepAwake: Boolean = true,
) {
    /** A game needs somewhere to happen, someone to play it, and a legal setup. */
    val canStart: Boolean
        get() = scenarioCode != null && heroes.isNotEmpty() && difficultyIsComplete

    /**
     * False while an Expert difficulty has no Standard set chosen.
     *
     * Expert is played with a Standard set shuffled in, never on its own, so
     * until that is answered the setup describes a game that cannot happen.
     */
    val difficultyIsComplete: Boolean
        get() = !isExpertDifficulty || standardSet != null

    val isExpertDifficulty: Boolean
        get() = Difficulty.entries
            .firstOrNull { it.name.lowercase() == difficulty }
            ?.isExpert == true
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
    private val encounterRepository: EncounterRepository,
    private val preferences: AppPreferences,
    private val deckRepository: DeckRepository,
    val photoStore: PhotoStore,
    private val pausedGameDao: PausedGameDao,
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
        // Collected rather than read once: a player sent to the Decks tab
        // because they had none must find the new one waiting when they come
        // back, not an empty list until the app restarts.
        viewModelScope.launch {
            deckRepository.observeDecks().collect { decks ->
                state.value = state.value.copy(decks = decks)
            }
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
        standardSet: String? = null,
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
            // Without this an Expert draw is an incomplete setup, start()
            // refuses it, and autoStart silently drops the player on the setup
            // page having chosen nothing.
            standardSet = standardSet?.takeIf { it.isNotBlank() } ?: state.value.standardSet,
        )

        // A draw arrives complete, so there is nothing to choose: it goes
        // straight to the briefing, which is the part the player needs — what
        // to fetch out of the boxes and how the scenario is laid out.
        if (autoStart) {
            start()
        }
    }

    /**
     * Picks up a game that was put away for a long break.
     *
     * Lands on the briefing rather than straight into play. The board is in a
     * box or on a shelf, and the notes the table left themselves have to be
     * read before anybody can carry on, which is exactly what the briefing is
     * for.
     *
     * The clock comes back where it stopped, so the record still says how long
     * the game actually took rather than how long this sitting took.
     */
    fun resume(pausedId: String) {
        if (prefilled) {
            return
        }
        prefilled = true

        viewModelScope.launch {
            // There is only ever one paused game, so this reads it and checks
            // it is the one the player tapped rather than one saved since.
            val saved = pausedGameDao.current()?.takeIf { it.id == pausedId } ?: return@launch
            val locale = preferences.currentCardLocale()
            val pools = randomizerRepository.loadPools(locale)
            val names = randomizerRepository.loadNames(locale)

            // The names were stored with the heroes, because a pack can be
            // unticked between putting a game away and coming back to it, and
            // a seat reading "01001a" helps nobody.
            val heroes = saved.heroes.split(",")
                .filter { it.isNotBlank() }
                .map { entry ->
                    val parts = entry.split("|")
                    SessionHero(
                        heroCode = parts[0],
                        aspect = "",
                        heroName = parts.getOrNull(1),
                    )
                }

            val expert = Difficulty.entries
                .firstOrNull { it.name.lowercase() == saved.difficulty }
                ?.isExpert == true
            val setup = encounterRepository.setupFor(
                scenarioCode = saved.scenarioCode,
                players = heroes.size.coerceAtLeast(1),
                expert = expert,
            )

            state.value = state.value.copy(
                pools = pools,
                names = names,
                scenarioCode = saved.scenarioCode,
                difficulty = saved.difficulty,
                heroes = heroes,
                modularSetCodes = saved.modularSetCodes.split(",").filter { it.isNotBlank() },
                photos = saved.photos.split(",").filter { it.isNotBlank() },
                briefing = randomizerRepository.schemeBriefing(saved.scenarioCode, locale),
                encounter = Encounter(setup = setup, progress = saved.progress(setup)),
                trackEncounter = setup.isUsable,
                elapsedMillis = saved.elapsedMillis,
                timer = TimerState(accumulatedMillis = saved.elapsedMillis),
                resumedFrom = saved,
                phase = SessionPhase.BRIEFING,
                isLoading = false,
            )
        }
    }

    fun setScenario(code: String) {
        state.value = state.value.copy(scenarioCode = code)
    }

    fun setDifficulty(difficulty: String) {
        val isExpert = Difficulty.entries
            .firstOrNull { it.name.lowercase() == difficulty }
            ?.isExpert == true
        state.value = state.value.copy(
            difficulty = difficulty,
            // A Standard difficulty has no companion, so a leftover one from an
            // earlier Expert choice would put a set on the table that nobody
            // asked for.
            standardSet = if (isExpert) state.value.standardSet else null,
        )
    }

    /** The Standard set shuffled in alongside an Expert difficulty. */
    fun setStandardSet(standardSet: String) {
        state.value = state.value.copy(standardSet = standardSet)
    }

    /** Replaces the modular sets outright, which is what the picker returns. */
    fun setModularSets(codes: List<String>) {
        state.value = state.value.copy(modularSetCodes = codes)
    }

    /**
     * Seats a deck.
     *
     * The same deck twice is allowed on purpose: two people at one table can
     * bring the same list, and refusing it would be the app inventing a rule.
     */
    fun addDeck(deck: SavedDeckEntity) {
        state.value = state.value.copy(
            heroes = state.value.heroes + SessionHero(
                heroCode = deck.heroCode,
                aspect = DeckRepository.parseAspects(deck.aspects).joinToString(", "),
                deckId = deck.id,
                deckName = deck.name,
                heroName = deck.heroName,
            ),
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
    /** Opens the page that writes the table down before it is cleared. */
    fun beginLongBreak() {
        val current = state.value
        state.value = current.copy(
            longBreak = LongBreakDraft(
                heroLives = current.heroes.associate { it.heroCode to "" },
            ),
        )
        pause()
    }

    fun updateLongBreak(draft: LongBreakDraft) {
        state.value = state.value.copy(longBreak = draft)
    }

    fun cancelLongBreak() {
        state.value = state.value.copy(longBreak = null)
    }

    /**
     * Files the game away and leaves.
     *
     * Photographs taken during the game go with it rather than being deleted:
     * a picture of the table is the most useful thing in the record, and the
     * whole point of stopping this way is coming back to it.
     */
    fun saveLongBreak(onSaved: () -> Unit) {
        val current = state.value
        val draft = current.longBreak ?: return
        val scenarioCode = current.scenarioCode ?: return
        viewModelScope.launch {
            pausedGameDao.upsert(
                PausedGameEntity(
                    id = UUID.randomUUID().toString(),
                    savedAt = System.currentTimeMillis(),
                    scenarioCode = scenarioCode,
                    scenarioName = current.names.scenarios[scenarioCode] ?: scenarioCode,
                    difficulty = current.difficulty,
                    heroes = current.heroes.joinToString(",") {
                        "${it.heroCode}|${current.names.heroes[it.heroCode] ?: it.heroCode}"
                    },
                    modularSetCodes = current.modularSetCodes.joinToString(","),
                    elapsedMillis = current.timer.elapsedAt(System.currentTimeMillis()),
                    phase = draft.phase.name,
                    villainStep = if (draft.phase == PausedPhase.VILLAIN) {
                        draft.villainStep.name
                    } else {
                        ""
                    },
                    heroLives = draft.heroLives.entries.joinToString(",") { (code, life) ->
                        "$code|${life.ifBlank { "?" }}"
                    },
                    villainLife = draft.villainLife.toIntOrNull() ?: 0,
                    villainStage = draft.villainStage,
                    photos = current.photos.joinToString(","),
                ),
            )
            state.value = state.value.copy(longBreak = null)
            onSaved()
        }
    }

    /** Remembers a photograph just taken, in the order they were taken. */
    fun addPhoto(name: String) {
        state.value = state.value.copy(photos = state.value.photos + name)
    }

    /** Throws one away, file and all: an unwanted photo is not a record. */
    fun removePhoto(name: String) {
        state.value = state.value.copy(photos = state.value.photos - name)
        viewModelScope.launch { photoStore.delete(name) }
    }

    fun start() {
        val current = state.value
        if (!current.canStart) {
            return
        }
        state.value = current.copy(phase = SessionPhase.BRIEFING)

        val scenario = current.scenarioCode ?: return
        viewModelScope.launch {
            val locale = preferences.currentCardLocale()
            val tracking = preferences.trackEncounter.first()
            val briefing = randomizerRepository.schemeBriefing(scenario, locale)
            // Read into locals before touching the state: the same stale-read
            // trap the pools above are commented for.
            val setup = if (tracking) {
                encounterRepository.setupFor(
                    scenarioCode = scenario,
                    players = current.heroes.size.coerceAtLeast(1),
                    expert = current.isExpertDifficulty,
                )
            } else {
                EncounterSetup()
            }
            state.value = state.value.copy(
                briefing = briefing,
                trackEncounter = tracking,
                encounter = Encounter.startOf(setup),
            )
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

    fun damageVillain(amount: Int) = updateEncounter { damaged(amount) }

    fun changeThreat(amount: Int) = updateEncounter { threatened(amount) }

    /** The acceleration goes on the main scheme. */
    fun endRound() = updateEncounter { roundEnded() }

    fun advanceVillain() = updateEncounter { villainAdvanced() }

    fun advanceScheme() = updateEncounter { schemeAdvanced() }

    /** For the handful of cards that print a star where the number goes. */
    fun setManualVillainHealth(health: Int?) =
        updateEncounter { withManualVillainHealth(health) }

    fun setManualSchemeLimit(limit: Int?) = updateEncounter { withManualSchemeLimit(limit) }

    fun setKeepAwake(keep: Boolean) {
        state.value = state.value.copy(keepAwake = keep)
    }

    private inline fun updateEncounter(change: Encounter.() -> Encounter) {
        val current = state.value
        state.value = current.copy(encounter = current.encounter.change())
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
                    photos = current.photos.joinToString(","),
                    playedAt = System.currentTimeMillis(),
                    scenarioCode = scenarioCode,
                    scenarioName = current.names.scenarios[scenarioCode] ?: scenarioCode,
                    difficulty = current.difficulty,
                    standardSet = current.standardSet.orEmpty(),
                    heroCode = first.heroCode,
                    heroName = first.displayName(current.names.heroes),
                    aspects = current.heroes.map { it.aspect }.distinct().joinToString(", "),
                    otherHeroes = current.heroes.drop(1)
                        .joinToString(", ") { current.names.heroes[it.heroCode] ?: it.heroCode },
                    // Every seat, each hero with its own aspect. The fields
                    // above cannot express that, which is why the statistics
                    // used to credit the first player and nobody else.
                    roster = current.heroes.map {
                        PlayHero(
                            code = it.heroCode,
                            name = it.displayName(current.names.heroes),
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

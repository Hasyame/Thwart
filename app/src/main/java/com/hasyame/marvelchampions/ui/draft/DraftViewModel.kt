package com.hasyame.marvelchampions.ui.draft

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.data.repository.DraftHero
import com.hasyame.marvelchampions.data.repository.DraftOutcome
import com.hasyame.marvelchampions.data.repository.DraftRepository
import com.hasyame.marvelchampions.data.settings.AppPreferences
import com.hasyame.marvelchampions.domain.deckbuilder.DeckProblem
import com.hasyame.marvelchampions.domain.draft.DraftCard
import com.hasyame.marvelchampions.domain.draft.DraftContext
import com.hasyame.marvelchampions.domain.draft.DraftEngine
import com.hasyame.marvelchampions.domain.draft.DraftNaming
import com.hasyame.marvelchampions.domain.draft.DraftPhase
import com.hasyame.marvelchampions.domain.draft.DraftPlayer
import com.hasyame.marvelchampions.domain.draft.DraftRules
import com.hasyame.marvelchampions.domain.draft.DraftSettings
import com.hasyame.marvelchampions.domain.draft.DraftState
import com.hasyame.marvelchampions.domain.draft.IdentityMode
import com.hasyame.marvelchampions.domain.draft.SealedEngine
import com.hasyame.marvelchampions.domain.model.CardLocale
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/** Why the draft cannot go on from where it is. */
sealed interface DraftMessage {
    /** The collection holds no identity to draft. */
    data object NoHeroes : DraftMessage

    /** The shelf cannot fill the decks asked for; sizes have to come down. */
    data class Shortfalls(val shortfalls: List<DraftEngine.Shortfall>) : DraftMessage

    /** A deck the standard validation refused at the end: a bug, said plainly. */
    data class Illegal(val playerIndex: Int, val problems: List<DeckProblem>) : DraftMessage
}

/** One title in the deck being drafted, with how many copies are in. */
data class DeckLine(val card: DraftCard, val count: Int, val signature: Boolean)

data class DraftUiState(
    val packNames: Map<String, String> = emptyMap(),
    /** Null until the saved session, or its absence, has been read. */
    val draft: DraftState? = null,
    val isLoading: Boolean = true,
    /** The identities on the shelf, with their rules, for the identity page. */
    val heroes: List<DraftHero> = emptyList(),
    val poolAspectAvailable: Boolean = false,
    /** The pack on the table, resolved from its codes. */
    val offer: List<DraftCard> = emptyList(),
    /** The cards of the pack the current player may take; the rest are shown greyed. */
    val takeable: Set<String> = emptySet(),
    /** Packs the current player has opened, this one included, and how many they get in all. */
    val packsOpened: Int = 0,
    val packsTotal: Int = 0,
    /** The current player's deck so far: the identity's own cards, then the picks. */
    val deck: List<DeckLine> = emptyList(),
    val message: DraftMessage? = null,
    val isSaving: Boolean = false,
    /** The decks written at the end, for the screen to leave on. */
    val savedDeckIds: List<String>? = null,
) {
    val phase: DraftPhase get() = draft?.phase ?: DraftPhase.SETUP

    val currentPlayer: DraftPlayer? get() = draft?.takeIf { it.players.isNotEmpty() }?.currentPlayer

    fun hero(code: String?): DraftHero? = heroes.firstOrNull { it.card.code == code }
}

/**
 * The draft, from the settings page to the saved decks. Every change is
 * written to the session row before it reaches the screen, so the state on
 * screen is always the state on disk, and a rotation or a week away come
 * back to the same table.
 */
@HiltViewModel
class DraftViewModel @Inject constructor(
    private val repository: DraftRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val state = MutableStateFlow(DraftUiState())
    val uiState: StateFlow<DraftUiState> = state.asStateFlow()

    /** The card data, built once identities exist and kept for the draft's life. */
    private var context: DraftContext? = null
    private val persistence = Mutex()

    init {
        viewModelScope.launch {
            val locale = preferences.currentCardLocale()
            val heroes = repository.ownedHeroes(locale)
            val saved = repository.currentSession()
            val collection = saved?.collection ?: repository.collection()
            val packNames = repository.packNames(locale)
            state.update {
                it.copy(
                    draft = (saved ?: DraftState()).copy(collection = collection),
                    packNames = packNames,
                    heroes = heroes,
                    poolAspectAvailable = repository.poolAspectAvailable(),
                    isLoading = false,
                    message = DraftMessage.NoHeroes.takeIf { heroes.isEmpty() },
                )
            }
            if (saved != null && saved.phase >= DraftPhase.PICK) {
                ensureContext(saved, locale)
                resolveTable(saved)
            }
        }
    }

    // --- setup ---------------------------------------------------------------------

    fun setSealed(on: Boolean) = updateSettings { it.copy(sealed = on) }

    fun setPackQuantity(code: String, quantity: Int) {
        val draft = state.value.draft ?: return
        if (draft.phase != DraftPhase.IDENTITY) return
        commit(draft.copy(collection = draft.collection.orEmpty() + (code to quantity.coerceIn(0, 99))))
        context = null
    }

    fun selectSealed(code: String, add: Boolean) {
        val draft = state.value.draft ?: return
        val ctx = context ?: return
        val next = SealedEngine.select(draft, code, add, ctx)
        commit(next)
        resolveTable(next)
    }

    fun openBooster() {
        val draft = state.value.draft ?: return
        val next = SealedEngine.openBooster(draft)
        commit(next)
        resolveTable(next)
    }

    fun buildSealedDeck() {
        val draft = state.value.draft ?: return
        val next = SealedEngine.buildDeck(draft)
        commit(next)
        resolveTable(next)
    }

    fun openAllBoosters() {
        val draft = state.value.draft ?: return
        val next = SealedEngine.openAll(draft)
        commit(next)
        resolveTable(next)
    }

    fun confirmSealed() {
        val draft = state.value.draft ?: return
        if (!draft.currentPlayer.isFull) return
        val next = if (draft.current + 1 < draft.players.size) draft.copy(current = draft.current + 1)
        else draft.copy(phase = DraftPhase.FINISH)
        commit(next)
        resolveTable(next)
        if (next.phase == DraftPhase.FINISH) proposeNames(next)
    }

    fun setPlayers(count: Int) = updateSettings { it.copy(players = count.coerceIn(DraftSettings.MIN_PLAYERS, DraftSettings.MAX_PLAYERS)) }

    fun setSynergyOnly(on: Boolean) = updateSettings { it.copy(synergyOnly = on) }

    fun setIdentityMode(mode: IdentityMode) = updateSettings { it.copy(identityMode = mode) }

    fun setOfferSize(size: Int) = updateSettings { it.copy(offerSize = size.coerceIn(DraftSettings.MIN_OFFER_SIZE, DraftSettings.MAX_OFFER_SIZE)) }

    private fun updateSettings(change: (DraftSettings) -> DraftSettings) {
        val draft = state.value.draft ?: return
        // Settings are not worth a row until the draft begins: nothing to
        // resume yet, and an abandoned settings page should not appear on
        // the Play screen as a draft in progress.
        state.update { it.copy(draft = draft.copy(settings = change(draft.settings))) }
    }

    /** From the settings to the first player's identity page. */
    fun beginIdentities() {
        val draft = state.value.draft ?: return
        val players = (0 until draft.settings.players).map { DraftPlayer(index = it) }
        val begun = draft.copy(
            players = players,
            phase = DraftPhase.IDENTITY,
            current = 0,
            seed = System.currentTimeMillis(),
        )
        commit(openIdentity(begun))
    }

    /** Applies the identity mode for the current player: a draw, five draws, or nothing. */
    private fun openIdentity(draft: DraftState): DraftState {
        val owned = state.value.heroes.map { it.card.code }
        val player = draft.currentPlayer
        return when (draft.settings.identityMode) {
            IdentityMode.RANDOM -> {
                val code = DraftEngine.randomHero(draft, owned)
                withHero(draft.copy(rolls = draft.rolls + 1), code)
            }
            IdentityMode.RANDOM_OF_FIVE -> {
                val choices = DraftEngine.randomHeroChoices(draft, owned)
                draft.copy(rolls = draft.rolls + 1).updatePlayer(player.copy(heroChoices = choices))
            }
            IdentityMode.CHOICE -> draft
        }
    }

    // --- identities ----------------------------------------------------------------

    fun chooseHero(code: String) {
        val draft = state.value.draft ?: return
        commit(withHero(draft, code))
    }

    /** Another draw, in the random modes. */
    fun drawAgain() {
        val draft = state.value.draft ?: return
        val cleared = draft.updatePlayer(draft.currentPlayer.copy(heroCode = null, heroName = "", heroSetCode = null, aspects = emptyList(), signature = emptyMap(), heroChoices = emptyList()))
        commit(openIdentity(cleared))
    }

    fun toggleAspect(aspect: String) {
        val draft = state.value.draft ?: return
        val player = draft.currentPlayer
        val rules = state.value.hero(player.heroCode)?.rules
        val count = rules?.aspectCount ?: 1
        val chosen = when {
            aspect in player.aspects -> player.aspects - aspect
            count == 1 -> listOf(aspect)
            player.aspects.size < count -> player.aspects + aspect
            else -> player.aspects
        }
        commit(draft.updatePlayer(player.copy(aspects = chosen)))
    }

    fun drawAspects() {
        val draft = state.value.draft ?: return
        val player = draft.currentPlayer
        val rules = state.value.hero(player.heroCode)?.rules
        val drawn = DraftEngine.randomAspects(draft, rules, state.value.poolAspectAvailable)
        commit(draft.copy(rolls = draft.rolls + 1).updatePlayer(player.copy(aspects = drawn)))
    }

    fun setDeckSize(size: Int) {
        val draft = state.value.draft ?: return
        commit(draft.updatePlayer(draft.currentPlayer.copy(deckSize = size.coerceIn(DraftRules.MIN_DECK_SIZE, DraftRules.MAX_DECK_SIZE))))
    }

    /**
     * The current player's identity is settled: on to the next player's,
     * or, after the last, to the table, once the shelf has been checked
     * against every deck asked for.
     */
    fun confirmIdentity() {
        val draft = state.value.draft ?: return
        if (!draft.currentPlayer.isReady) {
            return
        }
        if (draft.current + 1 < draft.players.size) {
            commit(openIdentity(draft.copy(current = draft.current + 1)))
            return
        }
        viewModelScope.launch {
            val locale = preferences.currentCardLocale()
            val ctx = ensureContext(draft, locale, rebuild = true)
            // The shelf as it stands today: what the draft will draw down.
            val stocked = draft.copy(stock = ctx.initialStock)
            val shortfalls = DraftEngine.shortfalls(stocked, ctx)
            if (shortfalls.isNotEmpty()) {
                state.update { it.copy(message = DraftMessage.Shortfalls(shortfalls)) }
                return@launch
            }
            val started = if (draft.settings.sealed) SealedEngine.deal(stocked, ctx) else DraftEngine.start(stocked, ctx)
            if (draft.settings.sealed) {
                val shortages = started.sealedPools.mapIndexedNotNull { i, pool ->
                    if (pool.size < SealedEngine.POOL_SIZE) DraftEngine.Shortfall(i, SealedEngine.POOL_SIZE, pool.size) else null
                }
                if (shortages.isNotEmpty()) {
                    state.update { it.copy(message = DraftMessage.Shortfalls(shortages)) }
                    return@launch
                }
            }
            commit(started)
            resolveTable(started)
        }
    }

    /** Back to a player's identity page from the shortfall message, to lower a size. */
    fun dismissMessage() = state.update { it.copy(message = null) }

    /** From the shortfall message: go back to the named player's page. */
    fun reviseIdentity(playerIndex: Int) {
        val draft = state.value.draft ?: return
        state.update { it.copy(message = null) }
        commit(draft.copy(current = playerIndex, phase = DraftPhase.IDENTITY))
    }

    // --- the table ------------------------------------------------------------------

    fun pick(canonicalCode: String) {
        val draft = state.value.draft ?: return
        val ctx = context ?: return
        val next = DraftEngine.pick(draft, canonicalCode, ctx)
        commit(next)
        resolveTable(next)
        if (next.phase == DraftPhase.FINISH) {
            proposeNames(next)
        }
    }

    /** The pack holds nothing this deck may take: it goes back, the next one opens. */
    fun skipPack() {
        val draft = state.value.draft ?: return
        val ctx = context ?: return
        val next = DraftEngine.skipPack(draft, ctx)
        commit(next)
        resolveTable(next)
        if (next.phase == DraftPhase.FINISH) {
            proposeNames(next)
        }
    }

    /** No pack could be built for this player; their deck stops short. */
    fun skipCurrent() {
        val draft = state.value.draft ?: return
        val ctx = context ?: return
        val next = DraftEngine.skipCurrent(draft, ctx)
        commit(next)
        resolveTable(next)
        if (next.phase == DraftPhase.FINISH) {
            proposeNames(next)
        }
    }

    // --- the end ---------------------------------------------------------------------

    fun setDeckName(playerIndex: Int, name: String) {
        val draft = state.value.draft ?: return
        val players = draft.players.toMutableList()
        players[playerIndex] = players[playerIndex].copy(deckName = name)
        commit(draft.copy(players = players))
    }

    fun reviseSealed() {
        val draft = state.value.draft ?: return
        if (!draft.settings.sealed || draft.phase != DraftPhase.FINISH) return
        val next = draft.copy(phase = DraftPhase.PICK, current = 0)
        commit(next)
        resolveTable(next)
    }

    fun finish() {
        if (state.value.isSaving || state.value.savedDeckIds != null) return
        val draft = state.value.draft ?: return
        val ctx = context ?: return
        state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (val outcome = persistence.withLock { repository.finish(draft, ctx, preferences.currentCardLocale()) }) {
                is DraftOutcome.Saved -> state.update { it.copy(isSaving = false, savedDeckIds = outcome.deckIds) }
                is DraftOutcome.Illegal -> state.update {
                    it.copy(isSaving = false, message = DraftMessage.Illegal(outcome.playerIndex, outcome.problems))
                }
            }
        }
    }

    fun abandon() {
        viewModelScope.launch {
            persistence.withLock { repository.clear() }
            context = null
            val collection = repository.collection()
            state.update { it.copy(draft = DraftState(collection = collection), offer = emptyList(), deck = emptyList(), message = null, savedDeckIds = null) }
        }
    }

    // --- plumbing ------------------------------------------------------------------

    private fun withHero(draft: DraftState, code: String?): DraftState {
        val hero = state.value.hero(code) ?: return draft
        val player = draft.currentPlayer
        return draft.updatePlayer(
            player.copy(
                heroCode = hero.card.code,
                heroName = hero.card.name,
                heroSetCode = hero.rules.heroSetCode,
                signature = hero.rules.requiredCards,
                // Adam Warlock's four are not a choice; anyone else starts blank.
                aspects = DraftEngine.imposedAspects(hero.rules).orEmpty(),
            ),
        )
    }

    private fun DraftState.updatePlayer(player: DraftPlayer): DraftState {
        val players = this.players.toMutableList()
        players[player.index] = player
        return copy(players = players)
    }

    /** Writes the state down, then shows it. */
    private fun commit(draft: DraftState) {
        if (state.value.isSaving || state.value.savedDeckIds != null) return
        state.update { it.copy(draft = draft) }
        viewModelScope.launch { persistence.withLock { repository.save(draft) } }
    }

    private suspend fun ensureContext(draft: DraftState, locale: CardLocale, rebuild: Boolean = false): DraftContext {
        val existing = context
        if (existing != null && !rebuild) {
            return existing
        }
        return repository.context(draft, locale).also { context = it }
    }

    /** The offer and the deck as cards, for the screen. */
    private fun resolveTable(draft: DraftState) {
        val ctx = context ?: return
        val player = draft.players.getOrNull(draft.current)
        val signature = player?.heroCode?.let { ctx.signatureCards[it] }.orEmpty()
        val own = player?.signature.orEmpty().mapNotNull { (code, count) ->
            signature[code]?.let { DeckLine(it, count, signature = true) }
        }
        val picked = player?.picks.orEmpty()
            .groupingBy { it }.eachCount()
            .mapNotNull { (code, count) -> ctx.pool[code]?.let { DeckLine(it, count, signature = false) } }
        val left = draft.packsOf(draft.current).size
        val opened = player?.picks?.size ?: 0
        state.update {
            it.copy(
                offer = (if (draft.settings.sealed) draft.sealedPools.getOrNull(draft.current).orEmpty().distinct() else draft.offer)
                    .mapNotNull { code -> ctx.pool[code] },
                takeable = if (draft.settings.sealed) {
                    draft.sealedPools.getOrNull(draft.current).orEmpty().filter { code ->
                        SealedEngine.select(draft, code, true, ctx) != draft
                    }.toSet()
                } else draft.offer.filter { code -> DraftEngine.takeable(draft, code, ctx) }.toSet(),
                // The pack open now counts as opened; what waits after it
                // is what was built, which can be fewer than the picks left.
                packsOpened = opened + 1,
                packsTotal = opened + left,
                deck = own + picked,
            )
        }
    }

    /** Default names for the finish page, each avoiding the ones before it. */
    private fun proposeNames(draft: DraftState) {
        viewModelScope.launch {
            val taken = repository.takenDeckNames().toMutableList()
            val current = state.value.draft ?: return@launch
            if (current.phase != DraftPhase.FINISH) return@launch
            val players = current.players.map { player ->
                val rules = state.value.hero(player.heroCode)?.rules
                val prefix = if (draft.settings.sealed) "SEALED-" else "DRAFT-"
                val name = DraftNaming.defaultName(player.heroName, player.aspects, rules,
                    taken.map { it.replaceFirst(prefix, "DRAFT-") }).replaceFirst("DRAFT-", prefix)
                taken += name
                player.copy(deckName = player.deckName ?: name)
            }
            commit(current.copy(players = players))
        }
    }
}

package com.hasyame.marvelchampions.domain.draft

import kotlin.random.Random

/** Physical copies are reserved round-robin, including cards left out of the decks. */
object SealedEngine {
    const val POOL_SIZE = 60
    const val BOOSTER_SIZE = 10
    const val BOOSTER_COUNT = POOL_SIZE / BOOSTER_SIZE

    fun openedBoosters(state: DraftState): Int = state.sealedOpened.getOrNull(state.current) ?: BOOSTER_COUNT
    fun isBuilding(state: DraftState): Boolean = state.sealedBuilding.getOrNull(state.current) ?: true

    fun buildDeck(state: DraftState): DraftState {
        if (state.phase != DraftPhase.PICK || !state.settings.sealed || openedBoosters(state) < BOOSTER_COUNT) return state
        return state.copy(sealedBuilding = state.players.indices.map { i ->
            i == state.current || (state.sealedBuilding.getOrNull(i) ?: true)
        })
    }

    fun openBooster(state: DraftState): DraftState {
        if (state.phase != DraftPhase.PICK || !state.settings.sealed || openedBoosters(state) >= BOOSTER_COUNT) return state
        return state.copy(sealedOpened = state.players.indices.map { i ->
            if (i == state.current) openedBoosters(state) + 1 else state.sealedOpened.getOrNull(i) ?: BOOSTER_COUNT
        })
    }

    fun openAll(state: DraftState): DraftState {
        var opened = state
        repeat(BOOSTER_COUNT) { opened = openBooster(opened) }
        return buildDeck(opened)
    }

    fun deal(state: DraftState, context: DraftContext): DraftState {
        val stock = state.stock.toMutableMap()
        val pools = state.players.map { mutableListOf<String>() }
        val random = Random(state.seed)
        val eligible = state.players.map { player ->
            DraftEngine.playerPool(state, player, context).filter { DraftEngine.canTake(player, it, context) }
        }
        repeat(POOL_SIZE) {
            state.players.forEachIndexed { index, player ->
                val choices = eligible[index].flatMap { card ->
                    val code = card.canonicalCode
                    val held = pools[index].count { it == code }
                    val available = minOf(stock[code] ?: 0, DraftEngine.copyLimit(player, card, context) - held)
                    List(available.coerceAtLeast(0)) { code }
                }
                choices.randomOrNull(random)?.let { code ->
                    pools[index].add(code)
                    stock[code] = stock.getValue(code) - 1
                }
            }
        }
        return state.copy(stock = stock, sealedPools = pools, sealedOpened = state.players.map { 0 },
            sealedBuilding = state.players.map { false }, phase = DraftPhase.PICK, current = 0)
    }

    fun select(state: DraftState, code: String, add: Boolean, context: DraftContext): DraftState {
        if (state.phase != DraftPhase.PICK || !state.settings.sealed) return state
        if (openedBoosters(state) < BOOSTER_COUNT || !isBuilding(state)) return state
        val player = state.currentPlayer
        val picks = player.picks.toMutableList()
        if (add) {
            val card = context.pool[code] ?: return state
            val available = state.sealedPools.getOrNull(state.current).orEmpty().count { it == code }
            if (player.isFull || picks.count { it == code } >= available || !DraftEngine.canTake(player, card, context)) return state
            picks.add(code)
        } else if (!picks.remove(code)) return state
        return state.copy(players = state.players.mapIndexed { index, p ->
            if (index == state.current) p.copy(picks = picks) else p
        })
    }
}

package com.hasyame.marvelchampions.data.repository

import com.hasyame.marvelchampions.data.db.dao.CardDao
import com.hasyame.marvelchampions.data.db.dao.RandomizerHistoryDao
import com.hasyame.marvelchampions.data.db.entity.RandomizerHistoryEntity
import com.hasyame.marvelchampions.data.seed.CardSeedSource
import com.hasyame.marvelchampions.data.seed.SetNameOverrides
import com.hasyame.marvelchampions.domain.campaign.SchemeSetup
import com.hasyame.marvelchampions.domain.model.CardLocale
import com.hasyame.marvelchampions.domain.randomizer.Difficulty
import com.hasyame.marvelchampions.domain.randomizer.HeroAssignment
import com.hasyame.marvelchampions.domain.randomizer.HeroRef
import com.hasyame.marvelchampions.domain.randomizer.RandomizerDraw
import com.hasyame.marvelchampions.domain.randomizer.RandomizerPools
import com.hasyame.marvelchampions.domain.randomizer.ScenarioRule
import com.hasyame.marvelchampions.domain.randomizer.SetRef
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A versus game: who you face, and which side you are on. */
private data class VersusScenario(
    val code: String,
    val packCode: String,
    val name: String,
)

/** Display names for the codes a draw contains, in the user's card language. */
data class RandomizerNames(
    val scenarios: Map<String, String> = emptyMap(),
    val modularSets: Map<String, String> = emptyMap(),
    val heroes: Map<String, String> = emptyMap(),
    /** Pack code to its name, so a picker can say where a scenario came from. */
    val packs: Map<String, String> = emptyMap(),
)

/**
 * A scenario's first main scheme, and the setup printed on it.
 *
 * [steps] is empty for the two scenarios that keep their setup in the rules
 * insert, and for any scenario the card database has nothing for.
 */
data class SchemeBriefing(
    val schemeName: String? = null,
    val steps: List<String> = emptyList(),
)

@Singleton
class RandomizerRepository @Inject constructor(
    private val cardDao: CardDao,
    private val historyDao: RandomizerHistoryDao,
    private val collectionRepository: CollectionRepository,
    private val seed: CardSeedSource,
    private val setNameOverrides: SetNameOverrides,
    private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Builds the draw pools from the packs the user owns.
     *
     * Which scenarios and modular sets exist is derived from the card database
     * rather than curated — only the relationship between them needs a file.
     */
    suspend fun loadPools(locale: CardLocale): RandomizerPools = withContext(ioDispatcher) {
        val owned = collectionRepository.getOwnedCodes()
        // Dropped from the pool rather than filtered per draw: a scenario the
        // player has not got is not a scenario, so it should be missing from
        // My own setup and the pickers too, not only from the roll.
        val missingScenarios = collectionRepository.getExcludedScenarios()
        val versus = versusScenarios(locale)
        val scenarios = cardDao.getPlayableScenarios(locale.code)
        val modulars = cardDao.getCardSets(MODULAR_SET, locale.code)
        val heroes = cardDao.getHeroes(locale.code)

        RandomizerPools(
            // The card database first, then the scenarios only the boxes know
            // about. Civil War is a whole campaign box the randomiser would
            // otherwise pretend does not exist.
            scenarios = scenarios
                .filter { it.packCode in owned && it.code !in missingScenarios }
                .map { SetRef(it.code, it.packCode) } +
                versus
                    .filter { it.packCode in owned && it.code !in missingScenarios }
                    .map { SetRef(it.code, it.packCode) },
            modularSets = modulars.filter { it.packCode in owned }
                .map { SetRef(it.code, it.packCode) },
            heroes = heroes.filter { it.packCode in owned }
                .map { HeroRef(it.code, it.packCode) },
            // Aspects are the primary factions. 'basic' is not an aspect you
            // choose, and 'pool' is filtered per hero by the randomiser itself.
            //
            // 'Pool is also a pack: its cards came with Deadpool, so without
            // that hero there is no such aspect to build a deck in.
            aspects = ASPECTS.filter { it !in ASPECT_PACKS || ASPECT_PACKS[it] in owned },
            // Each difficulty is a set of encounter cards that came in a box.
            // Core is assumed rather than checked: without it there is no game
            // to set a difficulty for, and offering nothing would be worse than
            // offering the two levels everybody has.
            difficulties = Difficulty.entries.filter {
                it.packCode == "core" || it.packCode in owned
            },
        )
    }

    /**
     * Modular sets the user has told the collection they cannot field.
     *
     * A flow rather than a one-shot read: the collection screen is a tap away
     * from the draw, and coming back to a stale pool would be a bug the player
     * could see.
     */
    fun observeExcludedModularSets(): Flow<Set<String>> =
        collectionRepository.observeExcludedModularSets()

    suspend fun getExcludedModularSets(): Set<String> =
        collectionRepository.getExcludedModularSets()

    /** The packs owned, so a draw can be rebuilt when the collection changes. */
    fun observeOwnedPackCodes(): Flow<Set<String>> =
        collectionRepository.observeOwnedCodes()

    /** Scenarios the collection says are missing, for the same reason. */
    fun observeExcludedScenarios(): Flow<Set<String>> =
        collectionRepository.observeExcludedScenarios()

    suspend fun loadRules(): Map<String, ScenarioRule> = withContext(ioDispatcher) {
        // Versus packs own their modular sets, and their sets are illegal
        // anywhere else. Which packs those are is read off the cards — a pack
        // with leaders in it is a versus pack — rather than being a list
        // somebody has to remember to update.
        val versus = versusScenarios(CardLocale.ENGLISH)
        val restricted = versus.map { it.packCode }.distinct()

        val fromCards = seed.readScenarioRules().scenarios.associate { dto ->
            dto.code to ScenarioRule(
                code = dto.code,
                packCode = dto.packCode,
                modularCount = dto.modularCount,
                mandatoryModulars = dto.mandatoryModulars,
                recommendedModulars = dto.recommendedModulars,
                needsReview = dto.needsReview,
                modularPacks = if (dto.packCode in restricted) restricted else emptyList(),
            )
        }

        val fromVersus = versus.associate { pair ->
            pair.code to ScenarioRule(
                code = pair.code,
                packCode = pair.packCode,
                // Three or four, decided at the table and so by the draw.
                modularCount = VERSUS_MODULAR_MIN,
                modularCountMax = VERSUS_MODULAR_MAX,
                modularPacks = restricted,
            )
        }

        fromCards + fromVersus
    }

    /**
     * Every playable versus game: a leader, and the side you play it on.
     *
     * Neither half is a game on its own — Captain Marvel is who you face, and
     * Resistance is how — so the pair is what the randomiser offers. Four
     * leaders and two sides in Civil War make eight; She-Hulk and Vision make
     * four more in Synthezoid Smackdown.
     */
    private suspend fun versusScenarios(locale: CardLocale): List<VersusScenario> {
        val sides = cardDao.getVersusSides(locale.code).groupBy { it.packCode }
        return cardDao.getLeaders(locale.code).flatMap { leader ->
            sides[leader.packCode].orEmpty().map { side ->
                VersusScenario(
                    code = "${side.code}__${leader.code}",
                    packCode = leader.packCode,
                    name = "${side.name ?: side.code} : ${leader.name ?: leader.code}",
                )
            }
        }
    }

    /**
     * The setup printed on a scenario's own main scheme.
     *
     * The same text the campaign briefings show, for the games that are not
     * campaigns. Every scenario tells you how to set itself up on the 1A side
     * of its main scheme, and a player who rolled a scenario they have not
     * played is otherwise reading it off the card with one hand.
     */
    suspend fun schemeBriefing(
        scenarioCode: String,
        locale: CardLocale,
    ): SchemeBriefing = withContext(ioDispatcher) {
        val schemes = cardDao.getCardSet(scenarioCode, locale.code)
            .filter { it.typeCode == MAIN_SCHEME_TYPE }
        if (schemes.isEmpty()) {
            return@withContext SchemeBriefing()
        }

        // Chosen by what the card carries rather than by its stage. Only the
        // first stage has a setup, but the stage is written "1A" on sixty of
        // them and plain "A" on two, and older scenarios number theirs "1" and
        // print no setup at all because it lives in the campaign book. Asking
        // for the one with a setup on it answers all three.
        val withSetup = schemes.firstNotNullOfOrNull { scheme ->
            SchemeSetup.steps(scheme.text)
                .takeIf { it.isNotEmpty() }
                ?.let { scheme to it }
        }

        SchemeBriefing(
            // Named even when there is no setup to print, so the briefing can
            // still say which main scheme to put out.
            schemeName = (withSetup?.first ?: schemes.minByOrNull { it.stage.orEmpty() })?.name,
            steps = withSetup?.second.orEmpty(),
        )
    }

    suspend fun loadNames(locale: CardLocale): RandomizerNames = withContext(ioDispatcher) {
        // Corrections first, then whatever the card database says. MarvelCDB
        // leaves some French set names in English, and a player reading a name
        // off the app while holding the card cannot tell whose fault that is.
        val overrides = setNameOverrides.forLocale(locale)
        RandomizerNames(
            scenarios = cardDao.getPlayableScenarios(locale.code)
                .mapNotNull { s -> (overrides[s.code] ?: s.name)?.let { s.code to it } }.toMap() +
                versusScenarios(locale).associate { it.code to it.name },
            modularSets = cardDao.getCardSets(MODULAR_SET, locale.code)
                .mapNotNull { s -> (overrides[s.code] ?: s.name)?.let { s.code to it } }.toMap(),
            heroes = cardDao.getHeroes(locale.code)
                .mapNotNull { s -> s.name?.let { s.code to it } }.toMap(),
            packs = collectionRepository.packNames(locale),
        )
    }

    fun observeHistory(): Flow<List<RandomizerHistoryEntity>> = historyDao.observeHistory()

    fun observeBeatenScenarios(): Flow<List<String>> = historyDao.observeBeatenScenarios()

    suspend fun save(draw: RandomizerDraw) {
        val scenario = draw.scenarioCode ?: return
        val difficulty = draw.difficulty ?: return
        historyDao.insert(
            RandomizerHistoryEntity(
                id = UUID.randomUUID().toString(),
                createdAt = System.currentTimeMillis(),
                scenarioCode = scenario,
                difficulty = difficulty.name,
                playerCount = draw.playerCount,
                heroes = draw.heroes.joinToString(",") { "${it.heroCode}:${it.aspect}" },
                modularSetCodes = draw.modularSetCodes.joinToString(","),
            ),
        )
    }

    suspend fun setBeaten(id: String, beaten: Boolean) = historyDao.setBeaten(id, beaten)

    suspend fun deleteHistoryEntry(id: String) = historyDao.delete(id)

    companion object {
        private const val MAIN_SCHEME_TYPE = "main_scheme"
        private const val MODULAR_SET = "modular"

        /** A versus game takes three or four modular sets, never one. */
        private const val VERSUS_MODULAR_MIN = 3
        private const val VERSUS_MODULAR_MAX = 4

        /** Primary factions that are playable aspects. */
        val ASPECTS: List<String> =
            listOf("aggression", "justice", "leadership", "protection", "pool")

        /**
         * Aspects that arrived in a pack of their own.
         *
         * The four originals are in the Core Set, so owning the game is owning
         * them. 'Pool came with Deadpool and is the only one anybody can be
         * without.
         */
        val ASPECT_PACKS: Map<String, String> = mapOf("pool" to "deadpool")

        /** Rebuilds the hero assignments stored in a history row. */
        fun parseHeroes(stored: String): List<HeroAssignment> = stored
            .split(',')
            .filter { it.isNotBlank() }
            .mapNotNull { pair ->
                val parts = pair.split(':')
                if (parts.size == 2) HeroAssignment(parts[0], parts[1]) else null
            }

        fun parseDifficulty(stored: String): Difficulty? =
            Difficulty.entries.firstOrNull { it.name == stored }
    }
}

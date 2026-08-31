package com.hasyame.marvelchampions.data.repository

import com.hasyame.marvelchampions.data.db.dao.CardDao
import com.hasyame.marvelchampions.data.seed.SetNameOverrides
import com.hasyame.marvelchampions.data.db.dao.ExcludedModularSetDao
import com.hasyame.marvelchampions.data.db.dao.ExcludedScenarioDao
import com.hasyame.marvelchampions.data.db.dao.OwnedPackDao
import com.hasyame.marvelchampions.data.db.dao.PackDao
import com.hasyame.marvelchampions.data.db.dao.SyncStateDao
import com.hasyame.marvelchampions.data.db.entity.ExcludedModularSetEntity
import com.hasyame.marvelchampions.data.db.entity.ExcludedScenarioEntity
import com.hasyame.marvelchampions.data.db.entity.OwnedPackEntity
import com.hasyame.marvelchampions.data.db.entity.PackEntity
import com.hasyame.marvelchampions.data.db.entity.SyncCollection
import com.hasyame.marvelchampions.domain.model.CardLocale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** A pack together with how many copies the user owns. */
data class PackOwnership(
    val pack: PackEntity,
    val quantity: Int,
    /** Falls back to the pack code when no translation exists yet. */
    val name: String,
) {
    val isOwned: Boolean get() = quantity > 0
}

/**
 * The user's collection. This is the single source of truth for what the
 * randomiser may draw and for deck legality checks.
 */
@Singleton
class CollectionRepository @Inject constructor(
    private val packDao: PackDao,
    private val ownedPackDao: OwnedPackDao,
    private val excludedModularSetDao: ExcludedModularSetDao,
    private val excludedScenarioDao: ExcludedScenarioDao,
    private val cardDao: CardDao,
    private val syncStateDao: SyncStateDao,
    private val setNameOverrides: SetNameOverrides,
) {

    fun observeCollection(locale: CardLocale): Flow<List<PackOwnership>> =
        combine(
            packDao.observeNamedPacks(locale.code),
            ownedPackDao.observeOwned(),
        ) { packs, owned ->
            val quantities = owned.associate { it.packCode to it.quantity }
            // Corrected here as well as in packNames: this is the screen where a
            // player actually reads pack titles, and it takes them from the
            // database rather than going through that function.
            val corrections = setNameOverrides.packsForLocale(locale)
            packs.map { named ->
                PackOwnership(
                    pack = named.pack,
                    quantity = quantities[named.pack.code] ?: 0,
                    name = corrections[named.pack.code]
                        ?: named.localizedName
                        ?: named.pack.code,
                )
            }
        }

    fun observeOwnedCodes(): Flow<Set<String>> =
        ownedPackDao.observeOwned().map { owned -> owned.map { it.packCode }.toSet() }

    suspend fun getOwnedCodes(): Set<String> = ownedPackDao.getOwnedCodes().toSet()

    suspend fun setOwned(packCode: String, owned: Boolean) = setQuantity(
        packCode = packCode,
        quantity = if (owned) 1 else 0,
    )

    suspend fun setQuantity(packCode: String, quantity: Int) {
        val now = System.currentTimeMillis()
        if (quantity <= 0) {
            ownedPackDao.remove(packCode, now)
        } else {
            ownedPackDao.upsert(
                OwnedPackEntity(packCode = packCode, quantity = quantity, updatedAt = now),
            )
        }
        syncStateDao.markDirty(SyncCollection.OWNED_PACKS.key, packCode)
    }

    suspend fun setOwnedBulk(packCodes: Collection<String>, owned: Boolean) {
        val now = System.currentTimeMillis()
        if (owned) {
            ownedPackDao.upsertAll(
                packCodes.map { OwnedPackEntity(it, quantity = 1, updatedAt = now) },
            )
        } else {
            packCodes.forEach { ownedPackDao.remove(it, now) }
        }
        packCodes.forEach { syncStateDao.markDirty(SyncCollection.OWNED_PACKS.key, it) }
    }

    /**
     * Replaces the whole collection, for the JSON import path.
     *
     * The rows this drops leave no tombstone: they are removed outright, not
     * deleted by the user. An import is a different collection taking the place
     * of the old one, and it is the one path where the device will have to tell
     * a server what it holds rather than what changed.
     */
    suspend fun replaceCollection(ownedPacks: Map<String, Int>) {
        val now = System.currentTimeMillis()
        val entities = ownedPacks.filterValues { it > 0 }
            .map { (code, quantity) -> OwnedPackEntity(code, quantity, updatedAt = now) }
        ownedPackDao.replaceAll(entities)
        entities.forEach { syncStateDao.markDirty(SyncCollection.OWNED_PACKS.key, it.packCode) }
    }

    suspend fun isEmpty(): Boolean = ownedPackDao.countOwned() == 0

    /**
     * The modular sets each pack contains, keyed by pack code.
     *
     * Derived from the cards themselves rather than curated: a set belongs to
     * whichever pack its cards came in.
     */
    suspend fun modularSetsByPack(locale: CardLocale): Map<String, List<ModularSet>> {
        val overrides = setNameOverrides.forLocale(locale)
        return cardDao.getCardSets(MODULAR_SET, locale.code)
            .map {
                ModularSet(
                    code = it.code,
                    name = overrides[it.code] ?: it.name ?: it.code,
                    packCode = it.packCode,
                )
            }
            .sortedBy { it.name }
            .groupBy { it.packCode }
    }

    /**
     * The scenarios each pack contains, keyed by pack code.
     *
     * Only villain sets that bring a scenario of their own, for the same reason
     * the draw uses that rule: the four Wrecking Crew villains are played inside
     * Wrecking Crew, and offering them as things to tick would be nonsense.
     */
    suspend fun scenariosByPack(locale: CardLocale): Map<String, List<ModularSet>> {
        val overrides = setNameOverrides.forLocale(locale)
        return cardDao.getPlayableScenarios(locale.code)
            .map {
                ModularSet(
                    code = it.code,
                    name = overrides[it.code] ?: it.name ?: it.code,
                    packCode = it.packCode,
                )
            }
            .sortedBy { it.name }
            .groupBy { it.packCode }
    }

    fun observeExcludedScenarios(): Flow<Set<String>> =
        excludedScenarioDao.observeExcluded().map { rows -> rows.map { it.scenarioCode }.toSet() }

    suspend fun getExcludedScenarios(): Set<String> =
        excludedScenarioDao.getExcludedCodes().toSet()

    /** [excluded] true means the user has not got it, so nothing may offer it. */
    suspend fun setScenarioExcluded(scenarioCode: String, excluded: Boolean) {
        val now = System.currentTimeMillis()
        if (excluded) {
            excludedScenarioDao.exclude(ExcludedScenarioEntity(scenarioCode, updatedAt = now))
        } else {
            excludedScenarioDao.include(scenarioCode, now)
        }
        syncStateDao.markDirty(SyncCollection.EXCLUDED_SCENARIOS.key, scenarioCode)
    }

    /**
     * Pack code to its localised name, falling back to the code.
     *
     * Corrected the same way set names are. MarvelCDB leaves a number of pack
     * titles in English on its French endpoint, so the collection screen and
     * everything that names a pack were showing "Agents of S.H.I.E.L.D." to
     * somebody holding the French box. Scenarios and modular sets have been
     * corrected here since the French names went in; packs were simply missed.
     */
    suspend fun packNames(locale: CardLocale): Map<String, String> {
        val overrides = setNameOverrides.packsForLocale(locale)
        return packDao.getTranslations(locale.code)
            .associate { it.packCode to (overrides[it.packCode] ?: it.name) }
    }

    fun observeExcludedModularSets(): Flow<Set<String>> =
        excludedModularSetDao.observeExcluded().map { rows -> rows.map { it.setCode }.toSet() }

    suspend fun getExcludedModularSets(): Set<String> =
        excludedModularSetDao.getExcludedCodes().toSet()

    /** [excluded] true means the user has not got it, so nothing may offer it. */
    suspend fun setModularSetExcluded(setCode: String, excluded: Boolean) {
        val now = System.currentTimeMillis()
        if (excluded) {
            excludedModularSetDao.exclude(ExcludedModularSetEntity(setCode, updatedAt = now))
        } else {
            excludedModularSetDao.include(setCode, now)
        }
        syncStateDao.markDirty(SyncCollection.EXCLUDED_MODULAR_SETS.key, setCode)
    }

    /** Replaces the exclusions wholesale, for the restore path. */
    suspend fun replaceExcludedModularSets(setCodes: Collection<String>) {
        val now = System.currentTimeMillis()
        val entities = setCodes.map { ExcludedModularSetEntity(it, updatedAt = now) }
        excludedModularSetDao.replaceAll(entities)
        entities.forEach {
            syncStateDao.markDirty(SyncCollection.EXCLUDED_MODULAR_SETS.key, it.setCode)
        }
    }

    private companion object {
        const val MODULAR_SET = "modular"
    }
}

/** A modular set as the collection screen shows it, under its pack. */
data class ModularSet(
    val code: String,
    val name: String,
    val packCode: String,
)

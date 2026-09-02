package com.hasyame.marvelchampions.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.hasyame.marvelchampions.data.db.entity.ExcludedModularSetEntity
import com.hasyame.marvelchampions.data.db.entity.ExcludedScenarioEntity
import com.hasyame.marvelchampions.data.db.entity.OwnedPackEntity
import com.hasyame.marvelchampions.data.db.entity.PackEntity
import com.hasyame.marvelchampions.data.db.entity.PackTranslationEntity
import kotlinx.coroutines.flow.Flow

/** A pack with its name resolved for one locale. */
data class NamedPack(
    @Embedded val pack: PackEntity,
    val localizedName: String?,
)

@Dao
interface PackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPacks(packs: List<PackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranslations(translations: List<PackTranslationEntity>)

    @Query("DELETE FROM packs")
    suspend fun deleteAllPacks()

    /**
     * Replaces the whole pack table in one transaction. `pack_translations`
     * cascades on delete, so it does not need clearing separately.
     */
    @Transaction
    suspend fun replaceAll(
        packs: List<PackEntity>,
        translations: List<PackTranslationEntity>,
    ) {
        deleteAllPacks()
        insertPacks(packs)
        insertTranslations(translations)
    }

    /**
     * Re-applies the curated type and wave to a pack already in the database.
     *
     * Everything else about a pack comes from MarvelCDB; these four columns come
     * from `assets/pack_metadata.json` and are the only part a release can fix
     * on its own. Without this the curated file reached new installs only: it is
     * read when the database is empty or when the player runs a card update, so
     * Jessica Jones and Luke Cage stayed at wave 0 on every phone that already
     * had the app, however many releases went by.
     */
    @Query(
        """
        UPDATE packs
        SET type = :type, wave = :wave, waveInferred = :waveInferred, typeManual = :typeManual
        WHERE code = :code
        """,
    )
    suspend fun applyCuration(
        code: String,
        type: String,
        wave: Int,
        waveInferred: Boolean,
        typeManual: Boolean,
    )

    @Query("SELECT * FROM packs ORDER BY wave, position")
    fun observePacks(): Flow<List<PackEntity>>

    /**
     * Packs with their name in [locale].
     *
     * A LEFT JOIN so a pack whose translation is missing still appears — the
     * newest packs are not translated for months, and hiding them would be
     * worse than showing an English name.
     */
    @Query(
        """
        SELECT packs.*, pack_translations.name AS localizedName
        FROM packs
        LEFT JOIN pack_translations
          ON pack_translations.packCode = packs.code AND pack_translations.locale = :locale
        ORDER BY packs.wave, packs.position
        """,
    )
    fun observeNamedPacks(locale: String): Flow<List<NamedPack>>

    @Query("SELECT * FROM packs ORDER BY wave, position")
    suspend fun getPacks(): List<PackEntity>

    @Query("SELECT * FROM packs WHERE code = :code")
    suspend fun getPack(code: String): PackEntity?

    @Query("SELECT * FROM pack_translations WHERE locale = :locale")
    suspend fun getTranslations(locale: String): List<PackTranslationEntity>

    @Query("SELECT COUNT(*) FROM packs")
    suspend fun countPacks(): Int
}

@Dao
interface OwnedPackDao {

    @Query("SELECT * FROM owned_packs WHERE quantity > 0 AND deletedAt IS NULL")
    fun observeOwned(): Flow<List<OwnedPackEntity>>

    @Query("SELECT * FROM owned_packs WHERE quantity > 0 AND deletedAt IS NULL")
    suspend fun getOwned(): List<OwnedPackEntity>

    @Query("SELECT packCode FROM owned_packs WHERE quantity > 0 AND deletedAt IS NULL")
    suspend fun getOwnedCodes(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(ownedPack: OwnedPackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(ownedPacks: List<OwnedPackEntity>)

    /**
     * "I no longer own this", which is not the same fact as "I never owned
     * this" and now no longer looks like it.
     */
    @Query(
        "UPDATE owned_packs SET deletedAt = :now, updatedAt = :now WHERE packCode = :packCode",
    )
    suspend fun remove(packCode: String, now: Long)

    /**
     * For the import and restore paths, which replace rather than merge.
     *
     * A real DELETE, unlike [remove]; see CampaignDao.deleteAllRuns.
     */
    @Query("DELETE FROM owned_packs")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM owned_packs WHERE quantity > 0 AND deletedAt IS NULL")
    suspend fun countOwned(): Int

    /** Replaces the whole collection, for the import path. */
    @Transaction
    suspend fun replaceAll(ownedPacks: List<OwnedPackEntity>) {
        clear()
        upsertAll(ownedPacks)
    }
}

@Dao
interface ExcludedModularSetDao {

    @Query("SELECT * FROM excluded_modular_sets WHERE deletedAt IS NULL")
    fun observeExcluded(): Flow<List<ExcludedModularSetEntity>>

    @Query("SELECT * FROM excluded_modular_sets WHERE deletedAt IS NULL")
    suspend fun getExcluded(): List<ExcludedModularSetEntity>

    @Query("SELECT setCode FROM excluded_modular_sets WHERE deletedAt IS NULL")
    suspend fun getExcludedCodes(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun exclude(set: ExcludedModularSetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun excludeAll(sets: List<ExcludedModularSetEntity>)

    /**
     * The set is back in the collection.
     *
     * A tombstone rather than a removal, because this table is presence-only:
     * the row *is* the value. Without one, "put back on the other device" and
     * "never taken out here" are the same absence, and a merge cannot tell
     * which the user meant.
     */
    @Query(
        """
        UPDATE excluded_modular_sets SET deletedAt = :now, updatedAt = :now
        WHERE setCode = :setCode
        """,
    )
    suspend fun include(setCode: String, now: Long)

    /** For the import and restore paths. A real DELETE; see CampaignDao. */
    @Query("DELETE FROM excluded_modular_sets")
    suspend fun clear()

    /** Replaces the whole list, for the import and restore paths. */
    @Transaction
    suspend fun replaceAll(sets: List<ExcludedModularSetEntity>) {
        clear()
        excludeAll(sets)
    }
}

@Dao
interface ExcludedScenarioDao {

    @Query("SELECT * FROM excluded_scenarios WHERE deletedAt IS NULL")
    fun observeExcluded(): Flow<List<ExcludedScenarioEntity>>

    @Query("SELECT * FROM excluded_scenarios WHERE deletedAt IS NULL")
    suspend fun getExcluded(): List<ExcludedScenarioEntity>

    @Query("SELECT scenarioCode FROM excluded_scenarios WHERE deletedAt IS NULL")
    suspend fun getExcludedCodes(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun exclude(scenario: ExcludedScenarioEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun excludeAll(scenarios: List<ExcludedScenarioEntity>)

    /** The scenario is back in the collection. Presence-only, so a tombstone. */
    @Query(
        """
        UPDATE excluded_scenarios SET deletedAt = :now, updatedAt = :now
        WHERE scenarioCode = :scenarioCode
        """,
    )
    suspend fun include(scenarioCode: String, now: Long)

    /** For the import and restore paths. A real DELETE; see CampaignDao. */
    @Query("DELETE FROM excluded_scenarios")
    suspend fun clear()

    /** Replaces the whole list, for the import and restore paths. */
    @Transaction
    suspend fun replaceAll(scenarios: List<ExcludedScenarioEntity>) {
        clear()
        excludeAll(scenarios)
    }
}

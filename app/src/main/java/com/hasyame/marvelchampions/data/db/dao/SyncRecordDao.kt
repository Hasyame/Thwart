package com.hasyame.marvelchampions.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.hasyame.marvelchampions.data.db.entity.CampaignEventEntity
import com.hasyame.marvelchampions.data.db.entity.CampaignRunEntity
import com.hasyame.marvelchampions.data.db.entity.ExcludedModularSetEntity
import com.hasyame.marvelchampions.data.db.entity.ExcludedScenarioEntity
import com.hasyame.marvelchampions.data.db.entity.FavouriteCardEntity
import com.hasyame.marvelchampions.data.db.entity.OwnedPackEntity
import com.hasyame.marvelchampions.data.db.entity.PlayEntity
import com.hasyame.marvelchampions.data.db.entity.RandomizerHistoryEntity
import com.hasyame.marvelchampions.data.db.entity.SavedDeckEntity

/**
 * The one place that reads rows a user has deleted.
 *
 * Every other DAO filters `deletedAt IS NULL`, and `SoftDeleteTest` fails the
 * build if one forgets. This file is the deliberate exception, exempted there by
 * name, because sync needs the opposite of what a screen needs: a row somebody
 * threw away is precisely the thing that has to travel, or the other device
 * never learns it is gone and hands it back.
 *
 * Kept as its own file for that reason. An exemption granted to a whole file
 * that exists only for sync is auditable — you can read all of it in a minute
 * and see that nothing here feeds a screen. An exemption granted per query,
 * scattered through the ordinary DAOs, is one somebody eventually copies onto
 * a read that does feed a screen.
 *
 * Writes go through `@Upsert` rather than `@Insert(REPLACE)`. REPLACE in SQLite
 * is a DELETE followed by an INSERT, and `campaign_events` has an
 * `ON DELETE CASCADE` onto `campaign_runs`: replacing a run would silently take
 * its whole event log with it, which for this app is the campaign itself.
 */
@Dao
interface SyncRecordDao {

    // --- owned packs --------------------------------------------------------

    @Query("SELECT * FROM owned_packs WHERE packCode = :id")
    suspend fun ownedPack(id: String): OwnedPackEntity?

    @Query("SELECT * FROM owned_packs")
    suspend fun ownedPacks(): List<OwnedPackEntity>

    @Upsert
    suspend fun putOwnedPack(row: OwnedPackEntity)

    // --- exclusions ---------------------------------------------------------

    @Query("SELECT * FROM excluded_modular_sets WHERE setCode = :id")
    suspend fun excludedModularSet(id: String): ExcludedModularSetEntity?

    @Query("SELECT * FROM excluded_modular_sets")
    suspend fun excludedModularSets(): List<ExcludedModularSetEntity>

    @Upsert
    suspend fun putExcludedModularSet(row: ExcludedModularSetEntity)

    @Query("SELECT * FROM excluded_scenarios WHERE scenarioCode = :id")
    suspend fun excludedScenario(id: String): ExcludedScenarioEntity?

    @Query("SELECT * FROM excluded_scenarios")
    suspend fun excludedScenarios(): List<ExcludedScenarioEntity>

    @Upsert
    suspend fun putExcludedScenario(row: ExcludedScenarioEntity)

    // --- favourites ---------------------------------------------------------

    @Query("SELECT * FROM favourite_cards WHERE cardCode = :id")
    suspend fun favourite(id: String): FavouriteCardEntity?

    @Query("SELECT * FROM favourite_cards")
    suspend fun favourites(): List<FavouriteCardEntity>

    @Upsert
    suspend fun putFavourite(row: FavouriteCardEntity)

    // --- decks --------------------------------------------------------------

    @Query("SELECT * FROM saved_decks WHERE id = :id")
    suspend fun deck(id: String): SavedDeckEntity?

    @Query("SELECT * FROM saved_decks")
    suspend fun decks(): List<SavedDeckEntity>

    @Upsert
    suspend fun putDeck(row: SavedDeckEntity)

    // --- campaigns ----------------------------------------------------------

    @Query("SELECT * FROM campaign_runs WHERE id = :id")
    suspend fun campaignRun(id: String): CampaignRunEntity?

    @Query("SELECT * FROM campaign_runs")
    suspend fun campaignRuns(): List<CampaignRunEntity>

    @Upsert
    suspend fun putCampaignRun(row: CampaignRunEntity)

    @Query("SELECT * FROM campaign_events WHERE id = :id")
    suspend fun campaignEvent(id: String): CampaignEventEntity?

    @Query("SELECT * FROM campaign_events")
    suspend fun campaignEvents(): List<CampaignEventEntity>

    /**
     * True when a run exists to hang an event on.
     *
     * An event whose run has not arrived yet would fail the foreign key and
     * take the whole batch with it, so the engine asks first and holds the
     * event back rather than losing the page.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM campaign_runs WHERE id = :runId)")
    suspend fun hasCampaignRun(runId: String): Boolean

    @Upsert
    suspend fun putCampaignEvent(row: CampaignEventEntity)

    // --- plays --------------------------------------------------------------

    @Query("SELECT * FROM plays WHERE id = :id")
    suspend fun play(id: String): PlayEntity?

    @Query("SELECT * FROM plays")
    suspend fun plays(): List<PlayEntity>

    @Upsert
    suspend fun putPlay(row: PlayEntity)

    // --- randomiser ---------------------------------------------------------

    @Query("SELECT * FROM randomizer_history WHERE id = :id")
    suspend fun draw(id: String): RandomizerHistoryEntity?

    @Query("SELECT * FROM randomizer_history")
    suspend fun draws(): List<RandomizerHistoryEntity>

    @Upsert
    suspend fun putDraw(row: RandomizerHistoryEntity)
}

package com.hasyame.marvelchampions.data.db

import androidx.room.AutoMigration
import androidx.room.TypeConverters
import androidx.room.Database
import androidx.room.RoomDatabase
import com.hasyame.marvelchampions.data.db.dao.CampaignDao
import com.hasyame.marvelchampions.data.db.dao.CardDao
import com.hasyame.marvelchampions.data.db.dao.ExcludedModularSetDao
import com.hasyame.marvelchampions.data.db.dao.ExcludedScenarioDao
import com.hasyame.marvelchampions.data.db.dao.OwnedPackDao
import com.hasyame.marvelchampions.data.db.dao.PackDao
import com.hasyame.marvelchampions.data.db.dao.FavouriteDao
import com.hasyame.marvelchampions.data.db.dao.PlayDao
import com.hasyame.marvelchampions.data.db.dao.RandomizerHistoryDao
import com.hasyame.marvelchampions.data.db.dao.SavedDeckDao
import com.hasyame.marvelchampions.data.db.entity.CampaignEventEntity
import com.hasyame.marvelchampions.data.db.entity.CampaignRunEntity
import com.hasyame.marvelchampions.data.db.entity.CardEntity
import com.hasyame.marvelchampions.data.db.entity.CardFtsEntity
import com.hasyame.marvelchampions.data.db.entity.ExcludedModularSetEntity
import com.hasyame.marvelchampions.data.db.entity.ExcludedScenarioEntity
import com.hasyame.marvelchampions.data.db.entity.OwnedPackEntity
import com.hasyame.marvelchampions.data.db.entity.FavouriteCardEntity
import com.hasyame.marvelchampions.data.db.entity.PackEntity
import com.hasyame.marvelchampions.data.db.dao.PausedGameDao
import com.hasyame.marvelchampions.data.db.entity.PausedGameEntity
import com.hasyame.marvelchampions.data.db.entity.PlayEntity
import com.hasyame.marvelchampions.data.db.entity.PlayHeroConverters
import com.hasyame.marvelchampions.data.db.entity.PackTranslationEntity
import com.hasyame.marvelchampions.data.db.entity.RandomizerHistoryEntity
import com.hasyame.marvelchampions.data.db.entity.SavedDeckEntity
import com.hasyame.marvelchampions.data.db.dao.SyncStateDao
import com.hasyame.marvelchampions.data.db.entity.SyncStateEntity

/**
 * Note that this database holds two very different kinds of data:
 *
 * - **cache** — `cards`, `cards_fts`, `packs`, `pack_translations`. Rebuilt from
 *   MarvelCDB on any device, excluded from backup, never exported.
 * - **user state** — `owned_packs`, the two exclusion tables, `favourite_cards`,
 *   `saved_decks`, `campaign_runs`, `campaign_events`, `plays` and
 *   `randomizer_history`. Owned by the user, carried between devices in the
 *   export bundle, and the only tables that will ever sync.
 * - **device state** — `paused_games`. It describes the table in front of one
 *   phone: which villain card is face up, whose life total is what. Syncing it
 *   would mean a tablet claiming there is a game in progress that is sitting on
 *   somebody else's coffee table.
 * - **sync bookkeeping** — `sync_state`, which belongs to none of the above and
 *   is kept apart from all of them. See SyncStateEntity.
 *
 * They share a file because the cross-device bundle is a separate
 * serialisation concern, not a storage one.
 */
@TypeConverters(PlayHeroConverters::class)
@Database(
    entities = [
        CardEntity::class,
        CardFtsEntity::class,
        PackEntity::class,
        PackTranslationEntity::class,
        OwnedPackEntity::class,
        RandomizerHistoryEntity::class,
        SavedDeckEntity::class,
        CampaignRunEntity::class,
        CampaignEventEntity::class,
        PlayEntity::class,
        FavouriteCardEntity::class,
        ExcludedModularSetEntity::class,
        ExcludedScenarioEntity::class,
        PausedGameEntity::class,
        SyncStateEntity::class,
    ],
    version = 19,
    exportSchema = true,
    // Room generates these from the exported schemas, which it can do for
    // anything that only adds a table, or adds a column with a SQL default.
    // Anything else needs a handwritten migration. A spec is how a generated
    // migration gets extra statements: see SyncMigration17To18, which seeds
    // updatedAt on rows written before the column existed.
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11),
        AutoMigration(from = 11, to = 12),
        AutoMigration(from = 12, to = 13),
        AutoMigration(from = 13, to = 14),
        AutoMigration(from = 14, to = 15),
        AutoMigration(from = 15, to = 16),
        AutoMigration(from = 16, to = 17),
        AutoMigration(from = 17, to = 18, spec = SyncMigration17To18::class),
        AutoMigration(from = 18, to = 19),
    ],
)
abstract class MarvelChampionsDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
    abstract fun pausedGameDao(): PausedGameDao
    abstract fun packDao(): PackDao
    abstract fun ownedPackDao(): OwnedPackDao
    abstract fun randomizerHistoryDao(): RandomizerHistoryDao
    abstract fun savedDeckDao(): SavedDeckDao
    abstract fun campaignDao(): CampaignDao
    abstract fun playDao(): PlayDao
    abstract fun favouriteDao(): FavouriteDao
    abstract fun excludedModularSetDao(): ExcludedModularSetDao
    abstract fun excludedScenarioDao(): ExcludedScenarioDao
    abstract fun syncStateDao(): SyncStateDao

    companion object {
        const val NAME: String = "marvelchampions.db"
    }
}

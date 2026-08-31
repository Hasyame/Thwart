package com.hasyame.marvelchampions.data.db

import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema 17 to 18: the two sync columns, and honest values for the rows that
 * are already on people's phones.
 *
 * ## Why a spec rather than a handwritten migration
 *
 * The column changes are all additions with SQL defaults, which is exactly what
 * Room can write for itself, and letting it do so is the safer half: the
 * `ALTER TABLE` statements are generated from the exported schemas, so the
 * migration and the entities cannot drift apart. Sixteen handwritten ALTERs
 * could, and Room would only notice on a user's phone, at which point the
 * database will not open at all.
 *
 * What Room cannot generate is the seeding, because it is not a schema change.
 * [onPostMigrate] is where a generated migration lets you add statements, and
 * it runs inside the same transaction as the ALTERs, so a failure here rolls
 * the whole upgrade back rather than leaving half of it applied.
 *
 * ## Why the seeding matters
 *
 * Every existing row would otherwise carry `updatedAt = 0`, which is a lie: a
 * game played last week is not older than one played in 2024. On the first sync
 * that lie costs the ordering, and last-write-wins would resolve conflicts
 * between two devices' histories by coin toss.
 *
 * So each table is seeded from the timestamp it already carries. Three tables
 * carry none — `owned_packs` and the two exclusion tables are presence-only —
 * and those keep `updatedAt = 0`, meaning "older than anything". That is the
 * right answer rather than a gap: on a first merge they lose to any dated row
 * and win over nothing, which matches the truth that we do not know when the
 * user ticked that box.
 *
 * `WHERE updatedAt = 0` rather than an unconditional UPDATE, so that re-running
 * this can never overwrite a real timestamp.
 */
class SyncMigration17To18 : AutoMigrationSpec {

    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        SEEDING.forEach(db::execSQL)
    }

    private companion object {
        /**
         * One statement per table that has a natural timestamp to seed from.
         *
         * `saved_decks` uses `lastSyncedAt`, which is when the deck was
         * imported or refreshed rather than when it was last edited. It is the
         * closest thing the row has, and it is right for the common case: a
         * deck that has never been edited was last written when it was
         * imported.
         */
        val SEEDING = listOf(
            "UPDATE plays SET updatedAt = playedAt WHERE updatedAt = 0",
            "UPDATE campaign_runs SET updatedAt = createdAt WHERE updatedAt = 0",
            "UPDATE randomizer_history SET updatedAt = createdAt WHERE updatedAt = 0",
            "UPDATE favourite_cards SET updatedAt = addedAt WHERE updatedAt = 0",
            "UPDATE saved_decks SET updatedAt = lastSyncedAt WHERE updatedAt = 0",
        )
    }
}

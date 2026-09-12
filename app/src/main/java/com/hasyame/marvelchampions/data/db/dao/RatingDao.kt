package com.hasyame.marvelchampions.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.hasyame.marvelchampions.data.db.entity.RatingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RatingDao {

    /** Rating again is the same row, newer: an upsert, not an insert. */
    @Upsert
    suspend fun put(rating: RatingEntity)

    /**
     * Takes a rating back. A tombstone rather than a delete, so the removal
     * reaches the account's other devices and the server subtracts it.
     */
    @Query("UPDATE ratings SET deletedAt = :now, updatedAt = :now WHERE subject = :subject")
    suspend fun remove(subject: String, now: Long)

    /**
     * Forgets a rating the server refused. A real delete: the server never
     * stored it, so there is nothing to tell anyone about, and a tombstone
     * would be a deletion this device then owed for a record that never was.
     */
    @Query("DELETE FROM ratings WHERE subject = :subject")
    suspend fun forget(subject: String)

    @Query("SELECT * FROM ratings WHERE subject = :subject AND deletedAt IS NULL")
    suspend fun get(subject: String): RatingEntity?

    /** The player's own current ratings for the subjects a screen shows. */
    @Query("SELECT * FROM ratings WHERE subject IN (:subjects) AND deletedAt IS NULL")
    fun observe(subjects: List<String>): Flow<List<RatingEntity>>

    @Query("SELECT * FROM ratings WHERE deletedAt IS NULL")
    suspend fun getAll(): List<RatingEntity>

    /** For a restore, which replaces rather than merges. A real DELETE; see CampaignDao. */
    @Query("DELETE FROM ratings")
    suspend fun deleteAll()

    @Upsert
    suspend fun putAll(ratings: List<RatingEntity>)
}

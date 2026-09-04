package com.hasyame.marvelchampions.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.hasyame.marvelchampions.data.db.entity.SyncStateEntity
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes the sync bookkeeping.
 *
 * See [SyncStateEntity] for what the two columns mean and why they are not on
 * the entities themselves.
 */
@Dao
interface SyncStateDao {

    /**
     * Records that a record has local changes to push.
     *
     * An upsert written by hand rather than `@Insert(REPLACE)`, because REPLACE
     * is a DELETE followed by an INSERT in SQLite and would throw away the
     * [SyncStateEntity.serverRevision] this record already has. That revision is
     * what tells the server which version was overwritten, so losing it turns a
     * reported conflict into a silent one.
     */
    @Query(
        """
        INSERT INTO sync_state (collection, rowId, serverRevision, dirty)
        VALUES (:collection, :rowId, 0, 1)
        ON CONFLICT (collection, rowId) DO UPDATE SET dirty = 1
        """,
    )
    suspend fun markDirty(collection: String, rowId: String)

    /** Records that the server has accepted this record at [serverRevision]. */
    @Query(
        """
        INSERT INTO sync_state (collection, rowId, serverRevision, dirty)
        VALUES (:collection, :rowId, :serverRevision, 0)
        ON CONFLICT (collection, rowId) DO UPDATE SET
            serverRevision = :serverRevision,
            dirty = 0
        """,
    )
    suspend fun markSynced(collection: String, rowId: String, serverRevision: Long)

    /**
     * Records the revision the server gave a record **without** clearing the
     * dirty flag.
     *
     * For the record this device has edited and not yet pushed, when a pull
     * brings a newer version of it. The local edit is kept and pushed
     * afterwards, where it wins by arriving last; this is what lets that push
     * say which revision it is overwriting, so the overwrite is reported
     * rather than silent.
     *
     * Inserted dirty when there is no row at all, because a record with no row
     * here has never been pushed, which is the definition of dirty.
     */
    @Query(
        """
        INSERT INTO sync_state (collection, rowId, serverRevision, dirty)
        VALUES (:collection, :rowId, :serverRevision, 1)
        ON CONFLICT (collection, rowId) DO UPDATE SET serverRevision = :serverRevision
        """,
    )
    suspend fun noteServerRevision(collection: String, rowId: String, serverRevision: Long)

    @Query("SELECT * FROM sync_state WHERE collection = :collection AND rowId = :rowId")
    suspend fun get(collection: String, rowId: String): SyncStateEntity?

    /** Every row of the bookkeeping, for working out what has never been sent. */
    @Query("SELECT * FROM sync_state")
    suspend fun all(): List<SyncStateEntity>

    /**
     * Forgets one record's bookkeeping.
     *
     * For a row that is marked dirty and no longer exists in its table at all —
     * a hard delete from before tombstones, say. There is nothing to push and
     * nothing to remember.
     */
    @Query("DELETE FROM sync_state WHERE collection = :collection AND rowId = :rowId")
    suspend fun forget(collection: String, rowId: String)

    @Query("SELECT * FROM sync_state WHERE dirty = 1")
    suspend fun dirtyRecords(): List<SyncStateEntity>

    @Query("SELECT COUNT(*) FROM sync_state WHERE dirty = 1")
    fun observeDirtyCount(): Flow<Int>

    /**
     * Forgets everything.
     *
     * For a restore, which replaces the local data wholesale: the revisions
     * described rows that no longer exist, and every restored row is new to the
     * server until it has been pushed.
     */
    @Query("DELETE FROM sync_state")
    suspend fun clear()
}

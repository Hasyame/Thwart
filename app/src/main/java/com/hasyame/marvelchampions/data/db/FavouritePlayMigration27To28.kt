package com.hasyame.marvelchampions.data.db

import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONObject

/** Older clients carried Web stars in the opaque backup envelope. Materialize them. */
class FavouritePlayMigration27To28 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.query("SELECT id, extras FROM backup_metadata").use { rows ->
            while (rows.moveToNext()) {
                val extras = JSONObject(rows.getString(1))
                val stars = extras.optJSONArray("favouritePlays") ?: continue
                for (i in 0 until stars.length()) {
                    val star = stars.optJSONObject(i) ?: continue
                    val id = star.optString("playId").takeIf { it.isNotBlank() } ?: continue
                    val addedAt = star.optLong("addedAt")
                    db.execSQL(
                        "INSERT OR IGNORE INTO favourite_plays(playId, addedAt, updatedAt, deletedAt) VALUES (?, ?, ?, NULL)",
                        arrayOf<Any>(id, addedAt, addedAt),
                    )
                    db.execSQL(
                        "INSERT OR IGNORE INTO sync_state(collection, rowId, serverRevision, dirty) VALUES ('favourite_plays', ?, 0, 1)",
                        arrayOf(id),
                    )
                }
                extras.remove("favouritePlays")
                db.execSQL("UPDATE backup_metadata SET extras = ? WHERE id = ?", arrayOf<Any>(extras.toString(), rows.getInt(0)))
            }
        }
    }
}

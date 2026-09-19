package com.hasyame.marvelchampions.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Unknown document fields belong to the restored dataset, not to device preferences. */
@Entity(tableName = "backup_metadata")
data class BackupMetadataEntity(
    @PrimaryKey val id: Int = 0,
    val extras: String,
)

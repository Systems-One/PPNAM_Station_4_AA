package com.mitas.ppnam.station4aa.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WasteOutboxEntity::class,
        WasteCategoryEntity::class,
        WasteTypeEntity::class,
        CatalogueMetaEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class WasteOutboxDatabase : RoomDatabase() {
    abstract fun wasteOutboxDao(): WasteOutboxDao
    abstract fun wasteCatalogueDao(): WasteCatalogueDao

    companion object {
        fun create(context: Context): WasteOutboxDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                WasteOutboxDatabase::class.java,
                "ppnam_station4_outbox.db",
            )
                // Version 4 changes the outbox to schema v4 (machine fields out, jobNumber and
                // operatorId in) and adds the catalogue tables. Destructive migration is kept
                // deliberately: a queued v3 event carries machineCode and no jobNumber, so it can
                // never be accepted by a v4 consumer — a migration would preserve only messages
                // guaranteed to be rejected. The catalogue re-seeds and re-syncs on next launch.
                // ROLLOUT: upgrade when no handheld holds unsent collections.
                .fallbackToDestructiveMigration()
                .build()
    }
}

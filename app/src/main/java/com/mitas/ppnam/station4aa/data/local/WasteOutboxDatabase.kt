package com.mitas.ppnam.station4aa.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [WasteOutboxEntity::class], version = 3, exportSchema = false)
abstract class WasteOutboxDatabase : RoomDatabase() {
    abstract fun wasteOutboxDao(): WasteOutboxDao

    companion object {
        fun create(context: Context): WasteOutboxDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                WasteOutboxDatabase::class.java,
                "ppnam_station4_outbox.db",
            )
                // No migration path exists yet for the pre-schema-v3 outbox (version 1). The
                // outbox is a transient in-flight queue, not a permanent record, so dropping and
                // recreating it on upgrade is acceptable rather than authoring a real migration
                // for a handful of columns pre-production.
                .fallbackToDestructiveMigration()
                .build()
    }
}

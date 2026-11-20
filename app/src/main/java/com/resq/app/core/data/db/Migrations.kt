package com.resq.app.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 1 to 2: the columns the update counter, cancel flag, position time,
 * Nearby/Relayed label and Dismiss need. Existing rows keep their data; the
 * new columns get safe defaults.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for (statement in MIGRATION_1_2_SQL) db.execSQL(statement)
    }
}

/** Kept as plain strings so the statements are easy to read and check against the schema file. */
val MIGRATION_1_2_SQL = listOf(
    "ALTER TABLE sos_log ADD COLUMN updateCounter INTEGER NOT NULL DEFAULT 0",
    "ALTER TABLE sos_log ADD COLUMN isRelayed INTEGER NOT NULL DEFAULT 0",
    "ALTER TABLE sos_log ADD COLUMN isCancelled INTEGER NOT NULL DEFAULT 0",
    "ALTER TABLE sos_log ADD COLUMN positionUnknown INTEGER NOT NULL DEFAULT 0",
    "ALTER TABLE sos_log ADD COLUMN positionTimeSeconds INTEGER",
    "ALTER TABLE sos_log ADD COLUMN sosStartedSeconds INTEGER",
    "ALTER TABLE sos_log ADD COLUMN sendIntervalMinutes INTEGER",
    "ALTER TABLE sos_log ADD COLUMN lastSeenElapsedMillis INTEGER NOT NULL DEFAULT 0",
    "ALTER TABLE sos_log ADD COLUMN dismissed INTEGER NOT NULL DEFAULT 0",
)

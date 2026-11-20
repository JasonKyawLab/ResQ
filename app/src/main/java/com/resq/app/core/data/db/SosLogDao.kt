package com.resq.app.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SosLogDao {

    @Upsert
    suspend fun upsert(entity: SosLogEntity)

    @Query("SELECT * FROM sos_log ORDER BY lastSeenAtEpochMillis DESC")
    fun observeAll(): Flow<List<SosLogEntity>>

    /** What the Scan screen shows: everything the user has not dismissed. */
    @Query("SELECT * FROM sos_log WHERE dismissed = 0 ORDER BY lastSeenAtEpochMillis DESC")
    fun observeVisible(): Flow<List<SosLogEntity>>

    /** Hides one entry on this phone only. A newer update from that sender brings it back. */
    @Query("UPDATE sos_log SET dismissed = 1 WHERE senderId = :senderId")
    suspend fun dismiss(senderId: Int)

    @Query("SELECT * FROM sos_log ORDER BY lastSeenAtEpochMillis DESC")
    suspend fun getAllSnapshot(): List<SosLogEntity>

    @Query("SELECT * FROM sos_log WHERE senderId = :senderId LIMIT 1")
    suspend fun findBySenderId(senderId: Int): SosLogEntity?

    @Query("DELETE FROM sos_log WHERE lastSeenAtEpochMillis < :olderThanEpochMillis")
    suspend fun pruneStaleEntries(olderThanEpochMillis: Long)

    @Query("DELETE FROM sos_log")
    suspend fun clearAll()
}

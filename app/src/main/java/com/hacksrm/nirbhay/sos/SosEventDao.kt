package com.hacksrm.nirbhay.sos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface SosEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: SosEventEntity): Long

    @Update
    suspend fun update(event: SosEventEntity)

    @Query("SELECT * FROM sos_events WHERE uploaded = 0 ORDER BY timestamp ASC")
    suspend fun getUnuploaded(): List<SosEventEntity>

    @Query("SELECT id FROM sos_events WHERE timestamp = :timestamp AND victimId = :victimId AND uploaded = 0 LIMIT 1")
    suspend fun findUnuploadedIdByTimestamp(timestamp: Long, victimId: String): Long?

    @Query("UPDATE sos_events SET uploaded = 1 WHERE id = :id")
    suspend fun markUploaded(id: Long)
}


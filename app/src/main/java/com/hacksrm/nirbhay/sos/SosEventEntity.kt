package com.hacksrm.nirbhay.sos

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a persisted SOS event.
 */
@Entity(tableName = "sos_events")
data class SosEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val victimId: String,
    val lat: Double,
    val lng: Double,
    val triggerMethod: String,
    val riskScore: Int,
    val batteryLevel: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val uploaded: Boolean = false,
    val audioFilePath: String? = null,
)


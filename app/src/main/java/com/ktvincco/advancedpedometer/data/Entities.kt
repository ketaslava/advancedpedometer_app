package com.ktvincco.advancedpedometer.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "measurement_cycles")
data class MeasurementCycle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    var startTime: Long,
    var endTime: Long? = null,
    var totalSteps: Int = 0,
    var totalDistance: Double = 0.0
)

@Entity(
    tableName = "path_points",
    foreignKeys = [
        ForeignKey(
            entity = MeasurementCycle::class,
            parentColumns = ["id"],
            childColumns = ["cycleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("cycleId")]
)
data class PathPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cycleId: Long,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val isBreak: Boolean = false
)

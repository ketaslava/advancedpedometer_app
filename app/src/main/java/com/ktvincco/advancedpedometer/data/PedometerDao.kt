package com.ktvincco.advancedpedometer.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PedometerDao {
    @Query("SELECT * FROM measurement_cycles ORDER BY startTime DESC")
    fun getAllCycles(): Flow<List<MeasurementCycle>>

    @Query("SELECT * FROM measurement_cycles WHERE id = :id")
    suspend fun getCycleById(id: Long): MeasurementCycle?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCycle(cycle: MeasurementCycle): Long

    @Update
    suspend fun updateCycle(cycle: MeasurementCycle)

    @Delete
    suspend fun deleteCycle(cycle: MeasurementCycle)

    @Query("SELECT * FROM path_points WHERE cycleId = :cycleId ORDER BY timestamp ASC")
    fun getPointsForCycle(cycleId: Long): Flow<List<PathPoint>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoint(point: PathPoint)

    @Query("DELETE FROM path_points WHERE cycleId = :cycleId")
    suspend fun deletePointsForCycle(cycleId: Long)

    @Query("DELETE FROM path_points WHERE id = (SELECT id FROM path_points WHERE cycleId = :cycleId ORDER BY timestamp DESC LIMIT 1)")
    suspend fun deleteLastPoint(cycleId: Long)

    @Query("SELECT SUM(totalSteps) FROM measurement_cycles")
    fun getTotalSteps(): Flow<Int?>
}

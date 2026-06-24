package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY createdAt DESC")
    fun getAllRoutines(): Flow<List<Routine>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(routine: Routine): Long

    @Update
    suspend fun updateRoutine(routine: Routine)

    @Delete
    suspend fun deleteRoutine(routine: Routine)

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun getRoutineById(id: Int): Routine?

    // Log queries
    @Query("SELECT * FROM routine_logs ORDER BY date ASC LIMIT :limit")
    fun getRecentLogs(limit: Int = 7): Flow<List<RoutineLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateLog(log: RoutineLog)

    @Query("SELECT * FROM routine_logs WHERE date = :date")
    suspend fun getLogForDate(date: String): RoutineLog?
}

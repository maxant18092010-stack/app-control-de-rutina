package com.example.data

import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RoutineRepository(private val routineDao: RoutineDao) {
    val allRoutines: Flow<List<Routine>> = routineDao.getAllRoutines()
    val recentLogs: Flow<List<RoutineLog>> = routineDao.getRecentLogs(7)

    fun getCurrentDateString(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(Date())
    }

    suspend fun insertRoutine(routine: Routine): Long {
        return routineDao.insertRoutine(routine)
    }

    suspend fun updateRoutine(routine: Routine) {
        routineDao.updateRoutine(routine)
    }

    suspend fun deleteRoutine(routine: Routine) {
        routineDao.deleteRoutine(routine)
    }

    suspend fun toggleRoutineCompleted(routine: Routine, isCompleted: Boolean) {
        val today = getCurrentDateString()
        val updatedRoutine = routine.copy(
            isCompleted = isCompleted,
            completedDate = if (isCompleted) today else null
        )
        routineDao.updateRoutine(updatedRoutine)
    }

    suspend fun saveLogForDate(date: String, completedCount: Int, totalCount: Int) {
        val existing = routineDao.getLogForDate(date)
        val log = RoutineLog(
            id = existing?.id ?: 0,
            date = date,
            completedCount = completedCount,
            totalCount = totalCount
        )
        routineDao.insertOrUpdateLog(log)
    }
}

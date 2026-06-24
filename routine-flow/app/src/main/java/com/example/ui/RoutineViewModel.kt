package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.Routine
import com.example.data.RoutineLog
import com.example.data.RoutineRepository
import com.example.util.AlarmSchedulerHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class RoutineViewModel(private val repository: RoutineRepository) : ViewModel() {

    val allRoutines: StateFlow<List<Routine>> = repository.allRoutines
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val recentLogs: StateFlow<List<RoutineLog>> = repository.recentLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Derived states
    val todayDateString: String = repository.getCurrentDateString()

    val todayProgress: StateFlow<ProgressStats> = allRoutines
        .combine(recentLogs) { routines, logs ->
            val total = routines.size
            val completed = routines.count { it.isCompleted && it.completedDate == todayDateString }
            val streak = calculateStreak(logs, todayDateString)
            ProgressStats(completed, total, streak)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ProgressStats(0, 0, 0)
        )

    init {
        // Daily rollover reset and sync
        viewModelScope.launch {
            repository.allRoutines.collect { routines ->
                val todayStr = repository.getCurrentDateString()
                val staleRoutines = routines.filter { it.isCompleted && it.completedDate != todayStr }
                if (staleRoutines.isNotEmpty()) {
                    staleRoutines.forEach { stale ->
                        repository.updateRoutine(stale.copy(isCompleted = false, completedDate = null))
                    }
                } else if (routines.isNotEmpty()) {
                    val completed = routines.count { it.isCompleted && it.completedDate == todayStr }
                    val total = routines.size
                    repository.saveLogForDate(todayStr, completed, total)
                }
            }
        }
    }

    fun addRoutine(context: Context, title: String, description: String, priority: String, category: String, reminderTime: String?) {
        viewModelScope.launch {
            val routine = Routine(
                title = title,
                description = description,
                priority = priority,
                category = category,
                reminderTime = reminderTime,
                isCompleted = false,
                completedDate = null
            )
            val newId = repository.insertRoutine(routine)
            if (reminderTime != null) {
                // Fetch full routine with ID to schedule alarm
                val scheduledRoutine = routine.copy(id = newId.toInt())
                AlarmSchedulerHelper.scheduleAlarm(context, scheduledRoutine)
            }
        }
    }

    fun updateRoutine(context: Context, routine: Routine) {
        viewModelScope.launch {
            repository.updateRoutine(routine)
            if (routine.reminderTime != null) {
                AlarmSchedulerHelper.scheduleAlarm(context, routine)
            } else {
                AlarmSchedulerHelper.cancelAlarm(context, routine)
            }
        }
    }

    fun toggleRoutineCompleted(routine: Routine, isCompleted: Boolean) {
        viewModelScope.launch {
            repository.toggleRoutineCompleted(routine, isCompleted)
        }
    }

    fun deleteRoutine(context: Context, routine: Routine) {
        viewModelScope.launch {
            AlarmSchedulerHelper.cancelAlarm(context, routine)
            repository.deleteRoutine(routine)
        }
    }

    private fun calculateStreak(logs: List<RoutineLog>, todayStr: String): Int {
        if (logs.isEmpty()) return 0
        // Find distinct logs with entries
        val sortedLogs = logs.filter { it.totalCount > 0 }.sortedByDescending { it.date }
        if (sortedLogs.isEmpty()) return 0

        val completedDates = sortedLogs.filter { it.completedCount == it.totalCount && it.totalCount > 0 }
            .map { it.date }
            .toSet()

        var streak = 0
        val calendar = Calendar.getInstance()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        // If today is fully completed, start from today. Else start checking from yesterday.
        var checkDateStr = if (completedDates.contains(todayStr)) {
            todayStr
        } else {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            sdf.format(calendar.time)
        }

        while (completedDates.contains(checkDateStr)) {
            streak++
            val d = sdf.parse(checkDateStr) ?: break
            calendar.time = d
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            checkDateStr = sdf.format(calendar.time)
        }

        return streak
    }
}

data class ProgressStats(
    val completedCount: Int,
    val totalCount: Int,
    val currentStreak: Int
)

class RoutineViewModelFactory(private val repository: RoutineRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RoutineViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RoutineViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

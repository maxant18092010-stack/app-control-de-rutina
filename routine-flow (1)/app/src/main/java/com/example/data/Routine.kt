package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routines")
data class Routine(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val priority: String, // "High", "Medium", "Low"
    val category: String, // "Morning", "Afternoon", "Evening", "Health", "Work", "Custom"
    val reminderTime: String?, // "HH:MM" format or null
    val isCompleted: Boolean = false,
    val completedDate: String? = null, // "YYYY-MM-DD" of last completion
    val repeatDays: String = "Mon,Tue,Wed,Thu,Fri,Sat,Sun", // Comma-separated list of short weekdays: Mon, Tue, Wed, Thu, Fri, Sat, Sun
    val createdAt: Long = System.currentTimeMillis()
)

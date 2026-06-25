package com.example

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppDatabase
import com.example.data.Routine
import com.example.data.RoutineLog
import com.example.data.RoutineRepository
import com.example.ui.ProgressStats
import com.example.ui.RoutineViewModel
import com.example.ui.RoutineViewModelFactory
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Database & Repository
        val database = AppDatabase.getDatabase(this)
        val repository = RoutineRepository(database.routineDao())

        setContent {
            MyApplicationTheme {
                // Request notification permissions for Android 13+
                var hasNotificationPermission by remember {
                    mutableStateOf(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                        } else {
                            true
                        }
                    )
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    hasNotificationPermission = isGranted
                    if (!isGranted) {
                        Toast.makeText(
                            this,
                            "Enable notifications to receive routine reminders!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: RoutineViewModel = viewModel(
                        factory = RoutineViewModelFactory(repository)
                    )
                    RoutineDashboardScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun RoutineDashboardScreen(viewModel: RoutineViewModel) {
    val context = LocalContext.current
    val routines by viewModel.allRoutines.collectAsStateWithLifecycle()
    val recentLogs by viewModel.recentLogs.collectAsStateWithLifecycle()
    val progressStats by viewModel.todayProgress.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingRoutine by remember { mutableStateOf<Routine?>(null) }
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var selectedDayFilter by remember { mutableStateOf(viewModel.getCurrentDayOfWeek()) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .testTag("add_routine_fab"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Routine")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Routine", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            IndigoDarkBg
                        )
                    )
                )
                .padding(innerPadding)
        ) {
            // Screen Header
            DashboardHeader(streak = progressStats.currentStreak)

            // Categorized Scrollable Area
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hero Banner & Quick Stats Box
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        HeroBannerCard()
                        StatsCardsRow(stats = progressStats, routines = routines)
                    }
                }

                // 7-day Progress Chart Card
                item {
                    WeeklyProgressCard(recentLogs = recentLogs, todayDateString = viewModel.todayDateString)
                }

                // Filters
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Scheduled Days",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = CosmicGray,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        WeekdayFiltersRow(
                            selectedDay = selectedDayFilter,
                            onDaySelected = { selectedDayFilter = it },
                            routines = routines
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Categories",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = CosmicGray,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        CategoryFiltersRow(
                            selectedCategory = selectedCategoryFilter,
                            onCategorySelected = { selectedCategoryFilter = it },
                            routines = routines
                        )
                    }
                }

                // Routines List
                val filteredRoutines = routines.filter { routine ->
                    val matchesCategory = selectedCategoryFilter == "All" || routine.category == selectedCategoryFilter
                    val matchesDay = selectedDayFilter == "All" || routine.repeatDays.contains(selectedDayFilter, ignoreCase = true)
                    matchesCategory && matchesDay
                }

                if (filteredRoutines.isEmpty()) {
                    item {
                        EmptyStateView(category = selectedCategoryFilter, day = selectedDayFilter)
                    }
                } else {
                    items(filteredRoutines, key = { it.id }) { routine ->
                        RoutineItemCard(
                            routine = routine,
                            onToggleComplete = { isCompleted ->
                                viewModel.toggleRoutineCompleted(routine, isCompleted)
                            },
                            onEditClick = {
                                editingRoutine = routine
                            },
                            onDeleteClick = {
                                viewModel.deleteRoutine(context, routine)
                            }
                        )
                    }
                }
            }
        }
    }

    // Add Routine Dialog
    if (showAddDialog) {
        AddEditRoutineDialog(
            onDismiss = { showAddDialog = false },
            onSave = { title, desc, priority, category, reminderTime, repeatDays ->
                viewModel.addRoutine(context, title, desc, priority, category, reminderTime, repeatDays)
                showAddDialog = false
            }
        )
    }

    // Edit Routine Dialog
    editingRoutine?.let { routine ->
        AddEditRoutineDialog(
            routineToEdit = routine,
            onDismiss = { editingRoutine = null },
            onSave = { title, desc, priority, category, reminderTime, repeatDays ->
                viewModel.updateRoutine(
                    context,
                    routine.copy(
                        title = title,
                        description = desc,
                        priority = priority,
                        category = category,
                        reminderTime = reminderTime,
                        repeatDays = repeatDays
                    )
                )
                editingRoutine = null
            }
        )
    }
}

@Composable
fun DashboardHeader(streak: Int) {
    val currentDate = remember {
        val sdf = SimpleDateFormat("EEEE, MMMM dd", Locale.getDefault())
        sdf.format(Date())
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Routine Flow",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = currentDate,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = CosmicGray,
                    fontWeight = FontWeight.Medium
                )
            )
        }

        // Streak Chip
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (streak > 0) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surface
                )
                .clickable { /* Streak Info could go here */ }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Streak",
                    tint = if (streak > 0) MaterialTheme.colorScheme.tertiary else CosmicGray,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (streak > 0) "$streak Day Streak" else "No Streak",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (streak > 0) MaterialTheme.colorScheme.tertiary else CosmicGray
                )
            }
        }
    }
}

@Composable
fun HeroBannerCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = com.example.R.drawable.img_routine_hero_1782260106106),
                contentDescription = "Routine Landscape Illustration",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.75f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Control Your Routine",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Control your days, establish habits, reach milestones.",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun StatsCardsRow(stats: ProgressStats, routines: List<Routine>) {
    val progress = if (stats.totalCount > 0) {
        stats.completedCount.toFloat() / stats.totalCount
    } else {
        0f
    }

    val animatedProgress by animateFloatAsState(targetValue = progress, label = "progress")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Completion Card
        Card(
            modifier = Modifier
                .weight(1.2f)
                .height(115.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Today's Flow", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = CosmicGray)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        fontWeight = FontWeight.Black,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${stats.completedCount}/${stats.totalCount} Done",
                        fontSize = 11.sp,
                        color = CosmicGray,
                        fontWeight = FontWeight.Medium
                    )
                }

                Box(
                    modifier = Modifier.size(55.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = CosmicBorder,
                            style = Stroke(width = 6.dp.toPx())
                        )
                        drawArc(
                            color = IndigoPrimary,
                            startAngle = -90f,
                            sweepAngle = animatedProgress * 360f,
                            useCenter = false,
                            style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Text(
                        text = "${stats.completedCount}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Details summary card
        Card(
            modifier = Modifier
                .weight(1f)
                .height(115.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Alarms", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = CosmicGray)

                val alarmsCount = routines.count { it.reminderTime != null }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Alarms",
                        tint = if (alarmsCount > 0) MaterialTheme.colorScheme.tertiary else CosmicGray,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$alarmsCount Active",
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp
                    )
                }

                Text(
                    text = "Triggers configured",
                    fontSize = 10.sp,
                    color = CosmicGray
                )
            }
        }
    }
}

@Composable
fun WeeklyProgressCard(recentLogs: List<RoutineLog>, todayDateString: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Completion Analytics",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = CosmicWhite
            )

            // Let's compute last 7 days including today
            val last7Days = remember(recentLogs) {
                val list = mutableListOf<DayProgress>()
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val dayFormat = SimpleDateFormat("E", Locale.getDefault())
                val calendar = Calendar.getInstance()

                // Go back 6 days + today
                for (i in 6 downTo 0) {
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.DAY_OF_YEAR, -i)
                    val dateStr = sdf.format(cal.time)
                    val label = dayFormat.format(cal.time)

                    val log = recentLogs.find { it.date == dateStr }
                    val rate = if (log != null && log.totalCount > 0) {
                        log.completedCount.toFloat() / log.totalCount
                    } else {
                        0f
                    }
                    list.add(DayProgress(label, rate, dateStr == todayDateString))
                }
                list
            }

            // Draw custom Canvas bar chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val barWidth = 24.dp.toPx()
                    val spacing = (size.width - (barWidth * last7Days.size)) / (last7Days.size + 1)
                    val maxBarHeight = size.height - 20.dp.toPx()

                    last7Days.forEachIndexed { index, day ->
                        val x = spacing + index * (barWidth + spacing)
                        val barHeight = day.completionRate * maxBarHeight

                        // Background pillar
                        drawRoundRect(
                            color = CosmicBorder,
                            topLeft = Offset(x, 0f),
                            size = Size(barWidth, maxBarHeight),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )

                        // Highlight pill
                        if (barHeight > 0) {
                            drawRoundRect(
                                color = if (day.isToday) IndigoPrimary else IndigoSecondary,
                                topLeft = Offset(x, maxBarHeight - barHeight),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                        }
                    }
                }
            }

            // Labels Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                last7Days.forEach { day ->
                    Text(
                        text = day.dayLabel,
                        fontSize = 11.sp,
                        fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (day.isToday) IndigoPrimaryLight else CosmicGray,
                        modifier = Modifier.width(32.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

data class DayProgress(
    val dayLabel: String,
    val completionRate: Float,
    val isToday: Boolean
)

@Composable
fun CategoryFiltersRow(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    routines: List<Routine>
) {
    val categories = listOf("All", "Morning", "Afternoon", "Evening", "Health", "Work", "Custom")
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { category ->
            val count = if (category == "All") {
                routines.size
            } else {
                routines.count { it.category == category }
            }

            val isSelected = selectedCategory == category

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface
                    )
                    .clickable { onCategorySelected(category) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = category,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else CosmicWhite
                    )
                    if (count > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) Color.White.copy(alpha = 0.25f)
                                    else CosmicBorder
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "$count",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else CosmicGray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WeekdayFiltersRow(
    selectedDay: String,
    onDaySelected: (String) -> Unit,
    routines: List<Routine>
) {
    val weekDays = listOf("All", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        weekDays.forEach { day ->
            val count = if (day == "All") {
                routines.size
            } else {
                routines.count { it.repeatDays.contains(day, ignoreCase = true) }
            }

            val isSelected = selectedDay == day

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.surface
                    )
                    .clickable { onDaySelected(day) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (day == "All") "All Week" else day,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.onSecondary else CosmicWhite
                    )
                    if (count > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) Color.White.copy(alpha = 0.25f)
                                    else CosmicBorder
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "$count",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else CosmicGray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RoutineItemCard(
    routine: Routine,
    onToggleComplete: (Boolean) -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    val priorityColor = when (routine.priority) {
        "High" -> PriorityHigh
        "Medium" -> PriorityMedium
        else -> PriorityLow
    }

    // Interactive complete pop scale animation (makes completed cards pop/scale bigger dynamically!)
    val completionScale by animateFloatAsState(
        targetValue = if (routine.isCompleted) 1.03f else 1.00f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "completion_scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(
                scaleX = completionScale,
                scaleY = completionScale
            )
            .animateContentSize()
            .testTag("routine_card_${routine.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (routine.isCompleted) MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(24.dp), // More modern, beautifully rounded large card
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (routine.isCompleted) 6.dp else 3.dp
        ),
        onClick = { isExpanded = !isExpanded }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isExpanded) 22.dp else 18.dp), // Visually bigger and spacious padding
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Priority Tag Indicator - grows bigger when card is expanded!
                Box(
                    modifier = Modifier
                        .width(if (isExpanded) 8.dp else 6.dp) // Thicker indicator
                        .height(if (isExpanded) 95.dp else 60.dp) // Taller indicator
                        .clip(CircleShape)
                        .background(priorityColor)
                )

                // Details
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = routine.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isExpanded) 22.sp else 18.sp, // Distinctly larger heading typography
                            color = if (routine.isCompleted) CosmicGray else CosmicWhite,
                            maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Category Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(CosmicBorder)
                                .padding(horizontal = 12.dp, vertical = 5.dp) // Larger tag padding
                        ) {
                            Text(
                                text = routine.category,
                                fontSize = 12.sp, // Larger font size
                                fontWeight = FontWeight.Bold,
                                color = CosmicGray
                            )
                        }
                    }

                    if (routine.description.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = routine.description,
                            fontSize = if (isExpanded) 15.sp else 14.sp, // Larger readable description text
                            color = CosmicGray,
                            maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = if (isExpanded) 22.sp else 19.sp // More breathing space for line-height
                        )
                    }

                    // Scheduled trigger status
                    routine.reminderTime?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Reminder Alert Set",
                                tint = IndigoPrimaryLight,
                                modifier = Modifier.size(18.dp) // Larger notification bell icon
                            )
                            Text(
                                text = "Reminder: $it",
                                fontSize = 13.sp, // Larger reminder text
                                fontWeight = FontWeight.Bold,
                                color = IndigoPrimaryLight
                            )
                        }
                    }
                }

                // Completion Toggle Checkbox
                Checkbox(
                    checked = routine.isCompleted,
                    onCheckedChange = { checked ->
                        if (checked) {
                            // Play a double acknowledgement beep sound to celebrate routine completion!
                            try {
                                val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                                toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 160)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        onToggleComplete(checked)
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color.White,
                        uncheckedColor = Color.White.copy(alpha = 0.7f),
                        checkmarkColor = IndigoSurface
                    ),
                    modifier = Modifier
                        .size(54.dp) // Even larger accessible touch target
                        .scale(1.25f) // Scale the Checkbox drawing itself so the checkmark circle is bigger!
                        .testTag("routine_checkbox_${routine.id}")
                )
            }

            // Expanded Mode: Extra beautiful info card
            if (isExpanded) {
                HorizontalDivider(color = CosmicBorder, thickness = 1.2.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Priority Level: ${routine.priority}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = priorityColor
                        )
                        if (routine.isCompleted) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Done",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Completed today!",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        } else {
                            Text(
                                text = "Pending today's completion",
                                fontSize = 13.sp,
                                color = CosmicGray
                            )
                        }
                    }

                    // Action Controls Block
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onEditClick,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(CosmicBorder)
                                .size(44.dp) // Larger buttons
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Routine",
                                tint = CosmicWhite,
                                modifier = Modifier.size(20.dp) // Larger icon inside button
                            )
                        }

                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(PriorityHigh.copy(alpha = 0.1f))
                                .size(44.dp) // Larger buttons
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Routine",
                                tint = PriorityHigh,
                                modifier = Modifier.size(20.dp) // Larger icon inside button
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateView(category: String, day: String) {
    val message = when {
        category == "All" && day == "All" -> "Your routine is empty!"
        category == "All" -> "No routines scheduled for $day"
        day == "All" -> "No routines in $category"
        else -> "No $category routines scheduled for $day"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "No tasks",
                tint = CosmicDarkGray,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = message,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = CosmicWhite,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Tap the 'Add Routine' button below to define custom routines, configure priority levels, and trigger local alerts.",
                fontSize = 12.sp,
                color = CosmicGray,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun AddEditRoutineDialog(
    routineToEdit: Routine? = null,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String?, String) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(routineToEdit?.title ?: "") }
    var desc by remember { mutableStateOf(routineToEdit?.description ?: "") }
    var selectedPriority by remember { mutableStateOf(routineToEdit?.priority ?: "Medium") }
    var selectedCategory by remember { mutableStateOf(routineToEdit?.category ?: "Morning") }
    var reminderTime by remember { mutableStateOf(routineToEdit?.reminderTime) }

    val weekDays = remember { listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun") }
    val initialRepeatDays = remember {
        routineToEdit?.repeatDays?.split(",") ?: weekDays
    }
    var selectedDays by remember { mutableStateOf(initialRepeatDays.toSet()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (routineToEdit != null) "Edit Routine" else "New Routine",
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    color = CosmicWhite
                )

                // Title Input
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Routine Name") },
                    placeholder = { Text("e.g. Drink Water, Morning Gym") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = CosmicBorder
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("routine_title_input")
                )

                // Description Input
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description (Optional)") },
                    placeholder = { Text("Explain or add steps...") },
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = CosmicBorder
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("routine_desc_input")
                )

                // Priority Selection
                Text("Priority Level", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = CosmicGray)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Low", "Medium", "High").forEach { priority ->
                        val isSelected = selectedPriority == priority
                        val btnColor = when (priority) {
                            "High" -> PriorityHigh
                            "Medium" -> PriorityMedium
                            else -> PriorityLow
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) btnColor else CosmicBorder
                                )
                                .clickable { selectedPriority = priority }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = priority,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else CosmicGray
                            )
                        }
                    }
                }

                // Category Selection
                Text("Category", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = CosmicGray)
                val categories = listOf("Morning", "Afternoon", "Evening", "Health", "Work", "Custom")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { category ->
                        val isSelected = selectedCategory == category
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else CosmicBorder
                                )
                                .clickable { selectedCategory = category }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = category,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else CosmicGray
                            )
                        }
                    }
                }

                // Days of Week Selection
                Text("Repeat on Days", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = CosmicGray)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isAllWeek = selectedDays.size == 7
                    Text(
                        text = if (isAllWeek) "Every day of the week" else "${selectedDays.size} days selected",
                        fontSize = 12.sp,
                        color = if (isAllWeek) MaterialTheme.colorScheme.secondary else CosmicGray,
                        fontWeight = FontWeight.Medium
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isAllWeek) MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f) else CosmicBorder)
                            .clickable {
                                selectedDays = if (isAllWeek) emptySet() else weekDays.toSet()
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isAllWeek) "Deselect All" else "Select All",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isAllWeek) MaterialTheme.colorScheme.secondary else CosmicWhite
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    weekDays.forEach { day ->
                        val isDaySelected = selectedDays.contains(day)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(
                                    if (isDaySelected) MaterialTheme.colorScheme.primary
                                    else CosmicBorder
                                )
                                .clickable {
                                    selectedDays = if (isDaySelected) {
                                        selectedDays - day
                                    } else {
                                        selectedDays + day
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = day.take(2),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isDaySelected) Color.White else CosmicGray
                            )
                        }
                    }
                }

                // Reminder / Alarm Setup
                Text("Daily Alert Trigger", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = CosmicGray)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(CosmicBorder)
                            .clickable {
                                val calendar = Calendar.getInstance()
                                val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
                                val currentMinute = calendar.get(Calendar.MINUTE)

                                TimePickerDialog(
                                    context,
                                    { _, hourOfDay, minute ->
                                        reminderTime = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
                                    },
                                    currentHour,
                                    currentMinute,
                                    true
                                ).show()
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Notifications, contentDescription = "Alarm", tint = IndigoPrimaryLight, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = reminderTime ?: "Set Alarm Alert",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (reminderTime != null) IndigoPrimaryLight else CosmicGray
                            )
                        }
                    }

                    if (reminderTime != null) {
                        TextButton(onClick = { reminderTime = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Alarm", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear", fontSize = 12.sp)
                        }
                    }
                }

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CosmicWhite)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                Toast.makeText(context, "Routine name cannot be empty!", Toast.LENGTH_SHORT).show()
                            } else if (selectedDays.isEmpty()) {
                                Toast.makeText(context, "Please select at least one repeat day!", Toast.LENGTH_SHORT).show()
                            } else {
                                onSave(
                                    title.trim(),
                                    desc.trim(),
                                    selectedPriority,
                                    selectedCategory,
                                    reminderTime,
                                    selectedDays.joinToString(",")
                                )
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_routine_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

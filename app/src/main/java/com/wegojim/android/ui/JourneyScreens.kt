package com.wegojim.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.wegojim.android.app
import com.wegojim.android.data.JourneyAchievementGoal
import com.wegojim.android.data.JourneyAchievementSeries
import com.wegojim.android.data.JourneyAchievements
import com.wegojim.android.data.JourneyDay
import com.wegojim.android.data.JourneyFact
import com.wegojim.android.data.JourneyFormat
import com.wegojim.android.data.JourneyMilestone
import com.wegojim.android.data.JourneyMonth
import com.wegojim.android.data.JourneySnapshot
import com.wegojim.android.data.JourneyYear
import com.wegojim.android.data.MilestoneKind
import com.wegojim.android.data.TrainingJourney
import com.wegojim.android.data.TrainingYearRecap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Rebuilds the Journey from saved history whenever it or the units change. Null while loading. */
@Composable
fun rememberJourney(): JourneySnapshot? {
    val app = LocalContext.current.app
    val workouts by app.repository.workouts.collectAsStateWithLifecycle(null)
    val catalog by app.repository.exercises.collectAsStateWithLifecycle(emptyList())
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val snapshot by produceState<JourneySnapshot?>(null, workouts, catalog, settings.distanceUnit, settings.weightUnit) {
        val list = workouts ?: return@produceState
        value = withContext(Dispatchers.Default) {
            val (journeyWorkouts, exercises) = TrainingJourney.inputs(list, catalog.associateBy { it.uuid })
            TrainingJourney.build(journeyWorkouts, exercises, settings.distanceUnit, settings.weightUnit)
        }
    }
    return snapshot
}

/** Entry card on the Progress tab (port of TrainingJourneyEntryView). */
@Composable
fun JourneyEntryCard(onOpen: () -> Unit) {
    SectionCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(Wgj.colors.cyan.copy(alpha = 0.12f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.AutoAwesome, null, tint = Wgj.colors.cyan)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Your Journey", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Explore your training story", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private enum class JourneyFilter(val title: String) {
    All("All"), Records("PRs"), Strength("Strength"), Cardio("Cardio"), Milestones("Milestones");

    fun includes(e: JourneyMilestone) = when (this) {
        All -> true
        Records -> e.personalRecord != null
        Strength -> e.kind == MilestoneKind.strength
        Cardio -> e.kind == MilestoneKind.cardio
        Milestones -> e.kind != MilestoneKind.strength && e.kind != MilestoneKind.cardio
    }
}

private val zone: ZoneId get() = ZoneId.systemDefault()
private fun Long.day(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

/** Port of TrainingJourneyView: lifetime totals, a yearly calendar, what's next and the moments that mattered. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneyScreen(nav: NavHostController) {
    val app = LocalContext.current.app
    val snapshot = rememberJourney()
    var selectedYear by rememberSaveable { mutableStateOf<Int?>(null) }
    var filter by rememberSaveable { mutableStateOf(JourneyFilter.All) }
    var month by remember { mutableStateOf<JourneyMonth?>(null) }
    var recap by remember { mutableStateOf<List<TrainingYearRecap>?>(null) }
    var yearMenu by remember { mutableStateOf(false) }
    var expandedDays by remember { mutableStateOf(setOf<LocalDate>()) }
    var celebrate by remember { mutableIntStateOf(0) }

    LaunchedEffect(snapshot) {
        val s = snapshot ?: return@LaunchedEffect
        if (app.settings.claimCelebrations(s.milestones) > 0) celebrate++
    }

    Box {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Your Journey") },
                    navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                    actions = {
                        val years = snapshot?.years.orEmpty()
                        if ((snapshot?.workoutCount ?: 0) > 0) Box {
                            TextButton(onClick = { yearMenu = true }) {
                                Text("${(years.firstOrNull { it.year == selectedYear } ?: years.firstOrNull())?.year ?: ""} ▾", fontWeight = FontWeight.SemiBold)
                            }
                            DropdownMenu(expanded = yearMenu, onDismissRequest = { yearMenu = false }) {
                                years.forEach { y -> DropdownMenuItem(text = { Text(y.title) }, onClick = { selectedYear = y.year; yearMenu = false }) }
                            }
                        }
                    },
                )
            },
        ) { padding ->
            val s = snapshot
            if (s == null) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Scaffold
            }
            val groups = remember(s, filter) {
                s.milestones.filter(filter::includes).groupBy { it.date.day() }.toList().sortedByDescending { it.first }
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (s.workoutCount == 0) {
                    item { EmptyState(Icons.Default.AutoAwesome, "Your story starts here", "Finish your first workout to start your journey. Every session becomes part of the bigger picture.") }
                    item { JourneyInsightsSection(s, nav) }
                    return@LazyColumn
                }
                item { LifetimeSummary(s) }
                item {
                    SectionCard(onClick = { recap = TrainingYearRecap.build(s) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BarChart, null, tint = Wgj.colors.cyan)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text("My Year in Training", fontWeight = FontWeight.SemiBold)
                                Text("Your year, ready to share.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                item {
                    val year = s.years.firstOrNull { it.year == selectedYear } ?: s.years.firstOrNull()
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Days you showed up", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        year?.let { YearCalendar(it) { m -> month = m } }
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Legend("Strength", MaterialTheme.colorScheme.primary)
                            Legend("Cardio", Wgj.colors.cyan)
                            Legend("Mixed", Wgj.colors.gold)
                        }
                        Text("Tap a month to explore your workouts.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item { JourneyInsightsSection(s, nav) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("The moments that matter", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(JourneyFilter.entries) { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.title) }) }
                        }
                        if (groups.isEmpty()) {
                            Text("Your next milestone is still ahead. Keep showing up.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                groups.forEachIndexed { index, (day, events) ->
                    item(key = "day-$day") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (index == 0 || groups[index - 1].first.year != day.year) {
                                Text("${day.year}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                            }
                            Text(day.format(DateTimeFormatter.ofPattern("d MMMM")), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val sessions = events.groupBy { it.sessionId }.values.sortedWith(compareByDescending<List<JourneyMilestone>> { it.first().date }.thenBy { it.first().sessionId })
                            val expanded = day in expandedDays
                            (if (expanded) sessions else sessions.take(3)).forEach { SessionMoments(it, nav) }
                            if (sessions.size > 3) {
                                TextButton(onClick = { expandedDays = if (expanded) expandedDays - day else expandedDays + day }) {
                                    Text(if (expanded) "Show less" else "Show all ${sessions.size} workouts")
                                }
                            }
                        }
                    }
                }
            }
        }
        if (celebrate > 0) Confetti(celebrate)
    }

    month?.let { m -> MonthDetailDialog(m, onDismiss = { month = null }) { id -> month = null; nav.navigate(Routes.workoutDetail(id)) } }
    recap?.let { r -> YearRecapDialog(r, selectedYear) { recap = null } }
}

@Composable
private fun Legend(title: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
        Text(" $title", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LifetimeSummary(s: JourneySnapshot) {
    val first = s.firstWorkoutDate?.day()
    Surface(shape = RoundedCornerShape(26.dp), color = Color.Transparent) {
        Column(
            Modifier
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), MaterialTheme.colorScheme.surfaceContainer)))
                .padding(22.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("EVERY SESSION ADDS UP", style = MaterialTheme.typography.labelSmall, color = Wgj.colors.cyan, fontWeight = FontWeight.Bold)
                Text("Look how far you've come.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                first?.let {
                    Text("Training since ${it.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())} ${it.year}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val stats = listOf(
                Triple(JourneyFormat.number(s.workoutCount), "Workouts", Icons.Default.FitnessCenter),
                Triple(JourneyFormat.time(s.durationSeconds), "Training time", Icons.Default.Schedule),
                Triple(JourneyFormat.number(s.activeDays), "Active days", Icons.Default.CalendarMonth),
                Triple(JourneyFormat.distance(s.walkRunDistanceMeters, s.distanceUnit), "Walking & running", Icons.AutoMirrored.Filled.DirectionsRun),
                Triple(JourneyFormat.distance(s.otherCardioDistanceMeters, s.distanceUnit), "Cycling & other cardio", Icons.AutoMirrored.Filled.DirectionsBike),
                Triple(JourneyFormat.number(s.recordCount), "Journey PRs", Icons.Default.EmojiEvents),
            )
            stats.chunked(2).forEach { row ->
                Row {
                    row.forEach { (value, title, icon) ->
                        Column(Modifier.weight(1f)) {
                            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(" $title", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YearCalendar(year: JourneyYear, onSelect: (JourneyMonth) -> Unit) {
    val strength = MaterialTheme.colorScheme.primary
    val cardio = Wgj.colors.cyan
    val mixed = Wgj.colors.gold
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    fun color(day: JourneyDay) = when {
        day.workoutCount == 0 -> empty
        day.hasStrength && day.hasCardio -> mixed
        day.hasCardio -> cardio
        else -> strength
    }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        year.months.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { month ->
                    Column(
                        Modifier.weight(1f).clickableNoRipple { onSelect(month) }.semantics {
                            contentDescription = "${JourneyFormat.monthYear(month.month)}, ${month.workouts.size} workouts, ${month.activeDays} active days"
                        },
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(month.month.month.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.getDefault()), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        val cells = List(month.leadingDays) { null } + month.days
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            cells.chunked(7).forEach { week ->
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    week.forEach { day ->
                                        Box(Modifier.weight(1f).aspectRatio(1f).background(day?.let(::color) ?: Color.Transparent, RoundedCornerShape(2.dp)))
                                    }
                                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                        Text(plural(month.activeDays, "active day"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Port of JourneyInsightsSection: the next three milestones and the way into achievements. */
@Composable
private fun JourneyInsightsSection(s: JourneySnapshot, nav: NavHostController) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (s.insights.nextMilestones.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Next up", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { nav.navigate(Routes.achievements("goals")) }) { Text("See all") }
            }
            Text("Good things ahead. No deadlines.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            s.insights.nextMilestones.forEach { m ->
                SectionCard {
                    Text(m.title, fontWeight = FontWeight.SemiBold)
                    LinearProgressIndicator(progress = { m.progress.toFloat() }, modifier = Modifier.fillMaxWidth(), color = Wgj.colors.gold)
                    Text(m.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        SectionCard(onClick = { nav.navigate(Routes.achievements("earned")) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MilitaryTech, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text("Your achievements", fontWeight = FontWeight.SemiBold)
                    Text("Earned moments, goals and stats", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun SessionMoments(events: List<JourneyMilestone>, nav: NavHostController) {
    var expanded by remember { mutableStateOf(false) }
    val ordered = events.sortedWith(compareBy<JourneyMilestone> { it.personalRecord == null }.thenBy { it.id })
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (events.size > 1) Text("${events.size} moments from this workout", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        (if (expanded) ordered else ordered.take(3)).forEach { MilestoneRow(it, nav) }
        if (events.size > 3) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Show less" else "Show all ${events.size} moments") }
    }
}

private fun MilestoneKind.icon(): ImageVector = when (this) {
    MilestoneKind.beginning -> Icons.Default.Flag
    MilestoneKind.workouts -> Icons.Default.EmojiEvents
    MilestoneKind.strength -> Icons.Default.FitnessCenter
    MilestoneKind.cardio -> Icons.AutoMirrored.Filled.DirectionsRun
    MilestoneKind.consistency -> Icons.Default.LocalFireDepartment
    MilestoneKind.celebration -> Icons.Default.AutoAwesome
}

/** Port of JourneyMilestoneRow: opens the workout, shows the trend for PRs, and shares the moment. */
@Composable
fun MilestoneRow(event: JourneyMilestone, nav: NavHostController) {
    SectionCard {
        Row(Modifier.fillMaxWidth().clickableNoRipple { nav.navigate(Routes.workoutDetail(event.sessionId)) }, verticalAlignment = Alignment.Top) {
            Icon(event.kind.icon(), null, Modifier.size(22.dp), tint = Wgj.colors.cyan)
            Column(Modifier.weight(1f).padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                event.playfulTitle?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                Text(event.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(event.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Open workout", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (event.chart.size > 1) Sparkline(event.chart.map { it.value }, "${event.title}. ${event.detail}")
        val record = event.personalRecord
        if (record != null) PrShareButton(record, JourneyFormat.date(event.date), event.playfulTitle) else MilestoneShareButton(event)
        event.activityId?.let { SavedRoute(event.sessionId, it, Modifier.height(150.dp), caption = false) }
    }
}

@Composable
private fun Sparkline(values: List<Double>, description: String) {
    val line = MaterialTheme.colorScheme.primary
    val lo = values.min()
    val hi = values.max()
    Canvas(Modifier.fillMaxWidth().height(60.dp).semantics { contentDescription = description }) {
        val span = if (hi - lo < 1e-9) 1.0 else hi - lo
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * size.width / (values.size - 1)
            val y = (size.height - (v - lo) / span * size.height * 0.85 - size.height * 0.075).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, line, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        val lastY = (size.height - (values.last() - lo) / span * size.height * 0.85 - size.height * 0.075).toFloat()
        drawCircle(line, 3.dp.toPx(), Offset(size.width, lastY))
    }
}

/** Port of JourneyMonthDetail. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthDetailDialog(month: JourneyMonth, onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(JourneyFormat.monthYear(month.month)) },
                    navigationIcon = { TextButton(onClick = onDismiss) { Text("Close") } },
                )
            },
        ) { padding ->
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Text("${plural(month.workouts.size, "workout")} · ${plural(month.activeDays, "active day")}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                if (month.workouts.isEmpty()) {
                    item { Text("No workouts recorded this month.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    item { DayBars(month) }
                    items(month.workouts.sortedByDescending { it.date }, key = { it.id }) { w ->
                        SectionCard(onClick = { onOpen(w.id) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (w.hasCardio && !w.hasStrength) Icons.AutoMirrored.Filled.DirectionsRun else Icons.Default.FitnessCenter, null, tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(w.name, fontWeight = FontWeight.SemiBold)
                                    Text("${formatDayShort(w.date)} · ${formatTime(w.date)} · ${JourneyFormat.time(w.durationSeconds)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayBars(month: JourneyMonth) {
    val bar = MaterialTheme.colorScheme.primary
    val most = month.days.maxOf { it.workoutCount }.coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = "Workouts per day" }) {
        val w = size.width / month.days.size
        month.days.forEachIndexed { i, d ->
            if (d.workoutCount == 0) return@forEachIndexed
            val h = size.height * d.workoutCount / most
            drawRect(bar, Offset(i * w + w * 0.15f, size.height - h), androidx.compose.ui.geometry.Size(w * 0.7f, h))
        }
    }
}

// region Achievements

private enum class AchievementPage(val key: String, val title: String, val icon: ImageVector) {
    Goals("goals", "Goals", Icons.Default.TrackChanges),
    Earned("earned", "Earned", Icons.Default.MilitaryTech),
    Stats("stats", "Stats", Icons.Default.BarChart),
}

/** Port of JourneyAchievementsView: goals, everything earned, and all-time or yearly stats. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(nav: NavHostController, initialPage: String) {
    val snapshot = rememberJourney()
    var page by rememberSaveable { mutableStateOf(AchievementPage.entries.firstOrNull { it.key == initialPage } ?: AchievementPage.Earned) }
    var year by rememberSaveable { mutableStateOf<Int?>(null) }
    var goalFilter by rememberSaveable { mutableIntStateOf(0) }
    var recordFilter by rememberSaveable { mutableIntStateOf(0) }
    var series by remember { mutableStateOf<JourneyAchievementSeries?>(null) }
    var yearMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Achievements") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (page != AchievementPage.Goals && snapshot != null) Box {
                        TextButton(onClick = { yearMenu = true }) { Text("${year ?: "All time"} ▾") }
                        DropdownMenu(expanded = yearMenu, onDismissRequest = { yearMenu = false }) {
                            DropdownMenuItem(text = { Text("All time") }, onClick = { year = null; yearMenu = false })
                            snapshot.years.forEach { y -> DropdownMenuItem(text = { Text(y.title) }, onClick = { year = y.year; yearMenu = false }) }
                        }
                    }
                },
            )
        },
    ) { padding ->
        val s = snapshot
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Segmented(AchievementPage.entries, page, { it.title }) { page = it } }
            when (page) {
                AchievementPage.Goals -> {
                    val all = JourneyAchievements.series(s.achievementGoals)
                    val shown = all.filter { if (goalFilter == 2) true else if (goalFilter == 1) it.latestCleared != null else it.nextGoal != null }
                    item {
                        Text("${s.achievementGoals.count { it.isEarned }} of ${s.achievementGoals.size} goals cleared", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    item { Segmented(listOf(0, 1, 2), goalFilter, { listOf("To unlock", "Cleared", "All goals")[it] }) { goalFilter = it } }
                    shown.map { it.category }.distinct().forEach { category ->
                        item(key = "cat-$category") { Text(category, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp)) }
                        items(shown.filter { it.category == category }, key = { "series-${it.id}" }) { item ->
                            val goal = (if (goalFilter == 1) item.latestCleared else item.currentGoal) ?: return@items
                            GoalCard(goal, item.takeIf { it.goals.size > 1 }) { series = item }
                        }
                    }
                    if (shown.isEmpty()) item { Text("No goals in this view yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                AchievementPage.Earned -> {
                    val sessionIds = s.years.firstOrNull { it.year == year }?.months?.flatMap { it.workouts }?.map { it.id }?.toSet()
                    val earned = s.milestones.filter { e ->
                        (sessionIds == null || e.sessionId in sessionIds) &&
                            (recordFilter == 0 || (recordFilter == 1) == (e.personalRecord != null))
                    }
                    item { Text("${earned.size} achievements", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    item { Segmented(listOf(0, 1, 2), recordFilter, { listOf("All", "PRs", "Milestones")[it] }) { recordFilter = it } }
                    items(earned, key = { it.id }) { e ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(JourneyFormat.date(e.date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            MilestoneRow(e, nav)
                        }
                    }
                    if (earned.isEmpty()) item { Text("Your next achievement is ahead of you.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                AchievementPage.Stats -> {
                    val facts = if (year == null) s.insights.facts else s.insights.years.firstOrNull { it.year == year }?.facts.orEmpty()
                    items(facts, key = { it.id }) { FactCard(it) }
                    if (facts.isEmpty()) item { Text("Complete a workout to start this collection.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }

    series?.let { item -> SeriesDialog(item) { series = null } }
}

@Composable
private fun GoalCard(goal: JourneyAchievementGoal, series: JourneyAchievementSeries? = null, onOpenSeries: () -> Unit = {}) {
    SectionCard(onClick = if (series != null) onOpenSeries else null) {
        series?.let { Text(it.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
        Row(verticalAlignment = Alignment.Top) {
            Icon(if (goal.isEarned) Icons.Default.Verified else Icons.Default.WorkspacePremium, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(start = 12.dp)) {
                Text(goal.title, fontWeight = FontWeight.SemiBold)
                Text(if (goal.isEarned) "Cleared" else "To unlock", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        Text(goal.requirement, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (goal.progress != null && goal.progressText != null) {
            LinearProgressIndicator(
                progress = { goal.progress.toFloat() },
                modifier = Modifier.fillMaxWidth(),
                color = if (goal.isEarned) MaterialTheme.colorScheme.primary else Wgj.colors.gold,
            )
            Text(goal.progressText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        series?.let {
            Row {
                Text("${it.clearedCount} of ${it.goals.size} cleared", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text("View goals ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeriesDialog(series: JourneyAchievementSeries, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(topBar = { TopAppBar(title = { Text(series.title) }, navigationIcon = { TextButton(onClick = onDismiss) { Text("Close") } }) }) { padding ->
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Text("${series.clearedCount} of ${series.goals.size} goals cleared", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(series.goals, key = { it.id }) { goal ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (goal.id == series.nextGoal?.id) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.TrackChanges, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(" Current goal", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        GoalCard(goal)
                    }
                }
            }
        }
    }
}

@Composable
private fun FactCard(fact: JourneyFact) {
    val icon = when (fact.id) {
        "streak" -> Icons.Default.LocalFireDepartment
        "average" -> Icons.Default.FitnessCenter
        "month" -> Icons.Default.CalendarMonth
        "year-comparison" -> Icons.AutoMirrored.Filled.TrendingUp
        "moments" -> Icons.Default.MilitaryTech
        "prs" -> Icons.Default.EmojiEvents
        "favorite" -> Icons.Default.Favorite
        else -> Icons.AutoMirrored.Filled.TrendingUp
    }
    SectionCard {
        Box(Modifier.size(50.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(25.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (fact.id == "prs") Wgj.colors.gold else MaterialTheme.colorScheme.primary)
        }
        Text(fact.value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(fact.title, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// endregion

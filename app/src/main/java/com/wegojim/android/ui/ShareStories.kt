package com.wegojim.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wegojim.android.R
import com.wegojim.android.data.JourneyFormat
import com.wegojim.android.data.JourneyMilestone
import com.wegojim.android.data.PersonalRecord
import com.wegojim.android.data.TrainingYearRecap
import kotlinx.coroutines.launch
import kotlin.math.max

private val storyWhite = Color(0xFFF5F7FA)
private val storyAccent = Color(0xFF6DB5FF)
private val storyCyan = Color(0xFF7CE3EF)
private val storyGold = Color(0xFFE0C56B)

/** Port of TrainingShareBackground: deep blue with two soft glows. */
fun Modifier.storyBackground() = this
    .background(Brush.linearGradient(listOf(Color(0xFF060B16), Color(0xFF09172A), Color(0xFF050911))))
    .drawWithContent {
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFF1A7AF2).copy(alpha = 0.32f), Color.Transparent), center = Offset(size.width * 0.9f, size.height * 0.08f), radius = size.width * 0.75f),
            radius = size.width * 0.75f, center = Offset(size.width * 0.9f, size.height * 0.08f),
        )
        drawCircle(
            Brush.radialGradient(listOf(Color.Cyan.copy(alpha = 0.12f), Color.Transparent), center = Offset(size.width * 0.05f, size.height * 0.95f), radius = size.width * 0.7f),
            radius = size.width * 0.7f, center = Offset(size.width * 0.05f, size.height * 0.95f),
        )
        drawContent()
    }

/** Port of TrainingShareBrandHeader, shared by workout, milestone, PR and recap stories. */
@Composable
fun StoryBrandHeader(timestamp: String, subtitle: String = "TRAINING LOG") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.splash_icon), null, Modifier.size(31.dp).clip(RoundedCornerShape(9.dp)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("WE GO JIM", color = storyWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Text(subtitle, color = Color.White.copy(alpha = 0.46f), fontSize = 7.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        }
        Text(timestamp.uppercase(), color = Color.White.copy(alpha = 0.48f), fontSize = 8.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/**
 * A 9:16 story drawn at a fixed 360×640 dp design size and scaled to fit, so
 * the preview and the shared image always match. Records into [layer].
 */
@Composable
fun StoryFrame(layer: GraphicsLayer, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(22.dp))) {
        val scale = maxWidth / 360.dp
        Box(
            Modifier
                .requiredSize(360.dp, 640.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
                .align(Alignment.TopStart),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    },
            ) { content() }
        }
    }
}

/** Full-screen preview with a Share button, as on iOS. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryPreviewDialog(
    title: String,
    shareLabel: String,
    fileName: String,
    onDismiss: () -> Unit,
    header: @Composable () -> Unit = {},
    story: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") } },
                )
            },
            bottomBar = {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Button(
                        onClick = { scope.launch { shareImage(context, fileName, layer.toImageBitmap().asAndroidBitmap(), shareLabel) } },
                        modifier = Modifier.navigationBarsPadding().padding(16.dp).fillMaxWidth().height(52.dp),
                    ) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text(shareLabel) }
                }
            },
        ) { padding ->
            Column(
                Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                header()
                StoryFrame(layer, Modifier.widthIn(max = 420.dp)) { story() }
            }
        }
    }
}

// region PRs and milestones

/** Port of PersonalRecordShareCard: one achievement, its date, and nothing else. */
@Composable
fun PersonalRecordStory(record: PersonalRecord, achievedAt: String, isMilestone: Boolean, playfulTitle: String?) {
    Column(Modifier.fillMaxSize().storyBackground().padding(horizontal = 28.dp, vertical = 30.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StoryBrandHeader(achievedAt)
        Spacer(Modifier.height(18.dp))
        Box(
            Modifier.size(76.dp).background(storyGold.copy(alpha = 0.08f), CircleShape).border(1.dp, storyGold.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(if (isMilestone) Icons.Default.MilitaryTech else Icons.Default.EmojiEvents, null, Modifier.size(38.dp), tint = storyGold) }
        Text(if (isMilestone) "MILESTONE UNLOCKED" else "PERSONAL RECORD", color = storyGold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text(playfulTitle ?: "New PR. Who dis?", color = storyAccent, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(record.exerciseName, color = storyWhite, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (record.performanceText.isNotEmpty()) {
            Text(record.performanceText, color = Color.White, fontSize = 27.sp, lineHeight = 31.sp, fontWeight = FontWeight.ExtraBold, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        Text(record.detailText, color = Color.White.copy(alpha = 0.65f), fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 5, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun PersonalRecordShareDialog(record: PersonalRecord, achievedAt: String, isMilestone: Boolean = false, playfulTitle: String? = null, onDismiss: () -> Unit) {
    StoryPreviewDialog(
        title = if (isMilestone) "Milestone" else "Personal Record",
        shareLabel = if (isMilestone) "Share Milestone" else "Share PR",
        fileName = record.exerciseName,
        onDismiss = onDismiss,
    ) { PersonalRecordStory(record, achievedAt, isMilestone, playfulTitle) }
}

/** "Share PR" link for one record (port of PersonalRecordShareButton). */
@Composable
fun PrShareButton(record: PersonalRecord, achievedAt: String, playfulTitle: String? = null) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Icon(Icons.Default.Share, null, Modifier.size(18.dp)); Text(" Share PR") }
    if (open) PersonalRecordShareDialog(record, achievedAt, playfulTitle = playfulTitle) { open = false }
}

/** "Share Milestone" link (port of JourneyMilestoneShareButton). */
@Composable
fun MilestoneShareButton(event: JourneyMilestone) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Icon(Icons.Default.Share, null, Modifier.size(18.dp)); Text(" Share Milestone") }
    if (open) {
        PersonalRecordShareDialog(
            PersonalRecord(event.id, event.title, "", event.detail),
            JourneyFormat.date(event.date),
            isMilestone = true,
            playfulTitle = event.playfulTitle ?: "The training montage is paying off",
        ) { open = false }
    }
}

// endregion

// region Year recap

/** Port of TrainingYearRecapCard. */
@Composable
fun YearRecapStory(recap: TrainingYearRecap) {
    val dim = Color.White.copy(alpha = 0.5f)
    Column(
        Modifier.fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF060B16), Color(0xFF062135), Color(0xFF050D14))))
            .padding(28.dp),
    ) {
        StoryBrandHeader("", subtitle = "MY YEAR IN TRAINING")
        Row(Modifier.padding(top = 24.dp), verticalAlignment = Alignment.Top) {
            Text("My year\nin training.", color = storyWhite, fontSize = 30.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${recap.year}", color = storyCyan, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Bottom) {
            Text(JourneyFormat.number(recap.workoutCount), color = storyCyan, fontSize = 72.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("  WORKOUTS", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 16.dp))
        }
        @Composable
        fun metric(value: String, label: String, modifier: Modifier) = Column(modifier) {
            Text(value, color = storyWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, color = Color.White.copy(alpha = 0.45f), fontSize = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        }
        Row(Modifier.padding(top = 12.dp)) {
            metric(JourneyFormat.time(recap.durationSeconds), "TRAINING TIME", Modifier.weight(1f))
            metric(JourneyFormat.number(recap.activeDays), "ACTIVE DAYS", Modifier.weight(1f))
        }
        Row(Modifier.padding(top = 14.dp)) {
            metric(JourneyFormat.distance(recap.walkRunDistanceMeters, recap.distanceUnit), "WALKING & RUNNING", Modifier.weight(1f))
            metric(JourneyFormat.number(recap.trainingWeeks), "TRAINING WEEKS", Modifier.weight(1f))
        }
        Text("WORKOUTS THROUGH THE YEAR", color = Color.White.copy(alpha = 0.55f), fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 26.dp, bottom = 10.dp))
        val most = max(1, recap.months.maxOfOrNull { it.second } ?: 1)
        Row(Modifier.fillMaxWidth().height(76.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
            recap.months.forEach { (label, count) ->
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Box(
                        Modifier.fillMaxWidth().height(max(2f, 58f * count / most).dp)
                            .background(if (count > 0) storyCyan else Color.White.copy(alpha = 0.1f), RoundedCornerShape(3.dp)),
                    )
                    Text(label.take(3), color = dim, fontSize = 6.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        Text(
            "${recap.milestoneCount} ${if (recap.milestoneCount == 1) "milestone" else "milestones"} along the way",
            color = dim, fontSize = 9.sp, modifier = Modifier.padding(top = 10.dp),
        )
        recap.busiestMonth?.let { month ->
            Row(Modifier.padding(top = 20.dp)) {
                Icon(Icons.Default.CalendarMonth, null, tint = storyCyan, modifier = Modifier.size(18.dp))
                Column(Modifier.padding(start = 10.dp)) {
                    Text("Busiest month · $month", color = storyWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text("${recap.busiestMonthWorkoutCount} ${if (recap.busiestMonthWorkoutCount == 1) "workout" else "workouts"}", color = Color.White.copy(alpha = 0.55f), fontSize = 9.sp)
                }
            }
        }
        recap.highlight?.let {
            Row(Modifier.padding(top = 14.dp)) {
                Icon(Icons.Default.AutoAwesome, null, tint = storyCyan, modifier = Modifier.size(18.dp))
                Text(it, color = storyWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
}

@Composable
fun YearRecapDialog(recaps: List<TrainingYearRecap>, initialYear: Int?, onDismiss: () -> Unit) {
    var year by remember { mutableStateOf(recaps.firstOrNull { it.year == initialYear }?.year ?: recaps.firstOrNull()?.year) }
    val recap = recaps.firstOrNull { it.year == year }
    var picking by remember { mutableStateOf(false) }
    StoryPreviewDialog(
        title = "My Year in Training",
        shareLabel = "Share Recap",
        fileName = "My year in training ${recap?.year ?: ""}",
        onDismiss = onDismiss,
        header = {
            if (recap != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(recap.periodLabel, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box {
                    TextButton(onClick = { picking = true }) { Text("${recap.year} ▾", fontWeight = FontWeight.SemiBold) }
                    DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
                        recaps.forEach { r -> DropdownMenuItem(text = { Text("${r.year}") }, onClick = { year = r.year; picking = false }) }
                    }
                }
            }
        },
    ) {
        if (recap != null) YearRecapStory(recap)
        else Box(Modifier.fillMaxSize().storyBackground(), contentAlignment = Alignment.Center) {
            Text("Complete a workout to create your recap.", color = storyWhite)
        }
    }
}

// endregion

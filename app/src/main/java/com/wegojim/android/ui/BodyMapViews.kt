package com.wegojim.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wegojim.android.ui.body.BodyMap
import com.wegojim.android.ui.body.BodyMapStyle
import com.wegojim.android.ui.body.BodySide
import com.wegojim.android.ui.body.MuscleRegions

@Composable
private fun bodyStyle() = BodyMapStyle(
    defaultFill = MaterialTheme.colorScheme.surfaceContainerHighest,
    stroke = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
    head = MaterialTheme.colorScheme.surfaceContainerHighest,
    hair = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
    selectedFill = MaterialTheme.colorScheme.primary,
    selectedStroke = MaterialTheme.colorScheme.primary,
)

/** Front and back bodies side by side on a field-coloured panel. */
@Composable
private fun BodyPair(
    fills: Map<String, Color>,
    height: Int,
    selected: Set<String> = emptySet(),
    onTap: ((String) -> Unit)? = null,
) {
    val style = bodyStyle()
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BodySide.entries.forEach { side ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                BodyMap(
                    side = side,
                    fills = fills,
                    style = style,
                    selected = selected,
                    onTap = onTap,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(14.dp))
                        .padding(vertical = 10.dp, horizontal = 4.dp)
                        .height(height.dp),
                )
                Text(
                    if (side == BodySide.Front) "Front" else "Back",
                    Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Port of ExerciseBodyMapSection: primary muscles strong, secondary faint. */
@Composable
fun ExerciseBodyMap(primary: List<String>, secondary: List<String>) {
    val accent = MaterialTheme.colorScheme.primary
    val (p, s) = MuscleRegions.highlight(primary, secondary)
    val fills = s.associateWith { accent.copy(alpha = 0.28f) } + p.associateWith { accent.copy(alpha = 0.88f) }
    BodyPair(fills, height = 210)
}

/** Training load per muscle on the body (replaces the tile grid). */
@Composable
fun BodyHeatmap(scores: List<Pair<String, Double>>) {
    if (scores.isEmpty()) {
        Text("No working sets in this period yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val hot = MaterialTheme.colorScheme.primary
    val fills = MuscleRegions.regionScores(scores).mapValues { (_, v) -> hot.copy(alpha = (0.18 + 0.77 * v).toFloat()) }
    BodyPair(fills, height = 190)
    Text(
        "Most worked: " + scores.take(3).joinToString { it.first },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Port of ExerciseBodyMapFilterSheet: tap the body or the list to filter by primary muscle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyPartFilterDialog(
    muscles: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Body Part") },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close") } },
                    actions = { TextButton(onClick = onDismiss) { Text("Done") } },
                )
            },
        ) { padding ->
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(selected ?: "Any body part", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Tap a muscle to filter exercises that train it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    BodyPair(
                        fills = emptyMap(),
                        height = 260,
                        selected = selected?.let { MuscleRegions.regions(it).toSet() }.orEmpty(),
                        onTap = { slug -> MuscleRegions.muscleFor(slug)?.takeIf { it in muscles }?.let { onSelect(if (it == selected) null else it) } },
                    )
                }
                item { OutlinedButton(onClick = { onSelect(null) }, Modifier.fillMaxWidth()) { Text("Any body part") } }
                items(muscles) { m ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                if (m == selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainer,
                                RoundedCornerShape(10.dp),
                            )
                            .clickable { onSelect(if (m == selected) null else m) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(m, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        if (m == selected) Icon(Icons.Default.Check, "Selected", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

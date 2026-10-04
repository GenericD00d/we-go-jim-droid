package com.wegojim.android.ui.body

import android.graphics.Region
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.min

enum class BodySide { Front, Back }

/** Port of MuscleMap's sub-group hierarchy (Muscle.parentGroup). */
object BodySlugs {
    val parent = mapOf(
        "upper-chest" to "chest", "lower-chest" to "chest",
        "inner-quad" to "quadriceps", "outer-quad" to "quadriceps", "hip-flexors" to "quadriceps",
        "upper-abs" to "abs", "lower-abs" to "abs",
        "front-deltoid" to "deltoids", "rear-deltoid" to "deltoids",
        "upper-trapezius" to "trapezius", "lower-trapezius" to "trapezius",
        "serratus" to "obliques", "ankles" to "feet", "adductors" to "hamstring", "neck" to "head",
    )
    val cosmetic = setOf("head", "hair")
}

/**
 * Parsed shapes in viewBox coordinates, built once per process. Hit regions are
 * rasterised lazily on the first tap, since most body maps are display-only.
 */
private class ParsedPart(val slug: String, val paths: List<Path>) {
    val regions: List<Region> by lazy {
        val clip = Region(-2000, -2000, 4000, 4000)
        paths.map { Region().apply { setPath(it.asAndroidPath(), clip) } }
    }
}

private object ParsedBody {
    private fun parse(parts: List<BodyPart>) = parts.map { p ->
        ParsedPart(p.slug, p.all.map { PathParser().parsePathString(it).toPath() })
    }
    val front by lazy { parse(BodyPaths.front) }
    val back by lazy { parse(BodyPaths.back) }
    fun of(side: BodySide) = if (side == BodySide.Front) front else back
}

data class BodyMapStyle(
    val defaultFill: Color,
    val stroke: Color,
    val head: Color,
    val hair: Color,
    val selectedFill: Color,
    val selectedStroke: Color,
)

/**
 * Give it a size; the body is scaled to fit and centred.
 *
 * Port of MuscleMap's BodyView/BodyRenderer with sub-groups shown: draws the
 * male front or back, fills muscles by slug (sub-groups inherit their parent's
 * fill) and reports taps on muscles.
 */
@Composable
fun BodyMap(
    side: BodySide,
    fills: Map<String, Color>,
    style: BodyMapStyle,
    modifier: Modifier = Modifier,
    selected: Set<String> = emptySet(),
    description: String = if (side == BodySide.Front) "Front muscle map" else "Back muscle map",
    onTap: ((slug: String) -> Unit)? = null,
) {
    val parts = ParsedBody.of(side)
    val originX = if (side == BodySide.Front) BodyPaths.FRONT_X else BodyPaths.BACK_X
    var tapModifier: Modifier = Modifier
    if (onTap != null) {
        tapModifier = Modifier.pointerInput(side) {
            detectTapGestures { tap ->
                val s = min(size.width / BodyPaths.WIDTH, size.height / BodyPaths.HEIGHT)
                val dx = (size.width - BodyPaths.WIDTH * s) / 2
                val dy = (size.height - BodyPaths.HEIGHT * s) / 2
                val x = ((tap.x - dx) / s + originX).toInt()
                val y = ((tap.y - dy) / s + BodyPaths.Y).toInt()
                // Sub-groups sit on top of their parents, so test them first (as MuscleMap does).
                parts.filter { it.slug !in BodySlugs.cosmetic }
                    .sortedByDescending { it.slug in BodySlugs.parent }
                    .firstOrNull { part -> part.regions.any { it.contains(x, y) } }
                    ?.let { onTap(it.slug) }
            }
        }
    }
    Canvas(
        modifier
            .clipToBounds()
            .semantics { contentDescription = description }
            .then(tapModifier),
    ) {
        val s = min(size.width / BodyPaths.WIDTH, size.height / BodyPaths.HEIGHT)
        val dx = (size.width - BodyPaths.WIDTH * s) / 2 - originX * s
        val dy = (size.height - BodyPaths.HEIGHT * s) / 2 - BodyPaths.Y * s
        val strokeWidth = 0.55.dp.toPx() / s
        val selectedStrokeWidth = 1.25.dp.toPx() / s
        translate(dx, dy) {
            scale(s, s, pivot = Offset.Zero) {
                parts.forEach { part ->
                    val isSelected = part.slug in selected || BodySlugs.parent[part.slug] in selected
                    val fill = when {
                        part.slug == "hair" -> style.hair
                        part.slug == "head" -> style.head
                        isSelected -> style.selectedFill
                        else -> fills[part.slug] ?: BodySlugs.parent[part.slug]?.let { fills[it] } ?: style.defaultFill
                    }
                    part.paths.forEach { path ->
                        drawPath(path, fill)
                        drawPath(path, style.stroke, style = Stroke(strokeWidth))
                        if (isSelected) drawPath(path, style.selectedStroke, style = Stroke(selectedStrokeWidth))
                    }
                }
            }
        }
    }
}

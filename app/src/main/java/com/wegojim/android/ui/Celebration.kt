package com.wegojim.android.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import android.provider.Settings
import java.io.File
import kotlin.math.sin
import kotlin.random.Random

private class Piece(val x: Float, val delay: Float, val speed: Float, val drift: Float, val spin: Float, val color: Color, val w: Float, val h: Float)

/**
 * One-shot falling confetti (port of WorkoutCompletionConfettiLayerView).
 * Skipped when the system "remove animations" setting is on.
 */
@Composable
fun Confetti(key: Any, festive: Boolean = false, modifier: Modifier = Modifier.fillMaxSize()) {
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    if (reduceMotion) return
    val palette = if (festive) listOf(Color(0xFFFF6675), Color(0xFFF3CA6C), Color(0xFF80E3BC), Color(0xFFFFFFFF))
    else listOf(Color(0xFF6DB5FF), Color(0xFF7CE3EF), Color(0xFF62D8A6), Color(0xFFE0C56B), Color(0xFF9FB0FF))
    val pieces = remember(key) {
        val r = Random(key.hashCode())
        List(90) {
            Piece(r.nextFloat(), r.nextFloat() * 0.35f, 0.6f + r.nextFloat() * 0.6f, (r.nextFloat() - 0.5f) * 0.15f,
                r.nextFloat() * 720f, palette[r.nextInt(palette.size)], 8f + r.nextFloat() * 8f, 5f + r.nextFloat() * 6f)
        }
    }
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { progress.animateTo(1f, tween(3200, easing = LinearEasing)) }
    if (progress.value >= 1f) return
    Canvas(modifier) {
        pieces.forEach { p ->
            val t = ((progress.value - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (t <= 0f) return@forEach
            val y = -20f + t * p.speed * size.height * 1.3f
            val x = (p.x + p.drift * sin(t * 6f)) * size.width
            rotate(p.spin * t, Offset(x, y)) {
                drawRect(p.color.copy(alpha = 1f - t * 0.6f), Offset(x - p.w / 2, y - p.h / 2), Size(p.w, p.h))
            }
        }
    }
}

/** Profile photo stored privately in app files (it never leaves the device except via backups). */
object Avatar {
    private fun file(context: Context) = File(context.filesDir, "avatar.jpg")

    fun load(context: Context): Bitmap? = file(context).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    fun save(context: Context, uri: Uri): Boolean = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 512) sample *= 2
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return false
        val side = minOf(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, minOf(side, 512), minOf(side, 512), true)
        file(context).outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        true
    }.getOrDefault(false)

    fun clear(context: Context) = file(context).delete()
}

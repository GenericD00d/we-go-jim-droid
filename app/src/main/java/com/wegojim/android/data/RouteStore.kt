package com.wegojim.android.data

import android.content.Context
import java.io.File

/** One JSON file per recorded route, named by its cardio block id, in private app storage. */
class RouteStore(context: Context) {
    private val dir = File(context.filesDir, "routes")

    private fun file(activityId: String) = File(dir, "${activityId.filter { it.isLetterOrDigit() || it == '-' }}.json")

    fun load(activityId: String): CardioRoute? = runCatching {
        file(activityId).takeIf { it.exists() }?.let { AppJson.decodeFromString(CardioRoute.serializer(), it.readText()) }
    }.getOrNull()

    @Synchronized
    fun save(route: CardioRoute) {
        dir.mkdirs()
        val target = file(route.activityId)
        val tmp = File(dir, target.name + ".tmp")
        tmp.writeText(AppJson.encodeToString(CardioRoute.serializer(), route))
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    @Synchronized
    fun delete(activityIds: Collection<String>) = activityIds.forEach { file(it).delete() }

    fun all(): List<CardioRoute> = dir.listFiles { f -> f.extension == "json" }.orEmpty()
        .mapNotNull { f -> runCatching { AppJson.decodeFromString(CardioRoute.serializer(), f.readText()) }.getOrNull() }

    /** Restore: keep routes in [keep], replace everything else with [routes]. */
    @Synchronized
    fun replaceAll(routes: List<CardioRoute>, keep: Set<String>) {
        all().map { it.activityId }.filter { it !in keep }.let(::delete)
        routes.forEach(::save)
    }
}

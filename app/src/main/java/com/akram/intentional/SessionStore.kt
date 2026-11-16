package com.akram.intentional

import android.content.Context
import android.content.res.Configuration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object SessionStore {
    private const val FILE = "intentional_preferences"
    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isWatched(context: Context, packageName: String) =
        prefs(context).getStringSet("watched_apps", emptySet()).orEmpty().contains(packageName)

    fun watched(context: Context) = prefs(context).getStringSet("watched_apps", emptySet()).orEmpty()

    fun setWatched(context: Context, packageName: String, watched: Boolean) {
        val set = prefs(context).getStringSet("watched_apps", emptySet()).orEmpty().toMutableSet()
        if (watched) set.add(packageName) else set.remove(packageName)
        prefs(context).edit().putStringSet("watched_apps", set).apply()
    }

    fun dismissedPackage(context: Context) = prefs(context).getString("dismissed_until_leave", null)
    fun dismissUntilLeave(context: Context, packageName: String) {
        prefs(context).edit().putString("dismissed_until_leave", packageName).apply()
    }
    fun clearDismissedPackage(context: Context) {
        prefs(context).edit().remove("dismissed_until_leave").apply()
    }

    fun activePackage(context: Context) = prefs(context).getString("active_package", null)
    fun deadline(context: Context) = prefs(context).getLong("deadline", 0L)
    fun started(context: Context) = prefs(context).getLong("started", 0L)
    fun completed(context: Context) = prefs(context).getBoolean("completed", false)
    fun defaultDuration(context: Context) = prefs(context).getInt("default_duration", 15)
    fun themeMode(context: Context) = prefs(context).getString("theme_mode", "system") ?: "system"
    fun setThemeMode(context: Context, mode: String) { prefs(context).edit().putString("theme_mode", mode).apply() }
    fun isDark(context: Context): Boolean = when (themeMode(context)) {
        "dark" -> true
        "light" -> false
        else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    fun start(context: Context, packageName: String, minutes: Int) {
        if (activePackage(context) != null && activePackage(context) != packageName && deadline(context) > 0L) {
            finish(context)
        }
        val now = System.currentTimeMillis()
        prefs(context).edit()
            .putString("active_package", packageName)
            .putLong("started", now)
            .putLong("deadline", now + minutes * 60_000L)
            .putInt("planned_minutes", minutes)
            .putInt("default_duration", minutes)
            .putInt("added_minutes", 0)
            .putBoolean("is_extension", false)
            .putBoolean("completed", false)
            .apply()
    }

    fun extendSession(context: Context, minutes: Int) {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        p.edit()
            .putLong("started", now)
            .putLong("deadline", now + minutes * 60_000L)
            .putInt("added_minutes", p.getInt("added_minutes", 0) + minutes)
            .putBoolean("is_extension", true)
            .putInt("planned_minutes", 0)
            .putBoolean("completed", false)
            .apply()
    }

    fun finish(context: Context) {
        val p = prefs(context)
        val started = p.getLong("started", 0L)
        val end = minOf(System.currentTimeMillis(), p.getLong("deadline", 0L))
        val pkg = p.getString("active_package", null)
        if (started > 0 && pkg != null && end > started) {
            val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val key = "time_${pkg}_$day"
            val elapsed = end - started
            val edit = p.edit().putLong(key, p.getLong(key, 0L) + elapsed)
            val plannedKey = "planned_${pkg}_$day"
            edit.putLong(plannedKey, p.getLong(plannedKey, 0L) + p.getInt("planned_minutes", 0) * 60_000L)
            if (p.getBoolean("is_extension", false)) {
                val extraKey = "extra_${pkg}_$day"
                edit.putLong(extraKey, p.getLong(extraKey, 0L) + elapsed)
            }
            edit.apply()
        }
        p.edit().putBoolean("completed", true).putLong("deadline", 0L).apply()
    }

    fun clearCompletedSession(context: Context) {
        prefs(context).edit()
            .putString("active_package", null)
            .putLong("started", 0L)
            .putLong("deadline", 0L)
            .putInt("added_minutes", 0)
            .putBoolean("is_extension", false)
            .putBoolean("completed", false)
            .apply()
    }

    fun totalToday(context: Context): Long {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return prefs(context).all.entries
            .filter { it.key.startsWith("time_") && it.key.endsWith(date) }
            .sumOf { (it.value as? Long) ?: 0L }
    }

    fun dailyTotal(context: Context, date: String): Long = prefs(context).all.entries
        .filter { it.key.startsWith("time_") && it.key.endsWith(date) }
        .sumOf { (it.value as? Long) ?: 0L }

    fun extraToday(context: Context): Long {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return extraDailyTotal(context, date)
    }

    fun extraDailyTotal(context: Context, date: String): Long = prefs(context).all.entries
        .filter { it.key.startsWith("extra_") && it.key.endsWith(date) }
        .sumOf { (it.value as? Long) ?: 0L }

    fun plannedDailyTotal(context: Context, date: String): Long = prefs(context).all.entries
        .filter { it.key.startsWith("planned_") && it.key.endsWith("_$date") }
        .sumOf { (it.value as? Long) ?: 0L }

    fun totalsByPackage(context: Context, dates: Set<String>, prefix: String): Map<String, Long> {
        if (dates.isEmpty()) return emptyMap()
        return prefs(context).all.entries.mapNotNull { (key, value) ->
            val date = dates.firstOrNull { key.endsWith("_$it") } ?: return@mapNotNull null
            if (!key.startsWith(prefix)) return@mapNotNull null
            val pkg = key.removePrefix(prefix).removeSuffix("_$date")
            pkg to ((value as? Long) ?: 0L)
        }.groupBy({ it.first }, { it.second }).mapValues { (_, values) -> values.sum() }
    }
}

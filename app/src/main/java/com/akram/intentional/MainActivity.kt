package com.akram.intentional

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.pm.ApplicationInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

private data class AppPalette(val ink: Color, val muted: Color, val mint: Color, val warm: Color, val canvas: Color, val surface: Color, val soft: Color, val track: Color)
private val LightPalette = AppPalette(Color(0xFF252A32), Color(0xFF707984), Color(0xFF1C7668), Color(0xFFB66A40), Color(0xFFF5F7F5), Color.White, Color(0xFFE6F2EE), Color(0xFFE5EAE7))
private val DarkPalette = AppPalette(Color(0xFFF0F2F1), Color(0xFFB8C0BE), Color(0xFF76CDB6), Color(0xFFE7A47E), Color(0xFF151A1C), Color(0xFF22292B), Color(0xFF2A3836), Color(0xFF3A4545))
private val LocalPalette = compositionLocalOf { LightPalette }
private val Ink: Color @Composable get() = LocalPalette.current.ink
private val Muted: Color @Composable get() = LocalPalette.current.muted
private val Mint: Color @Composable get() = LocalPalette.current.mint
private val Warm: Color @Composable get() = LocalPalette.current.warm
private val Canvas: Color @Composable get() = LocalPalette.current.canvas
private val SurfaceColor: Color @Composable get() = LocalPalette.current.surface
private val SoftColor: Color @Composable get() = LocalPalette.current.soft
private val TrackColor: Color @Composable get() = LocalPalette.current.track

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { IntentionalApp() }
    }
}

private data class UserApp(val label: String, val packageName: String, val likelySocial: Boolean)

private val KnownSocialPackages = setOf(
    "com.instagram.android", "com.instagram.barcelona", "com.facebook.katana", "com.facebook.orca",
    "com.whatsapp", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.snapchat.android",
    "com.twitter.android", "com.reddit.frontpage", "com.pinterest", "com.linkedin.android",
    "org.telegram.messenger", "com.discord", "org.thoughtcrime.securesms", "com.google.android.youtube"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntentionalApp() {
    val context = LocalContext.current
    var tab by remember { mutableStateOf("Today") }
    var refresh by remember { mutableIntStateOf(0) }
    var serviceEnabled by remember { mutableStateOf(isWatcherEnabled(context)) }
    var explainPermission by remember { mutableStateOf(false) }
    var themeMode by remember { mutableStateOf(SessionStore.themeMode(context)) }
    val darkTheme = when (themeMode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val palette = if (darkTheme) DarkPalette else LightPalette
    val openPermissionSettings = { explainPermission = true }
    LifecycleResumeEffect(Unit) {
        serviceEnabled = isWatcherEnabled(context)
        refresh++
        onPauseOrDispose { }
    }
    val todayMs = remember(refresh) { SessionStore.totalToday(context) }

    CompositionLocalProvider(LocalPalette provides palette) {
      MaterialTheme(
        colorScheme = if (darkTheme) darkColorScheme(primary = palette.mint, onPrimary = Color(0xFF10211D), background = palette.canvas, surface = palette.surface, onSurface = palette.ink, secondary = palette.warm) else lightColorScheme(primary = palette.mint, onPrimary = Color.White, background = palette.canvas, surface = palette.surface, onSurface = palette.ink, secondary = palette.warm)
      ) {
        Scaffold(
            containerColor = Canvas,
            bottomBar = {
                NavigationBar(containerColor = SurfaceColor) {
                    listOf("Today" to Icons.Filled.Insights, "Apps" to Icons.Filled.Apps, "Settings" to Icons.Filled.Settings).forEach { (name, icon) ->
                        NavigationBarItem(
                            selected = tab == name,
                            onClick = { tab = name },
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(name) }
                        )
                    }
                }
            }
        ) { padding ->
            when (tab) {
                "Apps" -> AppsScreen(Modifier.padding(padding), refresh) { refresh++ }
                "Settings" -> SettingsScreen(Modifier.padding(padding), serviceEnabled, themeMode, { mode -> themeMode = mode; SessionStore.setThemeMode(context, mode) }) {
                    openPermissionSettings()
                }
                else -> TodayScreen(Modifier.padding(padding), todayMs, serviceEnabled, refresh) {
                    openPermissionSettings()
                }
            }
            if (explainPermission) {
                AlertDialog(
                    onDismissRequest = { explainPermission = false },
                    title = { Text("Turn on app check-ins") },
                    text = { Text("Android will ask you to enable Intentional’s accessibility service. It notices when an app you selected opens, so it can show this check-in and return you Home when your time is up. It does not read messages or what you do on screen. Your session times stay on this phone. You can switch this off later in Android Settings.") },
                    confirmButton = {
                        TextButton(onClick = {
                            explainPermission = false
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }) { Text("Continue to Settings") }
                    },
                    dismissButton = { TextButton(onClick = { explainPermission = false }) { Text("Not now") } }
                )
            }
        }
      }
    }
}

@Composable
private fun TodayScreen(modifier: Modifier, todayMs: Long, serviceEnabled: Boolean, refresh: Int, onSetup: () -> Unit) {
    val context = LocalContext.current
    var range by remember { mutableStateOf("Today") }
    val rangeMs = remember(refresh, range) { when (range) {
        "Week" -> (0..6).sumOf { offset -> SessionStore.dailyTotal(context, dateOffset(-offset)) }
        "Month" -> monthTotal(context)
        else -> todayMs
    } }
    val extraMs = remember(refresh, range) { when (range) {
        "Week" -> (0..6).sumOf { offset -> SessionStore.extraDailyTotal(context, dateOffset(-offset)) }
        "Month" -> monthExtraTotal(context)
        else -> SessionStore.extraToday(context)
    } }
    val dates = remember(range, refresh) { when (range) {
        "Week" -> (0..6).map { dateOffset(-it) }.toSet()
        "Month" -> { val month = SimpleDateFormat("yyyy-MM", Locale.US).format(Date()); (1..31).map { "$month-${it.toString().padStart(2, '0')}" }.toSet() }
        else -> setOf(dateOffset(0))
    } }
    val plannedMs = remember(refresh, range) { dates.sumOf { SessionStore.plannedDailyTotal(context, it) } }
    val appActual = remember(refresh, range) { SessionStore.totalsByPackage(context, dates, "time_") }
    val appPlanned = remember(refresh, range) { SessionStore.totalsByPackage(context, dates, "planned_") }
    val appExtra = remember(refresh, range) { SessionStore.totalsByPackage(context, dates, "extra_") }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Text(if (range == "Today") "Today" else "$range overview", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("A little more intention, one session at a time.", color = Muted)
        }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("Today", "Week", "Month").forEachIndexed { index, label ->
                    SegmentedButton(selected = range == label, onClick = { range = label }, shape = SegmentedButtonDefaults.itemShape(index, 3)) {
                        Text(label)
                    }
                }
            }
        }
        if (!serviceEnabled) item {
            Card(colors = CardDefaults.cardColors(containerColor = SoftColor), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.Psychology, null, tint = Mint)
                    Text("Set up your check-in", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("Choose which apps to pause before opening. Your purpose and time choices stay on your phone.", color = Muted)
                    Button(onClick = onSetup) { Text("Turn on app check-ins") }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = SurfaceColor)) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Your time this ${range.lowercase()}", color = Muted)
                    Text(formatTime(rangeMs), fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("Time spent in intentional sessions", color = Muted)
                    Text("Planned ${formatTime(plannedMs)}   ·   Actual ${formatTime(rangeMs)}", color = Muted, fontSize = 13.sp)
                    if (extraMs > 0L) {
                        Text("Extra time  ${formatTime(extraMs)}", color = Warm, fontWeight = FontWeight.SemiBold)
                    }
                    LinearProgressIndicator(
                        progress = { if (rangeMs == 0L) 0f else ((rangeMs / 60_000f) / if (range == "Month") 1200f else 120f).coerceAtMost(1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = Mint,
                        trackColor = TrackColor
                    )
                    Text(if (range == "Today") "A simple snapshot of today." else "Your time adds up from sessions on this phone.", fontSize = 13.sp, color = Muted)
                }
            }
        }
        if (appActual.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("App breakdown", fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = Ink)
                appActual.entries.sortedByDescending { it.value }.take(6).forEach { (pkg, actual) ->
                    val appName = remember(pkg) { runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg) }
                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = SurfaceColor)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(appName, color = Ink, fontWeight = FontWeight.Medium)
                                Text(formatTime(actual), color = Ink, fontWeight = FontWeight.SemiBold)
                            }
                            Text("Planned ${formatTime(appPlanned[pkg] ?: 0L)}", color = Muted, fontSize = 12.sp)
                            if ((appExtra[pkg] ?: 0L) > 0) Text("Extra ${formatTime(appExtra[pkg] ?: 0L)}", color = Warm, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).background(SoftColor, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.TipsAndUpdates, null, tint = Mint)
                }
                Column {
                    Text("No scores, no guilt", fontWeight = FontWeight.SemiBold)
                    Text("This is here to help you notice your habits.", color = Muted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun AppsScreen(modifier: Modifier, refresh: Int, onChanged: () -> Unit) {
    val context = LocalContext.current
    val apps = remember(refresh) {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        pm.getInstalledApplications(0)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null && it.packageName != context.packageName }
            .map { info ->
                val socialCategory = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && info.category == ApplicationInfo.CATEGORY_SOCIAL
                UserApp(pm.getApplicationLabel(info).toString(), info.packageName, info.packageName in KnownSocialPackages || socialCategory)
            }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }
    val watched = remember(refresh) { SessionStore.watched(context) }
    val enabledApps = apps.filter { it.packageName in watched }
    val suggestedApps = apps.filter { it.likelySocial && it.packageName !in watched }
    val otherApps = apps.filter { !it.likelySocial && it.packageName !in watched }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Your apps", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink) }
        item { Text("Likely social apps are suggested automatically. You choose which ones to watch.", color = Muted) }
        if (apps.isEmpty()) item { Text("No launchable apps found yet.", modifier = Modifier.padding(top = 24.dp), color = Muted) }
        if (enabledApps.isNotEmpty()) {
            item { Text("On", modifier = Modifier.padding(top = 12.dp), color = Mint, fontWeight = FontWeight.SemiBold) }
            items(enabledApps, key = { "on-${it.packageName}" }) { app ->
                AppToggleRow(app, checked = true) {
                    SessionStore.setWatched(context, app.packageName, false)
                    onChanged()
                }
            }
        }
        if (suggestedApps.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Suggested social apps", color = Ink, fontWeight = FontWeight.SemiBold)
                        Text("Check the suggestions, then choose what to turn on.", color = Muted, fontSize = 12.sp)
                    }
                    TextButton(onClick = {
                        suggestedApps.forEach { SessionStore.setWatched(context, it.packageName, true) }
                        onChanged()
                    }) { Text("Enable all") }
                }
            }
            items(suggestedApps, key = { "suggested-${it.packageName}" }) { app ->
                AppToggleRow(app, checked = false) {
                    SessionStore.setWatched(context, app.packageName, true)
                    onChanged()
                }
            }
        }
        if (otherApps.isNotEmpty()) {
            item { Text("Other apps", modifier = Modifier.padding(top = 12.dp), color = Ink, fontWeight = FontWeight.SemiBold) }
            items(otherApps, key = { "other-${it.packageName}" }) { app ->
                AppToggleRow(app, checked = false) {
                    SessionStore.setWatched(context, app.packageName, true)
                    onChanged()
                }
            }
        }
    }
}

@Composable
private fun AppToggleRow(app: UserApp, checked: Boolean, onToggle: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = SurfaceColor) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(app.label, fontWeight = FontWeight.Medium, color = Ink)
                Text(if (checked) "Check-in is on" else "Check-in is off", fontSize = 12.sp, color = Muted)
            }
            Switch(checked = checked, onCheckedChange = { onToggle() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(modifier: Modifier, serviceEnabled: Boolean, themeMode: String, onThemeModeChange: (String) -> Unit, onPermission: () -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Settings", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink) }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("App check-ins", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Text(if (serviceEnabled) "Enabled. Selected apps will ask for a purpose and time." else "Android needs your permission to notice when a selected app opens.", color = Muted)
                    OutlinedButton(onClick = onPermission) { Text(if (serviceEnabled) "Review permission" else "Open permission settings") }
                }
            }
        }
        item { Text("Appearance", fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = Ink) }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("system" to "Follow phone", "light" to "Light", "dark" to "Dark").forEachIndexed { index, (mode, label) ->
                    SegmentedButton(selected = themeMode == mode, onClick = { onThemeModeChange(mode) }, shape = SegmentedButtonDefaults.itemShape(index, 3)) { Text(label) }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = SurfaceColor)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("YouTube picture-in-picture", fontWeight = FontWeight.SemiBold, color = Ink)
                    Text("If YouTube keeps playing in a small window after you finish, turn off Picture-in-picture for YouTube in your phone’s app settings.", color = Muted)
                }
            }
        }
        item { Text("Your data stays on this phone. You can clear your history in a future update.", color = Muted) }
    }
}

private fun isWatcherEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { it.resolveInfo.serviceInfo.packageName == context.packageName && it.resolveInfo.serviceInfo.name.endsWith("AppWatchService") }
}

private fun formatTime(ms: Long): String {
    val minutes = ms / 60_000
    val hours = minutes / 60
    return if (hours > 0) "${hours}h ${minutes % 60}m" else "${minutes} min"
}

private fun dateOffset(days: Int): String {
    val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, days) }
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
}

private fun monthTotal(context: Context): Long {
    val month = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
    return (1..31).sumOf { day -> SessionStore.dailyTotal(context, "$month-${day.toString().padStart(2, '0')}") }
}

private fun monthExtraTotal(context: Context): Long {
    val month = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
    return (1..31).sumOf { day -> SessionStore.extraDailyTotal(context, "$month-${day.toString().padStart(2, '0')}") }
}

package com.akram.intentional

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class SessionGateActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PACKAGE = "target_package"
        const val EXTRA_COMPLETED = "session_completed"
    }

    private var targetPackage: String? = null
    private var completed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetPackage = intent.getStringExtra(EXTRA_PACKAGE)
        completed = intent.getBooleanExtra(EXTRA_COMPLETED, false)
        showGate()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        targetPackage = intent.getStringExtra(EXTRA_PACKAGE)
        completed = intent.getBooleanExtra(EXTRA_COMPLETED, false)
        showGate()
    }

    private fun showGate() {
        val pkg = targetPackage ?: run { finish(); return }
        val appName = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }
            .getOrDefault("this app")
        setContent {
            val dark = SessionStore.isDark(this@SessionGateActivity)
            MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color(0xFF76CDB6), background = Color(0xFF151A1C), surface = Color(0xFF22292B)) else lightColorScheme(primary = Color(0xFF1C7668), background = Color(0xFFF5F7F5))) {
                GateContent(
                    appName = appName,
                    isCompleted = completed,
                    onDone = {
                        SessionStore.clearCompletedSession(this)
                        SessionStore.dismissUntilLeave(this, pkg)
                        performHome()
                    },
                    onNotNow = { SessionStore.dismissUntilLeave(this, pkg); performHome() },
                    onStart = { minutes ->
                        SessionStore.start(this, pkg, minutes)
                        finish()
                    },
                    onExtend = { minutes ->
                        SessionStore.extendSession(this, minutes)
                        finish()
                    }
                )
            }
        }
    }

    private fun performHome() {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(home)
        finish()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GateContent(appName: String, isCompleted: Boolean, onDone: () -> Unit, onNotNow: () -> Unit, onStart: (Int) -> Unit, onExtend: (Int) -> Unit) {
    val appContext = LocalContext.current
    val savedDuration = SessionStore.defaultDuration(appContext)
    var reason by remember { mutableStateOf<String?>(null) }
    var customReason by remember { mutableStateOf("") }
    var duration by remember { mutableIntStateOf(savedDuration.takeIf { it in listOf(5, 10, 15, 30) } ?: 15) }
    var customDuration by remember { mutableStateOf("") }
    var useCustomDuration by remember { mutableStateOf(false) }
    BackHandler { if (isCompleted) onDone() else onNotNow() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 30.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(if (isCompleted) "Your session is over" else "A quick pause", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Text(if (isCompleted) "What would you like to do?" else "Before opening $appName", fontSize = 29.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
            if (isCompleted) {
                Text("You planned a stopping point. You can finish here or choose a little more time.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) { Text("Finish and go Home") }
                OutlinedButton(onClick = { onExtend(5) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) { Text("5 more minutes") }
                OutlinedButton(onClick = { useCustomDuration = !useCustomDuration }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) { Text("Choose a custom time") }
                if (useCustomDuration) {
                    OutlinedTextField(
                        value = customDuration,
                        onValueChange = { customDuration = it.filter(Char::isDigit).take(3) },
                        label = { Text("Minutes, 1 to 240") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Button(
                        onClick = { customDuration.toIntOrNull()?.let { onExtend(it.coerceIn(1, 240)) } },
                        enabled = (customDuration.toIntOrNull() ?: 0) in 1..240,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    ) { Text("Start custom time") }
                }
            } else {
                Text("What brings you here?", fontWeight = FontWeight.SemiBold)
                val reasons = listOf("Reply", "Post", "Work or learn", "Browse for fun", "Custom")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    reasons.forEach { option ->
                        FilterChip(selected = reason == option, onClick = { reason = option }, label = { Text(option) })
                    }
                }
                if (reason == "Custom") {
                    OutlinedTextField(
                        value = customReason,
                        onValueChange = { customReason = it.take(80) },
                        label = { Text("Your reason (not saved)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                Text("How long do you plan to stay?", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15, 30).forEach { mins ->
                        FilterChip(selected = !useCustomDuration && duration == mins, onClick = { duration = mins; useCustomDuration = false }, label = { Text("${mins}m") })
                    }
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = useCustomDuration, onCheckedChange = { useCustomDuration = it })
                    Text("Choose a custom time")
                }
                if (useCustomDuration) {
                    OutlinedTextField(
                        value = customDuration,
                        onValueChange = { customDuration = it.filter(Char::isDigit).take(3) },
                        label = { Text("Minutes, 1 to 240") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                Button(
                    onClick = { onStart(if (useCustomDuration) customDuration.toIntOrNull()?.coerceIn(1, 240) ?: 15 else duration) },
                    enabled = reason != null && (reason != "Custom" || customReason.isNotBlank()) && (!useCustomDuration || (customDuration.toIntOrNull() ?: 0) in 1..240),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp)
                ) { Text("Start my session") }
                TextButton(onClick = onNotNow, modifier = Modifier.fillMaxWidth()) { Text("Not now") }
                Text("Your purpose is used for this check-in only. We don’t save it.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
    }
}

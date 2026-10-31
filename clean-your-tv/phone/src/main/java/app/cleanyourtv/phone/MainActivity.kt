package app.cleanyourtv.phone

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.cleanyourtv.core.Category
import app.cleanyourtv.core.HomeSwitchPolicy
import app.cleanyourtv.core.PackageView
import app.cleanyourtv.core.Risk
import app.cleanyourtv.core.Tweaks

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                val vm: CleanViewModel = viewModel()
                val state by vm.state.collectAsStateWithLifecycle()
                App(state, vm, onShare = { text ->
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share TV report"))
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(state: UiState, vm: CleanViewModel, onShare: (String) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Clean Your TV") },
                actions = {
                    if (state.props != null) {
                        TextButton(onClick = { onShare(vm.report()) }) { Text("Report") }
                        TextButton(onClick = vm::disconnect) { Text("Disconnect") }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.phase == Phase.CONNECTING || state.phase == Phase.WORKING) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            when (state.phase) {
                Phase.DISCONNECTED, Phase.CONNECTING -> ConnectScreen(state, vm)
                else -> Dashboard(state, vm)
            }
        }
    }

    state.message?.let { msg ->
        AlertDialog(
            onDismissRequest = vm::dismissMessage,
            confirmButton = { TextButton(onClick = vm::dismissMessage) { Text("OK") } },
            text = { Text(msg) },
        )
    }

    state.trialSecondsLeft?.let { secs ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Press Home on your TV remote") },
            text = {
                Text(
                    "Did Clean Home open?\n\nIf you do not answer in $secs s, the stock home comes back automatically.",
                )
            },
            confirmButton = { Button(onClick = vm::confirmCleanHome) { Text("Yes, keep it") } },
            dismissButton = { OutlinedButton(onClick = vm::rejectCleanHome) { Text("No, put it back") } },
        )
    }
}

@Composable
private fun ConnectScreen(state: UiState, vm: CleanViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Connect to your TV", style = MaterialTheme.typography.headlineSmall)
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("One-time setup on the TV", fontWeight = FontWeight.SemiBold)
                Text("1. Settings > Device Preferences > About > select Build 7 times until it says you are a developer.")
                Text("2. Settings > Device Preferences > Developer options > turn on USB debugging.")
                Text("3. Settings > Network & Internet > your Wi-Fi network: note the IP address, e.g. 192.168.1.20.")
                Text("4. Keep the phone on the same Wi-Fi as the TV.")
                Text("5. When you connect, the TV asks \"Allow USB debugging?\" — tick Always allow, then OK.")
            }
        }
        OutlinedTextField(
            value = state.host, onValueChange = vm::setHost, label = { Text("TV IP address") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        OutlinedTextField(
            value = state.port, onValueChange = vm::setPort, label = { Text("Port") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Button(onClick = vm::connect, enabled = state.phase == Phase.DISCONNECTED, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.phase == Phase.CONNECTING) "Connecting — check the TV screen…" else "Connect")
        }
        Text(
            "Nothing is deleted from the TV. Apps are only turned off, every change is recorded on this phone, " +
                "and Undo puts it all back. A factory reset also restores everything.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun Dashboard(state: UiState, vm: CleanViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val busy = state.phase == Phase.WORKING
    Column(Modifier.fillMaxSize()) {
        TvCard(state)
        TabRow(selectedTabIndex = tab) {
            listOf("Clean", "Speed", "Look", "Undo").forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
            }
        }
        Column(Modifier.weight(1f)) {
            when (tab) {
                0 -> CleanTab(state, vm, busy)
                1 -> SpeedTab(state, vm, busy)
                2 -> LookTab(state, vm, busy)
                else -> UndoTab(state, vm, busy)
            }
        }
        LogView(state.log)
    }
}

@Composable
private fun TvCard(state: UiState) {
    val p = state.props ?: return
    Card(Modifier.fillMaxWidth().padding(12.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("${p.brand} ${p.model}", style = MaterialTheme.typography.titleMedium)
            Text("${p.manufacturer} · ${p.device} · Android SDK ${p.sdk}", style = MaterialTheme.typography.bodySmall)
            Text("Profile: ${state.profile?.name ?: "generic (no exact match)"}", style = MaterialTheme.typography.bodySmall)
            Text("Home screen: ${state.homeComponent ?: "unknown"}", style = MaterialTheme.typography.bodySmall)
            Text("Phone app version ${state.appVersion}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CleanTab(state: UiState, vm: CleanViewModel, busy: Boolean) {
    val groups = remember(state.inventory) {
        state.inventory.filter { it.installed.enabled }.groupBy { it.rule.category }.toSortedMap()
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = vm::selectRecommended, label = { Text("Recommended") })
            AssistChip(onClick = vm::clearSelection, label = { Text("None") })
        }
        LazyColumn(Modifier.weight(1f)) {
            for ((category, views) in groups) {
                item(key = "h-$category") {
                    Text(
                        category.title + if (category == Category.SYSTEM) " — ${views.size} apps, never changed" else "",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                if (category != Category.SYSTEM) {
                    items(views, key = { it.pkg }) { v -> PackageRow(v, v.pkg in state.selection, !busy) { vm.toggle(v.pkg) } }
                }
            }
        }
        Button(
            onClick = vm::applyClean, enabled = !busy && state.selection.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) { Text("Turn off ${state.selection.size} selected apps") }
    }
}

@Composable
private fun PackageRow(v: PackageView, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled && v.canDisable, onClick = onToggle).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled && v.canDisable)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(v.rule.label, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(6.dp))
                RiskBadge(v.rule.risk)
            }
            Text(v.rule.why, style = MaterialTheme.typography.bodySmall)
            Text(v.pkg, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun RiskBadge(risk: Risk) {
    val (text, color) = when (risk) {
        Risk.SAFE -> "safe" to Color(0xFF2E7D32)
        Risk.OPTIONAL -> "your choice" to Color(0xFF1565C0)
        Risk.ADVANCED -> "advanced" to Color(0xFFE65100)
        Risk.PROTECTED -> "protected" to Color(0xFF757575)
    }
    Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SpeedTab(state: UiState, vm: CleanViewModel, busy: Boolean) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f)) {
            items(Tweaks.all, key = { it.id }) { t ->
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !busy) { vm.toggleTweak(t.id) }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = t.id in state.tweakSelection, onCheckedChange = { vm.toggleTweak(t.id) }, enabled = !busy)
                    Column(Modifier.weight(1f)) {
                        Text(t.title, fontWeight = FontWeight.Medium)
                        Text(t.description, style = MaterialTheme.typography.bodySmall)
                        if (!t.reversible) Text("One-time clean-up, nothing to undo.", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            item {
                Text(
                    "Turning off the apps on the Clean tab gives the biggest speed gain: they stop starting in the background.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp),
                )
            }
        }
        Button(onClick = vm::applyTweaks, enabled = !busy && state.tweakSelection.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text("Speed up my TV")
        }
    }
}

@Composable
private fun LookTab(state: UiState, vm: CleanViewModel, busy: Boolean) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Ad-free home screen", style = MaterialTheme.typography.titleMedium)
        Text(
            "Step 1 — the Clean tab with \"Recommended\" turns off the services that put sponsored rows, " +
                "S-Channel promo rows and movie promotions on the stock home screen.",
        )
        Text(
            "Step 2 — Clean Home is a fast home screen with no ads, no recommendations and no tracking: " +
                "a clock, your apps and quick links to Inputs and Settings.",
        )
        Card(colors = CardDefaults.cardColors()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    !state.cleanHomeInstalled && !state.cleanHomeApkBundled -> Text(
                        "This build of the phone app does not include Clean Home. Install clean-home.apk on the TV, " +
                            "then come back here.",
                    )
                    !state.cleanHomeInstalled -> Button(onClick = vm::installCleanHome, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text("Install Clean Home on the TV")
                    }
                    else -> {
                        val vendorLocked = state.profile?.homeSwitch == HomeSwitchPolicy.VENDOR_HOME_REDIRECT
                        Text("Clean Home is installed. It is in the apps row of your TV.")
                        val tvV = state.tvHomeVersion ?: "unknown"
                        Text(
                            if (tvV == state.appVersion) "Clean Home on the TV is up to date (version $tvV)."
                            else "Clean Home on the TV is version $tvV; this app has version ${state.appVersion}. Tap Update.",
                            fontWeight = FontWeight.Medium,
                        )
                        OutlinedButton(onClick = vm::openCleanHome, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Open it on the TV now") }
                        if (state.cleanHomeApkBundled) {
                            OutlinedButton(onClick = vm::installCleanHome, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                Text("Update Clean Home on the TV")
                            }
                        }
                        when {
                            state.redirectOn -> {
                                Text("The Home button opens Clean Home.", fontWeight = FontWeight.Medium)
                                OutlinedButton(onClick = vm::disableHomeRedirect, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                    Text("Give Home back to the stock home")
                                }
                            }
                            state.history.cleanHomeActive -> Text("Clean Home is your home screen.")
                            vendorLocked -> {
                                Button(onClick = vm::enableHomeRedirect, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                    Text("Make the Home button open Clean Home")
                                }
                                Text(
                                    "Recommended for your TV. The stock home stays installed and sends you straight on to " +
                                        "Clean Home, so the remote's Netflix, YouTube and Inputs keys keep working.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                TextButton(onClick = vm::startCleanHomeTrial, enabled = !busy) {
                                    Text("Or try replacing the stock home (60 s trial)")
                                }
                            }
                            else -> Button(onClick = vm::startCleanHomeTrial, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                Text("Make it my home screen (60 s trial)")
                            }
                        }
                    }
                }
            }
        }
        if (state.profile?.homeSwitch == HomeSwitchPolicy.VENDOR_HOME_REDIRECT) {
            Text(
                "On your ${state.props?.brand ?: "TV"}, the manufacturer's key service locks the Home key to the stock home. " +
                    "The Home redirect works around that without disabling anything. The 60 s trial instead turns the stock " +
                    "home off, and puts it back if Clean Home doesn't open or you don't answer.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        state.profile?.notes?.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun UndoTab(state: UiState, vm: CleanViewModel, busy: Boolean) {
    val disabledOnTv = state.inventory.filter { !it.installed.enabled }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f)) {
            item {
                Text(
                    "Turned off on the TV (${disabledOnTv.size})",
                    style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp),
                )
            }
            items(disabledOnTv, key = { it.pkg }) { v ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(v.rule.label)
                        Text(
                            v.pkg + if (v.pkg in state.history.disabled) "" else " · not by Clean Your TV",
                            style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                        )
                    }
                    TextButton(onClick = { vm.enablePackage(v.pkg) }, enabled = !busy) { Text("Turn on") }
                }
                HorizontalDivider()
            }
            if (state.history.savedSettings.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${state.history.savedSettings.size} speed settings changed", Modifier.weight(1f))
                        TextButton(onClick = vm::undoTweaks, enabled = !busy) { Text("Undo") }
                    }
                }
            }
        }
        Button(
            onClick = vm::restoreEverything, enabled = !busy && !state.history.isEmpty,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) { Text("Undo everything Clean Your TV changed") }
    }
}

@Composable
private fun LogView(log: List<String>) {
    if (log.isEmpty()) return
    Column(Modifier.fillMaxWidth().height(96.dp).verticalScroll(rememberScrollState(Int.MAX_VALUE)).padding(horizontal = 12.dp)) {
        log.takeLast(30).forEach { Text(it, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
    }
}

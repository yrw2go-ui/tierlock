package app.tierlock

import android.annotation.SuppressLint
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import androidx.core.graphics.drawable.toBitmap

// Colors from the web app
private val Ink = Color(0xFF151D2B)
private val Muted = Color(0xFF586377)
private val Background = Color(0xFFECEFF3)
private val Accent = Color(0xFF8F6418)
private val Bad = Color(0xFFAF3229)
private val Ok = Color(0xFF1D7443)
private fun levelColor(level: Int) = when (level) {
    1 -> Color(0xFF8F6418)
    2 -> Color(0xFF12706A)
    3 -> Color(0xFF4752B0)
    else -> Color(0xFF636B78)
}

@Composable
fun TierlockApp(a: MainActivity) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Accent, onPrimary = Color.White, background = Background, surface = Color.White)) {
        Surface(Modifier.fillMaxSize(), color = Background) {
            Box(Modifier.safeDrawingPadding()) {
                when (val s = a.screen) {
                    Screen.Setup -> SetupScreen(a)
                    Screen.Pair -> PairScreen(a)
                    Screen.Parent -> ParentScreen(a)
                    Screen.Finish -> FinishScreen(a)
                    Screen.Lock -> LockScreen(a)
                    is Screen.Pin -> PinScreen(a, s)
                    is Screen.Home -> HomeScreen(a, s.person)
                    Screen.Settings -> SettingsScreen(a)
                }
            }
        }
    }
}

@Composable
private fun Page(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}

@Composable
private fun Title(text: String, sub: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink)
        if (sub != null) Text(sub, color = Muted)
    }
}

@Composable
private fun Notice(a: MainActivity) {
    a.notice?.let { Text(it, color = Bad, fontSize = 14.sp) }
}

@Composable
private fun ChoiceCard(title: String, body: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(body, color = Muted, fontSize = 14.sp)
        }
    }
}

// --- setup ---

@Composable
private fun SetupScreen(a: MainActivity) = Page {
    Title("Set up this device", "Choose how this phone or tablet will be used. You can change it later.")
    ChoiceCard(
        "Family device",
        "A shared or kid's phone or tablet. Tierlock becomes its home screen: each person unlocks with their own PIN and only sees the apps their level allows.",
        a::chooseFamily,
    )
    ChoiceCard(
        "Parent device",
        "Your own phone or tablet. Shows the parent controls only, with no locks.",
        a::chooseParent,
    )
}

@Composable
private fun PairScreen(a: MainActivity) = Page {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(Build.MODEL ?: "Family tablet") }
    Title("Link to your family", "On a parent device, open Tierlock, sign in, and tap Link a family device. Enter the code it shows.")
    OutlinedTextField(
        value = code,
        onValueChange = { v -> code = v.uppercase().filter { it.isLetterOrDigit() }.take(8) },
        label = { Text("8-character code") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = name,
        onValueChange = { name = it.take(60) },
        label = { Text("Name for this device") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Notice(a)
    Button(onClick = { a.pair(code, name.trim()) }, enabled = code.length == 8 && name.isNotBlank() && !a.busy, modifier = Modifier.fillMaxWidth()) {
        Text(if (a.busy) "Linking…" else "Link device")
    }
    TextButton(onClick = a::backToSetup) { Text("Back") }
}

@Composable
private fun FinishScreen(a: MainActivity) = Page {
    Title("Finish setting up ${a.deviceName}", "Two Android settings let Tierlock run this device.")
    SetupStep(
        "Make Tierlock the home screen",
        "Pressing Home then always opens Tierlock's lock screen.",
        a.isHomeApp,
        "Set as home screen",
        a::requestHomeApp,
    )
    SetupStep(
        "Allow screen time tracking",
        "In the list that opens, find Tierlock and turn on usage access. Minutes used are saved to your family.",
        a.hasUsageAccess,
        "Open usage access",
        a::requestUsageAccess,
    )
    Button(onClick = a::finishSetup, enabled = a.isHomeApp, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
    if (!a.isHomeApp) Text("Set Tierlock as the home screen to continue.", color = Muted, fontSize = 13.sp)
}

@Composable
private fun SetupStep(title: String, body: String, done: Boolean, action: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(body, color = Muted, fontSize = 14.sp)
            if (done) Text("Done", color = Ok, fontWeight = FontWeight.SemiBold) else OutlinedButton(onClick = onClick) { Text(action) }
        }
    }
}

// --- parent device ---

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ParentScreen(a: MainActivity) {
    var web by remember { mutableStateOf<WebView?>(null) }
    var confirm by remember { mutableStateOf(false) }
    BackHandler(enabled = web?.canGoBack() == true) { web?.goBack() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(Ink).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Tierlock · Parent", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = { confirm = true }) { Text("Device type", color = Color(0xFFF2E5CB)) }
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    CookieManager.getInstance().setAcceptCookie(true)
                    webViewClient = WebViewClient()
                    loadUrl(BuildConfig.API_BASE)
                    web = this
                }
            },
        )
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Change device type?") },
            text = { Text("This device will go back to setup, where you can choose Family device instead.") },
            confirmButton = { TextButton(onClick = { confirm = false; a.backToSetup() }) { Text("Change") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

// --- family device ---

@Composable
private fun Avatar(person: Person, size: Int = 44) {
    Box(Modifier.size(size.dp).background(levelColor(person.level), CircleShape), contentAlignment = Alignment.Center) {
        Text(person.name.take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size / 2.4).sp)
    }
}

@Composable
private fun LockScreen(a: MainActivity) {
    val family = a.family
    BackHandler { }
    Page {
        Title("Who's using ${a.deviceName}?")
        if (family == null || family.people.isEmpty()) {
            Text("Loading your family…", color = Muted)
            OutlinedButton(onClick = a::refresh) { Text("Try again") }
            return@Page
        }
        family.people.forEach { p ->
            Card(Modifier.fillMaxWidth().clickable { a.pick(p) }, colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Avatar(p)
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 17.sp)
                        Text("Level ${p.level} · ${p.levelName}", color = levelColor(p.level), fontSize = 13.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        var pickGrownUp by remember { mutableStateOf(false) }
        TextButton(onClick = { pickGrownUp = true }) { Text("Device settings (grown-ups)") }
        if (pickGrownUp) {
            AlertDialog(
                onDismissRequest = { pickGrownUp = false },
                title = { Text("Who's changing settings?") },
                text = {
                    Column {
                        family.people.filter { it.isGrownUp && it.pin != null }.forEach { g ->
                            TextButton(onClick = { pickGrownUp = false; a.openSettings(g) }) { Text(g.name) }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { pickGrownUp = false }) { Text("Cancel") } },
            )
        }
        Notice(a)
    }
}

@Composable
private fun PinScreen(a: MainActivity, target: Screen.Pin) {
    var pin by remember(target) { mutableStateOf("") }
    var wrong by remember(target) { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val bio = a.canUseBiometrics(target)
    val waitSeconds = ((a.pinWaitUntil - now + 999) / 1000).coerceAtLeast(0)
    BackHandler { a.cancelPin() }
    LaunchedEffect(target) {
        a.refreshPinWait()
        if (bio && target.person.login == "bio") a.askBiometrics(target)
    }
    // tick while locked out so the countdown updates and the keypad comes back
    LaunchedEffect(a.pinWaitUntil) {
        while (System.currentTimeMillis() < a.pinWaitUntil) {
            now = System.currentTimeMillis()
            delay(1000)
        }
        now = System.currentTimeMillis()
    }
    fun press(d: String) {
        if (pin.length >= 4 || waitSeconds > 0) return
        pin += d
        wrong = false
        if (pin.length == 4) {
            if (!a.pinEntered(target, pin)) { wrong = true; pin = "" }
        }
    }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Spacer(Modifier.height(12.dp))
        Avatar(target.person, 64)
        Text(if (target.forSettings) "${target.person.name}'s PIN for settings" else "Enter ${target.person.name}'s PIN", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(4) { i -> Box(Modifier.size(14.dp).background(if (i < pin.length) Ink else Color(0xFFD3D9E2), CircleShape)) }
        }
        val message = when {
            waitSeconds > 0 -> "Too many wrong PINs. Try again in ${if (waitSeconds >= 60) "${(waitSeconds + 59) / 60} min" else "$waitSeconds s"}."
            wrong -> "Wrong PIN. Try again."
            else -> " "
        }
        Text(message, color = Bad, fontSize = 14.sp, textAlign = TextAlign.Center)
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
        LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.width(280.dp), userScrollEnabled = false,
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(keys) { k ->
                if (k.isEmpty()) Spacer(Modifier.height(64.dp)) else Box(
                    Modifier.height(64.dp).background(Color.White, RoundedCornerShape(14.dp)).clickable {
                        if (k == "⌫") pin = pin.dropLast(1) else press(k)
                    },
                    contentAlignment = Alignment.Center,
                ) { Text(k, fontSize = 24.sp, color = Ink) }
            }
        }
        if (bio) OutlinedButton(onClick = { a.askBiometrics(target) }) { Text("Use fingerprint or face") }
        TextButton(onClick = a::cancelPin) { Text("Cancel") }
    }
}

@Composable
private fun HomeScreen(a: MainActivity, person: Person) {
    BackHandler { }
    val apps = remember(person, a.installed) { Apps.allowedFor(a, person, a.installed) }
    val family = a.family
    val used = family?.screenTime?.get(person.id) ?: 0
    val tasks = family?.openTasks(person.id) ?: emptyList()
    val kids = !person.isGrownUp && person.space == "kids"
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (kids) 112.dp else 84.dp),
        contentPadding = PaddingValues(18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(person)
                Column(Modifier.weight(1f)) {
                    Text(person.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
                    val time = if (person.limitMinutes > 0) "$used of ${person.limitMinutes} min used today" else "$used min used today"
                    Text(time, color = Muted, fontSize = 13.sp)
                }
                Button(onClick = a::lockNow, colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Text("Lock") }
            }
        }
        if (tasks.isNotEmpty()) {
            items(tasks, span = { GridItemSpan(maxLineSpan) }) { t ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF2E5CB))) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(t.title, fontWeight = FontWeight.SemiBold, color = Ink)
                        if (t.note.isNotBlank()) Text(t.note, color = Muted, fontSize = 13.sp)
                        val status = when (t.status) { "review" -> "Waiting for a grown-up to check"; "redo" -> "Sent back, please try again"; else -> "From ${t.sentBy}" }
                        Text(status, color = Accent, fontSize = 13.sp)
                    }
                }
            }
        }
        items(apps, key = { it.pkg }) { app ->
            Column(
                Modifier.clickable { a.launch(app) }.padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val bitmap = remember(app.pkg) { app.icon.toBitmap(144, 144).asImageBitmap() }
                Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(if (kids) 72.dp else 54.dp))
                Text(app.label, fontSize = if (kids) 15.sp else 12.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
        if (apps.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("No apps are allowed for ${person.name} on this device yet.", color = Muted)
            }
        }
    }
}

@Composable
private fun SettingsScreen(a: MainActivity) {
    var confirm by remember { mutableStateOf(false) }
    BackHandler { a.cancelPin() }
    Page {
        Title("Device settings", a.deviceName)
        SetupStep("Tierlock is the home screen", "Needed so Home always returns to the lock screen.", a.isHomeApp, "Set as home screen", a::requestHomeApp)
        SetupStep("Screen time tracking", "Usage access lets Tierlock count minutes for each person.", a.hasUsageAccess, "Open usage access", a::requestUsageAccess)
        OutlinedButton(onClick = a::refresh, modifier = Modifier.fillMaxWidth()) { Text("Refresh family rules now") }
        OutlinedButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) { Text("Unlink this device", color = Bad) }
        Button(onClick = a::cancelPin, modifier = Modifier.fillMaxWidth()) { Text("Back to lock screen") }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Unlink this device?") },
            text = { Text("It will stop following your family's rules until it's linked again with a new code. Also choose another home screen app in Android settings if you're removing Tierlock.") },
            confirmButton = { TextButton(onClick = { confirm = false; a.backToSetup() }) { Text("Unlink", color = Bad) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

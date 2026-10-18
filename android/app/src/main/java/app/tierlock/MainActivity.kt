package app.tierlock

import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

sealed interface Screen {
    data object Setup : Screen
    data object Pair : Screen
    data object Parent : Screen
    data object Finish : Screen
    data object Lock : Screen
    data class Pin(val person: Person, val forSettings: Boolean = false) : Screen
    data class Home(val person: Person) : Screen
    data object Settings : Screen
}

// One activity for both device types. On a family device it is also the home
// screen, so pressing Home always comes back here.
class MainActivity : FragmentActivity() {
    private lateinit var store: Store

    var screen by mutableStateOf<Screen>(Screen.Setup)
        private set
    var family by mutableStateOf<FamilyState?>(null)
        private set
    var installed by mutableStateOf<List<LaunchApp>>(emptyList())
        private set
    var notice by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
        private set
    var isHomeApp by mutableStateOf(false)
        private set
    var hasUsageAccess by mutableStateOf(false)
        private set

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = lockNow()
    }

    private val askForHome = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { checkSetup() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        family = store.stateJson?.let { runCatching { parseFamily(JSONObject(it)) }.getOrNull() }
        // a shared device locks itself when its screen turns off
        ContextCompat.registerReceiver(this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        setContent { TierlockApp(this) }
        checkSetup()
        route()
    }

    override fun onDestroy() {
        unregisterReceiver(screenOff)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        installed = Apps.installed(this)
        checkSetup()
        if (store.role == "family" && store.token != null) refresh()
    }

    private fun route() {
        screen = when {
            store.role == null -> Screen.Setup
            store.role == "parent" -> Screen.Parent
            store.token == null -> Screen.Pair
            !isHomeApp -> Screen.Finish
            else -> family?.person(store.currentMember)?.let { Screen.Home(it) } ?: Screen.Lock
        }
    }

    private fun checkSetup() {
        val home = packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
        isHomeApp = home?.activityInfo?.packageName == packageName
        hasUsageAccess = ScreenTime.hasAccess(this)
    }

    // --- setup ---

    fun chooseParent() {
        store.role = "parent"
        route()
    }

    fun chooseFamily() {
        screen = Screen.Pair
    }

    fun backToSetup() {
        store.unlink()
        WorkManager.getInstance(this).cancelAllWork()
        family = null
        notice = null
        screen = Screen.Setup
    }

    fun pair(code: String, name: String) {
        if (busy) return
        busy = true
        notice = null
        lifecycleScope.launch {
            try {
                val res = Api.pair(code, name)
                store.role = "family"
                store.token = res.getString("token")
                store.deviceName = name
                SyncWorker.schedule(this@MainActivity)
                loadState()
                checkSetup()
                route()
            } catch (e: Exception) {
                notice = e.message ?: "Couldn't link this device. Check the connection and try again."
            }
            busy = false
        }
    }

    fun requestHomeApp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = getSystemService(RoleManager::class.java)
            if (roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
                askForHome.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
                return
            }
        }
        startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
    }

    fun requestUsageAccess() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    fun finishSetup() {
        checkSetup()
        route()
    }

    // --- family data ---

    private suspend fun loadState() {
        val token = store.token ?: return
        val json = Api.state(token, ScreenTime.today())
        store.stateJson = json.toString()
        family = parseFamily(json)
    }

    fun refresh() {
        lifecycleScope.launch {
            try {
                loadState()
            } catch (e: ApiException) {
                if (e.status == 401) {
                    store.token = null
                    store.endSession()
                    notice = "This device was removed from the family. Link it again with a new code from a parent."
                    screen = Screen.Pair
                }
            } catch (e: Exception) {
                // offline: keep using the last saved family
            }
        }
    }

    // --- lock screen ---

    fun pick(person: Person) {
        if (person.login == "none" || person.pin == null) unlock(person) else screen = Screen.Pin(person)
    }

    fun openSettings(grownUp: Person) {
        screen = Screen.Pin(grownUp, forSettings = true)
    }

    fun pinEntered(target: Screen.Pin, pin: String): Boolean {
        if (pin != target.person.pin) return false
        if (target.forSettings) screen = Screen.Settings else unlock(target.person)
        return true
    }

    fun canUseBiometrics(person: Person) =
        person.login in setOf("bio", "either") &&
            BiometricManager.from(this).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    fun askBiometrics(target: Screen.Pin) {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (target.forSettings) screen = Screen.Settings else unlock(target.person)
            }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock as ${target.person.name}")
                .setNegativeButtonText("Use PIN")
                .setAllowedAuthenticators(BIOMETRIC_WEAK)
                .build(),
        )
    }

    private fun unlock(person: Person) {
        store.startSession(person.id)
        screen = Screen.Home(person)
    }

    fun lockNow() {
        if (store.role != "family") return
        if (store.currentMember != null) {
            store.endSession()
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { runCatching { ScreenTime.collect(this@MainActivity) } }
                SyncWorker.runNow(this@MainActivity)
            }
        }
        if (screen is Screen.Home || screen is Screen.Pin || screen is Screen.Settings) screen = Screen.Lock
    }

    fun cancelPin() {
        screen = Screen.Lock
    }

    fun launch(app: LaunchApp) {
        if (!Apps.launch(this, app.pkg)) notice = "${app.label} couldn't be opened."
    }

    val deviceName get() = family?.deviceName?.takeIf { it.isNotBlank() } ?: store.deviceName ?: "this device"
}

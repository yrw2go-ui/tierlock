package app.tierlock

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Telephony

data class LaunchApp(val pkg: String, val label: String, val icon: Drawable)

// Turns the family's app rules into real apps on this device.
object Apps {
    /** phone and texting stay available to everyone, as in the web prototype */
    private val ALWAYS = listOf("phone", "messages")

    fun installed(context: Context): List<LaunchApp> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { LaunchApp(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
            .sortedBy { it.label.lowercase() }
    }

    /** Owners and managers get every app; everyone else gets the apps their rules allow. */
    fun allowedFor(context: Context, person: Person, installed: List<LaunchApp>): List<LaunchApp> {
        if (person.isGrownUp) return installed
        val packages = (person.apps + ALWAYS).flatMap { packagesFor(context, it) }.toSet()
        return installed.filter { it.pkg in packages }
    }

    /** Rule ids are the web prototype's app names, or "pkg:<package>" for a specific app. */
    fun packagesFor(context: Context, id: String): Set<String> {
        if (id.startsWith("pkg:")) return setOf(id.removePrefix("pkg:"))
        val intents = when (id) {
            "phone" -> listOf(Intent(Intent.ACTION_DIAL))
            "messages" -> listOf(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")))
            "camera" -> listOf(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
            "browser" -> listOf(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE))
            "maps" -> listOf(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=park")))
            "music" -> listOf(selector(Intent.CATEGORY_APP_MUSIC))
            "photos" -> listOf(selector(Intent.CATEGORY_APP_GALLERY))
            "calendar" -> listOf(selector(Intent.CATEGORY_APP_CALENDAR))
            "calc" -> listOf(selector(Intent.CATEGORY_APP_CALCULATOR))
            "email" -> listOf(selector(Intent.CATEGORY_APP_EMAIL))
            "clock" -> listOf(Intent(AlarmClock.ACTION_SHOW_ALARMS))
            else -> emptyList()
        }
        val found = intents.flatMap { intent ->
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).map { it.activityInfo.packageName }
        }.toMutableSet()
        if (id == "messages") Telephony.Sms.getDefaultSmsPackage(context)?.let { found += it }
        if (id == "video") found += KNOWN_VIDEO
        return found
    }

    fun launch(context: Context, pkg: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    private fun selector(category: String) = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category)

    private val KNOWN_VIDEO = setOf("com.google.android.youtube", "com.google.android.apps.youtube.kids")
}

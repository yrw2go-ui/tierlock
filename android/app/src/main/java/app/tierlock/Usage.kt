package app.tierlock

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

// Screen time: Android reports which app is in front and when; Tierlock adds each
// stretch to whoever was signed in on its lock screen at the time.
object ScreenTime {
    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun today(): String = LocalDate.now().toString()

    private fun dayOf(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    /** Reads new app activity since the last run and adds it to each person's totals. */
    fun collect(context: Context) {
        if (!hasAccess(context)) return
        val store = Store(context)
        val now = System.currentTimeMillis()
        val from = store.usageCheckpoint.takeIf { it > 0 } ?: (now - TimeUnit.MINUTES.toMillis(15))
        var fg = store.foregroundPackage
        var fgSince = store.foregroundSince
        val stretches = mutableListOf<Triple<String, Long, Long>>()

        val events = context.getSystemService(UsageStatsManager::class.java).queryEvents(from, now)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (fg != null && fg != e.packageName) stretches += Triple(fg, fgSince, e.timeStamp)
                    if (fg != e.packageName) { fg = e.packageName; fgSince = e.timeStamp }
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    if (fg == e.packageName) { stretches += Triple(fg!!, fgSince, e.timeStamp); fg = null }
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    if (fg != null) { stretches += Triple(fg!!, fgSince, e.timeStamp); fg = null }
                }
            }
        }
        // the app still in front counts up to now and carries on next time
        if (fg != null) { stretches += Triple(fg!!, fgSince, now); fgSince = now }

        val usage = store.usage()
        val sessions = store.sessions()
        for ((pkg, start, end) in stretches) {
            if (pkg == context.packageName || end <= start) continue
            for (i in 0 until sessions.length()) {
                val s = sessions.getJSONObject(i)
                val sEnd = s.optLong("e").takeIf { it > 0 } ?: now
                val overlap = minOf(end, sEnd) - maxOf(start, s.getLong("s"))
                if (overlap <= 0) continue
                val day = usage.optJSONObject(dayOf(start)) ?: JSONObject().also { usage.put(dayOf(start), it) }
                val member = day.optJSONObject(s.getString("m")) ?: JSONObject().also { day.put(s.getString("m"), it) }
                member.put(pkg, member.optLong(pkg) + overlap)
            }
        }
        // keep the last three days
        val keep = (0..2).map { LocalDate.now().minusDays(it.toLong()).toString() }.toSet()
        usage.keys().asSequence().toList().filter { it !in keep }.forEach { usage.remove(it) }
        store.saveUsage(usage, now, fg, fgSince)
    }

    /** Sends today's and yesterday's totals; the server keeps the larger value, so resending is safe. */
    suspend fun report(context: Context) {
        val store = Store(context)
        val token = store.token ?: return
        val usage = store.usage()
        for (day in listOf(LocalDate.now().minusDays(1).toString(), today())) {
            val members = usage.optJSONObject(day) ?: continue
            val entries = JSONArray()
            for (member in members.keys()) {
                val apps = members.getJSONObject(member)
                for (pkg in apps.keys()) {
                    val minutes = (apps.getLong(pkg) / 60_000L).toInt()
                    if (minutes > 0) entries.put(JSONObject().put("member", member).put("app", pkg).put("minutes", minutes.coerceAtMost(1440)))
                }
            }
            if (entries.length() > 0) Api.usage(token, day, entries)
        }
    }
}

/** Every 15 minutes: count screen time, send it, and fetch the latest family rules. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val store = Store(applicationContext)
        val token = store.token ?: return Result.success()
        runCatching { ScreenTime.collect(applicationContext) }
        runCatching { ScreenTime.report(applicationContext) }
        try {
            store.stateJson = Api.state(token, ScreenTime.today()).toString()
        } catch (e: ApiException) {
            if (e.status == 401) store.token = null
        } catch (e: Exception) {
            // offline: try again next time
        }
        return Result.success()
    }

    companion object {
        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "sync",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(online).build(),
            )
        }

        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(online).build())
        }
    }
}

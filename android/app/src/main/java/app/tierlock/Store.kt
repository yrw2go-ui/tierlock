package app.tierlock

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// Everything this device remembers between launches.
class Store(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("tierlock", Context.MODE_PRIVATE)

    /** "family" or "parent", picked at setup */
    var role: String?
        get() = prefs.getString("role", null)
        set(v) = prefs.edit().putString("role", v).apply()

    /** device token from pairing; only family devices have one */
    var token: String?
        get() = prefs.getString("token", null)
        set(v) = prefs.edit().putString("token", v).apply()

    var deviceName: String?
        get() = prefs.getString("deviceName", null)
        set(v) = prefs.edit().putString("deviceName", v).apply()

    /** last family state from the server, used when offline */
    var stateJson: String?
        get() = prefs.getString("state", null)
        set(v) = prefs.edit().putString("state", v).apply()

    fun unlink() {
        prefs.edit().clear().apply()
    }

    // Who used the device when: [{m: memberId, s: startMillis, e: endMillis or 0 while open}]
    fun sessions(): JSONArray = synchronized(lock) { JSONArray(prefs.getString("sessions", "[]")) }

    val currentMember: String?
        get() {
            synchronized(lock) {
                val all = sessions()
                if (all.length() == 0) return null
                val last = all.getJSONObject(all.length() - 1)
                return if (last.optLong("e") == 0L) last.getString("m") else null
            }
        }

    fun startSession(member: String) = synchronized(lock) {
        endSession()
        val all = sessions().put(JSONObject().put("m", member).put("s", System.currentTimeMillis()).put("e", 0L))
        // keep two days of sessions for screen-time attribution
        val cutoff = System.currentTimeMillis() - 2 * DAY_MS
        val kept = JSONArray()
        for (i in 0 until all.length()) {
            val s = all.getJSONObject(i)
            if (s.optLong("e") == 0L || s.optLong("e") > cutoff) kept.put(s)
        }
        prefs.edit().putString("sessions", kept.toString()).commit()
    }

    fun endSession() = synchronized(lock) {
        val all = sessions()
        if (all.length() == 0) return@synchronized
        val last = all.getJSONObject(all.length() - 1)
        if (last.optLong("e") == 0L) {
            last.put("e", System.currentTimeMillis())
            prefs.edit().putString("sessions", all.toString()).commit()
        }
    }

    // Screen time collected on this device: {day: {member: {package: millis}}}
    fun usage(): JSONObject = synchronized(lock) { JSONObject(prefs.getString("usage", "{}")) }

    fun saveUsage(usage: JSONObject, checkpoint: Long, foreground: String?, foregroundSince: Long) = synchronized(lock) {
        prefs.edit()
            .putString("usage", usage.toString())
            .putLong("usageCheckpoint", checkpoint)
            .putString("fgPkg", foreground)
            .putLong("fgSince", foregroundSince)
            .commit()
    }

    val usageCheckpoint: Long get() = prefs.getLong("usageCheckpoint", 0L)
    val foregroundPackage: String? get() = prefs.getString("fgPkg", null)
    val foregroundSince: Long get() = prefs.getLong("fgSince", 0L)

    companion object {
        private val lock = Any()
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}

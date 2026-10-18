package app.tierlock

import android.text.Html
import org.json.JSONObject

// The family as the server sends it (same shape the web prototype uses).

data class Person(
    val id: String,
    val name: String,
    val level: Int,
    val pin: String?,
    /** "pin", "bio", "either" or "none" */
    val login: String,
    /** "full", "allowed" or "kids" */
    val space: String,
    val apps: List<String>,
    val limitMinutes: Int,
    val downtimeOn: Boolean,
    val downtimeFrom: Double,
    val downtimeTo: Double,
    val hue: Int,
) {
    val isGrownUp get() = level <= 2
    val levelName get() = when (level) { 1 -> "Owner"; 2 -> "Manager"; 3 -> "Standard"; else -> "Restricted" }
}

data class Task(
    val id: Long,
    val who: String,
    val title: String,
    val note: String,
    val status: String,
    val locks: Boolean,
    val photoRequired: Boolean,
    val sentBy: String,
)

data class FamilyState(
    val deviceName: String,
    val people: List<Person>,
    val tasks: List<Task>,
    /** minutes used today, per member, across all family devices */
    val screenTime: Map<String, Int>,
) {
    fun person(id: String?) = people.firstOrNull { it.id == id }
    fun openTasks(member: String) = tasks.filter { it.who == member && it.status in setOf("open", "redo", "review") }
}

// the web app stores some text HTML-escaped
private fun plain(s: String) = Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString()

fun parseFamily(json: JSONObject): FamilyState {
    val people = json.optJSONArray("people")
    val tasks = json.optJSONArray("tasks")
    val screen = json.optJSONObject("screenTime") ?: JSONObject()
    return FamilyState(
        deviceName = json.optJSONObject("device")?.optString("name") ?: "",
        people = (0 until (people?.length() ?: 0)).map { i ->
            val p = people!!.getJSONObject(i)
            val r = p.optJSONObject("rules") ?: JSONObject()
            val down = r.optJSONObject("down") ?: JSONObject()
            val apps = r.optJSONArray("apps")
            Person(
                id = p.getString("id"),
                name = plain(p.optString("name")),
                level = p.optInt("level", 4),
                pin = if (p.isNull("pin")) null else p.optString("pin"),
                login = r.optString("login", "pin"),
                space = r.optString("space", "full"),
                apps = (0 until (apps?.length() ?: 0)).map { apps!!.getString(it) },
                limitMinutes = r.optInt("limit", 0),
                downtimeOn = down.optBoolean("on", false),
                downtimeFrom = down.optDouble("from", 21.0),
                downtimeTo = down.optDouble("to", 7.0),
                hue = p.optInt("wh", 210),
            )
        },
        tasks = (0 until (tasks?.length() ?: 0)).map { i ->
            val t = tasks!!.getJSONObject(i)
            Task(
                id = t.optLong("id"),
                who = t.optString("who"),
                title = plain(t.optString("title")),
                note = plain(t.optString("note")),
                status = t.optString("status"),
                locks = t.optBoolean("locks"),
                photoRequired = t.optBoolean("photo"),
                sentBy = plain(t.optString("by")),
            )
        },
        screenTime = screen.keys().asSequence().associateWith { screen.optInt(it) },
    )
}

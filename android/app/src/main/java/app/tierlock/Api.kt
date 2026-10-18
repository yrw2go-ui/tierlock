package app.tierlock

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ApiException(message: String, val status: Int) : Exception(message)

// Talks to the Tierlock site's /api functions (see api/ in the repo).
object Api {
    private fun call(method: String, path: String, token: String? = null, body: JSONObject? = null): JSONObject {
        val conn = URL(BuildConfig.API_BASE + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Accept", "application/json")
            if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (status !in 200..299) {
                throw ApiException(json.optString("error", "Something went wrong ($status)"), status)
            }
            return json
        } finally {
            conn.disconnect()
        }
    }

    suspend fun pair(code: String, name: String): JSONObject = withContext(Dispatchers.IO) {
        call("POST", "/api/pair", body = JSONObject().put("code", code).put("name", name).put("platform", "android"))
    }

    suspend fun state(token: String, day: String): JSONObject = withContext(Dispatchers.IO) {
        call("GET", "/api/device/state?day=$day", token)
    }

    suspend fun usage(token: String, day: String, entries: JSONArray): JSONObject = withContext(Dispatchers.IO) {
        call("POST", "/api/device/usage", token, JSONObject().put("day", day).put("entries", entries))
    }
}

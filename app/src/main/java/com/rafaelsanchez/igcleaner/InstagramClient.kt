package com.rafaelsanchez.igcleaner

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class Profile(val username: String, val mediaCount: Int)
data class MediaItem(val pk: String, val fullId: String, val type: Int)
enum class TaskKind { DELETE, ARCHIVE }

class AuthException : Exception("Session expired")
class RateLimitException : Exception("Rate limited")

/**
 * Cliente mínimo para la web de Instagram. Usa la sesión (cookies) creada en el WebView.
 * No existe API oficial para borrar/archivar masivamente, por eso se usan los endpoints
 * internos de Instagram, que pueden cambiar sin aviso.
 */
class InstagramClient(private val userAgent: String) {

    private val base = "https://www.instagram.com"
    private val appId = "936619743392459"

    private val http = OkHttpClient.Builder()
        .followRedirects(false)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val working = mutableMapOf<TaskKind, Int>()

    private fun cookieString(): String =
        CookieManager.getInstance().getCookie(base) ?: ""

    private fun cookie(name: String): String? =
        cookieString().split(";").map { it.trim() }
            .firstOrNull { it.startsWith("$name=") }?.substringAfter("=")

    fun isLoggedIn(): Boolean = !cookie("sessionid").isNullOrBlank() && !cookie("ds_user_id").isNullOrBlank()
    fun userId(): String? = cookie("ds_user_id")

    private fun call(url: String, post: Boolean, form: Map<String, String> = emptyMap()): Pair<Int, String> {
        val b = Request.Builder().url(url)
            .header("User-Agent", userAgent)
            .header("Cookie", cookieString())
            .header("X-CSRFToken", cookie("csrftoken") ?: "")
            .header("X-IG-App-ID", appId)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", "$base/")
            .header("Origin", base)
            .header("Accept", "*/*")
        if (post) {
            val fb = FormBody.Builder()
            form.forEach { (k, v) -> fb.add(k, v) }
            b.post(fb.build())
        }
        http.newCall(b.build()).execute().use { r ->
            return r.code to (r.body?.string() ?: "")
        }
    }

    private fun check(code: Int, body: String) {
        if (code == 429) throw RateLimitException()
        if (code == 302 || code == 401 || body.contains("login_required")) throw AuthException()
    }

    suspend fun getProfile(): Profile = withContext(Dispatchers.IO) {
        val id = userId() ?: throw AuthException()
        val (code, body) = call("$base/api/v1/users/$id/info/", false)
        check(code, body)
        if (code != 200) throw IOException("HTTP $code")
        val user = JSONObject(body).getJSONObject("user")
        Profile(user.optString("username"), user.optInt("media_count"))
    }

    /** Recorre todo el feed del perfil y devuelve las publicaciones. */
    suspend fun listMedia(onFound: (Int) -> Unit): List<MediaItem> = withContext(Dispatchers.IO) {
        val id = userId() ?: throw AuthException()
        val out = mutableListOf<MediaItem>()
        var maxId = ""
        var guard = 0
        while (guard++ < 500) {
            var url = "$base/api/v1/feed/user/$id/?count=33"
            if (maxId.isNotEmpty()) url += "&max_id=$maxId"
            val (code, body) = call(url, false)
            check(code, body)
            if (code != 200) throw IOException("HTTP $code")
            val json = JSONObject(body)
            val items = json.optJSONArray("items")
            if (items != null) {
                for (i in 0 until items.length()) {
                    val it = items.getJSONObject(i)
                    val pk = it.optString("pk")
                    if (pk.isNotEmpty()) out.add(MediaItem(pk, it.optString("id", pk), it.optInt("media_type", 1)))
                }
            }
            onFound(out.size)
            maxId = json.optString("next_max_id", "")
            if (!json.optBoolean("more_available", false) || maxId.isEmpty()) break
            delay(1200)
        }
        out.distinctBy { it.pk }
    }

    private fun candidates(kind: TaskKind, m: MediaItem): List<String> {
        val t = when (m.type) { 2 -> "VIDEO"; 8 -> "CAROUSEL"; else -> "PHOTO" }
        return when (kind) {
            TaskKind.DELETE -> listOf(
                "$base/api/v1/web/create/${m.pk}/delete/",
                "$base/api/v1/media/${m.fullId}/delete/?media_type=$t"
            )
            TaskKind.ARCHIVE -> listOf(
                "$base/api/v1/web/create/${m.pk}/only_me/",
                "$base/api/v1/media/${m.fullId}/only_me/"
            )
        }
    }

    /** Ejecuta la acción sobre una publicación. true = éxito. */
    suspend fun act(kind: TaskKind, m: MediaItem): Boolean = withContext(Dispatchers.IO) {
        val urls = candidates(kind, m)
        val order = (listOfNotNull(working[kind]) + urls.indices).distinct()
        for (idx in order) {
            val (code, body) = call(urls[idx], true, mapOf("media_id" to m.fullId))
            check(code, body)
            if (code == 200) {
                val ok = try {
                    val j = JSONObject(body)
                    j.optString("status") == "ok" || j.optBoolean("did_delete", false)
                } catch (e: Exception) { false }
                if (ok) { working[kind] = idx; return@withContext true }
            }
        }
        false
    }
}

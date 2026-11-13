package com.rafaelsanchez.igcleaner

import android.app.Application
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebSettings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.random.Random

enum class Screen { LOGIN, WEBLOGIN, PANEL }
enum class Phase { SCANNING, RUNNING, DONE, ERROR }

data class TaskState(
    val kind: TaskKind,
    val phase: Phase,
    val done: Int = 0,
    val total: Int = 0,
    val failed: Int = 0,
    val message: String = ""
)

data class UiState(
    val screen: Screen,
    val profile: Profile? = null,
    val task: TaskState? = null,
    val message: String? = null
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val ua: String = WebSettings.getDefaultUserAgent(app).replace("; wv", "").replace("Version/4.0 ", "")
    val client = InstagramClient(ua)

    private val _state = MutableStateFlow(
        UiState(if (client.isLoggedIn()) Screen.PANEL else Screen.LOGIN)
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    // Las credenciales viven solo en memoria y se borran al terminar el login.
    private var pendingUser: String? = null
    private var pendingPass: String? = null
    private var job: Job? = null

    init { if (client.isLoggedIn()) loadProfile() }

    fun peekCredentials(): Pair<String, String>? {
        val u = pendingUser; val p = pendingPass
        return if (u != null && p != null) u to p else null
    }

    private fun clearCredentials() { pendingUser = null; pendingPass = null }

    fun startLogin(user: String, pass: String) {
        pendingUser = user.trim(); pendingPass = pass
        _state.update { it.copy(screen = Screen.WEBLOGIN, message = null) }
    }

    fun cancelLogin() {
        clearCredentials()
        _state.update { it.copy(screen = Screen.LOGIN) }
    }

    fun checkSession() {
        if (_state.value.screen == Screen.WEBLOGIN && client.isLoggedIn()) {
            clearCredentials()
            CookieManager.getInstance().flush()
            _state.update { it.copy(screen = Screen.PANEL) }
            loadProfile()
        }
    }

    fun loadProfile() {
        viewModelScope.launch {
            try {
                val p = client.getProfile()
                _state.update { it.copy(profile = p) }
            } catch (e: AuthException) {
                signOut("Session expired. Please sign in again.")
            } catch (e: Exception) {
                _state.update { it.copy(message = "Could not load profile: ${e.message}") }
            }
        }
    }

    fun startTask(kind: TaskKind) {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            try {
                setTask(TaskState(kind, Phase.SCANNING))
                val items = client.listMedia { n -> setTask(TaskState(kind, Phase.SCANNING, total = n)) }
                val total = items.size
                if (total == 0) { setTask(TaskState(kind, Phase.DONE, message = "No posts found.")); return@launch }

                var done = 0; var failed = 0; var streak = 0
                setTask(TaskState(kind, Phase.RUNNING, 0, total))
                for (item in items) {
                    ensureActive()
                    var ok = false; var tries = 0
                    while (true) {
                        try { ok = client.act(kind, item); break }
                        catch (e: RateLimitException) {
                            tries++
                            if (tries >= 3) throw e
                            delay(60_000)
                        } catch (e: IOException) {
                            tries++
                            if (tries >= 3) break
                            delay(3_000)
                        }
                    }
                    if (ok) { done++; streak = 0 } else { failed++; streak++ }
                    setTask(TaskState(kind, Phase.RUNNING, done, total, failed))
                    if (streak >= 5) {
                        setTask(TaskState(kind, Phase.ERROR, done, total, failed,
                            "Instagram rejected several requests. Stopped to protect your account."))
                        loadProfile(); return@launch
                    }
                    delay(Random.nextLong(1500, 3000))
                }
                setTask(TaskState(kind, Phase.DONE, done, total, failed))
                loadProfile()
            } catch (e: CancellationException) {
                _state.update { it.copy(task = null) }
                loadProfile()
                throw e
            } catch (e: AuthException) {
                signOut("Session expired. Please sign in again.")
            } catch (e: RateLimitException) {
                setTask(TaskState(kind, Phase.ERROR, message = "Instagram is limiting requests. Try again later."))
            } catch (e: Exception) {
                setTask(TaskState(kind, Phase.ERROR, message = "Error: ${e.message}"))
            }
        }
    }

    private fun setTask(t: TaskState) = _state.update { it.copy(task = t) }

    fun cancelTask() { job?.cancel() }
    fun dismissTask() = _state.update { it.copy(task = null) }

    fun signOut(msg: String? = null) {
        job?.cancel()
        clearCredentials()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        _state.value = UiState(Screen.LOGIN, message = msg)
    }
}

package com.guardian.shield.viewmodel

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.domain.model.AppRule
import com.guardian.shield.domain.repository.RulesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

enum class AppFilter { ALL, BLOCKED, WHITELISTED }

data class AppListState(
    val apps: List<AppRule> = emptyList(),
    val filter: AppFilter = AppFilter.ALL,
    val query: String = "",
    val loading: Boolean = false
)

@HiltViewModel
class AppListViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: RulesRepository,
    private val prefs: GuardianPreferences
) : ViewModel() {

    private val _filter = MutableStateFlow(AppFilter.ALL)
    private val _query = MutableStateFlow("")
    private val _loading = MutableStateFlow(false)

    val state: StateFlow<AppListState> = combine(
        repo.observeApps(),
        _filter,
        _query,
        _loading
    ) { apps, filter, query, loading ->
        val byPkg = apps.associateBy { it.packageName }
        val installed = loadInstalledApps()
        val merged = installed.map { (pkg, name) ->
            byPkg[pkg] ?: AppRule(pkg, name, isBlocked = false, isWhitelisted = false, createdAt = 0L)
        }
        val filtered = merged.filter { app ->
            val matchesFilter = when (filter) {
                AppFilter.ALL -> true
                AppFilter.BLOCKED -> app.isBlocked
                AppFilter.WHITELISTED -> app.isWhitelisted
            }
            val q = query.trim().lowercase()
            val matchesQuery = q.isBlank() ||
                app.appName.lowercase().contains(q) ||
                app.packageName.lowercase().contains(q)
            matchesFilter && matchesQuery
        }.sortedBy { it.appName.lowercase() }
        AppListState(filtered, filter, query, loading)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppListState())

    private suspend fun loadInstalledApps(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(0)
            apps.filter { app ->
                // exclude system apps without launcher intent
                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val hasLauncher = pm.getLaunchIntentForPackage(app.packageName) != null
                !isSystem || hasLauncher
            }.map { app ->
                app.packageName to (pm.getApplicationLabel(app).toString().ifBlank { app.packageName })
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    fun setFilter(filter: AppFilter) { _filter.value = filter }
    fun setQuery(q: String) { _query.value = q }

    fun setBlocked(pkg: String, blocked: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val current = repo.getApp(pkg)
                val updated = (current ?: AppRule(pkg, pkg, false, false, System.currentTimeMillis()))
                    .copy(
                        isBlocked = blocked,
                        // Blocking clears the whitelist flag
                        isWhitelisted = if (blocked) false else current?.isWhitelisted ?: false,
                        // R12 — stamp the grace window when (re)blocked; clear
                        // it on a normal (unlockable state) unblock.
                        blockedAtMs = if (blocked) System.currentTimeMillis() else 0L
                    )
                repo.upsertApp(updated)
                prefs.bumpRulesVersion()
            } catch (t: Throwable) {
                Timber.e(t, "Failed to persist block rule for $pkg — rules version NOT bumped; RulesEngine snapshot may stay stale")
            }
        }
    }

    /**
     * R12 (v3.8.2) — undo an accidental block within the 3-minute grace
     * window. Intentionally does NOT consult TimeLockManager: the whole point
     * is that a mistake made (or discovered) DURING a commitment lock can
     * still be reversed quickly. Once the window lapses the block is
     * permanent (status quo).
     */
    fun undoBlockWithinGrace(pkg: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val current = repo.getApp(pkg)
                if (current != null && current.isBlocked &&
                    AppRule.isWithinGrace(current.blockedAtMs)
                ) {
                    repo.upsertApp(current.copy(isBlocked = false, blockedAtMs = 0L))
                    prefs.bumpRulesVersion()
                    _undoEvents.trySend(UndoOutcome(true, current.appName))
                } else {
                    _undoEvents.trySend(UndoOutcome(false, current?.appName ?: pkg))
                }
            } catch (t: Throwable) {
                Timber.e(t, "undoBlockWithinGrace failed for $pkg")
                _undoEvents.trySend(UndoOutcome(false, pkg))
            }
        }
    }

    data class UndoOutcome(val undone: Boolean, val appName: String)

    private val _undoEvents = Channel<UndoOutcome>(Channel.BUFFERED)
    val undoEvents = _undoEvents.receiveAsFlow()

    /**
     * TASK 1 — One-way block rule.
     * If an app is currently in the BLOCKLIST, it CANNOT be moved back to the
     * allowlist. Hard restriction — no PIN bypass, no dialog, no exception.
     */
    fun setWhitelisted(pkg: String, whitelisted: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val current = repo.getApp(pkg)
                // GUARD: if currently blocked, whitelist toggle is forbidden.
                if (current?.isBlocked == true) return@launch

                val updated = (current ?: AppRule(pkg, pkg, false, false, System.currentTimeMillis()))
                    .copy(
                        isWhitelisted = whitelisted,
                        isBlocked = if (whitelisted) false else current?.isBlocked ?: false
                    )
                repo.upsertApp(updated)
                prefs.bumpRulesVersion()
            } catch (t: Throwable) {
                Timber.e(t, "Failed to persist whitelist rule for $pkg — rules version NOT bumped; RulesEngine snapshot may stay stale")
            }
        }
    }
}

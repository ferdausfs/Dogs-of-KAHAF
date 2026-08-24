package com.guardian.shield.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.domain.model.KeywordRule
import com.guardian.shield.domain.repository.RulesRepository
import com.guardian.shield.util.FilterCategories
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * R4 — Smart Filters screen state. Enabled categories live in DataStore;
 * their keywords are materialized as regular KeywordRule rows (severity 2).
 */
@HiltViewModel
class FiltersViewModel @Inject constructor(
    private val repo: RulesRepository,
    private val prefs: GuardianPreferences
) : ViewModel() {

    val enabledIds: StateFlow<Set<String>> = prefs.filterCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val categories: List<FilterCategories.Category> = FilterCategories.all

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cat = FilterCategories.byId(id) ?: return@launch

                val current = prefs.filterCategories.first()
                val updated = if (enabled) current + id else current - id
                if (updated == current) return@launch
                prefs.setFilterCategories(updated)

                val existing = repo.observeKeywords().first()
                val existingByKeyword = existing.associateBy { it.keyword.lowercase() }

                if (enabled) {
                    cat.keywords
                        .filter { existingByKeyword[it.lowercase()] == null }
                        .forEach {
                            repo.upsertKeyword(
                                KeywordRule(
                                    id = 0,
                                    keyword = it,
                                    isRegex = false,
                                    severity = 2,
                                    enabled = true
                                )
                            )
                        }
                } else {
                    val preset = cat.keywords.map { it.lowercase() }.toSet()
                    existing
                        .filter { it.keyword.lowercase() in preset }
                        .forEach { repo.deleteKeyword(it.id) }
                }
                prefs.bumpRulesVersion()
            } catch (t: Throwable) {
                Timber.e(t, "filter category toggle failed — engine snapshot may stay stale")
            }
        }
    }
}

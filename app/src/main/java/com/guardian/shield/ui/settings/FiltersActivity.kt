package com.guardian.shield.ui.settings

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import android.widget.TextView
import com.guardian.shield.R
import com.guardian.shield.databinding.ActivityFiltersBinding
import com.guardian.shield.service.detection.TimeLockManager
import com.guardian.shield.util.FilterCategories
import com.guardian.shield.viewmodel.FiltersViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.guardian.shield.util.ScreenInsets

/**
 * R4 — Content Filters screen (reference "Filters" screen). One switch per
 * category; toggling materializes/removes that category's preset keywords as
 * ordinary KeywordRule rows (see [FiltersViewModel]).
 */
@AndroidEntryPoint
class FiltersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFiltersBinding
    private val viewModel: FiltersViewModel by viewModels()

    @Inject lateinit var timeLockManager: TimeLockManager
    private var locked = false

    private data class Row(
        val categoryId: String,
        val switch: MaterialSwitch,
        val count: TextView
    )

    private lateinit var rows: List<Row>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFiltersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        ScreenInsets.padTopForStatusBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.filters_title)
        binding.toolbar.setNavigationOnClickListener { finish() }

        timeLockManager.clearIfExpired()
        locked = timeLockManager.isLocked()

        rows = listOf(
            Row("adult", binding.switchAdult, binding.txtCountAdult),
            Row("gambling", binding.switchGambling, binding.txtCountGambling),
            Row("drugs", binding.switchDrugs, binding.txtCountDrugs),
            Row("violence", binding.switchViolence, binding.txtCountViolence),
            Row("dating", binding.switchDating, binding.txtCountDating),
            Row("doomscroll", binding.switchDoomscroll, binding.txtCountDoomscroll)
        )

        rows.forEach { row ->
            row.count.text = getString(
                R.string.filter_count_fmt,
                FilterCategories.keywordCount(row.categoryId)
            )
            attach(row)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.enabledIds.collect { enabled ->
                    rows.forEach { row ->
                        val wanted = enabled.contains(row.categoryId)
                        if (row.switch.isChecked != wanted) {
                            row.switch.setOnCheckedChangeListener(null)
                            row.switch.isChecked = wanted
                            attach(row)
                        }
                    }
                }
            }
        }
    }

    private fun attach(row: Row) {
        row.switch.setOnCheckedChangeListener { _, isChecked ->
            if (locked) {
                row.switch.setOnCheckedChangeListener(null)
                row.switch.isChecked = !isChecked
                attach(row)
                showLockedSnack()
                return@setOnCheckedChangeListener
            }
            viewModel.setEnabled(row.categoryId, isChecked)
        }
    }

    private fun showLockedSnack() {
        Snackbar.make(binding.root, R.string.lock_editing_disabled, Snackbar.LENGTH_SHORT).show()
    }
}

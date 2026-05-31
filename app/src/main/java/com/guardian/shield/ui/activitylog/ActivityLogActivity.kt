package com.guardian.shield.ui.activitylog

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.guardian.shield.R
import com.guardian.shield.databinding.ActivityLogBinding
import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.ui.dashboard.BlockEventAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Phase 1 — Activity Log screen (full timeline of block events with filtering).
 *
 * Kept additive to the existing dashboard: launches as a child activity, reuses
 * [BlockEventAdapter] and the same DAO flow, and the back-stack rule is the
 * regular `parentActivityName`.
 */
@AndroidEntryPoint
class ActivityLogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogBinding
    private val viewModel: ActivityLogViewModel by viewModels()
    private lateinit var adapter: BlockEventAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = BlockEventAdapter(
            pm = packageManager,
            onDelete = { viewModel.delete(it.id) }
        )
        binding.recyclerEvents.layoutManager = LinearLayoutManager(this)
        binding.recyclerEvents.adapter = adapter

        // Filter chips
        binding.chipAll.setOnClickListener { viewModel.setFilter(LogFilter.ALL) }
        binding.chipAi.setOnClickListener { viewModel.setFilter(LogFilter.AI) }
        binding.chipKeyword.setOnClickListener { viewModel.setFilter(LogFilter.KEYWORD) }
        binding.chipApp.setOnClickListener { viewModel.setFilter(LogFilter.APP) }
        binding.chipSchedule.setOnClickListener { viewModel.setFilter(LogFilter.SCHEDULE) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submit(state.events)
                    binding.txtEmpty.visibility =
                        if (state.events.isEmpty()) View.VISIBLE else View.GONE
                    updateChipSelection(state.filter)
                    binding.txtCount.text =
                        getString(R.string.activity_log_count_fmt, state.events.size)
                }
            }
        }
    }

    private fun updateChipSelection(filter: LogFilter) {
        binding.chipAll.isChecked = filter == LogFilter.ALL
        binding.chipAi.isChecked = filter == LogFilter.AI
        binding.chipKeyword.isChecked = filter == LogFilter.KEYWORD
        binding.chipApp.isChecked = filter == LogFilter.APP
        binding.chipSchedule.isChecked = filter == LogFilter.SCHEDULE
    }
}

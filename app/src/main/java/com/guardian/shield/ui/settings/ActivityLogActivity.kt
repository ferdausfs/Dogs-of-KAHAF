package com.guardian.shield.ui.settings

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.guardian.shield.R
import com.guardian.shield.databinding.ActivityLogBinding
import com.guardian.shield.ui.dashboard.BlockEventAdapter
import com.guardian.shield.ui.navigation.AppBottomNav
import com.guardian.shield.viewmodel.ActivityLogFilter
import com.guardian.shield.viewmodel.ActivityLogViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

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
            onDelete = { viewModel.deleteEvent(it.id) }
        )
        binding.recyclerLog.layoutManager = LinearLayoutManager(this)
        binding.recyclerLog.adapter = adapter

        binding.chipAll.setOnClickListener { viewModel.setFilter(ActivityLogFilter.ALL) }
        binding.chipAi.setOnClickListener { viewModel.setFilter(ActivityLogFilter.AI) }
        binding.chipKeyword.setOnClickListener { viewModel.setFilter(ActivityLogFilter.KEYWORD) }
        binding.chipApp.setOnClickListener { viewModel.setFilter(ActivityLogFilter.APP) }
        binding.chipSchedule.setOnClickListener { viewModel.setFilter(ActivityLogFilter.SCHEDULE) }

        binding.inputSearch.editText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun afterTextChanged(s: Editable?) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setQuery(s?.toString().orEmpty())
            }
        })

        AppBottomNav.bind(this, binding.bottomNav, R.id.nav_activity)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submit(state.visibleEvents)
                    binding.txtSummary.text = getString(
                        R.string.activity_log_summary_fmt,
                        state.visibleEvents.size,
                        state.allEvents.size
                    )
                    binding.emptyState.visibility =
                        if (state.visibleEvents.isEmpty()) View.VISIBLE else View.GONE
                    binding.recyclerLog.visibility =
                        if (state.visibleEvents.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }
    }
}

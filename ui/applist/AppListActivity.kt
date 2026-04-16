package com.kahaf.guardian.ui.applist

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.kahaf.guardian.databinding.ActivityAppListBinding
import com.kahaf.guardian.ui.common.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AppListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppListBinding
    private val viewModel: AppListViewModel by viewModels()
    private lateinit var adapter: AppListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupSearch()
        setupTabs()
        observeState()

        // Pre-select tab based on intent
        val mode = intent.getStringExtra("mode")
        when (mode) {
            "blocked" -> binding.tabLayout.selectTab(binding.tabLayout.getTabAt(1))
            "whitelisted" -> binding.tabLayout.selectTab(binding.tabLayout.getTabAt(2))
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = AppListAdapter(
            onBlockToggle = { app -> viewModel.toggleBlocked(app) },
            onWhitelistToggle = { app -> viewModel.toggleWhitelisted(app) }
        )
        binding.rvApps.layoutManager = LinearLayoutManager(this)
        binding.rvApps.adapter = adapter
    }

    private fun setupSearch() {
        binding.etSearch.doAfterTextChanged { text ->
            viewModel.setSearchQuery(text?.toString() ?: "")
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                viewModel.setTab(tab?.position ?: 0)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun observeState() {
        collectFlow(viewModel.uiState) { state ->
            if (state.isLoading) {
                binding.progressBar.visible()
                binding.rvApps.gone()
                binding.tvEmpty.gone()
            } else {
                binding.progressBar.gone()
                if (state.filteredApps.isEmpty()) {
                    binding.rvApps.gone()
                    binding.tvEmpty.visible()
                } else {
                    binding.rvApps.visible()
                    binding.tvEmpty.gone()
                }
                adapter.submitList(state.filteredApps)
            }
        }
    }
}
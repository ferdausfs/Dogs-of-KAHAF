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
    private val vm: AppListViewModel by viewModels()
    private lateinit var adapter: AppListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }
        adapter = AppListAdapter({ vm.toggleBlocked(it) }, { vm.toggleWhitelisted(it) })
        binding.rvApps.layoutManager = LinearLayoutManager(this); binding.rvApps.adapter = adapter
        binding.etSearch.doAfterTextChanged { vm.setSearchQuery(it?.toString() ?: "") }
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) { vm.setTab(tab?.position ?: 0) }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}; override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        when (intent.getStringExtra("mode")) { "blocked" -> binding.tabLayout.selectTab(binding.tabLayout.getTabAt(1)); "whitelisted" -> binding.tabLayout.selectTab(binding.tabLayout.getTabAt(2)) }
        collectFlow(vm.uiState) { s ->
            if (s.isLoading) { binding.progressBar.visible(); binding.rvApps.gone(); binding.tvEmpty.gone() }
            else { binding.progressBar.gone(); if (s.filteredApps.isEmpty()) { binding.rvApps.gone(); binding.tvEmpty.visible() } else { binding.rvApps.visible(); binding.tvEmpty.gone() }; adapter.submitList(s.filteredApps) }
        }
    }
}

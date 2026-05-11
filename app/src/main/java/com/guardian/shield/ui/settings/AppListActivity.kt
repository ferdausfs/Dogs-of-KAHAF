package com.guardian.shield.ui.settings

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.guardian.shield.databinding.ActivityAppListBinding
import com.guardian.shield.viewmodel.AppFilter
import com.guardian.shield.viewmodel.AppListViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AppListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppListBinding
    private val viewModel: AppListViewModel by viewModels()
    private lateinit var adapter: AppListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = AppListAdapter(
            pm = packageManager,
            onBlockChanged = { pkg, blocked -> viewModel.setBlocked(pkg, blocked) },
            onWhitelistChanged = { pkg, wl -> viewModel.setWhitelisted(pkg, wl) }
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.chipAll.setOnClickListener { viewModel.setFilter(AppFilter.ALL) }
        binding.chipBlocked.setOnClickListener { viewModel.setFilter(AppFilter.BLOCKED) }
        binding.chipWhitelisted.setOnClickListener { viewModel.setFilter(AppFilter.WHITELISTED) }

        binding.editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setQuery(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { adapter.submit(it.apps) }
            }
        }
    }
}

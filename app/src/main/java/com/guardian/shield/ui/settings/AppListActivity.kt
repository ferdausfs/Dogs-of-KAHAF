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
import com.google.android.material.snackbar.Snackbar
import com.guardian.shield.databinding.ActivityAppListBinding
import com.guardian.shield.service.detection.TimeLockManager
import com.guardian.shield.viewmodel.AppFilter
import com.guardian.shield.viewmodel.AppListViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AppListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppListBinding
    private val viewModel: AppListViewModel by viewModels()
    private lateinit var adapter: AppListAdapter

    @Inject lateinit var timeLockManager: TimeLockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        timeLockManager.clearIfExpired()
        val locked = timeLockManager.isLocked()

        if (locked) {
            binding.lockBanner.visibility = View.VISIBLE
            binding.txtLockRemaining.text = "🔒 ${timeLockManager.getRemainingFormatted()}"
        } else {
            binding.lockBanner.visibility = View.GONE
        }

        adapter = AppListAdapter(
            pm = packageManager,
            isLocked = locked,
            onBlockChanged = { pkg, blocked ->
                if (locked) showLockedSnack()
                else viewModel.setBlocked(pkg, blocked)
            },
            onWhitelistChanged = { pkg, wl ->
                if (locked) showLockedSnack()
                else viewModel.setWhitelisted(pkg, wl)
            }
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

    private fun showLockedSnack() {
        Snackbar.make(binding.root, "🔒 Commitment Lock active — editing disabled", Snackbar.LENGTH_SHORT).show()
    }
}

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
import com.guardian.shield.R
import com.guardian.shield.databinding.ActivityWhitelistBinding
import com.guardian.shield.service.detection.TimeLockManager
import com.guardian.shield.viewmodel.AppFilter
import com.guardian.shield.viewmodel.AppListViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * R4 — Whitelist screen (reference "Whitelist" screen). Lists installed apps
 * with the isWhitelisted flag; toggling writes the real AppRule flag that
 * RulesEngine (and Focus Mode) already honor. Reuses [AppListViewModel] with
 * the WHITELISTED filter and [AppListAdapter] rows.
 */
@AndroidEntryPoint
class WhitelistActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWhitelistBinding
    private val viewModel: AppListViewModel by viewModels()
    private lateinit var adapter: AppListAdapter

    @Inject lateinit var timeLockManager: TimeLockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWhitelistBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.whitelist_title)
        binding.toolbar.setNavigationOnClickListener { finish() }

        timeLockManager.clearIfExpired()
        val locked = timeLockManager.isLocked()

        if (locked) {
            binding.lockBannerWl.visibility = View.VISIBLE
            binding.txtLockRemainingWl.text = "🔒 ${timeLockManager.getRemainingFormatted()}"
        } else {
            binding.lockBannerWl.visibility = View.GONE
        }

        // Whitelist-only view of the app list
        viewModel.setFilter(AppFilter.WHITELISTED)
        viewModel.setQuery("")

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
        binding.recyclerWl.layoutManager = LinearLayoutManager(this)
        binding.recyclerWl.adapter = adapter

        binding.editSearchWl.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setQuery(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    adapter.submit(state.apps)
                    binding.txtEmptyWl.visibility =
                        if (state.apps.isEmpty() && !state.loading) View.VISIBLE else View.GONE
                    binding.txtCountWl.text =
                        getString(R.string.whitelist_count_fmt, state.apps.size)
                }
            }
        }
    }

    private fun showLockedSnack() {
        Snackbar.make(binding.root, R.string.lock_editing_disabled, Snackbar.LENGTH_SHORT).show()
    }
}

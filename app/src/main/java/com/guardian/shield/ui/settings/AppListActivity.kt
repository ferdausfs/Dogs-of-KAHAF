package com.guardian.shield.ui.settings

import com.guardian.shield.R

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
        // Hero toggle — premium UI, always ON when protection active, reflects locked state
        try {
            binding.switchHero.isChecked = true
            binding.switchHero.setOnCheckedChangeListener { _, isChecked ->
                if (locked) {
                    binding.switchHero.isChecked = true
                    showLockedSnack()
                } else {
                    // App Blocking ON is tied to protection — keep ON, show info if user tries OFF
                    if (!isChecked) {
                        binding.switchHero.isChecked = true
                        Snackbar.make(binding.root, "App Blocking stays ON while protection is active • সুরক্ষা সক্রিয় থাকলে অ্যাপ ব্লক সক্রিয় থাকে", Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (_: Throwable) {}

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
                viewModel.state.collect { state ->
                    adapter.submit(state.apps)
                    // Visual-only state completeness (mocks/oneui8/app-blocking.html):
                    // skeleton while AppListState.loading, designed empty when the
                    // filter/search matches nothing. No VM or blocking logic change.
                    if (state.loading) {
                        binding.skeleton.visibility = View.VISIBLE
                        binding.txtEmptyApps.visibility = View.GONE
                        val pulse = android.view.animation.AlphaAnimation(0.45f, 1f).apply {
                            duration = 700; repeatMode = android.view.animation.Animation.REVERSE
                            repeatCount = android.view.animation.Animation.INFINITE
                        }
                        binding.skeleton.startAnimation(pulse)
                    } else {
                        binding.skeleton.clearAnimation()
                        binding.skeleton.visibility = View.GONE
                        binding.txtEmptyApps.visibility =
                            if (state.apps.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    private fun showLockedSnack() {
        Snackbar.make(binding.root, R.string.lock_editing_disabled, Snackbar.LENGTH_SHORT).show()
    }
}

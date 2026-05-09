package com.guardian.shield.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.guardian.shield.databinding.ActivityAppListBinding
import com.guardian.shield.service.detection.PinManager
import com.guardian.shield.ui.setup.PinVerifyActivity
import com.guardian.shield.viewmodel.AppListViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class AppListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAppListBinding
    private val vm: AppListViewModel by viewModels()

    @Inject lateinit var pinManager: PinManager

    private val pinVerify = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == RESULT_OK) binding.root.visibility = View.VISIBLE
        else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.visibility = View.INVISIBLE

        // v16 (2.1.6) NEW-FIX-6: move isPinSet() off the main thread.
        lifecycleScope.launch {
            val pinSet = withContext(Dispatchers.IO) {
                runCatching { pinManager.isPinSet() }.getOrDefault(false)
            }
            if (pinSet) {
                runCatching {
                    pinVerify.launch(Intent(this@AppListActivity, PinVerifyActivity::class.java))
                }.onFailure { Timber.w(it, "Failed to launch PinVerifyActivity") }
            } else {
                binding.root.visibility = View.VISIBLE
            }
        }

        val adapter = AppListAdapter(
            onBlockToggle = vm::toggleBlock,
            onWhitelistToggle = vm::toggleWhitelist
        )
        binding.rv.layoutManager = LinearLayoutManager(this)
        binding.rv.adapter = adapter

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                vm.setSearchQuery(query.orEmpty())
                binding.searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                vm.setSearchQuery(newText.orEmpty())
                return true
            }
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.apps.collect { adapter.submitList(it) } }
                launch { vm.summary.collect { binding.tvSummary.text = it } }
            }
        }
    }
}

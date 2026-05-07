package com.guardian.shield.ui.settings

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.guardian.shield.databinding.ActivityAppListBinding
import com.guardian.shield.viewmodel.AppListViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AppListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAppListBinding
    private val vm: AppListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val adapter = AppListAdapter(
            onBlockToggle = vm::toggleBlock,
            onWhitelistToggle = vm::toggleWhitelist
        )
        binding.rv.layoutManager = LinearLayoutManager(this)
        binding.rv.adapter = adapter

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.apps.collect { adapter.submitList(it) }
            }
        }
    }
}

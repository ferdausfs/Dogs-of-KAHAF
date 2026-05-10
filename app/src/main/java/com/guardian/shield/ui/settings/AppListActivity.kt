package com.guardian.shield.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.guardian.shield.databinding.ActivityAppListBinding
import com.guardian.shield.service.detection.PinManager
import com.guardian.shield.ui.setup.PinVerifyActivity
import com.guardian.shield.viewmodel.AppListViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FIX-LOG (vs original):
 *  - BUG #6: blocked-app list also requires PIN now (was unprotected).
 */
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
        if (pinManager.isPinSet()) pinVerify.launch(Intent(this, PinVerifyActivity::class.java))
        else binding.root.visibility = View.VISIBLE

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

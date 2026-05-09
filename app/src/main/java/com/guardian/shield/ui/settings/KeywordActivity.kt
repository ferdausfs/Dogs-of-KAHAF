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
import com.guardian.shield.databinding.ActivityKeywordBinding
import com.guardian.shield.service.detection.PinManager
import com.guardian.shield.ui.setup.PinVerifyActivity
import com.guardian.shield.viewmodel.KeywordViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * FIX-LOG (vs original):
 *  - BUG #6: keyword list now requires PIN.
 *  - Validates regex patterns before persisting (was: invalid regex silently
 *    ignored at runtime in RulesEngine).
 */
@AndroidEntryPoint
class KeywordActivity : AppCompatActivity() {
    private lateinit var binding: ActivityKeywordBinding
    private val vm: KeywordViewModel by viewModels()
    @Inject lateinit var pinManager: PinManager

    private val pinVerify = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == RESULT_OK) binding.root.visibility = View.VISIBLE
        else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKeywordBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.visibility = View.INVISIBLE

        // v16 (2.1.6) NEW-FIX-6: move isPinSet() off the main thread.
        lifecycleScope.launch {
            val pinSet = withContext(Dispatchers.IO) {
                runCatching { pinManager.isPinSet() }.getOrDefault(false)
            }
            if (pinSet) {
                runCatching {
                    pinVerify.launch(Intent(this@KeywordActivity, PinVerifyActivity::class.java))
                }.onFailure { Timber.w(it, "Failed to launch PinVerifyActivity") }
            } else {
                binding.root.visibility = View.VISIBLE
            }
        }

        val adapter = KeywordAdapter { vm.delete(it.id) }
        binding.rv.layoutManager = LinearLayoutManager(this)
        binding.rv.adapter = adapter

        binding.btnAdd.setOnClickListener {
            val text = binding.etKeyword.text.toString().trim()
            val regex = binding.cbRegex.isChecked
            if (text.isBlank()) return@setOnClickListener
            if (regex) {
                val ok = runCatching { Regex(text) }.isSuccess
                if (!ok) {
                    binding.etKeyword.error = "Invalid regex"
                    return@setOnClickListener
                }
            }
            vm.add(text, regex)
            binding.etKeyword.text?.clear()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.keywords.collect { adapter.submitList(it) }
            }
        }
    }
}

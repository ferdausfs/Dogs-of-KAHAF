package com.guardian.shield.ui.settings

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.guardian.shield.databinding.ActivityKeywordBinding
import com.guardian.shield.viewmodel.KeywordViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class KeywordActivity : AppCompatActivity() {
    private lateinit var binding: ActivityKeywordBinding
    private val vm: KeywordViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKeywordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val adapter = KeywordAdapter { vm.delete(it.id) }
        binding.rv.layoutManager = LinearLayoutManager(this)
        binding.rv.adapter = adapter

        binding.btnAdd.setOnClickListener {
            vm.add(binding.etKeyword.text.toString(), binding.cbRegex.isChecked)
            binding.etKeyword.text?.clear()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.keywords.collect { adapter.submitList(it) }
            }
        }
    }
}

package com.guardian.shield.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.guardian.shield.R
import com.guardian.shield.databinding.ActivityKeywordBinding
import com.guardian.shield.service.detection.TimeLockManager
import com.guardian.shield.viewmodel.KeywordViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class KeywordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKeywordBinding
    private val viewModel: KeywordViewModel by viewModels()
    private lateinit var adapter: KeywordAdapter

    @Inject lateinit var timeLockManager: TimeLockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKeywordBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        timeLockManager.clearIfExpired()
        val locked = timeLockManager.isLocked()

        if (locked) {
            binding.lockBanner.visibility = View.VISIBLE
            binding.txtLockRemaining.text = "🔒 ${timeLockManager.getRemainingFormatted()}"
            binding.fabAdd.hide()
        } else {
            binding.lockBanner.visibility = View.GONE
        }

        adapter = KeywordAdapter(
            onDelete = { kw ->
                if (locked) showLockedSnack()
                else viewModel.delete(kw.id)
            }
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        if (!locked) {
            val swipe = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0,
                ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
                override fun onMove(rv: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    val item = adapter.currentList[viewHolder.bindingAdapterPosition]
                    viewModel.delete(item.id)
                }
            })
            swipe.attachToRecyclerView(binding.recycler)
            binding.fabAdd.setOnClickListener { showAddDialog() }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.keywords.collect {
                    adapter.submit(it)
                    binding.txtEmpty.visibility = if (it.isEmpty())
                        View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun showAddDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_keyword, null)
        val editText = view.findViewById<EditText>(R.id.editKeyword)
        val checkbox = view.findViewById<CheckBox>(R.id.checkRegex)
        AlertDialog.Builder(this)
            .setTitle(R.string.add_keyword)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val kw = editText.text.toString().trim()
                if (kw.isNotEmpty()) viewModel.add(kw, checkbox.isChecked)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showLockedSnack() {
        Snackbar.make(binding.root, "🔒 Commitment Lock active — editing disabled", Snackbar.LENGTH_SHORT).show()
    }
}

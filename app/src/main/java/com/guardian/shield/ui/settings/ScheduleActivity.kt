package com.guardian.shield.ui.settings

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guardian.shield.R
import com.guardian.shield.databinding.ActivityScheduleBinding
import com.guardian.shield.domain.model.ScheduleRule
import com.guardian.shield.service.detection.PinManager
import com.guardian.shield.ui.setup.PinVerifyActivity
import com.guardian.shield.viewmodel.ScheduleViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * v9 (2.0.0) — P4-A: simple management UI for time-based schedule rules.
 *
 * Uses a hand-rolled adapter (no DiffUtil dependency) to keep the diff small;
 * the list is short (one entry per scheduled package).
 */
@AndroidEntryPoint
class ScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScheduleBinding
    private val vm: ScheduleViewModel by viewModels()
    @Inject lateinit var pinManager: PinManager

    private val adapter = Adapter(
        onEdit = { showEditor(it) },
        onDelete = { vm.delete(it.packageName) }
    )

    private val pinVerify = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == RESULT_OK) binding.root.visibility = View.VISIBLE
        else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.visibility = View.INVISIBLE

        // v16 (2.1.6) NEW-FIX-6: pinManager.isPinSet() may perform Keystore
        // I/O — move it off the main thread.
        lifecycleScope.launch {
            val pinSet = withContext(Dispatchers.IO) {
                runCatching { pinManager.isPinSet() }.getOrDefault(false)
            }
            if (pinSet) {
                runCatching {
                    pinVerify.launch(Intent(this@ScheduleActivity, PinVerifyActivity::class.java))
                }.onFailure { Timber.w(it, "Failed to launch PinVerifyActivity") }
            } else {
                binding.root.visibility = View.VISIBLE
            }
        }

        binding.rv.layoutManager = LinearLayoutManager(this)
        binding.rv.adapter = adapter

        binding.btnAdd.setOnClickListener { showEditor(null) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.rules.collect { adapter.submit(it) }
            }
        }
    }

    private fun showEditor(existing: ScheduleRule?) {
        val view = layoutInflater.inflate(R.layout.dialog_schedule_editor, null, false)
        val etPackage = view.findViewById<android.widget.EditText>(R.id.etPackage)
        val tvStart   = view.findViewById<android.widget.TextView>(R.id.tvStart)
        val tvEnd     = view.findViewById<android.widget.TextView>(R.id.tvEnd)
        val btnStart  = view.findViewById<android.widget.Button>(R.id.btnStart)
        val btnEnd    = view.findViewById<android.widget.Button>(R.id.btnEnd)

        var startH = existing?.startHour ?: 22
        var startM = existing?.startMinute ?: 0
        var endH   = existing?.endHour ?: 6
        var endM   = existing?.endMinute ?: 0

        etPackage.setText(existing?.packageName ?: "")
        etPackage.isEnabled = (existing == null)
        tvStart.text = "%02d:%02d".format(startH, startM)
        tvEnd.text   = "%02d:%02d".format(endH, endM)

        btnStart.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                startH = h; startM = m
                tvStart.text = "%02d:%02d".format(h, m)
            }, startH, startM, true).show()
        }
        btnEnd.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                endH = h; endM = m
                tvEnd.text = "%02d:%02d".format(h, m)
            }, endH, endM, true).show()
        }

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Add Schedule Rule" else "Edit Schedule Rule")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val pkg = etPackage.text.toString().trim()
                if (pkg.isBlank()) {
                    Toast.makeText(this, "Package name required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                vm.save(
                    ScheduleRule(
                        packageName = pkg,
                        startHour = startH, startMinute = startM,
                        endHour = endH, endMinute = endM
                    )
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Inline adapter ─────────────────────────────────────────────────
    private class Adapter(
        val onEdit: (ScheduleRule) -> Unit,
        val onDelete: (ScheduleRule) -> Unit
    ) : RecyclerView.Adapter<Adapter.VH>() {
        private val items = mutableListOf<ScheduleRule>()

        fun submit(list: List<ScheduleRule>) {
            items.clear(); items.addAll(list); notifyDataSetChanged()
        }

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvPkg: TextView = v.findViewById(R.id.tvPkg)
            val tvWindow: TextView = v.findViewById(R.id.tvWindow)
            val btnDel: View = v.findViewById(R.id.btnDelete)
            val rowRoot: View = v
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_schedule_rule, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvPkg.text = item.packageName
            holder.tvWindow.text = "%02d:%02d – %02d:%02d".format(
                item.startHour, item.startMinute, item.endHour, item.endMinute
            )
            holder.rowRoot.setOnClickListener { onEdit(item) }
            holder.btnDel.setOnClickListener { onDelete(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}

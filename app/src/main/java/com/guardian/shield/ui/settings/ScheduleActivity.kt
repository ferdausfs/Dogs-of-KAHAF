package com.guardian.shield.ui.settings

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.guardian.shield.R
import com.guardian.shield.databinding.ActivityScheduleBinding
import com.guardian.shield.databinding.ItemScheduleRuleBinding
import com.guardian.shield.domain.model.ScheduleRule
import com.guardian.shield.service.detection.TimeLockManager
import com.guardian.shield.viewmodel.ScheduleViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScheduleBinding
    private val viewModel: ScheduleViewModel by viewModels()
    private lateinit var adapter: ScheduleAdapter

    @Inject lateinit var timeLockManager: TimeLockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScheduleBinding.inflate(layoutInflater)
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

        adapter = ScheduleAdapter(
            isLocked = locked,
            onEdit = { rule ->
                if (locked) showLockedSnack()
                else showEditor(rule)
            },
            onDelete = { rule ->
                if (locked) showLockedSnack()
                else viewModel.delete(rule.packageName)
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
                    viewModel.delete(item.packageName)
                }
            })
            swipe.attachToRecyclerView(binding.recycler)
            binding.fabAdd.setOnClickListener { showEditor(null) }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.rules.collect {
                    adapter.submitList(it)
                    binding.txtEmpty.visibility = if (it.isEmpty())
                        View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun showEditor(existing: ScheduleRule?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_schedule_editor, null)
        val editPkg = view.findViewById<EditText>(R.id.editPackage)
        val txtStart = view.findViewById<TextView>(R.id.txtStart)
        val txtEnd = view.findViewById<TextView>(R.id.txtEnd)
        val chipSu = view.findViewById<Chip>(R.id.chipSun)
        val chipMo = view.findViewById<Chip>(R.id.chipMon)
        val chipTu = view.findViewById<Chip>(R.id.chipTue)
        val chipWe = view.findViewById<Chip>(R.id.chipWed)
        val chipTh = view.findViewById<Chip>(R.id.chipThu)
        val chipFr = view.findViewById<Chip>(R.id.chipFri)
        val chipSa = view.findViewById<Chip>(R.id.chipSat)
        val chips = listOf(chipSu, chipMo, chipTu, chipWe, chipTh, chipFr, chipSa)

        var startH = existing?.startHour ?: 22
        var startM = existing?.startMinute ?: 0
        var endH = existing?.endHour ?: 6
        var endM = existing?.endMinute ?: 0
        editPkg.setText(existing?.packageName.orEmpty())
        txtStart.text = "%02d:%02d".format(startH, startM)
        txtEnd.text = "%02d:%02d".format(endH, endM)
        val days = existing?.enabledDays ?: (0..6).toSet()
        chips.forEachIndexed { i, c -> c.isChecked = days.contains(i) }

        txtStart.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                startH = h; startM = m
                txtStart.text = "%02d:%02d".format(h, m)
            }, startH, startM, true).show()
        }
        txtEnd.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                endH = h; endM = m
                txtEnd.text = "%02d:%02d".format(h, m)
            }, endH, endM, true).show()
        }

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) R.string.add_schedule else R.string.edit_schedule)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val pkg = editPkg.text.toString().trim()
                if (pkg.isEmpty()) return@setPositiveButton
                val enabledDays = mutableSetOf<Int>()
                chips.forEachIndexed { i, c -> if (c.isChecked) enabledDays.add(i) }
                viewModel.save(
                    ScheduleRule(
                        packageName = pkg,
                        startHour = startH, startMinute = startM,
                        endHour = endH, endMinute = endM,
                        enabledDays = enabledDays.ifEmpty { (0..6).toSet() },
                        enabled = true,
                        createdAt = existing?.createdAt ?: System.currentTimeMillis()
                    )
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showLockedSnack() {
        Snackbar.make(binding.root, "🔒 Commitment Lock active — editing disabled", Snackbar.LENGTH_SHORT).show()
    }
}

class ScheduleAdapter(
    private val isLocked: Boolean = false,
    private val onEdit: (ScheduleRule) -> Unit,
    private val onDelete: (ScheduleRule) -> Unit
) : ListAdapter<ScheduleRule, ScheduleAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val b = ItemScheduleRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemScheduleRuleBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(rule: ScheduleRule) {
            b.txtPackage.text = rule.packageName
            val daysShort = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")
            val daysText = (0..6).filter { rule.enabledDays.contains(it) }
                .joinToString("") { daysShort[it] + " " }
            b.txtSchedule.text = "%02d:%02d – %02d:%02d  %s".format(
                rule.startHour, rule.startMinute,
                rule.endHour, rule.endMinute,
                daysText.trim()
            )
            b.btnEdit.isEnabled = !isLocked
            b.btnEdit.setOnClickListener { onEdit(rule) }
            b.root.setOnLongClickListener { onDelete(rule); true }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<ScheduleRule>() {
            override fun areItemsTheSame(o: ScheduleRule, n: ScheduleRule) = o.packageName == n.packageName
            override fun areContentsTheSame(o: ScheduleRule, n: ScheduleRule) = o == n
        }
    }
}

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
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.databinding.ActivityScheduleBinding
import com.guardian.shield.databinding.ItemScheduleRuleBinding
import com.guardian.shield.domain.model.ScheduleRule
import com.guardian.shield.service.detection.TimeLockManager
import com.guardian.shield.viewmodel.ScheduleViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.guardian.shield.util.ScreenInsets

@AndroidEntryPoint
class ScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScheduleBinding
    private val viewModel: ScheduleViewModel by viewModels()
    private lateinit var adapter: ScheduleAdapter

    @Inject lateinit var timeLockManager: TimeLockManager
    @Inject lateinit var guardianPrefs: GuardianPreferences

    // ---- R4 Focus Mode state -------------------------------------------
    private data class FocusChip(val chip: Chip, val minutes: Int)
    private lateinit var focusChips: List<FocusChip>
    private var focusLocked = false
    private var focusActive = false
    private var latestFocusUntilMs = 0L
    private var latestFocusDurationMins = 45
    private val focusHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val focusTicker = object : Runnable {
        override fun run() {
            if (focusActive) {
                tickFocus()
                focusHandler.postDelayed(this, 1000L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        ScreenInsets.padTopForStatusBar(binding.toolbar)
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

        focusLocked = locked
        setupFocusMode()
        setupBedtime()

        adapter = ScheduleAdapter(
            isLocked = locked,
            onEdit = { rule ->
                if (locked) showLockedSnack()
                else showEditor(rule)
            },
            onDelete = { rule ->
                if (locked) showLockedSnack()
                else viewModel.delete(rule.id)
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

        // ---- R7.3 — searchable premium app picker ------------------------
        // editPackage doubles as the (search)box AND the value holder:
        // tapping a row writes its package there, so Save needs no change.
        val recyclerAppPick = view.findViewById<RecyclerView>(R.id.recyclerAppPick)
        val txtPickEmpty = view.findViewById<TextView>(R.id.txtPickEmpty)
        val pickAdapter = AppPickAdapter(packageManager) { pick ->
            editPkg.setText(pick.packageName)
            editPkg.setSelection(pick.packageName.length)
        }
        recyclerAppPick.layoutManager = LinearLayoutManager(this)
        recyclerAppPick.adapter = pickAdapter

        fun refreshPickList(query: String) {
            if (!pickAdapter.hasData()) return
            val count = pickAdapter.submitFiltered(query, query.trim())
            txtPickEmpty.visibility = if (count == 0) View.VISIBLE else View.GONE
        }

        editPkg.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                refreshPickList(s?.toString().orEmpty())
            }
        })

        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) { loadLaunchableApps() }
            pickAdapter.setData(apps, editPkg.text.toString().trim())
            refreshPickList(editPkg.text.toString())
        }
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
                        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                        // R7.7 — keep the row id on edit so it UPDATES this
                        // window; a new rule (or same app again) gets id 0
                        // and INSERTS as another window. Multi-window ✓.
                        id = existing?.id ?: 0
                    )
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showLockedSnack() {
        Snackbar.make(binding.root, R.string.lock_editing_disabled, Snackbar.LENGTH_SHORT).show()
    }

    /** Every launchable app (installed user apps + system apps with a launcher). */
    private fun loadLaunchableApps(): List<AppPick> = runCatching {
        val pm = packageManager
        pm.getInstalledApplications(0)
            .asSequence()
            .filter { it.packageName != packageName }
            .filter { app ->
                val isSystem =
                    (app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                !isSystem || pm.getLaunchIntentForPackage(app.packageName) != null
            }
            .map { app ->
                AppPick(
                    packageName = app.packageName,
                    label = pm.getApplicationLabel(app).toString()
                        .ifBlank { app.packageName }
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }.getOrDefault(emptyList())

    // ---- R4 Focus Mode ---------------------------------------------------
    // Temporary whole-device pause of distracting apps. State lives in
    // DataStore (FOCUS_UNTIL_MS / FOCUS_DURATION_MINS); starting/stopping
    // bumps rulesVersion so the accessibility service reloads RulesEngine —
    // whose focus short-circuit is fresh-time-checked, so expiry needs no
    // wake-up at all.

    private fun setupFocusMode() {
        focusChips = listOf(
            FocusChip(binding.chipFocus15, 15),
            FocusChip(binding.chipFocus25, 25),
            FocusChip(binding.chipFocus45, 45),
            FocusChip(binding.chipFocus60, 60)
        )
        val onColor = getColor(R.color.on_primary_container)
        binding.focusRing.setColors(
            onColor,
            android.graphics.Color.argb(
                70,
                android.graphics.Color.red(onColor),
                android.graphics.Color.green(onColor),
                android.graphics.Color.blue(onColor)
            )
        )
        focusChips.forEach { fc ->
            fc.chip.setOnClickListener {
                if (focusLocked) { showLockedSnack(); return@setOnClickListener }
                if (focusActive) return@setOnClickListener
                selectFocusDuration(fc.minutes)
            }
        }
        binding.btnFocusToggle.setOnClickListener {
            if (focusLocked) { showLockedSnack(); return@setOnClickListener }
            toggleFocus()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                guardianPrefs.focusDurationMins.collect { d ->
                    latestFocusDurationMins = d
                    if (!focusActive) {
                        selectFocusDuration(d)
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                guardianPrefs.focusUntilMs.collect { until ->
                    latestFocusUntilMs = until
                    refreshFocusState()
                }
            }
        }
        // Reflect the state immediately (in case the flows replay slowly).
        refreshFocusState()
    }

    private fun selectedFocusMinutes(): Int =
        focusChips.firstOrNull { it.chip.isChecked }?.minutes ?: latestFocusDurationMins

    private fun selectFocusDuration(minutes: Int) {
        focusChips.forEach { it.chip.isChecked = it.minutes == minutes }
        if (!focusActive) {
            binding.txtFocusRemaining.text = "%d:00".format(minutes)
        }
    }

    private fun refreshFocusState() {
        val now = System.currentTimeMillis()
        val active = latestFocusUntilMs > now
        if (active == focusActive) {
            if (active) tickFocus()
            return
        }
        focusActive = active
        if (active) startFocusUi() else stopFocusUi()
    }

    private fun startFocusUi() {
        binding.txtFocusStatus.text = getString(R.string.focus_status_active)
        binding.txtFocusHint.text = getString(R.string.focus_hint_active)
        binding.btnFocusToggle.text = getString(R.string.focus_stop)
        focusChips.forEach { it.chip.isEnabled = false }
        focusHandler.removeCallbacks(focusTicker)
        tickFocus()
        focusHandler.postDelayed(focusTicker, 1000L)
    }

    private fun stopFocusUi() {
        binding.txtFocusStatus.text = getString(R.string.focus_status_ready)
        binding.txtFocusHint.text = getString(R.string.focus_hint_ready)
        binding.btnFocusToggle.text = getString(R.string.focus_start)
        focusChips.forEach { it.chip.isEnabled = !focusLocked }
        binding.focusRing.setProgress(0f)
        focusHandler.removeCallbacks(focusTicker)
        selectFocusDuration(selectedFocusMinutes())
    }

    private fun tickFocus() {
        val now = System.currentTimeMillis()
        val remaining = latestFocusUntilMs - now
        if (remaining <= 0) {
            // Expired between emissions — render idle right away.
            stopFocusUi()
            focusActive = false
            return
        }
        val totalMs = latestFocusDurationMins * 60_000L
        val elapsed = (totalMs - remaining).toFloat() / totalMs.coerceAtLeast(1L).toFloat()
        binding.focusRing.setProgress(elapsed)
        val totalSec = remaining / 1000L
        binding.txtFocusRemaining.text = "%d:%02d".format(totalSec / 60, totalSec % 60)
    }

    private fun toggleFocus() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (focusActive) {
                    guardianPrefs.setFocusUntilMs(0L)
                    guardianPrefs.bumpRulesVersion()
                    withContext(Dispatchers.Main) {
                        Snackbar.make(binding.root, R.string.focus_ended, Snackbar.LENGTH_SHORT).show()
                    }
                } else {
                    val mins = selectedFocusMinutes()
                    guardianPrefs.setFocusDurationMins(mins)
                    guardianPrefs.setFocusUntilMs(System.currentTimeMillis() + mins * 60_000L)
                    guardianPrefs.bumpRulesVersion()
                    withContext(Dispatchers.Main) {
                        Snackbar.make(binding.root, R.string.focus_started, Snackbar.LENGTH_SHORT).show()
                    }
                }
            } catch (t: Throwable) {
                timber.log.Timber.e(t, "focus toggle failed")
            }
        }
    }

    // ---- R7.5 Bedtime Mode (nightly scheduled focus) ---------------------
    // Same pattern as DNS Auto Mode: DataStore is UI truth -> mirrored into
    // the scheduler's sync cache -> exact boundary alarm re-armed -> desired
    // state enforced IMMEDIATELY so the user can watch it work.

    private var bedtimeStartMin = 23 * 60
    private var bedtimeEndMin = 6 * 60
    private var bedtimeRendering = false

    private fun setupBedtime() {
        binding.switchBedtime.setOnCheckedChangeListener { _, _ ->
            if (!bedtimeRendering) applyBedtimeNow()
        }
        binding.btnBedtimeStart.setOnClickListener {
            if (focusLocked) { showLockedSnack(); return@setOnClickListener }
            TimePickerDialog(this, { _, h, m ->
                bedtimeStartMin = h * 60 + m
                renderBedtime()
                applyBedtimeNow()
            }, bedtimeStartMin / 60, bedtimeStartMin % 60, true).show()
        }
        binding.btnBedtimeEnd.setOnClickListener {
            if (focusLocked) { showLockedSnack(); return@setOnClickListener }
            TimePickerDialog(this, { _, h, m ->
                bedtimeEndMin = h * 60 + m
                renderBedtime()
                applyBedtimeNow()
            }, bedtimeEndMin / 60, bedtimeEndMin % 60, true).show()
        }
        binding.switchBedtime.setOnClickListener {
            if (focusLocked) {
                bedtimeRendering = true
                binding.switchBedtime.isChecked = false
                bedtimeRendering = false
                showLockedSnack()
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { guardianPrefs.bedtimeEnabled.collect { renderBedtime() } }
                launch { guardianPrefs.bedtimeStartMin.collect {
                    bedtimeStartMin = it; renderBedtime()
                } }
                launch { guardianPrefs.bedtimeEndMin.collect {
                    bedtimeEndMin = it; renderBedtime()
                } }
            }
        }
    }

    private fun renderBedtime() {
        lifecycleScope.launch {
            val on = guardianPrefs.bedtimeEnabled.first()
            bedtimeRendering = true
            binding.switchBedtime.isChecked = on
            bedtimeRendering = false
            binding.switchBedtime.isEnabled = !focusLocked
            binding.rowBedtimeTimes.visibility = if (on) View.VISIBLE else View.GONE
            val inWindow = com.guardian.shield.service.focus.BedtimeScheduler.isInWindow(
                com.guardian.shield.service.focus.BedtimeScheduler.nowMinutes(),
                bedtimeStartMin, bedtimeEndMin
            )
            binding.txtBedtimeStatus.text = when {
                !on -> getString(R.string.bedtime_status_off)
                inWindow -> getString(R.string.bedtime_status_active_fmt, fmtBed(bedtimeEndMin))
                else -> getString(
                    R.string.bedtime_status_next_fmt,
                    fmtBed(bedtimeStartMin), fmtBed(bedtimeEndMin)
                )
            }
            binding.btnBedtimeStart.text = getString(R.string.bedtime_start_fmt, fmtBed(bedtimeStartMin))
            binding.btnBedtimeEnd.text = getString(R.string.bedtime_end_fmt, fmtBed(bedtimeEndMin))
        }
    }

    /** Save UI -> DataStore, mirror cache, enforce right now, re-arm alarm. */
    private fun applyBedtimeNow() {
        val enabled = binding.switchBedtime.isChecked
        lifecycleScope.launch {
            guardianPrefs.setBedtime(enabled, bedtimeStartMin, bedtimeEndMin)
            withContext(Dispatchers.IO) {
                com.guardian.shield.service.focus.BedtimeScheduler.syncCache(
                    this@ScheduleActivity, enabled, bedtimeStartMin, bedtimeEndMin
                )
                runCatching {
                    if (enabled) {
                        com.guardian.shield.service.focus.BedtimeScheduler.tick(
                            this@ScheduleActivity, guardianPrefs
                        )
                    } else {
                        com.guardian.shield.service.focus.BedtimeScheduler.disableNow(
                            this@ScheduleActivity, guardianPrefs
                        )
                    }
                }.onFailure { timber.log.Timber.e(it, "bedtime apply failed") }
                com.guardian.shield.service.focus.BedtimeScheduler.reschedule(this@ScheduleActivity)
            }
            renderBedtime()
        }
    }

    private fun fmtBed(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

    override fun onDestroy() {
        focusHandler.removeCallbacks(focusTicker)
        super.onDestroy()
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
            // R7.3 — friendly label first ("Instagram"), raw package as
            // fallback so legacy/manual entries still display something.
            val label = runCatching {
                val pmi = b.root.context.packageManager
                    .getApplicationInfo(rule.packageName, 0)
                b.root.context.packageManager.getApplicationLabel(pmi).toString()
            }.getOrNull().orEmpty()
            b.txtPackage.text = label.ifBlank { rule.packageName }
            // R7.3 — real app icon in the row (falls back to the clock glyph).
            runCatching {
                b.imgAppIcon.setImageDrawable(
                    b.root.context.packageManager.getApplicationIcon(rule.packageName)
                )
            }.onFailure { b.imgAppIcon.setImageResource(R.drawable.ic_clock) }
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
            // R7.7 — identity is the row id: several windows may share one package.
            override fun areItemsTheSame(o: ScheduleRule, n: ScheduleRule) = o.id == n.id
            override fun areContentsTheSame(o: ScheduleRule, n: ScheduleRule) = o == n
        }
    }
}

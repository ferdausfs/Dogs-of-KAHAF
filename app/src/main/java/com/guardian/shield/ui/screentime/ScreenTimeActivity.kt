package com.guardian.shield.ui.screentime

import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.databinding.ActivityScreenTimeBinding
import com.guardian.shield.databinding.ItemScreenTimeBinding
import com.guardian.shield.domain.model.AppRule
import com.guardian.shield.domain.repository.RulesRepository
import com.guardian.shield.util.PermissionManager
import com.guardian.shield.util.ScreenInsets
import com.guardian.shield.util.ScreenTimeTracker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * R7.4 — Screen time detail: today's per-app usage from the (previously
 * dead) usage-access permission, with search and one-tap Block toggles
 * that feed the SAME AppRule table the blocker engine already reads.
 */
@AndroidEntryPoint
class ScreenTimeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScreenTimeBinding

    @Inject lateinit var repo: RulesRepository
    @Inject lateinit var prefs: GuardianPreferences

    private lateinit var adapter: ScreenTimeAdapter
    private var all: List<ScreenTimeTracker.AppUsage> = emptyList()
    private var blocked: Set<String> = emptySet()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScreenTimeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        ScreenInsets.padTopForStatusBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = ScreenTimeAdapter(packageManager) { usage, shouldBlock ->
            toggleBlock(usage, shouldBlock)
        }
        binding.recyclerUsage.layoutManager = LinearLayoutManager(this)
        binding.recyclerUsage.adapter = adapter

        binding.btnUsageGrant.setOnClickListener {
            PermissionManager.openUsageAccessSettings(this)
        }

        binding.editUsageSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { applyFilter() }
        })
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        val granted = PermissionManager.isUsageStatsGranted(this)
        binding.cardUsageGrant.visibility = if (granted) View.GONE else View.VISIBLE
        binding.tilUsageSearch.visibility = if (granted) View.VISIBLE else View.GONE
        binding.recyclerUsage.visibility = if (granted) View.VISIBLE else View.GONE
        if (!granted) {
            binding.txtScreenTotalSub.text = ""
            binding.txtUsageEmpty.visibility = View.GONE
            return
        }
        lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) {
                ScreenTimeTracker.summary(this@ScreenTimeActivity, 100)
            }
            val rules = withContext(Dispatchers.IO) {
                runCatching { repo.observeApps().first() }.getOrDefault(emptyList())
            }
            all = data.top
            blocked = rules.filter { it.isBlocked }.map { it.packageName }.toSet()
            binding.txtScreenTotalSub.text =
                getString(com.guardian.shield.R.string.screentime_total_fmt,
                    ScreenTimeTracker.formatMs(data.totalMs))
            applyFilter()
        }
    }

    private fun applyFilter() {
        val q = binding.editUsageSearch.text?.toString()?.trim()?.lowercase().orEmpty()
        val items = all.filter {
            q.isEmpty() ||
                it.label.lowercase().contains(q) ||
                it.packageName.lowercase().contains(q)
        }
        adapter.submit(items.map { ScreenTimeTracker.AppUsage(it.packageName, it.label, it.totalMs) to (it.packageName in blocked) })
        binding.txtUsageEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun toggleBlock(usage: ScreenTimeTracker.AppUsage, shouldBlock: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val current = repo.getApp(usage.packageName)
                val updated = (current ?: AppRule(
                    usage.packageName, usage.label, false, false,
                    System.currentTimeMillis()
                )).copy(
                    isBlocked = shouldBlock,
                    isWhitelisted = if (shouldBlock) false
                    else current?.isWhitelisted ?: false
                )
                repo.upsertApp(updated)
                prefs.bumpRulesVersion()
            }.onFailure { Timber.e(it, "screen-time block toggle failed") }
            val rules = runCatching { repo.observeApps().first() }.getOrDefault(emptyList())
            blocked = rules.filter { it.isBlocked }.map { it.packageName }.toSet()
            withContext(Dispatchers.Main) { applyFilter() }
        }
    }
}

private class ScreenTimeAdapter(
    private val pm: PackageManager,
    private val onToggle: (ScreenTimeTracker.AppUsage, Boolean) -> Unit
) : ListAdapter<Pair<ScreenTimeTracker.AppUsage, Boolean>, ScreenTimeAdapter.VH>(DIFF) {

    fun submit(list: List<Pair<ScreenTimeTracker.AppUsage, Boolean>>) = submitList(list)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemScreenTimeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemScreenTimeBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: Pair<ScreenTimeTracker.AppUsage, Boolean>) {
            val (usage, isBlocked) = item
            b.txtUsageName.text = usage.label
            b.txtUsagePkg.text = usage.packageName
            b.txtUsageTime.text = ScreenTimeTracker.formatMs(usage.totalMs)
            runCatching {
                b.imgUsageIcon.setImageDrawable(pm.getApplicationIcon(usage.packageName))
            }.onFailure { b.imgUsageIcon.setImageDrawable(null) }
            b.switchUsageBlock.setOnCheckedChangeListener(null)
            b.switchUsageBlock.isChecked = isBlocked
            b.txtUsageBadge.visibility = if (isBlocked) View.VISIBLE else View.GONE
            b.switchUsageBlock.setOnCheckedChangeListener { _, v ->
                onToggle(usage, v)
            }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Pair<ScreenTimeTracker.AppUsage, Boolean>>() {
            override fun areItemsTheSame(
                o: Pair<ScreenTimeTracker.AppUsage, Boolean>,
                n: Pair<ScreenTimeTracker.AppUsage, Boolean>
            ) = o.first.packageName == n.first.packageName

            override fun areContentsTheSame(
                o: Pair<ScreenTimeTracker.AppUsage, Boolean>,
                n: Pair<ScreenTimeTracker.AppUsage, Boolean>
            ) = o == n
        }
    }
}

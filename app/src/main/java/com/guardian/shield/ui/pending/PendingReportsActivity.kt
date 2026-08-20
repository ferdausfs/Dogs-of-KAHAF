package com.guardian.shield.ui.pending

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.guardian.shield.R
import com.guardian.shield.data.local.db.PendingReportEntity
import com.guardian.shield.databinding.ActivityPendingReportsBinding
import com.guardian.shield.service.blocker.PendingReportManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class PendingReportsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPendingReportsBinding

    @Inject lateinit var pendingReportManager: PendingReportManager

    private val adapter = PendingReportAdapter(
        onCancel = { entity -> cancelReport(entity) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPendingReportsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.recyclerPending.layoutManager = LinearLayoutManager(this)
        binding.recyclerPending.adapter = adapter

        lifecycleScope.launch {
            pendingReportManager.observePending().collect { reports ->
                adapter.submitList(reports)
                binding.txtEmpty.visibility = if (reports.isEmpty()) View.VISIBLE else View.GONE
                binding.recyclerPending.visibility = if (reports.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun cancelReport(entity: PendingReportEntity) {
        lifecycleScope.launch {
            val ok = pendingReportManager.cancel(entity.id)
            if (ok) {
                Snackbar.make(binding.root, R.string.pending_report_cancelled_msg, Snackbar.LENGTH_SHORT).show()
            }
        }
    }
}

/**
 * RecyclerView adapter for pending report items.
 * Uses a simple DiffUtil callback keyed on id + status.
 */
private class PendingReportAdapter(
    private val onCancel: (PendingReportEntity) -> Unit
) : ListAdapter<PendingReportEntity, PendingReportAdapter.VH>(DIFF) {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val txtPkg: TextView = view.findViewById(R.id.txtPkg)
        val txtSource: TextView = view.findViewById(R.id.txtSource)
        val txtConfidence: TextView = view.findViewById(R.id.txtConfidence)
        val txtRemaining: TextView = view.findViewById(R.id.txtRemaining)
        val btnCancel: MaterialButton = view.findViewById(R.id.btnCancel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pending_report, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val ctx = holder.itemView.context
        holder.txtPkg.text = item.packageName

        holder.txtSource.text = when (item.source) {
            PendingReportManager.Source.WARNING_CARD -> ctx.getString(R.string.pending_report_source_warning)
            PendingReportManager.Source.FULL_BLOCK -> ctx.getString(R.string.pending_report_source_full)
            else -> item.source
        }

        holder.txtConfidence.text = ctx.getString(R.string.ai_confidence_badge_fmt, item.confidence)

        val remainingMs = (item.scheduledApplyAt - System.currentTimeMillis()).coerceAtLeast(0L)
        val remainingMin = (remainingMs / 60_000).toInt()
        holder.txtRemaining.text = ctx.getString(R.string.pending_remaining_fmt, remainingMin)

        holder.btnCancel.setOnClickListener { onCancel(item) }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<PendingReportEntity>() {
            override fun areItemsTheSame(a: PendingReportEntity, b: PendingReportEntity) = a.id == b.id
            override fun areContentsTheSame(a: PendingReportEntity, b: PendingReportEntity) = a == b
        }
    }
}

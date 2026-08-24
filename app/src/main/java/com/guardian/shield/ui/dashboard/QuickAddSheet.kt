package com.guardian.shield.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.guardian.shield.databinding.SheetQuickAddBinding
import com.guardian.shield.ui.settings.AppListActivity
import com.guardian.shield.ui.settings.FiltersActivity
import com.guardian.shield.ui.settings.KeywordActivity
import com.guardian.shield.ui.settings.ScheduleActivity
import com.guardian.shield.ui.settings.WhitelistActivity

/**
 * R4 — center-FAB quick actions sheet (reference mock center action button).
 * Pure navigation: every row lands on a REAL screen.
 */
class QuickAddSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = SheetQuickAddBinding.inflate(inflater, container, false)
        binding.rowQuickApps.setOnClickListener { open(AppListActivity::class.java) }
        binding.rowQuickKeyword.setOnClickListener { open(KeywordActivity::class.java) }
        binding.rowQuickFilters.setOnClickListener { open(FiltersActivity::class.java) }
        binding.rowQuickWhitelist.setOnClickListener { open(WhitelistActivity::class.java) }
        binding.rowQuickFocus.setOnClickListener { open(ScheduleActivity::class.java) }
        return binding.root
    }

    private fun open(target: Class<*>) {
        val ctx = context ?: return
        startActivity(Intent(ctx, target))
        dismiss()
    }
}

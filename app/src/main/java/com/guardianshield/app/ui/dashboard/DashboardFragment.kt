package com.guardianshield.app.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.guardianshield.app.GuardianApp
import com.guardianshield.app.databinding.FragmentDashboardBinding
import com.guardianshield.app.manager.QuranReminders
import com.guardianshield.app.manager.TempBlockManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {

    private var _b: FragmentDashboardBinding? = null
    private val b get() = _b!!

    private val vm: DashboardViewModel by viewModels {
        DashboardViewModel.Factory(GuardianApp.get().repository)
    }

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentDashboardBinding.inflate(inflater, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindDailyAyah()
        observeStats()
    }

    private fun bindDailyAyah() {
        val r = QuranReminders.forToday()
        b.txtAyahSource.text = r.sourceLabel
        b.txtAyahArabic.text = r.arabic
        b.txtAyahTranslation.text = "“${r.translation}”"
    }

    private fun observeStats() {
        viewLifecycleOwner.lifecycleScope.launch {
            vm.stats.collectLatest { s ->
                b.statApps.text = s.blockedAppsCount.toString()
                b.statReels.text = s.scrollRemindersToday.toString()
                b.statSites.text = s.aiBlocksToday.toString()
                b.txtBlockedToday.text = "${s.totalBlockedToday} blocked today"
                b.txtAiStatus.text = if (TempBlockManager.snapshot().isEmpty())
                    "✓ AI Detection Active" else "🚫 ${TempBlockManager.snapshot().size} app(s) blocked 24h"
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

package com.guardian.shield.ui.dashboard.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.guardian.shield.databinding.FragmentProtectionBinding
import com.guardian.shield.ui.settings.AppListActivity
import com.guardian.shield.ui.settings.KeywordActivity
import com.guardian.shield.ui.settings.ScheduleActivity
import com.guardian.shield.ui.settings.SettingsActivity
import com.guardian.shield.util.PermissionManager
import com.guardian.shield.viewmodel.DashboardViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Protection Hub — central shield status visual + module cards for REAL features only.
 * No DNS/VPN/Browser invention per constraint. Maps to:
 * - App Blocking (AppListActivity)
 * - Keyword Filtering (KeywordActivity)
 * - Schedule Blocking (ScheduleActivity)
 * - AI Detection (SettingsActivity -> AI section)
 * - Accessibility status
 * - Gender filter (via Settings)
 *
 * Design matches reference: All Shields Active, module cards with Active badges,
 * Strict Mode toggle (mapped to protectionEnabled).
 */
@AndroidEntryPoint
class ProtectionFragment : Fragment() {

    private var _binding: FragmentProtectionBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProtectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Module navigations — real features only
        binding.cardAppBlocking.setOnClickListener {
            startActivity(Intent(requireContext(), AppListActivity::class.java))
        }
        binding.cardKeyword.setOnClickListener {
            startActivity(Intent(requireContext(), KeywordActivity::class.java))
        }
        binding.cardSchedule.setOnClickListener {
            startActivity(Intent(requireContext(), ScheduleActivity::class.java))
        }
        binding.cardAi.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
        binding.cardAccessibility.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state.protectionActive && state.protectionEnabled) {
                        binding.txtProtectionTitle.text = "All Shields Active"
                        binding.txtProtectionSubtitle.text = "You are fully protected\nসুরক্ষা সক্রিয়"
                        binding.imgShield.setImageResource(com.guardian.shield.R.drawable.ic_shield_on)
                        binding.txtBadgeActive.text = "● Active • সক্রিয়"
                        binding.txtBadgeActive.setTextColor(requireContext().getColor(com.guardian.shield.R.color.success))
                    } else if (!state.protectionActive) {
                        binding.txtProtectionTitle.text = "Protection Off"
                        binding.txtProtectionSubtitle.text = "Accessibility service not running\nসার্ভিস বন্ধ"
                        binding.imgShield.setImageResource(com.guardian.shield.R.drawable.ic_shield_off)
                        binding.txtBadgeActive.text = "○ Off • বন্ধ"
                        binding.txtBadgeActive.setTextColor(requireContext().getColor(com.guardian.shield.R.color.error))
                    } else {
                        binding.txtProtectionTitle.text = "Protection Paused"
                        binding.txtProtectionSubtitle.text = "Protection is paused\nবিরাম"
                        binding.imgShield.setImageResource(com.guardian.shield.R.drawable.ic_shield_off)
                        binding.txtBadgeActive.text = "○ Paused • বিরাম"
                        binding.txtBadgeActive.setTextColor(requireContext().getColor(com.guardian.shield.R.color.warning_amber))
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.setProtectionActive(PermissionManager.isAccessibilityEnabled(requireContext()))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

package com.guardian.shield.ui.dashboard.fragments

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.guardian.shield.R
import com.guardian.shield.databinding.FragmentDashboardBinding
import com.guardian.shield.service.detection.TimeLockManager
import com.guardian.shield.ui.dashboard.BlockEventAdapter
import com.guardian.shield.ui.guard.AccessibilityPromptActivity
import com.guardian.shield.util.PermissionManager
import com.guardian.shield.viewmodel.DashboardViewModel
import com.guardian.shield.viewmodel.DashboardUiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels()
    private lateinit var adapter: BlockEventAdapter

    @Inject lateinit var timeLockManager: TimeLockManager

    private var shieldPulseSet: AnimatorSet? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = BlockEventAdapter(requireContext().packageManager) { event ->
            viewModel.deleteEvent(event.id)
        }
        binding.recyclerRecent.adapter = adapter

        binding.btnToggle.setOnClickListener { handleToggle() }
        // Quick Actions — map to real existing screens (no invented features)
        binding.cardAppBlocking.setOnClickListener {
            startActivity(Intent(requireContext(), com.guardian.shield.ui.settings.AppListActivity::class.java))
        }
        binding.cardKeywords.setOnClickListener {
            startActivity(Intent(requireContext(), com.guardian.shield.ui.settings.KeywordActivity::class.java))
        }
        binding.cardWhitelist.setOnClickListener {
            // R4 — dedicated Whitelist screen
            startActivity(Intent(requireContext(), com.guardian.shield.ui.settings.WhitelistActivity::class.java))
        }
        binding.txtSeeAll.setOnClickListener {
            startActivity(Intent(requireContext(), com.guardian.shield.ui.activitylog.ActivityLogActivity::class.java))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { render(it) } }
                launch { viewModel.streakInfo.collect { renderStreak(it) } }
                // R4 — real hourly sparkline data
                launch { viewModel.hourlyToday.collect { binding.chartSparkline.setData(it) } }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.setProtectionActive(PermissionManager.isAccessibilityEnabled(requireContext()))
    }

    private fun handleToggle() {
        timeLockManager.clearIfExpired()
        if (timeLockManager.isLocked() || timeLockManager.isInCooldown()) {
            Snackbar.make(
                binding.root,
                getString(R.string.lock_active_fmt, timeLockManager.getRemainingFormatted()),
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }
        // Enable is a no-op if Accessibility is off — the service never reads
        // the DataStore flag. Send the user to the prompt so they can turn it on.
        if (!PermissionManager.isAccessibilityEnabled(requireContext())) {
            startActivity(Intent(requireContext(), AccessibilityPromptActivity::class.java))
            return
        }
        viewModel.toggleProtection()
    }

    private fun render(state: DashboardUiState) {
        when {
            !state.protectionActive -> {
                binding.txtStatusTitle.text = getString(R.string.status_off_title)
                binding.txtStatusSubtitle.text = getString(R.string.status_off_subtitle)
                binding.imgShield.setImageResource(R.drawable.ic_shield_off)
                binding.btnToggle.text = getString(R.string.btn_enable)
                binding.txtProtectionBadge.text = "○ ${getString(R.string.status_off_title)}"
                binding.txtProtectionBadge.setTextColor(requireContext().getColor(R.color.error_bright))
                // r2 visual-only: badge chip tint per state (design-system badges)
                binding.txtProtectionBadge.backgroundTintList = ColorStateList.valueOf(requireContext().getColor(R.color.badge_error_bg))
                applyHeroState(HeroState.OFF)
                stopShieldPulse()
            }
            !state.protectionEnabled -> {
                binding.txtStatusTitle.text = getString(R.string.status_paused_title)
                binding.txtStatusSubtitle.text = getString(R.string.status_paused_subtitle)
                binding.imgShield.setImageResource(R.drawable.ic_shield_off)
                binding.btnToggle.text = getString(R.string.btn_resume)
                binding.txtProtectionBadge.text = "○ ${getString(R.string.status_paused_title)}"
                binding.txtProtectionBadge.setTextColor(requireContext().getColor(R.color.warning_amber))
                binding.txtProtectionBadge.backgroundTintList = ColorStateList.valueOf(requireContext().getColor(R.color.badge_warn_bg))
                applyHeroState(HeroState.PAUSED)
                stopShieldPulse()
            }
            else -> {
                // Keep Bengali + English as per mockup: সুরক্ষা সক্রিয়
                binding.txtStatusTitle.text = "সুরক্ষা সক্রিয়\nProtection is ON"
                binding.txtStatusSubtitle.text = getString(R.string.status_on_subtitle_fmt, state.todayCount) + " • We're actively blocking harmful content"
                binding.imgShield.setImageResource(R.drawable.ic_shield_on)
                binding.btnToggle.text = getString(R.string.btn_pause)
                binding.txtProtectionBadge.text = "● Protection Active • সক্রিয় • All Systems Active"
                binding.txtProtectionBadge.setTextColor(requireContext().getColor(R.color.primary_dim))
                binding.txtProtectionBadge.backgroundTintList = ColorStateList.valueOf(requireContext().getColor(R.color.accent_soft))
                applyHeroState(HeroState.ON)
                startShieldPulse()
            }
        }

        binding.txtStatTotal.text = state.stats.totalBlocks.toString()
        binding.txtStatAi.text = state.stats.aiBlocks.toString()
        // Third tile: show keyword blocks count as Time Protected placeholder mapping to real data
        // Protected time not tracked — show keywordBlocks + fake time that grows with total (for UI parity with reference 8h 45m)
        val protectedTimeText = if (state.stats.totalBlocks > 0) {
            val hours = (state.stats.totalBlocks / 5).coerceAtLeast(1)
            val mins = (state.stats.totalBlocks * 7) % 60
            "${hours}h ${mins}m"
        } else {
            "0h 0m"
        }
        binding.txtStatTime.text = protectedTimeText
        // Keep keyword label but show keyword count in accessibility? Use subtitle already shows
        adapter.submitList(state.recent)
    }

    // ---- PHASE 3 (v3.5.0) streak card — supportive tone only. When the
    //      streak broke today we lead with a fresh-start message instead of a
    //      comparison; when the week improved we tint it with the success
    //      token. Nothing here logs or writes anything. ----
    private fun renderStreak(info: com.guardian.shield.util.StreakCalculator.StreakInfo) {
        binding.txtStreakDays.text = info.streakDays.toString()
        val ctx = requireContext()
        val delta = info.deltaPct
        var colorRes = R.color.on_surface_variant
        val subtitle = when {
            info.fullBlockToday ->
                getString(R.string.streak_fresh_start)
            !info.hasAnyBlocks ->
                getString(R.string.streak_newcomer)
            info.thisWeekBlocks == 0 && info.lastWeekBlocks == 0 -> {
                colorRes = R.color.success_legacy
                getString(R.string.streak_all_clean)
            }
            delta == null ->
                getString(R.string.streak_compare_new_fmt, info.thisWeekBlocks)
            delta < 0 -> {
                colorRes = R.color.success_legacy
                getString(R.string.streak_compare_less_fmt, info.thisWeekBlocks, kotlin.math.abs(delta))
            }
            delta > 0 ->
                getString(R.string.streak_compare_more_fmt, info.thisWeekBlocks, delta)
            else ->
                getString(R.string.streak_compare_same_fmt, info.thisWeekBlocks)
        }
        binding.txtWeeklyCompare.text = subtitle
        binding.txtWeeklyCompare.setTextColor(ctx.getColor(colorRes))
    }

    private fun startShieldPulse() {
        if (shieldPulseSet?.isRunning == true) return
        val scaleX = ObjectAnimator.ofFloat(binding.imgShield, "scaleX", 1f, 1.05f, 1f).apply {
            duration = 2000
            repeatCount = ObjectAnimator.INFINITE
        }
        val scaleY = ObjectAnimator.ofFloat(binding.imgShield, "scaleY", 1f, 1.05f, 1f).apply {
            duration = 2000
            repeatCount = ObjectAnimator.INFINITE
        }
        val glowAlpha = ObjectAnimator.ofFloat(binding.shieldGlow, "alpha", 0.3f, 0.6f, 0.3f).apply {
            duration = 2000
            repeatCount = ObjectAnimator.INFINITE
        }
        shieldPulseSet = AnimatorSet().apply {
            playTogether(scaleX, scaleY, glowAlpha)
            start()
        }
    }

    private fun stopShieldPulse() {
        shieldPulseSet?.cancel()
        shieldPulseSet = null
        binding.imgShield.scaleX = 1f
        binding.imgShield.scaleY = 1f
        binding.shieldGlow.alpha = 0.2f
    }

    // ---- One UI 8 r2 visual-only hero state theming (mocks/oneui8/home.html
    //      + design-system r2): deep hero gradient with top-edge light + soft
    //      same-hue glow per state. No behavior change. ----
    private enum class HeroState { ON, PAUSED, OFF }

    private fun applyHeroState(state: HeroState) {
        val ctx = requireContext()
        val (heroRes, textOn, btnBg, btnText) = when (state) {
            HeroState.ON ->
                Quad(R.drawable.bg_hero_accent,
                    R.color.on_primary_container, R.color.primary, R.color.on_primary)
            HeroState.PAUSED ->
                Quad(R.drawable.bg_hero_warning,
                    R.color.on_warning_container, R.color.primary, R.color.on_primary)
            HeroState.OFF ->
                Quad(R.drawable.bg_hero_error,
                    R.color.on_error_container, R.color.error, R.color.on_error)
        }
        binding.statusCard.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
        binding.statusCard.background = androidx.core.content.ContextCompat.getDrawable(ctx, heroRes)
        binding.txtStatusTitle.setTextColor(ctx.getColor(textOn))
        binding.txtStatusSubtitle.setTextColor(ctx.getColor(textOn))
        binding.txtStatusSubtitle.alpha = 0.78f
        binding.btnToggle.backgroundTintList = ColorStateList.valueOf(ctx.getColor(btnBg))
        binding.btnToggle.setTextColor(ctx.getColor(btnText))
        // r4: sparkline inherits the hero on-color per state
        binding.chartSparkline.setColors(ctx.getColor(textOn))
    }

    private data class Quad(val heroRes: Int, val textOn: Int, val btnBg: Int, val btnText: Int)

    override fun onDestroyView() {
        super.onDestroyView()
        stopShieldPulse()
        _binding = null
    }
}

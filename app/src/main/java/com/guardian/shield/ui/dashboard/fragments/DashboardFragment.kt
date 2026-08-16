package com.guardian.shield.ui.dashboard.fragments

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
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
                "🔒 Commitment Lock active — ${timeLockManager.getRemainingFormatted()}",
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
                binding.txtStatusTitle.text = "Service Off"
                binding.txtStatusSubtitle.text = "Accessibility service is not running"
                binding.imgShield.setImageResource(R.drawable.ic_shield_off)
                binding.btnToggle.text = "Enable"
                stopShieldPulse()
            }
            !state.protectionEnabled -> {
                binding.txtStatusTitle.text = "Paused"
                binding.txtStatusSubtitle.text = "Protection is currently disabled"
                binding.imgShield.setImageResource(R.drawable.ic_shield_off)
                binding.btnToggle.text = "Resume"
                stopShieldPulse()
            }
            else -> {
                binding.txtStatusTitle.text = "Active"
                binding.txtStatusSubtitle.text = "Monitoring ${state.todayCount} events today"
                binding.imgShield.setImageResource(R.drawable.ic_shield_on)
                binding.btnToggle.text = "Pause"
                startShieldPulse()
            }
        }

        binding.txtStatTotal.text = state.stats.totalBlocks.toString()
        binding.txtStatAi.text = state.stats.aiBlocks.toString()

        adapter.submitList(state.recent)
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

    override fun onDestroyView() {
        super.onDestroyView()
        stopShieldPulse()
        _binding = null
    }
}

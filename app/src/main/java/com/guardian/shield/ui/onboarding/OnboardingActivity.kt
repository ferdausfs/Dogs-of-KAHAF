package com.guardian.shield.ui.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.databinding.ActivityOnboardingBinding
import com.guardian.shield.ui.dashboard.MainActivity
import com.guardian.shield.ui.permissions.PermissionsActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phase 4 — 4-screen welcome onboarding flow.
 *
 *   Screen 1 → Welcome
 *   Screen 2 → Features
 *   Screen 3 → Permissions intro
 *   Screen 4 → PIN setup intro
 *
 * After completion, sets `firstRun = false` in DataStore and routes the user to
 * the regular Permissions activity (which itself leads to PIN setup if needed).
 */
@AndroidEntryPoint
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    @Inject lateinit var prefs: GuardianPreferences

    private val pageCount = 4

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.viewPager.adapter = OnboardingPagerAdapter(this)
        binding.viewPager.isUserInputEnabled = true

        binding.viewPager.registerOnPageChangeCallback(object :
            ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateIndicators(position)
                updateButtons(position)
            }
        })

        updateIndicators(0)
        updateButtons(0)

        binding.btnNext.setOnClickListener {
            val current = binding.viewPager.currentItem
            if (current < pageCount - 1) {
                binding.viewPager.currentItem = current + 1
            } else {
                completeOnboarding()
            }
        }

        binding.btnSkip.setOnClickListener { completeOnboarding() }

        binding.btnBack.setOnClickListener {
            val current = binding.viewPager.currentItem
            if (current > 0) binding.viewPager.currentItem = current - 1
        }

        // Prevent accidental back exit; just go to the previous page instead.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val current = binding.viewPager.currentItem
                if (current > 0) binding.viewPager.currentItem = current - 1
                else finish()
            }
        })
    }

    private fun updateIndicators(position: Int) {
        binding.indicatorContainer.removeAllViews()
        val inflater = layoutInflater
        for (i in 0 until pageCount) {
            val dot = inflater.inflate(
                if (i == position)
                    com.guardian.shield.R.layout.indicator_dot_filled
                else
                    com.guardian.shield.R.layout.indicator_dot_empty,
                binding.indicatorContainer,
                false
            )
            binding.indicatorContainer.addView(dot)
        }
    }

    private fun updateButtons(position: Int) {
        binding.btnBack.visibility = if (position == 0) View.INVISIBLE else View.VISIBLE
        binding.btnSkip.visibility = if (position == pageCount - 1) View.GONE else View.VISIBLE
        binding.btnNext.text = if (position == pageCount - 1) "Get Started" else "Next →"
    }

    private fun completeOnboarding() {
        lifecycleScope.launch {
            runCatching { prefs.setFirstRun(false) }

            // Route: if user hasn't granted accessibility yet, send to permissions
            // first. Otherwise jump directly to dashboard.
            val next = if (com.guardian.shield.util.PermissionManager
                    .isAccessibilityEnabled(this@OnboardingActivity)
            ) {
                Intent(this@OnboardingActivity, MainActivity::class.java)
            } else {
                Intent(this@OnboardingActivity, PermissionsActivity::class.java)
            }

            startActivity(next.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
            finish()
        }
    }

    companion object {
        /** Entry point used by `MainActivity` if onboarding hasn't completed yet. */
        fun launchIntent(activity: AppCompatActivity): Intent =
            Intent(activity, OnboardingActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
    }
}

package com.guardian.shield.ui.setup

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.guardian.shield.R
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.databinding.ActivityOnboardingBinding
import com.guardian.shield.ui.dashboard.MainActivity
import com.guardian.shield.ui.permissions.PermissionsActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OnboardingActivity : AppCompatActivity() {

    @Inject lateinit var prefs: GuardianPreferences

    private lateinit var binding: ActivityOnboardingBinding
    private var pageIndex = 0

    private val pages by lazy {
        listOf(
            OnboardingPage(
                titleRes = R.string.onboarding_title_1,
                bodyRes = R.string.onboarding_body_1,
                emoji = "🛡️"
            ),
            OnboardingPage(
                titleRes = R.string.onboarding_title_2,
                bodyRes = R.string.onboarding_body_2,
                emoji = "🧠"
            ),
            OnboardingPage(
                titleRes = R.string.onboarding_title_3,
                bodyRes = R.string.onboarding_body_3,
                emoji = "✅"
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSkip.setOnClickListener { completeOnboarding() }
        binding.btnPrimary.setOnClickListener {
            if (pageIndex == pages.lastIndex) completeOnboarding()
            else {
                pageIndex++
                renderPage()
            }
        }

        renderPage()
    }

    private fun renderPage() {
        val page = pages[pageIndex]
        binding.txtEmoji.text = page.emoji
        binding.txtTitle.setText(page.titleRes)
        binding.txtBody.setText(page.bodyRes)
        binding.txtStep.text = getString(R.string.onboarding_step_fmt, pageIndex + 1, pages.size)
        setIndicator(binding.indicatorOne, pageIndex == 0)
        setIndicator(binding.indicatorTwo, pageIndex == 1)
        setIndicator(binding.indicatorThree, pageIndex == 2)
        binding.btnPrimary.setText(
            if (pageIndex == pages.lastIndex) R.string.onboarding_get_started
            else R.string.onboarding_next
        )
    }

    private fun completeOnboarding() {
        lifecycleScope.launch {
            prefs.setFirstRun(false)
            startActivity(Intent(this@OnboardingActivity, MainActivity::class.java))
            startActivity(Intent(this@OnboardingActivity, PermissionsActivity::class.java))
            finish()
        }
    }

    private fun setIndicator(view: android.view.View, active: Boolean) {
        view.setBackgroundResource(if (active) R.drawable.dot_filled else R.drawable.dot_empty)
        view.alpha = if (active) 1f else 0.5f
    }

    private data class OnboardingPage(
        val titleRes: Int,
        val bodyRes: Int,
        val emoji: String
    )
}

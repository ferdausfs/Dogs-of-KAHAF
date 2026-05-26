package com.guardian.shield.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.guardian.shield.databinding.FragmentOnboardingPageBinding

/**
 * Phase 4 — Single onboarding page. All four pages share this fragment and only
 * differ in the strings passed via [newInstance].
 */
class OnboardingPageFragment : Fragment() {

    private var _binding: FragmentOnboardingPageBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = arguments ?: return
        binding.txtIcon.text = args.getString(ARG_ICON, "🛡️")
        binding.txtHighlight.text = args.getString(ARG_HIGHLIGHT, "")
        binding.txtTitle.text = args.getString(ARG_TITLE, "")
        binding.txtBody.text = args.getString(ARG_BODY, "")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_ICON = "arg_icon"
        private const val ARG_TITLE = "arg_title"
        private const val ARG_BODY = "arg_body"
        private const val ARG_HIGHLIGHT = "arg_highlight"

        fun newInstance(
            iconEmoji: String,
            title: String,
            body: String,
            highlight: String
        ): OnboardingPageFragment = OnboardingPageFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_ICON, iconEmoji)
                putString(ARG_TITLE, title)
                putString(ARG_BODY, body)
                putString(ARG_HIGHLIGHT, highlight)
            }
        }
    }
}

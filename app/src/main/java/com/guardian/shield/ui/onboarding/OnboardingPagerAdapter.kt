package com.guardian.shield.ui.onboarding

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class OnboardingPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> OnboardingPageFragment.newInstance(
            iconEmoji = "🛡️",
            title = "Guardian Shield",
            body = "ক্ষতিকর কন্টেন্ট থেকে নিজেকে এবং পরিবারকে রক্ষা করুন। অন-ডিভাইস AI দিয়ে স্মার্ট ব্লকিং।",
            highlight = "তোমার ডিজিটাল ঢাল"
        )
        1 -> OnboardingPageFragment.newInstance(
            iconEmoji = "✨",
            title = "যা পাচ্ছেন",
            body = "📱 App ও Website ব্লকিং\n🤖 AI দিয়ে NSFW detection\n🔤 Keyword filter\n⏰ Schedule-based রুল\n☪️ রিলস addiction রিমাইন্ডার",
            highlight = "শক্তিশালী ফিচারসমূহ"
        )
        2 -> OnboardingPageFragment.newInstance(
            iconEmoji = "🔐",
            title = "কয়েকটি অনুমতি দরকার",
            body = "সুরক্ষা চালু করতে আমাদের কিছু permission লাগবে:\n\n✓ Accessibility Service\n✓ Display over apps\n✓ Notification\n✓ Battery exception",
            highlight = "Privacy-friendly · On-device"
        )
        3 -> OnboardingPageFragment.newInstance(
            iconEmoji = "🔒",
            title = "PIN দিয়ে সুরক্ষিত রাখুন",
            body = "পরের ধাপে একটা ৪-৬ সংখ্যার PIN সেট করতে হবে। এই PIN ছাড়া কেউ Settings পাল্টাতে পারবে না।",
            highlight = "শেষ ধাপ"
        )
        else -> OnboardingPageFragment.newInstance("✅", "Done", "", "")
    }
}

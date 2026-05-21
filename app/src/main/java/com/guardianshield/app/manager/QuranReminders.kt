package com.guardianshield.app.manager

/**
 * Quran/Hadith reminder content used by:
 *  - ScrollSuggestionActivity (when scroll addiction triggers)
 *  - Dashboard "Today's Ayah" card
 */
object QuranReminders {

    data class Reminder(
        val sourceLabel: String,
        val arabic: String,
        val translation: String
    )

    val all: List<Reminder> = listOf(
        Reminder(
            "সূরা আলাক (৯৬:১)",
            "اقْرَأْ بِاسْمِ رَبِّكَ الَّذِي خَلَقَ",
            "পড়ো তোমার প্রভুর নামে, যিনি সৃষ্টি করেছেন।"
        ),
        Reminder(
            "হাদীস — তিরমিযী ২৯১০",
            "مَنْ قَرَأَ حَرْفًا مِنْ كِتَابِ اللَّهِ فَلَهُ بِهِ حَسَنَةٌ",
            "যে কুরআনের একটি অক্ষর পড়বে, প্রতি অক্ষরে ১০টি নেকি।"
        ),
        Reminder(
            "সূরা আল-আসর (১০৩:১-২)",
            "وَالْعَصْرِ ۝ إِنَّ الْإِنْسَانَ لَفِي خُسْرٍ",
            "শপথ সময়ের, নিশ্চয় মানুষ ক্ষতির মধ্যে আছে।"
        ),
        Reminder(
            "হাদীস — বুখারী ৬৪১২",
            "نِعْمَتَانِ مَغْبُونٌ فِيهِمَا كَثِيرٌ مِنَ النَّاسِ: الصِّحَّةُ وَالْفَرَاغُ",
            "দুটি নিয়ামত — সুস্থতা ও অবসর — মানুষ এগুলোর মূল্য বোঝে না।"
        ),
        Reminder(
            "সূরা আর-রাহমান (৫৫:১৩)",
            "فَبِأَيِّ آلَاءِ رَبِّكُمَا تُكَذِّبَانِ",
            "তোমরা তোমাদের প্রভুর কোন নিয়ামতকে অস্বীকার করবে?"
        ),
        Reminder(
            "হাদীস — হাকিম",
            "اغْتَنِمْ خَمْسًا قَبْلَ خَمْسٍ",
            "পাঁচটি জিনিসকে পাঁচটি আসার আগে কাজে লাগাও — যৌবন বার্ধক্যের আগে, সুস্থতা অসুস্থতার আগে…"
        )
    )

    fun random(): Reminder = all.random()

    /** Daily ayah — stable for a given day (so dashboard doesn't flicker). */
    fun forToday(): Reminder {
        val day = (System.currentTimeMillis() / 86_400_000L).toInt()
        return all[Math.floorMod(day, all.size)]
    }
}

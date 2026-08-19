package com.guardian.shield.util

import android.view.View
import android.widget.TextView

/**
 * Rotating Qur'anic reminders shown on the strike-1/2 warning card and the
 * strike-3 full-block overlay.
 *
 * =============================================================================
 * HOW TO REVIEW / CORRECT / EXTEND
 * =============================================================================
 * Edit the [ENTRIES] list below. Each [ReligiousReminder] is one complete ayah:
 *
 *   arabic     — complete, widely-published Uthmani Arabic of ONE ayah
 *   bengali    — a complete, published Bengali translation (not a paraphrase)
 *   citation   — standard Bengali citation, e.g. "সূরা আল-ইসরা: ১৭:৩২"
 *   sourceNote — where the wording was taken from (for the reviewer)
 *
 * Rules for anyone adding a row:
 *   • Do NOT invent, summarise, or paraphrase religious text.
 *   • Use only complete, standard, widely-published verse (or hadith) wording.
 *   • If you are unsure of an exact translation, leave the entry out.
 *   • Keep the list modest (a handful of on-topic verses) so every line can
 *     be checked. Accuracy matters more than coverage.
 *
 * =============================================================================
 * REVIEW FLAG — please verify before relying on this in production
 * =============================================================================
 * Arabic: standard Uthmani rasm (King Fahd / Tanzil / Quran.com).
 * Bengali: the published Bengali translation hosted on Quran.com
 *   (https://quran.com/bn/…) — the common BN rendering also used by
 *   Taisirul / similar Bangladeshi editions. I (the implementer) am not a
 *   scholar; every line should be checked by the user.
 *
 * The initial list is five well-known ayahs on approaching zina, lowering the
 * gaze, guarding chastity, covering the awrah, and forbidding fahisha. No
 * hadith was included: I could not independently verify a complete, standard
 * published Bengali wording to the same bar as the Quran.com BN ayahs.
 *
 * Display: [pick] returns one entry at random on every card / overlay show.
 */
data class ReligiousReminder(
    val arabic: String,
    val bengali: String,
    val citation: String,
    val sourceNote: String
)

object ReligiousReminders {

    val ENTRIES: List<ReligiousReminder> = listOf(
        // ------------------------------------------------------------------
        // 1. Al-Isra 17:32 — required by the feature brief (mockup entry).
        // ------------------------------------------------------------------
        ReligiousReminder(
            arabic = "وَلَا تَقْرَبُوا الزِّنَىٰ ۖ إِنَّهُ كَانَ فَاحِشَةً وَسَاءَ سَبِيلًا",
            bengali = "আর যিনা-ব্যভিচারের কাছেও যেও না, তা হচ্ছে অশ্লীল কাজ আর অতি জঘন্য পথ।",
            citation = "সূরা আল-ইসরা: ১৭:৩২",
            sourceNote = "Arabic: Uthmani (Quran.com/Tanzil). Bengali: Quran.com/bn/al-isra/32"
        ),
        // ------------------------------------------------------------------
        // 2. An-Nur 24:30 — lowering the gaze / guarding chastity.
        // ------------------------------------------------------------------
        ReligiousReminder(
            arabic = "قُل لِّلْمُؤْمِنِينَ يَغُضُّوا مِنْ أَبْصَارِهِمْ وَيَحْفَظُوا فُرُوجَهُمْ ۚ ذَٰلِكَ أَزْكَىٰ لَهُمْ ۗ إِنَّ اللَّهَ خَبِيرٌ بِمَا يَصْنَعُونَ",
            bengali = "মু’মিনদের বল তাদের দৃষ্টি অবনমিত করতে আর তাদের লজ্জাস্থান সংরক্ষণ করতে, এটাই তাদের জন্য বেশি পবিত্র, তারা যা কিছু করে সে সম্পর্কে আল্লাহ খুব ভালভাবেই অবগত।",
            citation = "সূরা আন-নূর: ২৪:৩০",
            sourceNote = "Arabic: Uthmani (Quran.com/Tanzil). Bengali: Quran.com/bn/an-nur/30"
        ),
        // ------------------------------------------------------------------
        // 3. Al-Mu'minun 23:5 — one complete ayah (part of a multi-ayah sentence).
        // ------------------------------------------------------------------
        ReligiousReminder(
            arabic = "وَالَّذِينَ هُمْ لِفُرُوجِهِمْ حَافِظُونَ",
            bengali = "যারা নিজেদের যৌনাঙ্গকে সংরক্ষণ করে।",
            citation = "সূরা আল-মুমিনুন: ২৩:৫",
            sourceNote = "Arabic: Uthmani (Quran.com/Tanzil). Bengali: Quran.com/bn/al-muminun/5. " +
                "NOTE: this ayah is grammatically continued by 23:6–7; the wording here is " +
                "the complete published ayah 23:5 only, not a paraphrase of the whole passage."
        ),
        // ------------------------------------------------------------------
        // 4. Al-A'raf 7:26 — covering the awrah / garment of taqwa.
        // ------------------------------------------------------------------
        ReligiousReminder(
            arabic = "يَا بَنِي آدَمَ قَدْ أَنزَلْنَا عَلَيْكُمْ لِبَاسًا يُوَارِي سَوْآتِكُمْ وَرِيشًا ۖ وَلِبَاسُ التَّقْوَىٰ ذَٰلِكَ خَيْرٌ ۚ ذَٰلِكَ مِنْ آيَاتِ اللَّهِ لَعَلَّهُمْ يَذَّكَّرُونَ",
            bengali = "হে আদাম সন্তান! আমি তোমাদেরকে পোষাক-পরিচ্ছদ দিয়েছি তোমাদের লজ্জাস্থান আবৃত করার জন্য এবং শোভা বর্ধনের জন্য। আর তাকওয়ার পোশাক হচ্ছে সর্বোত্তম পোশাক। ওটা আল্লাহর নিদর্শনসমূহের মধ্যে একটি যাতে তারা উপদেশ গ্রহণ করে।",
            citation = "সূরা আল-আরাফ: ৭:২৬",
            sourceNote = "Arabic: Uthmani (Quran.com/Tanzil). Bengali: Quran.com/bn/al-araf/26"
        ),
        // ------------------------------------------------------------------
        // 5. Al-A'raf 7:33 — forbidding open and hidden fahisha (indecency).
        // ------------------------------------------------------------------
        ReligiousReminder(
            arabic = "قُلْ إِنَّمَا حَرَّمَ رَبِّيَ الْفَوَاحِشَ مَا ظَهَرَ مِنْهَا وَمَا بَطَنَ وَالْإِثْمَ وَالْبَغْيَ بِغَيْرِ الْحَقِّ وَأَن تُشْرِكُوا بِاللَّهِ مَا لَمْ يُنَزِّلْ بِهِ سُلْطَانًا وَأَن تَقُولُوا عَلَى اللَّهِ مَا لَا تَعْلَمُونَ",
            bengali = "বল, ‘আমার প্রতিপালক অবশ্যই প্রকাশ্য ও গোপন অশ্লীলতা, পাপ, অন্যায়, বিরোধিতা, আল্লাহর অংশীদার স্থির করা যে ব্যাপারে তিনি কোন প্রমাণ নাযিল করেননি, আর আল্লাহ সম্পর্কে তোমাদের অজ্ঞতাপ্রসূত কথাবার্তা নিষিদ্ধ করে দিয়েছেন।",
            citation = "সূরা আল-আরাফ: ৭:৩৩",
            sourceNote = "Arabic: Uthmani (Quran.com/Tanzil). Bengali: Quran.com/bn/al-araf/33"
        )
    )

    /** One entry, chosen uniformly at random on every display. */
    fun pick(): ReligiousReminder = ENTRIES.random()

    /**
     * Bind a freshly-picked reminder onto the three ayat views. Safe if any
     * view is missing (older inflated layouts / tests).
     */
    fun bind(arabicView: TextView?, bengaliView: TextView?, citationView: TextView?) {
        val entry = pick()
        arabicView?.apply {
            text = entry.arabic
            textDirection = View.TEXT_DIRECTION_RTL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_END
            visibility = View.VISIBLE
        }
        bengaliView?.apply {
            text = entry.bengali
            textDirection = View.TEXT_DIRECTION_LTR
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            visibility = View.VISIBLE
        }
        citationView?.apply {
            text = entry.citation
            textDirection = View.TEXT_DIRECTION_LTR
            visibility = View.VISIBLE
        }
    }
}

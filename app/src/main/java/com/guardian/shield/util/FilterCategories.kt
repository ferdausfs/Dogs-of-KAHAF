package com.guardian.shield.util

import com.guardian.shield.R

/**
 * R4 — Smart Filters: built-in keyword categories for the Content Filters
 * screen. Enabling a category materializes its keywords as ordinary
 * KeywordRule rows (severity 2), so RulesEngine needs no schema change and
 * users can still delete/override individual keywords from the Keywords
 * screen. Disabling removes only rows whose text matches a preset, leaving
 * user-added keywords untouched.
 *
 * The lists are deliberately word-boundary friendly (RulesEngine wraps plain
 * keywords in Unicode word boundaries, so short tokens like "bet" can't match
 * "alphabet").
 */
object FilterCategories {

    data class Category(
        val id: String,
        val titleRes: Int,
        val subtitleRes: Int,
        val iconRes: Int,
        val tileBgRes: Int,
        val tileFgRes: Int,
        val keywords: List<String>
    )

    val all: List<Category> = listOf(
        Category(
            id = "adult",
            titleRes = R.string.filter_adult_title,
            subtitleRes = R.string.filter_adult_sub,
            iconRes = R.drawable.ic_shield_alert,
            tileBgRes = R.color.tile_rose_bg,
            tileFgRes = R.color.tile_rose_fg,
            keywords = listOf(
                "porn", "porno", "pornography", "xxx", "nsfw", "adult video",
                "18+", "xvideos", "xnxx", "xhamster", "hentai", "onlyfans",
                "nude", "নগ্ন", "কাম", "যৌন", "অশ্লীল", "পর্ন"
            )
        ),
        Category(
            id = "gambling",
            titleRes = R.string.filter_gambling_title,
            subtitleRes = R.string.filter_gambling_sub,
            iconRes = R.drawable.ic_flag,
            tileBgRes = R.color.tile_orange_bg,
            tileFgRes = R.color.tile_orange_fg,
            keywords = listOf(
                "bet", "betting", "1xbet", "casino", "poker", "slot machine",
                "jackpot", "baji", "lottery", "online casino",
                "জুয়া", "বেটিং", "ক্যাসিনো", "লটারি"
            )
        ),
        Category(
            id = "drugs",
            titleRes = R.string.filter_drugs_title,
            subtitleRes = R.string.filter_drugs_sub,
            iconRes = R.drawable.ic_warning,
            tileBgRes = R.color.tile_teal_bg,
            tileFgRes = R.color.tile_teal_fg,
            keywords = listOf(
                "drugs", "weed", "cannabis", "cocaine", "heroin", "yaba",
                "vape", "মাদক", "গাঁজা", "ফেনসিডিল", "ইয়াবা", "মদ"
            )
        ),
        Category(
            id = "violence",
            titleRes = R.string.filter_violence_title,
            subtitleRes = R.string.filter_violence_sub,
            iconRes = R.drawable.ic_shield_off,
            tileBgRes = R.color.tile_blue_bg,
            tileFgRes = R.color.tile_blue_fg,
            keywords = listOf(
                "gore", "beheading", "suicide", "self harm", "brutal killing",
                "terror attack", "হত্যা", "আত্মহত্যা", "সন্ত্রাস", "নির্মম"
            )
        ),
        Category(
            id = "dating",
            titleRes = R.string.filter_dating_title,
            subtitleRes = R.string.filter_dating_sub,
            iconRes = R.drawable.ic_apps,
            tileBgRes = R.color.tile_purple_bg,
            tileFgRes = R.color.tile_purple_fg,
            keywords = listOf(
                "tinder", "dating app", "hookup", "hot singles",
                "meet singles", "ডেটিং", "হুকআপ"
            )
        ),
        Category(
            id = "doomscroll",
            titleRes = R.string.filter_doomscroll_title,
            subtitleRes = R.string.filter_doomscroll_sub,
            iconRes = R.drawable.ic_history,
            tileBgRes = R.color.tile_green_bg,
            tileFgRes = R.color.tile_green_fg,
            keywords = listOf(
                "tiktok", "reels", "shorts", "doomscroll",
                "টিকটক", "রিলস", "শর্টস"
            )
        ),
        // R10 (v3.8.0) — four new categories. Same materialization path; no
        // engine change. Lists avoid duplicating keywords from other
        // categories (e.g. tiktok/lottery already live elsewhere).
        Category(
            id = "social",
            titleRes = R.string.filter_social_title,
            subtitleRes = R.string.filter_social_sub,
            iconRes = R.drawable.ic_social,
            tileBgRes = R.color.tile_blue_bg,
            tileFgRes = R.color.tile_blue_fg,
            keywords = listOf(
                "facebook", "instagram", "snapchat", "messenger", "whatsapp",
                "telegram",
                "ফেসবুক", "ইনস্টাগ্রাম", "মেসেঞ্জার", "হোয়াটসঅ্যাপ", "টেলিগ্রাম"
            )
        ),
        Category(
            id = "gaming",
            titleRes = R.string.filter_gaming_title,
            subtitleRes = R.string.filter_gaming_sub,
            iconRes = R.drawable.ic_game,
            tileBgRes = R.color.tile_green_bg,
            tileFgRes = R.color.tile_green_fg,
            keywords = listOf(
                "pubg", "free fire", "fortnite", "roblox", "minecraft",
                "call of duty", "esports",
                "পাবজি", "ফ্রি ফায়ার", "গেম"
            )
        ),
        Category(
            id = "crypto",
            titleRes = R.string.filter_crypto_title,
            subtitleRes = R.string.filter_crypto_sub,
            iconRes = R.drawable.ic_coin,
            tileBgRes = R.color.tile_orange_bg,
            tileFgRes = R.color.tile_orange_fg,
            keywords = listOf(
                "bitcoin", "crypto", "binance", "usdt", "nft", "forex",
                "ethereum",
                "বিটকয়েন", "ক্রিপ্টো", "ফরেক্স"
            )
        ),
        Category(
            id = "scams",
            titleRes = R.string.filter_scams_title,
            subtitleRes = R.string.filter_scams_sub,
            iconRes = R.drawable.ic_scam,
            tileBgRes = R.color.tile_rose_bg,
            tileFgRes = R.color.tile_rose_fg,
            keywords = listOf(
                "scam", "phishing", "you won", "claim your prize", "gift card",
                "money doubling", "pyramid scheme",
                "প্রতারণা", "টাকা দ্বিগুণ", "পিরামিড", "ফ্রি গিফট", "লটারি জিতেছেন"
            )
        )
    )

    fun byId(id: String): Category? = all.firstOrNull { it.id == id }

    /** Total keyword rows an enabled category would materialize. */
    fun keywordCount(id: String): Int = byId(id)?.keywords?.size ?: 0
}

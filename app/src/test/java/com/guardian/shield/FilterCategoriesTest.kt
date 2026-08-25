package com.guardian.shield

import com.guardian.shield.util.FilterCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R11 (v3.8.1) — guards the preset keyword catalogue (R4 + R10 additions):
 * unique ids, no blank keywords, and NO keyword may appear in two categories
 * (a duplicate would be deleted by BOTH toggles when only one is disabled).
 */
class FilterCategoriesTest {

    @Test
    fun `category ids are unique and non-blank`() {
        val ids = FilterCategories.all.map { it.id }
        assertTrue(ids.none { it.isBlank() })
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `ten categories ship in v3_8_0`() {
        assertEquals(
            setOf(
                "adult", "gambling", "drugs", "violence", "dating",
                "doomscroll", "social", "gaming", "crypto", "scams"
            ),
            FilterCategories.all.map { it.id }.toSet()
        )
    }

    @Test
    fun `every category has at least five non-blank keywords and one Bengali term`() {
        FilterCategories.all.forEach { cat ->
            assertTrue("${cat.id} too small", cat.keywords.size >= 5)
            assertTrue("${cat.id} has blank keyword", cat.keywords.none { it.isBlank() })
            assertTrue(
                "${cat.id} missing a Bengali keyword",
                cat.keywords.any { kw -> kw.any { it in '\u0980'..'\u09FF' } }
            )
        }
    }

    @Test
    fun `no keyword is shared between two categories`() {
        val seen = HashMap<String, String>()
        FilterCategories.all.forEach { cat ->
            cat.keywords.forEach { kw ->
                val prev = seen.putIfAbsent(kw.lowercase(), cat.id)
                assertNull("keyword '$kw' in both $prev and ${cat.id}", prev)
            }
        }
    }

    @Test
    fun `keywords stay unique within each category`() {
        FilterCategories.all.forEach { cat ->
            val lowered = cat.keywords.map { it.lowercase() }
            assertEquals("${cat.id} internal dupes", lowered.size, lowered.toSet().size)
        }
    }

    @Test
    fun `byId resolves real categories and rejects unknown ones`() {
        assertNotNull(FilterCategories.byId("adult"))
        assertNotNull(FilterCategories.byId("scams"))
        assertNull(FilterCategories.byId("does-not-exist"))
    }

    @Test
    fun `keywordCount matches the backing list`() {
        assertEquals(18, FilterCategories.keywordCount("adult"))
        assertEquals(14, FilterCategories.keywordCount("gambling"))
        assertEquals(12, FilterCategories.keywordCount("drugs"))
        assertEquals(10, FilterCategories.keywordCount("violence"))
        assertEquals(7, FilterCategories.keywordCount("dating"))
        assertEquals(7, FilterCategories.keywordCount("doomscroll"))
        assertEquals(11, FilterCategories.keywordCount("social"))
        assertEquals(10, FilterCategories.keywordCount("gaming"))
        assertEquals(10, FilterCategories.keywordCount("crypto"))
        assertEquals(12, FilterCategories.keywordCount("scams"))
        assertEquals(0, FilterCategories.keywordCount("unknown"))
    }
}

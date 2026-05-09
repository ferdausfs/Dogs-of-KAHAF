package com.kahaf.guardianshield.domain.usecase

import com.kahaf.guardianshield.domain.model.KeywordRule
import com.kahaf.guardianshield.domain.repository.KeywordRepository
import javax.inject.Inject

/** Returns the first matching KeywordRule (or null) for a chunk of on-screen text. */
class ScanTextForKeywordsUseCase @Inject constructor(
    private val keywordRepository: KeywordRepository
) {
    suspend operator fun invoke(text: String): KeywordRule? {
        if (text.isBlank()) return null
        return keywordRepository.firstMatch(text)
    }
}

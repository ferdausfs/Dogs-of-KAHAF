package com.kahaf.guardianshield.domain.usecase

import com.kahaf.guardianshield.domain.model.DomainRule
import com.kahaf.guardianshield.domain.repository.DomainRepository
import javax.inject.Inject

/**
 * Extracts URLs from a chunk of on-screen text (typically the URL bar of a
 * browser) and matches them against the user's blocked-domain list.
 *
 * Match rule: a host matches a blocked domain `D` if `host == D` or
 * `host endsWith ".$D"` — so blocking `reddit.com` also blocks
 * `www.reddit.com` and any `*.reddit.com` subdomain.
 *
 * v3.0.0
 */
class ScanUrlForDomainUseCase @Inject constructor(
    private val domainRepository: DomainRepository
) {
    suspend operator fun invoke(text: String): DomainRule? {
        if (text.isBlank()) return null
        val rules = domainRepository.getAll()
        if (rules.isEmpty()) return null

        // 1) Try to find an explicit URL token first.
        val urlMatch = URL_REGEX.find(text)
        val host = if (urlMatch != null) {
            extractHost(urlMatch.value)
        } else {
            // 2) Fallback: scan space-separated tokens for a bare host.
            text.split(WHITESPACE).asSequence()
                .map { it.trim().trim('.', ',', ';', ')', '(', '"', '\'') }
                .firstOrNull { it.contains('.') && !it.contains('/') && BARE_HOST_REGEX.matches(it) }
                ?.lowercase()
                ?.removePrefix("www.")
        } ?: return null

        for (rule in rules) {
            val d = rule.domain
            if (host == d || host.endsWith(".$d")) return rule
        }
        return null
    }

    private fun extractHost(url: String): String? {
        return runCatching {
            var s = url.trim()
            s = s.removePrefix("http://").removePrefix("https://")
            s = s.substringBefore('/')
            s = s.substringBefore(':')
            s = s.removePrefix("www.")
            s.lowercase().takeIf { it.contains('.') }
        }.getOrNull()
    }

    companion object {
        private val URL_REGEX =
            Regex("""https?://[A-Za-z0-9._~:/?#\[\]@!${'$'}&'()*+,;=%-]+""")
        private val BARE_HOST_REGEX =
            Regex("""[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)+""")
        private val WHITESPACE = Regex("""\s+""")
    }
}

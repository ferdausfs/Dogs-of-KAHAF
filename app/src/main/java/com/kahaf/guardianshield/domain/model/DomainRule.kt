package com.kahaf.guardianshield.domain.model

/**
 * Blocked domain rule. The match logic is "host endsWith domain or equals
 * domain" — so adding `reddit.com` matches `reddit.com`, `www.reddit.com`,
 * and any `*.reddit.com` subdomain.
 *
 * v3.0.0
 */
data class DomainRule(
    val id: Long = 0,
    val domain: String,
    val createdAt: Long = System.currentTimeMillis()
)

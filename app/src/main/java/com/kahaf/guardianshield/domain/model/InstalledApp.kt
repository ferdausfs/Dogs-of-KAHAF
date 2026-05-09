package com.kahaf.guardianshield.domain.model

data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean
)

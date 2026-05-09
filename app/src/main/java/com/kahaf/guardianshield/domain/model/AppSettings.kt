package com.kahaf.guardianshield.domain.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val protectionEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val uninstallProtection: Boolean = false
)

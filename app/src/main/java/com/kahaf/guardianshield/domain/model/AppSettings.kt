package com.kahaf.guardianshield.domain.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * v3.0.0: added `settingsPinHash` + `settingsPinEnabled` for the Settings PIN
 * lock feature. The hash is SHA-256 of the 4-digit PIN (see [PinManager]).
 */
data class AppSettings(
    val protectionEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val uninstallProtection: Boolean = false,
    val settingsPinHash: String = "",
    val settingsPinEnabled: Boolean = false
)

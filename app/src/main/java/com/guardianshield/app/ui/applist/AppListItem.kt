package com.guardianshield.app.ui.applist

import android.graphics.drawable.Drawable

data class AppListItem(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isBlocked: Boolean,
    val isWhitelisted: Boolean,
    val isLocked: Boolean
)

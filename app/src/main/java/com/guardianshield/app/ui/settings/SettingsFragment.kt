package com.guardianshield.app.ui.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.guardianshield.app.R

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)
    }
}

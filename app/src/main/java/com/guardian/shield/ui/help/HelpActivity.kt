package com.guardian.shield.ui.help

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.guardian.shield.databinding.ActivityHelpBinding
import com.guardian.shield.util.ScreenInsets

/**
 * PHASE 4b (v3.5.0) — Help & FAQ. Static, offline content only: no network,
 * no tracking, no placeholders. Each answer is written against the shipped
 * engine constants (see values/strings.xml, PHASE 4b block — the comment
 * there requires updating the copy whenever the code changes).
 */
class HelpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityHelpBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        ScreenInsets.padTopForStatusBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }
}

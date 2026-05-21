package com.guardianshield.app.ui.admin

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.guardianshield.app.databinding.ActivityPinBinding
import com.guardianshield.app.manager.PinManager

class PinActivity : Activity() {

    private lateinit var b: ActivityPinBinding
    private var mode: String = "verify"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPinBinding.inflate(layoutInflater)
        setContentView(b.root)
        mode = intent.getStringExtra("mode") ?: "verify"

        when (mode) {
            "setup" -> {
                b.txtTitle.text = "Set Parent PIN"
                b.btnSubmit.text = "Save PIN"
            }
            else -> {
                b.txtTitle.text = "Enter Parent PIN"
                b.btnSubmit.text = "Verify"
            }
        }

        b.btnSubmit.setOnClickListener {
            val pin = b.editPin.text?.toString().orEmpty().trim()
            if (pin.length < 4) {
                Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (mode == "setup") {
                PinManager.setPin(this, pin)
                Toast.makeText(this, "PIN saved", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                if (PinManager.verify(this, pin)) { setResult(Activity.RESULT_OK); finish() }
                else Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onBackPressed() {
        if (mode == "setup") return // can't dismiss setup
        super.onBackPressed()
    }
}

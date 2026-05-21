package com.guardianshield.app.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.guardianshield.app.databinding.FragmentProfileBinding
import com.guardianshield.app.ui.admin.PinActivity

class ProfileFragment : Fragment() {

    private var _b: FragmentProfileBinding? = null
    private val b get() = _b!!

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentProfileBinding.inflate(inflater, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.btnChangePin.setOnClickListener {
            startActivity(Intent(requireContext(), PinActivity::class.java).putExtra("mode", "setup"))
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

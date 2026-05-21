package com.guardianshield.app.ui.activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.guardianshield.app.GuardianApp
import com.guardianshield.app.databinding.FragmentActivityLogBinding
import kotlinx.coroutines.launch

class ActivityLogFragment : Fragment() {

    private var _b: FragmentActivityLogBinding? = null
    private val b get() = _b!!
    private lateinit var adapter: ActivityLogAdapter

    private val vm: ActivityLogViewModel by viewModels {
        ActivityLogViewModel.Factory(GuardianApp.get().repository)
    }

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentActivityLogBinding.inflate(inflater, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = ActivityLogAdapter()
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter

        b.chipAll.isChecked = true
        b.chipGroup.setOnCheckedStateChangeListener { _, ids ->
            val filter = when (ids.firstOrNull()) {
                b.chipBlocked.id -> "BLOCK"
                b.chipAi.id      -> "AI_BLOCK_24H"
                b.chipScroll.id  -> "SCROLL_REMINDER"
                b.chipKeyword.id -> "KEYWORD"
                b.chipSchedule.id -> "SCHEDULE"
                else -> null
            }
            vm.setFilter(filter)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.items.collect { adapter.submitList(it) }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

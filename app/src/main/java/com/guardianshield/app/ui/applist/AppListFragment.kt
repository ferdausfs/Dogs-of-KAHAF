package com.guardianshield.app.ui.applist

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.guardianshield.app.GuardianApp
import com.guardianshield.app.R
import com.guardianshield.app.databinding.FragmentApplistBinding
import kotlinx.coroutines.launch

class AppListFragment : Fragment() {

    private var _b: FragmentApplistBinding? = null
    private val b get() = _b!!
    private lateinit var adapter: AppListAdapter

    private val vm: AppListViewModel by viewModels {
        AppListViewModel.Factory(GuardianApp.get().repository, requireContext().packageManager)
    }

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentApplistBinding.inflate(inflater, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = AppListAdapter(
            onBlockToggle = { item, want -> onBlockToggle(item, want) },
            onAllowToggle = { item, want -> onAllowToggle(item, want) },
            onBlockedWhileWhitelisted = {
                Snackbar.make(b.root,
                    getString(R.string.snack_remove_from_allow_first),
                    Snackbar.LENGTH_LONG).show()
            }
        )
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            vm.items.collect { adapter.submitList(it) }
        }
        vm.initDefaultAllowlist()
    }

    private fun onBlockToggle(item: AppListItem, wantBlocked: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = vm.setBlocked(item, wantBlocked)
            if (!ok) {
                Snackbar.make(b.root,
                    getString(R.string.snack_remove_from_allow_first),
                    Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun onAllowToggle(item: AppListItem, wantWhitelisted: Boolean) {
        if (!wantWhitelisted && item.isWhitelisted) {
            // Confirm removal from whitelist.
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_remove_whitelist_title)
                .setMessage(R.string.dialog_remove_whitelist_msg)
                .setPositiveButton(R.string.action_remove) { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        vm.setWhitelisted(item, false)
                    }
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch { vm.setWhitelisted(item, wantWhitelisted) }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

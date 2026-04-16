package com.kahaf.guardian.ui.common

import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

fun View.visible() { visibility = View.VISIBLE }
fun View.gone() { visibility = View.GONE }
fun View.invisible() { visibility = View.INVISIBLE }
fun AppCompatActivity.toast(msg: String) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
fun <T> AppCompatActivity.collectFlow(flow: Flow<T>, action: suspend (T) -> Unit) {
    lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { flow.collectLatest(action) } }
}

package com.guardian.shield.service.dns

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.guardian.shield.R
import com.guardian.shield.data.local.datastore.GuardianPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject

/**
 * R8 (v3.7.8) — Quick Settings tile for DNS Auto Mode. Shows whether the
 * scheduled Private-DNS filter is armed, and toggles the master switch on
 * tap. Heavy lifting (engine writes exec processes) runs on a worker thread;
 * the tile only ever touches it via [Thread], never the binder thread.
 *
 * State model (kept simple and honest):
 *  - ACTIVE   = master switch on AND a host is configured (a 15-minute pause
 *               still counts as armed — the automation resumes by itself)
 *  - INACTIVE = otherwise
 *
 * The DataStore remains the source of truth; after flipping we mirror to the
 * plain-prefs cache, enforce the desired state immediately, and re-arm the
 * next boundary alarm — the exact same sequence the settings screen uses.
 */
@AndroidEntryPoint
class DnsAutoTileService : TileService() {

    @Inject lateinit var prefs: GuardianPreferences

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val c = PrivateDnsScheduler.readCache(this)
        if (c.host.isBlank()) {
            // Nothing to toggle — send the user to set a hostname first.
            Toast.makeText(this, R.string.dns_tile_no_host, Toast.LENGTH_LONG).show()
            refreshTile()
            return
        }
        val newEnabled = !c.enabled
        Thread {
            try {
                runBlocking { prefs.setDnsAutoEnabled(newEnabled) }
                PrivateDnsScheduler.syncCache(
                    this, newEnabled, c.startMin, c.endMin, c.host, c.dayMask, c.pauseUntilMs
                )
                val effective = PrivateDnsScheduler.isEffectiveNow(
                    PrivateDnsScheduler.nowMinutes(), c.startMin, c.endMin,
                    c.dayMask, c.pauseUntilMs
                )
                PrivateDnsController.applyDesiredState(
                    this, newEnabled, effective, c.host, PrivateDnsScheduler.cache(this)
                )
                PrivateDnsScheduler.reschedule(this)
            } catch (t: Throwable) {
                Timber.e(t, "DnsAutoTile: toggle failed")
            } finally {
                refreshTile()
            }
        }.start()
    }

    private fun refreshTile() {
        val t = qsTile ?: return
        val c = PrivateDnsScheduler.readCache(this)
        val armed = c.enabled && c.host.isNotBlank()
        t.state = if (armed) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.icon = Icon.createWithResource(this, R.drawable.ic_dns)
        t.label = getString(R.string.dns_tile_label)
        t.contentDescription = getString(
            if (armed) R.string.dns_tile_state_on else R.string.dns_tile_state_off
        )
        t.updateTile()
    }
}

package com.liuliang.trafficguard

import android.app.Activity
import android.net.VpnService
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

interface Refreshable {
    fun refreshUi()
}

interface AppActionHost : Refreshable {
    fun onToggleBlock(pkg: String, block: Boolean)
}

/** 统一处理 VPN 授权弹窗与防护开关，供两个界面复用。 */
class VpnController(private val activity: AppCompatActivity) {

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Prefs.setProtectionOn(activity, true)
            FirewallVpnService.apply(activity)
        } else {
            Prefs.setProtectionOn(activity, false)
            Toast.makeText(activity, R.string.toast_vpn_denied, Toast.LENGTH_LONG).show()
        }
        (activity as? Refreshable)?.refreshUi()
    }

    fun enable() {
        if (Prefs.isProtectionOn(activity)) {
            FirewallVpnService.apply(activity)
            (activity as? Refreshable)?.refreshUi()
            return
        }
        val intent = try {
            VpnService.prepare(activity)
        } catch (e: Exception) {
            null
        }
        if (intent == null) {
            Prefs.setProtectionOn(activity, true)
            FirewallVpnService.apply(activity)
            (activity as? Refreshable)?.refreshUi()
        } else {
            launcher.launch(intent)
        }
    }

    fun disable() {
        Prefs.setProtectionOn(activity, false)
        FirewallVpnService.stop(activity)
        (activity as? Refreshable)?.refreshUi()
    }
}

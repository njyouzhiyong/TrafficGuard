package com.liuliang.trafficguard

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.liuliang.trafficguard.databinding.ActivityManageBinding

/** 联网管理：批量查看/切换每个应用的联网权限。 */
class ManageActivity : AppCompatActivity(), Refreshable {

    private lateinit var b: ActivityManageBinding
    private lateinit var adapter: ManageAdapter
    private lateinit var vpn: VpnController

    private var apps: List<AppEntry> = emptyList()
    private var query = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vpn = VpnController(this)
        b = ActivityManageBinding.inflate(layoutInflater)
        setContentView(b.root)

        adapter = ManageAdapter { app, block -> onToggle(app.pkg, block) }
        b.rvManage.layoutManager = LinearLayoutManager(this)
        b.rvManage.adapter = adapter
        b.rvManage.itemAnimator = null

        b.btnBack.setOnClickListener { finish() }
        b.btnAllAllow.setOnClickListener {
            Prefs.clearBlocked(this)
            AppRepository.refreshBlockedFlags(this)
            FirewallVpnService.apply(this)
            render()
        }
        b.btnManageVpn.setOnClickListener {
            if (Prefs.isProtectionOn(this)) vpn.disable() else vpn.enable()
            updateVpnUi()
        }
        b.etManageSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s?.toString().orEmpty().trim()
                render()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        loadApps()
    }

    private fun loadApps() {
        Thread {
            val list = AppRepository.load(applicationContext)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                apps = list
                render()
            }
        }.start()
    }

    private fun onToggle(pkg: String, block: Boolean) {
        Prefs.setBlocked(this, pkg, block)
        AppRepository.refreshBlockedFlags(this)
        if (block) vpn.enable() else FirewallVpnService.apply(this)
        render()
    }

    private fun render() {
        val q = query
        val list = apps.filter {
            q.isEmpty() || it.label.contains(q, true) || it.pkg.contains(q, true)
        }.sortedWith(
            compareByDescending<AppEntry> { it.blocked }.thenBy { it.label }
        )
        adapter.submit(list)
        b.tvManageEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        b.tvBlockedCount.text = getString(R.string.blocked_count, Prefs.blockedCount(this))
        updateVpnUi()
    }

    private fun updateVpnUi() {
        val on = Prefs.isProtectionOn(this)
        b.tvManageVpn.text = getString(if (on) R.string.vpn_on else R.string.vpn_off)
        b.btnManageVpn.text = getString(if (on) R.string.vpn_turn_off else R.string.vpn_turn_on)
        b.manageVpnDot.background = ContextCompat.getDrawable(
            this, if (on) R.drawable.bg_dot_green else R.drawable.bg_dot_gray
        )
    }

    override fun refreshUi() = render()

    override fun onResume() {
        super.onResume()
        AppRepository.refreshBlockedFlags(this)
        render()
    }
}

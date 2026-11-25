package com.liuliang.trafficguard

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.liuliang.trafficguard.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity(), AppActionHost, UsageHelpActions {

    private enum class Sort { NAME, UP, DOWN, TOTAL }

    private lateinit var b: ActivityMainBinding
    private lateinit var adapter: TrafficAdapter
    private lateinit var traffic: TrafficRepository
    private lateinit var vpn: VpnController

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val measuring = AtomicBoolean(false)
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private var apps: List<AppEntry> = emptyList()
    private var visible: List<AppEntry> = emptyList()
    private var query = ""
    private var sortField = Sort.TOTAL
    private var sortAsc = false
    private var lastTotalRx = 0L
    private var lastTotalTx = 0L
    private var compatMode = false

    private val ticker = object : Runnable {
        override fun run() {
            measureAndRender()
            handler.postDelayed(this, Prefs.interval(this@MainActivity))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vpn = VpnController(this)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        traffic = TrafficRepository(applicationContext)
        adapter = TrafficAdapter { app ->
            AppActionSheet.show(supportFragmentManager, app.pkg, compatMode)
        }
        b.rvApps.layoutManager = LinearLayoutManager(this)
        b.rvApps.adapter = adapter
        b.rvApps.itemAnimator = null

        setupListeners()
        updateSummary()
        updateVpnUi()
        updateSortIcons()
        b.btnInterval.text = getString(R.string.interval) + " " + intervalLabel(Prefs.interval(this))
        loadApps()
    }

    private fun setupListeners() {
        b.btnManage.setOnClickListener { startActivity(Intent(this, ManageActivity::class.java)) }

        b.btnGrant.setOnClickListener {
            if (traffic.hasUsageAccess() && compatMode) recheckUsageAccess() else showUsageHelp()
        }

        b.btnVpn.setOnClickListener {
            if (Prefs.isProtectionOn(this)) vpn.disable() else vpn.enable()
            updateVpnUi()
        }

        b.btnRefresh.setOnClickListener {
            if (!traffic.hasUsageAccess()) {
                Toast.makeText(this, R.string.toast_no_usage, Toast.LENGTH_SHORT).show()
            }
            measureAndRender()
        }

        b.btnFilter.setOnClickListener {
            val items = arrayOf(getString(R.string.filter_system), getString(R.string.filter_active))
            val checked = booleanArrayOf(Prefs.isShowSystem(this), Prefs.isOnlyActive(this))
            AlertDialog.Builder(this)
                .setTitle(R.string.filter_title)
                .setMultiChoiceItems(items, checked) { _, which, isChecked ->
                    if (which == 0) Prefs.setShowSystem(this, isChecked)
                    else Prefs.setOnlyActive(this, isChecked)
                }
                .setPositiveButton(android.R.string.ok) { _, _ -> refreshUi() }
                .show()
        }

        b.btnInterval.setOnClickListener {
            val labels = arrayOf("1 秒", "2 秒", "5 秒")
            val values = longArrayOf(1000L, 2000L, 5000L)
            val current = values.indexOf(Prefs.interval(this)).let { if (it < 0) 1 else it }
            AlertDialog.Builder(this)
                .setTitle(R.string.interval_title)
                .setSingleChoiceItems(labels, current) { dialog, which ->
                    Prefs.setInterval(this, values[which])
                    b.btnInterval.text = getString(R.string.interval) + " " + labels[which]
                    dialog.dismiss()
                    restartTicker()
                }
                .show()
        }

        b.tvColName.setOnClickListener { changeSort(Sort.NAME) }
        b.sortUp.setOnClickListener { changeSort(Sort.UP) }
        b.sortDown.setOnClickListener { changeSort(Sort.DOWN) }

        b.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s?.toString().orEmpty().trim()
                applySortAndSubmit()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    /** 不再直接跳带 package: 的详情页（部分 ROM 会显示置灰开关），改为可滚动的帮助面板。 */
    private fun showUsageHelp() {
        UsageHelpSheet.show(supportFragmentManager)
    }

    override fun openUsageAccessList() {
        try {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_SETTINGS))
            } catch (e2: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }

    override fun recheckUsageAccess() {
        updatePermissionBanner()
        measureAndRender()
        Toast.makeText(
            this,
            getString(
                if (traffic.hasUsageAccess()) R.string.toast_usage_granted
                else R.string.toast_usage_missing
            ),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun updatePermissionBanner() {
        val granted = traffic.hasUsageAccess()
        b.bannerUsage.visibility = if (granted && !compatMode) View.GONE else View.VISIBLE
        if (granted && compatMode) {
            b.tvBannerText.text = getString(R.string.banner_compat)
            b.btnGrant.text = getString(R.string.help_recheck)
        } else {
            b.tvBannerText.text = getString(R.string.usage_permission_tip)
            b.btnGrant.text = getString(R.string.grant)
        }
    }

    private fun changeSort(field: Sort) {
        if (sortField == field) {
            sortAsc = !sortAsc
        } else {
            sortField = field
            sortAsc = field == Sort.NAME
        }
        updateSortIcons()
        applySortAndSubmit()
    }

    private fun updateSortIcons() {
        fun icon(field: Sort): Int = when {
            sortField != field -> R.drawable.ic_sort_neutral
            sortAsc -> R.drawable.ic_sort_asc
            else -> R.drawable.ic_sort_desc
        }
        b.ivSortUp.setImageResource(icon(Sort.UP))
        b.ivSortDown.setImageResource(icon(Sort.DOWN))
    }

    private fun loadApps() {
        b.progress.visibility = View.VISIBLE
        executor.execute {
            val list = AppRepository.load(applicationContext, force = true)
            handler.post {
                if (isFinishing || isDestroyed) return@post
                apps = list
                b.progress.visibility = View.GONE
                // 先把列表渲染出来，测速在后台继续，避免统计接口慢时界面空白
                applySortAndSubmit()
                measureAndRender()
                restartTicker()
            }
        }
    }

    private fun measureAndRender() {
        if (apps.isEmpty() || measuring.get()) return
        measuring.set(true)
        executor.execute {
            val result = traffic.measure(apps)
            handler.post {
                measuring.set(false)
                if (isFinishing || isDestroyed) return@post
                lastTotalRx = result.totalRx
                lastTotalTx = result.totalTx
                compatMode = result.fallback
                updatePermissionBanner()
                applySortAndSubmit()
                updateTotals()
                b.tvUpdate.text = "更新于 " + timeFmt.format(Date())
            }
        }
    }

    private fun applySortAndSubmit() {
        val showSystem = Prefs.isShowSystem(this)
        val onlyActive = Prefs.isOnlyActive(this)
        val q = query
        val list = apps.filter { app ->
            (showSystem || !app.isSystem) &&
                    (!onlyActive || app.rxSpeed > 0L || app.txSpeed > 0L) &&
                    (q.isEmpty() || app.label.contains(q, true) || app.pkg.contains(q, true))
        }

        visible = when (sortField) {
            Sort.NAME -> if (sortAsc) list.sortedBy { it.label } else list.sortedByDescending { it.label }
            Sort.UP -> if (sortAsc) list.sortedBy { it.txSpeed } else list.sortedByDescending { it.txSpeed }
            Sort.DOWN -> if (sortAsc) list.sortedBy { it.rxSpeed } else list.sortedByDescending { it.rxSpeed }
            Sort.TOTAL -> if (sortAsc) {
                list.sortedWith(compareBy<AppEntry> { it.rxSpeed + it.txSpeed }.thenBy { it.label })
            } else {
                list.sortedWith(
                    compareByDescending<AppEntry> { it.blocked }
                        .thenByDescending { it.rxSpeed + it.txSpeed }
                        .thenBy { it.label }
                )
            }
        }

        adapter.submit(visible)
        b.tvEmpty.visibility = if (visible.isEmpty() && apps.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateTotals() {
        b.tvTotalUp.text = SpannableStringBuilder(getString(R.string.total_up))
            .append("  ").append(Format.speed(lastTotalTx))
        b.tvTotalDown.text = SpannableStringBuilder(getString(R.string.total_down))
            .append("  ").append(Format.speed(lastTotalRx))
    }

    private fun updateSummary() {
        val n = Prefs.blockedCount(this)
        val prefix = getString(R.string.summary_prefix) + " "
        val suffix = " " + getString(R.string.summary_suffix)
        val num = n.toString()
        val s = SpannableString(prefix + num + suffix)
        val start = prefix.length
        val end = start + num.length
        s.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(this, R.color.accent)),
            start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        s.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        b.tvSummary.text = s
    }

    private fun updateVpnUi() {
        val on = Prefs.isProtectionOn(this)
        b.tvVpn.text = getString(if (on) R.string.vpn_on else R.string.vpn_off)
        b.btnVpn.text = getString(if (on) R.string.vpn_turn_off else R.string.vpn_turn_on)
        b.vpnDot.background = ContextCompat.getDrawable(
            this, if (on) R.drawable.bg_dot_green else R.drawable.bg_dot_gray
        )
    }

    override fun onToggleBlock(pkg: String, block: Boolean) {
        Prefs.setBlocked(this, pkg, block)
        AppRepository.refreshBlockedFlags(this)
        val label = AppRepository.find(this, pkg)?.label ?: pkg
        Toast.makeText(
            this,
            getString(if (block) R.string.toast_blocked else R.string.toast_unblocked, label),
            Toast.LENGTH_SHORT
        ).show()
        if (block) vpn.enable() else FirewallVpnService.apply(this)
        refreshUi()
    }

    override fun refreshUi() {
        AppRepository.refreshBlockedFlags(this)
        updateSummary()
        updateVpnUi()
        applySortAndSubmit()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionBanner()
        updateSummary()
        updateVpnUi()
        if (apps.isNotEmpty()) {
            applySortAndSubmit()
            measureAndRender()
        }
        restartTicker()
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun restartTicker() {
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, Prefs.interval(this))
    }

    private fun intervalLabel(ms: Long): String = when (ms) {
        1000L -> "1 秒"
        5000L -> "5 秒"
        else -> "2 秒"
    }
}

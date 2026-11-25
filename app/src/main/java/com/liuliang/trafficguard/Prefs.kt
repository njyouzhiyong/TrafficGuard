package com.liuliang.trafficguard

import android.content.Context

/** 轻量本地配置：禁止联网名单、筛选条件、刷新间隔、防护开关。 */
object Prefs {
    private const val FILE = "traffic_guard"
    private const val KEY_BLOCKED = "blocked_packages"
    private const val KEY_SHOW_SYSTEM = "show_system"
    private const val KEY_ONLY_ACTIVE = "only_active"
    private const val KEY_INTERVAL = "interval_ms"
    private const val KEY_PROTECTION = "protection_on"
    private const val KEY_VPN_ACTIVE = "vpn_active"

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun blocked(c: Context): MutableSet<String> =
        (sp(c).getStringSet(KEY_BLOCKED, emptySet()) ?: emptySet()).toMutableSet()

    fun blockedCount(c: Context): Int = blocked(c).size

    fun setBlocked(c: Context, pkg: String, value: Boolean) {
        val set = blocked(c)
        if (value) set.add(pkg) else set.remove(pkg)
        sp(c).edit().putStringSet(KEY_BLOCKED, HashSet(set)).apply()
    }

    fun clearBlocked(c: Context) {
        sp(c).edit().putStringSet(KEY_BLOCKED, emptySet()).apply()
    }

    fun isShowSystem(c: Context): Boolean = sp(c).getBoolean(KEY_SHOW_SYSTEM, true)
    fun setShowSystem(c: Context, v: Boolean) = sp(c).edit().putBoolean(KEY_SHOW_SYSTEM, v).apply()

    fun isOnlyActive(c: Context): Boolean = sp(c).getBoolean(KEY_ONLY_ACTIVE, false)
    fun setOnlyActive(c: Context, v: Boolean) = sp(c).edit().putBoolean(KEY_ONLY_ACTIVE, v).apply()

    fun interval(c: Context): Long = sp(c).getLong(KEY_INTERVAL, 2000L)
    fun setInterval(c: Context, v: Long) = sp(c).edit().putLong(KEY_INTERVAL, v).apply()

    /** 用户是否打开了「防护」总开关（与 VPN 连接实际是否存活分离）。 */
    fun isProtectionOn(c: Context): Boolean = sp(c).getBoolean(KEY_PROTECTION, false)
    fun setProtectionOn(c: Context, v: Boolean) = sp(c).edit().putBoolean(KEY_PROTECTION, v).apply()

    fun isVpnActive(c: Context): Boolean = sp(c).getBoolean(KEY_VPN_ACTIVE, false)
    fun setVpnActive(c: Context, v: Boolean) = sp(c).edit().putBoolean(KEY_VPN_ACTIVE, v).apply()
}

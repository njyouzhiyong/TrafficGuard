package com.liuliang.trafficguard

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream

/**
 * 无 Root 的按应用联网管控。
 *
 * 原理：建立一个本地 VPN，把「允许联网」的应用全部 addDisallowedApplication 让它们直连真实网络；
 * 只有被禁止联网的应用会把包送进 tun 接口，本服务只读取并丢弃这些包、不写回、不转发，
 * 因此这些应用无法收发任何数据。全程不连接外部服务器，也不上传任何流量。
 */
class FirewallVpnService : VpnService() {

    companion object {
        private const val TAG = "TrafficGuardVpn"
        const val ACTION_APPLY = "com.liuliang.trafficguard.APPLY"
        const val ACTION_STOP = "com.liuliang.trafficguard.STOP"

        @Volatile
        var running = false
            private set

        fun apply(c: Context) {
            try {
                c.startService(Intent(c, FirewallVpnService::class.java).setAction(ACTION_APPLY))
            } catch (e: Exception) {
                Log.w(TAG, "apply failed: ${e.message}")
            }
        }

        fun stop(c: Context) {
            try {
                c.startService(Intent(c, FirewallVpnService::class.java).setAction(ACTION_STOP))
            } catch (e: Exception) {
                Log.w(TAG, "stop failed: ${e.message}")
            }
        }
    }

    private var tunnel: ParcelFileDescriptor? = null
    private var drainThread: Thread? = null

    @Volatile
    private var draining = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            closeTunnel()
            running = false
            Prefs.setVpnActive(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        applyRules()
        return START_STICKY
    }

    private fun applyRules() {
        if (!Prefs.isProtectionOn(this)) {
            closeTunnel()
            running = false
            Prefs.setVpnActive(this, false)
            return
        }

        val blocked = Prefs.blocked(this)
        var fd: ParcelFileDescriptor? = null
        try {
            fd = buildTunnel(blocked, true).establish()
        } catch (e: Exception) {
            Log.w(TAG, "IPv6 tunnel failed, fallback to IPv4: ${e.message}")
        }
        if (fd == null) {
            try {
                fd = buildTunnel(blocked, false).establish()
            } catch (e: Exception) {
                Log.e(TAG, "establish failed: ${e.message}")
            }
        }

        if (fd == null) {
            running = false
            Prefs.setVpnActive(this, false)
            return
        }

        closeTunnel()
        tunnel = fd
        startDrain(fd)
        running = true
        Prefs.setVpnActive(this, true)
        Log.i(TAG, "VPN applied, blocked apps = ${blocked.size}")
    }

    private fun buildTunnel(blocked: Set<String>, ipv6: Boolean): Builder {
        val builder = Builder()
            .setSession(getString(R.string.vpn_session))
            .setMtu(1500)
            .addAddress("10.10.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")

        if (ipv6) {
            builder.addAddress("fd00:1:2:3::1", 128)
            builder.addRoute("::", 0)
        }

        // 自身必须直连，否则本应用也会被自己建立的 VPN 影响
        try {
            builder.addDisallowedApplication(packageName)
        } catch (e: Exception) {
            Log.w(TAG, "cannot exclude self: ${e.message}")
        }

        val apps: List<ApplicationInfo> = try {
            packageManager.getInstalledApplications(0)
        } catch (e: Exception) {
            emptyList()
        }
        for (ai in apps) {
            val pkg = ai.packageName
            if (pkg == packageName || blocked.contains(pkg)) continue
            try {
                builder.addDisallowedApplication(pkg)
            } catch (e: Exception) {
                // 个别系统包无法加入排除列表，忽略即可
            }
        }
        return builder
    }

    /** 读取 tun 输出并直接丢弃：被禁止的应用既发不出去，也收不到回包。 */
    private fun startDrain(fd: ParcelFileDescriptor) {
        draining = true
        drainThread = Thread {
            val buffer = ByteArray(32767)
            try {
                FileInputStream(fd.fileDescriptor).use { input ->
                    while (draining && !Thread.currentThread().isInterrupted) {
                        val read = input.read(buffer)
                        if (read < 0) break
                    }
                }
            } catch (e: Exception) {
                // 接口关闭时正常退出
            }
        }.apply {
            name = "traffic-guard-drain"
            isDaemon = true
            start()
        }
    }

    private fun closeTunnel() {
        draining = false
        try {
            drainThread?.interrupt()
        } catch (ignored: Exception) {
        }
        drainThread = null
        try {
            tunnel?.close()
        } catch (ignored: Exception) {
        }
        tunnel = null
    }

    override fun onRevoke() {
        closeTunnel()
        running = false
        Prefs.setVpnActive(this, false)
        Prefs.setProtectionOn(this, false)
        super.onRevoke()
    }

    override fun onDestroy() {
        closeTunnel()
        running = false
        Prefs.setVpnActive(this, false)
        super.onDestroy()
    }
}

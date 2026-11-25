package com.liuliang.trafficguard

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import java.util.Calendar

/**
 * 通过系统 NetworkStatsManager 读取「按 UID 累计字节数」，两次采样做差得到实时上/下行速率。
 * 这是无 Root、无抓包权限时 Android 官方支持的按应用流量统计方式。
 */
class TrafficRepository(private val ctx: Context) {

    data class Counters(val rx: Long = 0L, val tx: Long = 0L)
    data class Measure(
        val totalRx: Long,
        val totalTx: Long,
        val ok: Boolean,
        val fallback: Boolean = false
    )

    private val manager: NetworkStatsManager? =
        ctx.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager

    private var previous: Map<Int, Counters> = emptyMap()
    private var previousAt = 0L
    private var usedFallback = false

    fun hasUsageAccess(): Boolean = try {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        if (ops == null) false
        else {
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ops.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                ops.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        }
    } catch (e: Exception) {
        false
    }

    /** 在后台线程调用：更新每个 AppEntry 的累计流量与瞬时速率，返回全设备总速率。 */
    fun measure(apps: List<AppEntry>): Measure {
        val now = SystemClock.elapsedRealtime()
        val uids = HashSet<Int>(apps.size)
        apps.forEach { uids.add(it.uid) }
        val current = try {
            readCounters(uids)
        } catch (e: Exception) {
            usedFallback = false
            emptyMap()
        }
        val dt = now - previousAt
        var totalRx = 0L
        var totalTx = 0L
        val countedUids = HashSet<Int>()

        if (previousAt > 0L && dt > 0L) {
            for (app in apps) {
                val c = current[app.uid] ?: Counters()
                val p = previous[app.uid] ?: Counters()
                app.rxBytes = c.rx
                app.txBytes = c.tx
                app.rxSpeed = (c.rx - p.rx).coerceAtLeast(0L) * 1000L / dt
                app.txSpeed = (c.tx - p.tx).coerceAtLeast(0L) * 1000L / dt
                // 共享 UID 的多个包只计入总量一次
                if (countedUids.add(app.uid)) {
                    totalRx += app.rxSpeed
                    totalTx += app.txSpeed
                }
            }
        } else {
            for (app in apps) {
                val c = current[app.uid] ?: Counters()
                app.rxBytes = c.rx
                app.txBytes = c.tx
                app.rxSpeed = 0L
                app.txSpeed = 0L
            }
        }

        previous = current
        previousAt = now
        return Measure(totalRx, totalTx, manager != null && hasUsageAccess(), usedFallback)
    }

    /** 一次查询拿到全部 UID 的累计值；部分 ROM 上 querySummary 无数据，则逐 UID 回退。 */
    private fun readCounters(uids: Set<Int>): Map<Int, Counters> {
        usedFallback = false
        // 未授权时标准接口必然抛 SecurityException；不要再逐 UID 发起几百次无效调用
        if (!hasUsageAccess()) {
            val compat = FallbackTraffic.read(uids)
            if (compat.isNotEmpty()) {
                usedFallback = true
                return compat.mapValues { Counters(it.value.rx, it.value.tx) }
            }
            return emptyMap()
        }
        val m = manager ?: return emptyMap()
        val out = HashMap<Int, Counters>()
        val start = dayStart()
        val end = System.currentTimeMillis() + 60_000L

        try {
            merge(m.querySummary(ConnectivityManager.TYPE_WIFI, null, start, end), out)
        } catch (e: Exception) {
            // 某些设备对 WiFi 需要空的 subscriberId
            try {
                merge(m.querySummary(ConnectivityManager.TYPE_WIFI, "", start, end), out)
            } catch (ignored: Exception) {
            }
        }

        val sid = subscriberId()
        try {
            merge(m.querySummary(ConnectivityManager.TYPE_MOBILE, sid, start, end), out)
        } catch (e: Exception) {
            try {
                merge(m.querySummary(ConnectivityManager.TYPE_MOBILE, "", start, end), out)
            } catch (ignored: Exception) {
            }
        }

        if (out.isEmpty() && uids.isNotEmpty()) {
            for (uid in uids) {
                try {
                    merge(m.queryDetailsForUid(ConnectivityManager.TYPE_WIFI, null, start, end, uid), out)
                } catch (ignored: Exception) {
                }
                try {
                    merge(m.queryDetailsForUid(ConnectivityManager.TYPE_MOBILE, sid, start, end, uid), out)
                } catch (ignored: Exception) {
                }
            }
        }

        // 标准通道拿不到数据（例如未授权或定制 ROM），改用无需权限的兼容通道
        if (out.isEmpty()) {
            val compat = FallbackTraffic.read(uids)
            if (compat.isNotEmpty()) {
                usedFallback = true
                return compat.mapValues { Counters(it.value.rx, it.value.tx) }
            }
        }
        return out
    }

    private fun merge(stats: NetworkStats?, out: MutableMap<Int, Counters>) {
        if (stats == null) return
        try {
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                val uid = bucket.uid
                if (uid < 0) continue
                val old = out[uid] ?: Counters()
                out[uid] = Counters(old.rx + bucket.rxBytes, old.tx + bucket.txBytes)
            }
        } catch (e: Exception) {
            // 单个网络类型读取失败不影响其它类型
        } finally {
            try {
                stats.close()
            } catch (ignored: Exception) {
            }
        }
    }

    private fun subscriberId(): String? = try {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            @Suppress("MissingPermission", "DEPRECATION")
            tm?.subscriberId
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }

    private fun dayStart(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}

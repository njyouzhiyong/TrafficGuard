package com.liuliang.trafficguard

import android.net.TrafficStats
import android.os.Build
import java.io.File

/**
 * 兼容模式统计：当系统未授予使用情况访问权限、或 NetworkStatsManager 在该 ROM 上不返回数据时，
 * 尝试两条无需权限的只读通道。Android 9+ 通常不可用，失败时返回空表，不影响主流程。
 */
object FallbackTraffic {

    data class Counters(val rx: Long, val tx: Long)

    fun read(uids: Set<Int>): Map<Int, Counters> {
        val fromProc = readQtaguid()
        if (fromProc.isNotEmpty()) return fromProc

        // Android 9 起 TrafficStats 只允许查询自身 UID，逐个遍历只会浪费时间
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return emptyMap()

        val out = HashMap<Int, Counters>()
        for (uid in uids) {
            try {
                val rx = TrafficStats.getUidRxBytes(uid)
                val tx = TrafficStats.getUidTxBytes(uid)
                if (rx >= 0L && tx >= 0L) {
                    out[uid] = Counters(rx, tx)
                }
            } catch (e: Exception) {
                // 该 UID 不可读，跳过
            }
        }
        return out
    }

    /** /proc/net/xt_qtaguid/stats：idx iface tag uid cnt_set rx_bytes rx_pkts tx_bytes tx_pkts ... */
    private fun readQtaguid(): Map<Int, Counters> {
        val out = HashMap<Int, Counters>()
        try {
            File("/proc/net/xt_qtaguid/stats").forEachLine { line ->
                val f = line.trim().split(Regex("\\s+"))
                if (f.size < 9) return@forEachLine
                val uid = f[3].toIntOrNull() ?: return@forEachLine
                val cntSet = f[4].toIntOrNull() ?: return@forEachLine
                if (cntSet != 0) return@forEachLine
                val rx = f[5].toLongOrNull() ?: return@forEachLine
                val tx = f[7].toLongOrNull() ?: return@forEachLine
                val old = out[uid] ?: Counters(0L, 0L)
                out[uid] = Counters(old.rx + rx, old.tx + tx)
            }
        } catch (e: Exception) {
            return emptyMap()
        }
        return out
    }
}

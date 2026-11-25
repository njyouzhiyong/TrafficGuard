package com.liuliang.trafficguard

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat

data class AppEntry(
    val pkg: String,
    val label: String,
    val uid: Int,
    val icon: Drawable,
    val isSystem: Boolean
) {
    var blocked: Boolean = false
    var rxBytes: Long = 0L
    var txBytes: Long = 0L
    var rxSpeed: Long = 0L
    var txSpeed: Long = 0L
}

/** 已安装应用列表与其图标在进程内缓存，避免每次刷新重复加载。 */
object AppRepository {
    @Volatile
    private var cached: List<AppEntry>? = null
    private val lock = Any()

    fun load(c: Context, force: Boolean = false): List<AppEntry> {
        if (!force) cached?.let { return it }
        return synchronized(lock) {
            val existing = if (!force) cached else null
            if (existing != null) return@synchronized existing
            val appContext = c.applicationContext
            val pm = appContext.packageManager
            val apps: List<ApplicationInfo> = try {
                pm.getInstalledApplications(0)
            } catch (e: Exception) {
                emptyList()
            }
            val blocked = Prefs.blocked(appContext)
            val placeholder: Drawable = ContextCompat.getDrawable(
                appContext, R.drawable.ic_app_placeholder
            ) ?: ColorDrawable(Color.DKGRAY)

            val list = ArrayList<AppEntry>(apps.size)
            for (ai in apps) {
                val label = try {
                    ai.loadLabel(pm).toString()
                } catch (e: Exception) {
                    ai.packageName
                }
                val icon = try {
                    ai.loadIcon(pm) ?: placeholder
                } catch (e: Exception) {
                    placeholder
                }
                val entry = AppEntry(
                    pkg = ai.packageName,
                    label = if (label.isBlank()) ai.packageName else label,
                    uid = ai.uid,
                    icon = icon,
                    isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
                entry.blocked = blocked.contains(ai.packageName)
                list.add(entry)
            }
            list.sortBy { it.label }
            cached = list
            list
        }
    }

    /** 禁止名单变化后同步内存中的标记。 */
    fun refreshBlockedFlags(c: Context) {
        val blocked = Prefs.blocked(c.applicationContext)
        cached?.forEach { it.blocked = blocked.contains(it.pkg) }
    }

    fun find(c: Context, pkg: String): AppEntry? = load(c).firstOrNull { it.pkg == pkg }

    fun clearCache() {
        synchronized(lock) { cached = null }
    }
}

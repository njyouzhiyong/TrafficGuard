package com.liuliang.trafficguard

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import java.util.concurrent.TimeUnit

/** 「关闭应用」相关能力。无 Root 时 Android 不允许第三方直接 force-stop 其它应用。 */
object AppControl {

    @Volatile
    private var rootChecked: Boolean? = null

    fun killBackground(c: Context, pkg: String) {
        try {
            val am = c.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.killBackgroundProcesses(pkg)
        } catch (e: Exception) {
            // 权限或系统限制，忽略即可
        }
    }

    fun openAppDetails(c: Context, pkg: String) {
        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$pkg")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            c.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(c, R.string.toast_force_stop, Toast.LENGTH_SHORT).show()
        }
    }

    fun isRooted(): Boolean {
        rootChecked?.let { return it }
        val result = try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val out = p.inputStream.bufferedReader().use { it.readText() }
            p.waitFor(2, TimeUnit.SECONDS)
            out.contains("uid=0")
        } catch (e: Exception) {
            false
        }
        rootChecked = result
        return result
    }

    fun forceStopWithRoot(pkg: String): Boolean {
        // 只保留包名合法字符，避免命令注入
        val safe = pkg.replace(Regex("[^a-zA-Z0-9_.]"), "")
        if (safe.isEmpty()) return false
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec("su")
            process.outputStream.use { os ->
                os.write(("am force-stop $safe\nexit\n").toByteArray())
                os.flush()
            }
            process.waitFor(3, TimeUnit.SECONDS)
            process.exitValue() == 0
        } catch (e: Exception) {
            false
        } finally {
            try {
                process?.destroy()
            } catch (ignored: Exception) {
            }
        }
    }
}

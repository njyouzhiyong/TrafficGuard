package com.liuliang.trafficguard

import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import java.util.Locale

object Format {
    private val UNIT_COLOR: Int = 0xFF8B97A9.toInt()

    /** 参考图风格：数字大而醒目，单位小而灰，例如 24.6 KB/s。 */
    fun speed(bytesPerSecond: Long): CharSequence {
        val kb = bytesPerSecond.coerceAtLeast(0L) / 1024.0
        val (num, unit) = if (kb >= 1024.0) {
            String.format(Locale.US, "%.1f", kb / 1024.0) to " MB/s"
        } else {
            String.format(Locale.US, "%.1f", kb) to " KB/s"
        }
        val s = SpannableString(num + unit)
        s.setSpan(StyleSpan(Typeface.BOLD), 0, num.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(AbsoluteSizeSpan(10, true), num.length, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(ForegroundColorSpan(UNIT_COLOR), num.length, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return s
    }

    fun bytes(v: Long): String {
        val b = v.coerceAtLeast(0L)
        if (b < 1024L) return "$b B"
        val kb = b / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
        return String.format(Locale.US, "%.2f GB", mb / 1024.0)
    }
}

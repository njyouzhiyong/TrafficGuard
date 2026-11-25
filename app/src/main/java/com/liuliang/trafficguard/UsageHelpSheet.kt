package com.liuliang.trafficguard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.liuliang.trafficguard.databinding.DialogUsageHelpBinding

interface UsageHelpActions {
    fun openUsageAccessList()
    fun recheckUsageAccess()
}

/** 可滚动的授权帮助面板：小屏也能看到完整 ADB 命令与所有按钮。 */
class UsageHelpSheet : BottomSheetDialogFragment() {

    companion object {
        private const val TAG = "UsageHelpSheet"
        fun show(manager: FragmentManager) = UsageHelpSheet().show(manager, TAG)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val b = DialogUsageHelpBinding.inflate(inflater, container, false)
        val ctx = requireContext()

        b.btnCopyCmd.setOnClickListener {
            val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(
                ClipData.newPlainText("adb", getString(R.string.help_adb_cmd))
            )
            Toast.makeText(ctx, R.string.help_copied, Toast.LENGTH_LONG).show()
        }
        b.btnOpenAppInfo.setOnClickListener {
            AppControl.openAppDetails(ctx, ctx.packageName)
        }
        b.btnOpenList.setOnClickListener {
            (activity as? UsageHelpActions)?.openUsageAccessList()
            dismiss()
        }
        b.btnRecheck.setOnClickListener {
            (activity as? UsageHelpActions)?.recheckUsageAccess()
            dismiss()
        }
        return b.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundColor(Color.TRANSPARENT)
    }
}

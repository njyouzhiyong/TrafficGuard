package com.liuliang.trafficguard

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.liuliang.trafficguard.databinding.SheetAppActionsBinding

/** 点击应用行后的操作面板：禁止联网 / 关闭应用 / 应用信息。 */
class AppActionSheet : BottomSheetDialogFragment() {

    companion object {
        private const val KEY_PKG = "pkg"
        private const val KEY_COMPAT = "compat"
        private const val TAG = "AppActionSheet"

        fun show(
            manager: androidx.fragment.app.FragmentManager,
            pkg: String,
            compat: Boolean = false
        ) {
            AppActionSheet().apply {
                arguments = Bundle().apply {
                    putString(KEY_PKG, pkg)
                    putBoolean(KEY_COMPAT, compat)
                }
            }.show(manager, TAG)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val b = SheetAppActionsBinding.inflate(inflater, container, false)
        val pkg = arguments?.getString(KEY_PKG).orEmpty()
        val approx = arguments?.getBoolean(KEY_COMPAT, false) ?: false
        val app = AppRepository.find(requireContext(), pkg) ?: return b.root
        val ctx = requireContext()

        b.ivIcon.setImageDrawable(app.icon)
        b.tvName.text = app.label
        b.tvPackage.text = app.pkg
        b.tvUid.text = "${getString(R.string.uid)} ${app.uid}" +
                (if (app.isSystem) " · ${getString(R.string.system_app)}" else "") +
                (if (approx) " · ${getString(R.string.compat_detail)}" else "")

        val prefix = if (approx) "≈ " else ""
        b.tvTodayUp.text = prefix + Format.bytes(app.txBytes)
        b.tvTodayDown.text = prefix + Format.bytes(app.rxBytes)
        b.tvTodayTotal.text = prefix + Format.bytes(app.txBytes + app.rxBytes)
        b.tvLiveUp.text = "${getString(R.string.col_up)}  ${Format.speed(app.txSpeed)}"
        b.tvLiveDown.text = "${getString(R.string.col_down)}  ${Format.speed(app.rxSpeed)}"

        val block = !app.blocked
        b.btnBlock.text = getString(if (block) R.string.action_block else R.string.action_unblock)
        b.btnBlock.background = ContextCompat.getDrawable(
            ctx, if (block) R.drawable.bg_row_danger else R.drawable.bg_row
        )
        b.btnBlock.setTextColor(ContextCompat.getColor(ctx, if (block) R.color.danger else R.color.success))
        b.btnBlock.setOnClickListener {
            (activity as? AppActionHost)?.onToggleBlock(app.pkg, block)
            dismiss()
        }

        b.btnKill.setOnClickListener {
            AppControl.killBackground(ctx, app.pkg)
            Toast.makeText(ctx, getString(R.string.toast_killed, app.label), Toast.LENGTH_SHORT).show()
        }

        b.btnForceStop.setOnClickListener {
            AppControl.openAppDetails(ctx, app.pkg)
            Toast.makeText(ctx, R.string.toast_force_stop, Toast.LENGTH_LONG).show()
        }

        b.btnForceStopRoot.setOnClickListener {
            Thread {
                val ok = AppControl.forceStopWithRoot(app.pkg)
                activity?.runOnUiThread {
                    Toast.makeText(
                        ctx,
                        if (ok) getString(R.string.toast_root_done, app.label)
                        else getString(R.string.toast_root_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }.start()
        }

        b.btnAppInfo.setOnClickListener { AppControl.openAppDetails(ctx, app.pkg) }

        Thread {
            val rooted = AppControl.isRooted()
            activity?.runOnUiThread {
                if (isAdded && view != null) {
                    b.btnForceStopRoot.visibility = if (rooted) View.VISIBLE else View.GONE
                }
            }
        }.start()

        return b.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundColor(Color.TRANSPARENT)
    }
}

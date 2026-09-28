@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")

package com.mt.customDialog

import android.app.Dialog
import android.app.DialogFragment
import android.content.DialogInterface
import android.os.Bundle

class PreviewDialogFragment : DialogFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = arguments?.toDialogConfig()?.cancelable ?: true
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val config = arguments?.toDialogConfig() ?: DialogConfig()
        return NativeDialogs.create(activity, config, ::reportResult).apply { setOnCancelListener(null)}
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        reportResult("Dismissed")
    }

    private fun reportResult(result: String) {(activity as? MainActivity)?.onDialogResult(result)}

    companion object {
        const val TAG = "dialog_preview"

        fun newInstance(config: DialogConfig): PreviewDialogFragment = PreviewDialogFragment().apply { arguments = config.toBundle()}
    }
}
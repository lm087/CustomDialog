@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")

package com.mt.customDialog

import android.app.Dialog
import android.app.DialogFragment
import android.content.DialogInterface
import android.os.Bundle
import android.widget.NumberPicker

class PreviewDialogFragment : DialogFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = arguments?.toDialogConfig()?.cancelable ?: true
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val config = arguments?.toDialogConfig() ?: DialogConfig()
        return NativeDialogs.create(activity, config, ::reportResult).apply { setOnCancelListener(null) }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        if (savedInstanceState?.containsKey("numberValue") == true) dialog?.findViewById<NumberPicker>(R.id.dialog_number_picker)?.value = savedInstanceState.getInt("numberValue")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        dialog?.findViewById<NumberPicker>(R.id.dialog_number_picker)?.let {
            it.clearFocus()
            outState.putInt("numberValue", it.value)
        }
        super.onSaveInstanceState(outState)
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
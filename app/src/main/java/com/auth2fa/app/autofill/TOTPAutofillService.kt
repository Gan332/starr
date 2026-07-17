package com.auth2fa.app.autofill

import android.app.assist.AssistStructure
import android.app.assist.AssistStructure.ViewNode
import android.content.Context
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.*
import android.util.Log
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.auth2fa.app.R

/**
 * Android Autofill Service that provides TOTP codes for 2FA fields.
 *
 * This service responds to autofill requests by detecting fields
 * that look like they're asking for a 2FA code and filling them
 * with the most recently copied code from the clipboard.
 */
@RequiresApi(Build.VERSION_CODES.O)
class TOTPAutofillService : AutofillService() {

    companion object {
        private const val TAG = "TOTPAutofill"
    }

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val context = this
        val structure = request.fillContexts.lastOrNull()?.structure ?: run {
            callback.onSuccess(null)
            return
        }

        val autofillIds = mutableListOf<AutofillId>()
        parseStructure(structure.rootViewNode, autofillIds)

        if (autofillIds.isEmpty()) {
            callback.onSuccess(null)
            return
        }

        // Build fill response: populate with the first matched field
        val autofillId = autofillIds.first()

        // Try to get the most recently copied TOTP code
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
            as? android.content.ClipboardManager
        val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""

        // Check if clipboard text looks like a TOTP code (6-8 digits or 5-char Steam)
        val isLikelyTotp = clipText.matches(Regex("^[0-9]{6,8}$"))
        val isLikelySteam = clipText.matches(Regex("^[A-Z2-7]{5}$"))
        val fillValue = when {
            isLikelyTotp || isLikelySteam -> clipText
            else -> "" // No valid code in clipboard
        }

        val filledAutofillId = autofillId

        val dataset = Dataset.Builder(
            RemoteViews(packageName, R.layout.autofill_dropdown_item)
        ).apply {
            setValue(filledAutofillId, AutofillValue.forText(fillValue))
            // Only show the suggestion if we have a valid code
            if (fillValue.isNotEmpty()) {
                setAuthentication(filledAutofillId, null)
            }
        }.build()

        val response = FillResponse.Builder()
            .addDataset(dataset)
            .build()

        callback.onSuccess(response)
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        // No-op: we don't save any autofill data
        callback.onSuccess()
    }

    private fun parseStructure(node: ViewNode, results: MutableList<AutofillId>) {
        // Check if this view is likely a 2FA input field
        val idEntry = node.idEntry?.lowercase() ?: ""
        val hint = node.hint?.lowercase() ?: ""
        val autofillHints = node.autofillHints?.map { it.lowercase() } ?: emptyList()

        val isTotpField = autofillHints.any { hint ->
            hint.contains("otp") || hint.contains("2fa") ||
                hint.contains("token") || hint.contains("onetimecode")
        } || idEntry.contains("otp") || idEntry.contains("2fa") ||
            idEntry.contains("token") || idEntry.contains("code") ||
            idEntry.contains("totp") || idEntry.contains("auth") ||
            hint.contains("otp") || hint.contains("2fa") ||
            hint.contains("token") || hint.contains("verification") ||
            hint.contains("authenticator")

        if (isTotpField && node.autofillId != null &&
            node.autofillType == View.AUTOFILL_TYPE_TEXT
        ) {
            results.add(node.autofillId)
        }

        // Recurse into children
        for (i in 0 until node.childCount) {
            parseStructure(node.getChildAt(i), results)
        }
    }
}

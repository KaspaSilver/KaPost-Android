package com.kachat.app.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.kachat.app.R
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors

/**
 * Reporting a post or room message (iOS 9e22075 `ContentReport`): the report goes to support by
 * email, where a person reads it, in whatever mail app takes it (iOS e5a9b11). If nothing can open
 * it, the report is copied and [ContentReportFallback] says where to send it.
 */
class ContentReport(val body: String) {
    /** Opens the email, or copies the report and returns false, for the caller to show
     *  [ContentReportFallback]. */
    fun send(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = android.net.Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, "KaPost report")
            putExtra(Intent.EXTRA_TEXT, body)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: android.content.ActivityNotFoundException) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("KaPost report", body))
            false
        }
    }

    companion object {
        const val SUPPORT_EMAIL = "kaspasilver@gmail.com"
    }
}

/** The alert for a report [ContentReport.send] couldn't hand to a mail app. */
@Composable
fun ContentReportFallback(onDismiss: () -> Unit) {
    val colors = LocalAppColors.current
    com.kachat.app.ui.theme.IosAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("Report Copied", color = colors.textPrimary) },
        text = {
            Text(
                "No mail app could open it. Paste the report into an email to ${ContentReport.SUPPORT_EMAIL}.",
                color = colors.textSecondary,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok), color = KaspaTeal, fontWeight = FontWeight.Bold)
            }
        },
    )
}

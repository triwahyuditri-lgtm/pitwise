package com.example.pitwise.domain.reporting

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates Android share Intents for sharing calculation results
 * as images (Bitmap) to WhatsApp, Email, etc.
 */
@Singleton
class ShareIntentHelper @Inject constructor() {

    /**
     * Creates a share Intent with a Bitmap image and optional text caption.
     *
     * @param context Android context
     * @param bitmap The result card bitmap to share
     * @param caption Optional text to accompany the image
     * @return Intent ready to be used with startActivity
     */
    fun createShareIntent(
        context: Context,
        bitmap: Bitmap,
        caption: String = ""
    ): Intent {
        // Save bitmap to cache directory
        val cachePath = File(context.cacheDir, "shared_images")
        cachePath.mkdirs()

        val file = File(cachePath, "pitwise_result_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }

        val contentUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            if (caption.isNotBlank()) {
                putExtra(Intent.EXTRA_TEXT, caption)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Creates a chooser Intent that lets the user pick which app to share to.
     */
    fun createChooserIntent(
        context: Context,
        bitmap: Bitmap,
        caption: String = "",
        chooserTitle: String = "Share PITWISE Result"
    ): Intent {
        val shareIntent = createShareIntent(context, bitmap, caption)
        return Intent.createChooser(shareIntent, chooserTitle)
    }
}

package com.example.pitwise.domain.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of rendering a PDF page to a bitmap.
 */
data class PdfMapData(
    val bitmap: Bitmap,
    val pageWidth: Int,
    val pageHeight: Int
)

/**
 * Renders a PDF file to a Bitmap using Android's native PdfRenderer.
 *
 * - Opens via SAF URI
 * - Renders first page at configurable DPI
 * - Runs entirely on Dispatchers.IO
 * - Supports large PDFs (up to 100MB)
 */
@Singleton
class PdfRendererEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Render the first page of a PDF from the given URI.
     *
     * @param uri SAF content URI
     * @param dpi Target render DPI (default 150 for good quality without excessive memory)
     * @return PdfMapData containing the rendered bitmap, or null on failure
     */
    suspend fun renderFirstPage(uri: Uri, dpi: Int = 150): PdfMapData? = withContext(Dispatchers.IO) {
        var renderer: PdfRenderer? = null
        try {
            // Copy URI content to a temp file (PdfRenderer needs a seekable file descriptor)
            val tempFile = File(context.cacheDir, "temp_pdf_render.pdf")
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream, bufferSize = 8192)
                }
            } ?: return@withContext null

            val fileDescriptor = android.os.ParcelFileDescriptor.open(
                tempFile,
                android.os.ParcelFileDescriptor.MODE_READ_ONLY
            )

            renderer = PdfRenderer(fileDescriptor)

            if (renderer.pageCount == 0) {
                renderer.close()
                fileDescriptor.close()
                return@withContext null
            }

            val page = renderer.openPage(0)

            // Calculate bitmap dimensions based on DPI
            // PDF points are 1/72 inch, so width in pixels = (widthPoints / 72) * dpi
            val scale = dpi / 72f
            val bitmapWidth = (page.width * scale).toInt()
            val bitmapHeight = (page.height * scale).toInt()

            val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
            // Fill with white background
            bitmap.eraseColor(android.graphics.Color.WHITE)

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            val result = PdfMapData(bitmap, bitmapWidth, bitmapHeight)

            page.close()
            renderer.close()
            fileDescriptor.close()

            // Clean up temp file
            tempFile.delete()

            result
        } catch (e: Exception) {
            renderer?.close()
            null
        }
    }
}

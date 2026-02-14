package com.example.pitwise.domain.reporting

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.example.pitwise.domain.model.ShareCardData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates a shareable Bitmap card with calculation results.
 * Uses Android Canvas API — no external libraries needed.
 *
 * Card layout:
 *   ┌─────────────────────────────┐
 *   │  🟡 PITWISE                │
 *   │  [Title]                    │
 *   │─────────────────────────────│
 *   │  Label 1        Value 1    │
 *   │  Label 2        Value 2    │
 *   │  ...                       │
 *   │─────────────────────────────│
 *   │  🤖 AI Recommendation      │
 *   │  [recommendation text]      │
 *   │─────────────────────────────│
 *   │  Unit: XXX  Shift: X       │
 *   │  Timestamp                  │
 *   └─────────────────────────────┘
 */
@Singleton
class ShareCardGenerator @Inject constructor() {

    companion object {
        private const val CARD_WIDTH = 720
        private const val PADDING = 40
        private const val LINE_HEIGHT = 44
        private const val HEADER_HEIGHT = 100
        private const val FOOTER_HEIGHT = 80
        private const val SECTION_GAP = 20

        // Colors (PITWISE dark theme)
        private const val BG_COLOR = 0xFF231F0F.toInt()
        private const val PRIMARY_COLOR = 0xFFF9D006.toInt()
        private const val TEXT_COLOR = 0xFFFFFFFF.toInt()
        private const val TEXT_SECONDARY = 0xFF9CA3AF.toInt()
        private const val DIVIDER_COLOR = 0xFF3A3520.toInt()
    }

    fun generate(data: ShareCardData): Bitmap {
        // Calculate dynamic height
        val resultRows = data.resultLines.size
        val aiLines = data.aiRecommendation?.let { wrapText(it, 60).size } ?: 0
        val aiSectionHeight = if (aiLines > 0) SECTION_GAP + LINE_HEIGHT + (aiLines * LINE_HEIGHT) else 0

        val totalHeight = HEADER_HEIGHT +
                SECTION_GAP +
                (resultRows * LINE_HEIGHT) +
                aiSectionHeight +
                SECTION_GAP +
                FOOTER_HEIGHT +
                PADDING * 2

        val bitmap = Bitmap.createBitmap(CARD_WIDTH, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // ── Background ────────────────────────────────
        canvas.drawColor(BG_COLOR)

        // Draw rounded border
        val borderPaint = Paint().apply {
            color = PRIMARY_COLOR
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }
        canvas.drawRoundRect(
            RectF(4f, 4f, (CARD_WIDTH - 4).toFloat(), (totalHeight - 4).toFloat()),
            16f, 16f, borderPaint
        )

        var y = PADDING.toFloat()

        // ── Header ────────────────────────────────────
        val brandPaint = Paint().apply {
            color = PRIMARY_COLOR
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("⛏ PITWISE", PADDING.toFloat(), y + 24, brandPaint)

        val titlePaint = Paint().apply {
            color = TEXT_COLOR
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(data.title, PADDING.toFloat(), y + 68, titlePaint)
        y += HEADER_HEIGHT

        // ── Divider ───────────────────────────────────
        drawDivider(canvas, y)
        y += SECTION_GAP

        // ── Result Rows ───────────────────────────────
        val labelPaint = Paint().apply {
            color = TEXT_SECONDARY
            textSize = 20f
            isAntiAlias = true
        }
        val valuePaint = Paint().apply {
            color = TEXT_COLOR
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        for ((label, value) in data.resultLines) {
            canvas.drawText(label, PADDING.toFloat(), y + 30, labelPaint)
            val valueWidth = valuePaint.measureText(value)
            canvas.drawText(value, CARD_WIDTH - PADDING - valueWidth, y + 30, valuePaint)
            y += LINE_HEIGHT
        }

        // ── AI Recommendation ─────────────────────────
        if (data.aiRecommendation != null) {
            y += SECTION_GAP / 2
            drawDivider(canvas, y)
            y += SECTION_GAP

            val aiHeaderPaint = Paint().apply {
                color = PRIMARY_COLOR
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.drawText("🤖 AI Recommendation", PADDING.toFloat(), y + 24, aiHeaderPaint)
            y += LINE_HEIGHT

            val aiTextPaint = Paint().apply {
                color = TEXT_COLOR
                textSize = 18f
                isAntiAlias = true
            }
            val wrappedLines = wrapText(data.aiRecommendation, 60)
            for (line in wrappedLines) {
                canvas.drawText(line, PADDING.toFloat(), y + 24, aiTextPaint)
                y += LINE_HEIGHT
            }
        }

        // ── Footer ────────────────────────────────────
        y += SECTION_GAP / 2
        drawDivider(canvas, y)
        y += SECTION_GAP

        val footerPaint = Paint().apply {
            color = TEXT_SECONDARY
            textSize = 16f
            isAntiAlias = true
        }
        val footerText = "Unit: ${data.unitName}  |  Shift: ${data.shift}  |  ${data.timestamp}"
        canvas.drawText(footerText, PADDING.toFloat(), y + 20, footerPaint)

        return bitmap
    }

    private fun drawDivider(canvas: Canvas, y: Float) {
        val dividerPaint = Paint().apply {
            color = DIVIDER_COLOR
            strokeWidth = 1.5f
        }
        canvas.drawLine(
            PADDING.toFloat(), y,
            (CARD_WIDTH - PADDING).toFloat(), y,
            dividerPaint
        )
    }

    private fun wrapText(text: String, maxCharsPerLine: Int): List<String> {
        if (text.length <= maxCharsPerLine) return listOf(text)

        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            if (currentLine.length + word.length + 1 > maxCharsPerLine) {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                    currentLine = StringBuilder()
                }
            }
            if (currentLine.isNotEmpty()) currentLine.append(" ")
            currentLine.append(word)
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine.toString())

        return lines
    }
}

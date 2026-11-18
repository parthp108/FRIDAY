package com.health.friday.vision.grounding

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface

object SetOfMarksRenderer {

    private val boxColors = intArrayOf(
        Color.parseColor("#FF1744"), // Red
        Color.parseColor("#00E676"), // Green
        Color.parseColor("#2979FF"), // Blue
        Color.parseColor("#FF9100"), // Orange
        Color.parseColor("#D500F9"), // Purple
        Color.parseColor("#00E5FF")  // Cyan
    )

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val badgeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    /**
     * Draws numbered Set-of-Marks (SoM) bounding boxes and ID tags onto a mutable copy
     * of the screen bitmap for multimodal VLM grounding.
     */
    fun renderMarks(
        rawScreenshot: Bitmap,
        elements: Collection<GroundedUiElement>
    ): Bitmap {
        val annotated = rawScreenshot.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(annotated)

        for (element in elements) {
            // Only draw visual marks on interactive or primary content elements to avoid clutter
            if (!element.isClickable && !element.isEditable && !element.isScrollable) continue

            val color = boxColors[element.id % boxColors.size]
            borderPaint.color = color
            badgeFillPaint.color = color

            val b = element.boundsInScreen
            val rectF = RectF(
                b.left.toFloat(),
                b.top.toFloat(),
                b.right.toFloat(),
                b.bottom.toFloat()
            )
            canvas.drawRect(rectF, borderPaint)

            // Draw high-contrast numeric tag [ID] at the top-left of the bounding box
            val tagText = "${element.id}"
            val textBounds = Rect()
            textPaint.getTextBounds(tagText, 0, tagText.length, textBounds)

            val padH = 8f
            val padV = 6f
            val badgeWidth = textBounds.width() + padH * 2
            val badgeHeight = textBounds.height() + padV * 2

            val badgeLeft = b.left.toFloat().coerceAtLeast(0f)
            val badgeTop = (b.top.toFloat() - badgeHeight).coerceAtLeast(0f)

            val badgeRect = RectF(
                badgeLeft,
                badgeTop,
                badgeLeft + badgeWidth,
                badgeTop + badgeHeight
            )
            canvas.drawRoundRect(badgeRect, 6f, 6f, badgeFillPaint)
            canvas.drawText(
                tagText,
                badgeLeft + padH,
                badgeTop + badgeHeight - padV,
                textPaint
            )
        }

        return annotated
    }
}

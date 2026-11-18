package com.health.friday.agentic.ghost

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.os.Build
import java.util.Random
import kotlin.math.min

object GesturePhysicsEngine {

    private val random = Random()

    /**
     * Computes a human-like tap point inside [bounds] using a truncated 2D Gaussian
     * distribution centered at the element's geometric centroid.
     */
    fun sampleCentroidTapPoint(bounds: Rect): PointF {
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()

        val sigmaX = min(bounds.width() / 6f, 8f).coerceAtLeast(1f)
        val sigmaY = min(bounds.height() / 6f, 8f).coerceAtLeast(1f)

        val rawX = cx + (random.nextGaussian().toFloat() * sigmaX)
        val rawY = cy + (random.nextGaussian().toFloat() * sigmaY)

        val padX = min(4f, bounds.width() / 4f)
        val padY = min(4f, bounds.height() / 4f)

        val clampedX = rawX.coerceIn(bounds.left + padX, bounds.right - padX)
        val clampedY = rawY.coerceIn(bounds.top + padY, bounds.bottom - padY)

        return PointF(clampedX, clampedY)
    }

    /**
     * Builds a single tap or long-press GestureDescription.
     */
    fun buildTapGesture(bounds: Rect, isLongClick: Boolean = false): GestureDescription {
        val pt = sampleCentroidTapPoint(bounds)
        val path = Path().apply { moveTo(pt.x, pt.y) }
        val durationMs = if (isLongClick) 650L else (55L + random.nextInt(20))
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        return GestureDescription.Builder().addStroke(stroke).build()
    }

    /**
     * Builds a Cubic Bezier scroll gesture with an anti-fling friction tail on API 26+
     * so RecyclerViews stop cleanly without overshooting target elements.
     */
    fun buildBezierScrollGesture(
        screenWidth: Int,
        screenHeight: Int,
        direction: GuiActionType
    ): GestureDescription {
        val midX = screenWidth * 0.5f
        val midY = screenHeight * 0.5f

        val (p0, p3) = when (direction) {
            GuiActionType.SCROLL_DOWN -> {
                PointF(midX, screenHeight * 0.72f) to PointF(midX, screenHeight * 0.28f)
            }
            GuiActionType.SCROLL_UP -> {
                PointF(midX, screenHeight * 0.28f) to PointF(midX, screenHeight * 0.72f)
            }
            GuiActionType.SWIPE_LEFT -> {
                PointF(screenWidth * 0.82f, midY) to PointF(screenWidth * 0.18f, midY)
            }
            GuiActionType.SWIPE_RIGHT -> {
                PointF(screenWidth * 0.18f, midY) to PointF(screenWidth * 0.82f, midY)
            }
            else -> PointF(midX, screenHeight * 0.7f) to PointF(midX, screenHeight * 0.3f)
        }

        val curveOffset = 14f
        val p1 = PointF(
            p0.x + curveOffset,
            p0.y + (p3.y - p0.y) * 0.33f
        )
        val p2 = PointF(
            p0.x - curveOffset,
            p0.y + (p3.y - p0.y) * 0.66f
        )

        val bezierPath = Path().apply {
            moveTo(p0.x, p0.y)
            cubicTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
        }

        val builder = GestureDescription.Builder()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val moveStroke = GestureDescription.StrokeDescription(bezierPath, 0L, 280L, true)
            val holdPath = Path().apply { moveTo(p3.x, p3.y) }
            val stopStroke = moveStroke.continueStroke(holdPath, 0L, 75L, false)
            builder.addStroke(stopStroke)
        } else {
            val stroke = GestureDescription.StrokeDescription(bezierPath, 0L, 350L)
            builder.addStroke(stroke)
        }

        return builder.build()
    }
}

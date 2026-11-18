package com.health.friday.vision.grounding

import android.graphics.Bitmap
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min

class HybridScreenGrounder {

    private val ocrRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Builds a complete GroundedScreenState combining fast Accessibility DOM distillation
     * with Set-of-Marks (SoM) VLM visual fallback when needed.
     */
    suspend fun groundScreen(
        rootNode: AccessibilityNodeInfo?,
        screenshotProvider: suspend () -> Bitmap?,
        forceVisualSoM: Boolean = false
    ): GroundedScreenState = withContext(Dispatchers.Default) {

        val elementsMap = LinkedHashMap<Int, GroundedUiElement>()
        var nextId = 1
        val packageName = rootNode?.packageName?.toString() ?: "unknown"

        // PATH A: Fast Structural Accessibility DOM Extraction (~5-15ms)
        fun traverseDom(node: AccessibilityNodeInfo?) {
            node ?: return
            if (!node.isVisibleToUser) return

            val bounds = Rect().also { node.getBoundsInScreen(it) }
            val hasSemanticLabel = !node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank()
            val isInteractive = node.isClickable || node.isEditable || node.isScrollable || node.isCheckable

            // Filter out invisible 0-area nodes and empty structural layout wrappers
            if ((hasSemanticLabel || isInteractive) && bounds.width() > 8 && bounds.height() > 8) {
                val id = nextId++
                elementsMap[id] = GroundedUiElement(
                    id = id,
                    source = ElementSource.ACCESSIBILITY_DOM,
                    viewIdResource = node.viewIdResourceName?.substringAfterLast('/'),
                    className = node.className?.toString()?.substringAfterLast('.'),
                    text = node.text?.toString()?.trim(),
                    contentDescription = node.contentDescription?.toString()?.trim(),
                    boundsInScreen = bounds,
                    isClickable = node.isClickable,
                    isEditable = node.isEditable,
                    isScrollable = node.isScrollable,
                    liveNodeRef = node
                )
            }

            for (i in 0 until node.childCount) {
                traverseDom(node.getChild(i))
            }
        }

        traverseDom(rootNode)

        // Determine if the app is hiding its UI inside a Canvas/WebView/Flutter surface
        val interactiveCount = elementsMap.values.count { it.isClickable || it.isEditable }
        val needsVisualFallback = forceVisualSoM || interactiveCount < 3

        var annotatedBitmap: Bitmap? = null

        // PATH B: Visual OCR + Set-of-Marks (SoM) Grounding for non-standard UIs or visual queries
        if (needsVisualFallback) {
            val rawBitmap = screenshotProvider()
            if (rawBitmap != null) {
                val ocrBoxes = extractVisualTextBlocks(rawBitmap)

                for ((text, box) in ocrBoxes) {
                    // Deduplicate against existing DOM nodes using Intersection-over-Union (IoU > 0.65)
                    val isDuplicate = elementsMap.values.any { existing ->
                        computeIoU(existing.boundsInScreen, box) > 0.65f
                    }
                    if (!isDuplicate && box.width() > 12 && box.height() > 12) {
                        val id = nextId++
                        elementsMap[id] = GroundedUiElement(
                            id = id,
                            source = ElementSource.VISION_OCR_FALLBACK,
                            viewIdResource = null,
                            className = "CanvasText",
                            text = text,
                            contentDescription = null,
                            boundsInScreen = box,
                            isClickable = true, // Treat isolated visual text blocks on Canvas as tappable targets
                            isEditable = false,
                            isScrollable = false,
                            liveNodeRef = null
                        )
                    }
                }

                // Render the Set-of-Marks numbered bounding boxes onto the bitmap for the VLM
                annotatedBitmap = SetOfMarksRenderer.renderMarks(rawBitmap, elementsMap.values)
            }
        }

        // Build deterministic SHA-256 state hash and compact prompt table
        val digest = MessageDigest.getInstance("SHA-256")
        val sb = StringBuilder()
        sb.appendLine("SCREEN_PACKAGE: $packageName")
        sb.appendLine("GROUNDING_MODE: ${if (needsVisualFallback) "HYBRID_SOM_VISION" else "FAST_DOM"}")
        sb.appendLine("ELEMENTS:")

        for (element in elementsMap.values) {
            val line = element.toPromptLine()
            sb.appendLine(line)
            digest.update(line.toByteArray())
        }

        val stateHash = digest.digest().joinToString("") { "%02x".format(it) }.take(16)

        GroundedScreenState(
            timestamp = System.currentTimeMillis(),
            packageName = packageName,
            stateHash = stateHash,
            elements = elementsMap,
            annotatedSomBitmap = annotatedBitmap,
            promptContextTable = sb.toString(),
            usedVisionFallback = needsVisualFallback
        )
    }

    /**
     * Calculates Intersection-over-Union (IoU) between two screen rectangles:
     * IoU(A, B) = Area(A ∩ B) / (Area(A) + Area(B) - Area(A ∩ B))
     */
    private fun computeIoU(a: Rect, b: Rect): Float {
        val interLeft = max(a.left, b.left)
        val interTop = max(a.top, b.top)
        val interRight = min(a.right, b.right)
        val interBottom = min(a.bottom, b.bottom)

        if (interRight <= interLeft || interBottom <= interTop) return 0f

        val intersectionArea = (interRight - interLeft).toFloat() * (interBottom - interTop).toFloat()
        val areaA = a.width().toFloat() * a.height().toFloat()
        val areaB = b.width().toFloat() * b.height().toFloat()
        val unionArea = areaA + areaB - intersectionArea

        return if (unionArea <= 0f) 0f else intersectionArea / unionArea
    }

    private suspend fun extractVisualTextBlocks(bitmap: Bitmap): List<Pair<String, Rect>> =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            ocrRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val results = mutableListOf<Pair<String, Rect>>()
                    for (block in visionText.textBlocks) {
                        for (line in block.lines) {
                            val rect = line.boundingBox
                            if (rect != null && line.text.isNotBlank()) {
                                results.add(line.text.trim() to rect)
                            }
                        }
                    }
                    if (cont.isActive) cont.resume(results)
                }
                .addOnFailureListener {
                    if (cont.isActive) cont.resume(emptyList())
                }
        }
}

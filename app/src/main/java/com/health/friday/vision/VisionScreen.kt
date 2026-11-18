package com.health.friday.vision

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.health.friday.ai.AiOrchestrator
import com.health.friday.ui.components.FridayOrb
import com.health.friday.ui.components.OrbState
import com.health.friday.ui.theme.FridayBackground
import com.health.friday.ui.theme.FridayCyan
import com.health.friday.ui.theme.FridayMuted
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

data class VisionScanResult(
    val detectedText: String,
    val category: String,
    val perceptualHash: String,
    val confidence: Float
)

object PerceptualHasher {
    /**
     * Computes a 64-bit Difference Hash (dHash) for a Bitmap frame.
     * Generates a unique 16-character hex visual fingerprint for perceptual matching.
     */
    fun computeDHash(bitmap: Bitmap): String {
        return try {
            val scaled = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
            var hash = 0L
            for (y in 0 until 8) {
                for (x in 0 until 8) {
                    val left = scaled.getPixel(x, y) and 0xFF
                    val right = scaled.getPixel(x + 1, y) and 0xFF
                    if (left > right) {
                        hash = hash or (1L shl (y * 8 + x))
                    }
                }
            }
            scaled.recycle()
            "%016x".format(hash)
        } catch (e: Exception) {
            "0000000000000000"
        }
    }

    /** Classifies the visual text/object scene category based on extracted features. */
    fun classifyScene(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("calories") || lower.contains("protein") || lower.contains("carbs") || lower.contains("fat") || lower.contains("nutrition") ->
                "NUTRITION_LABEL 🥗"

            lower.contains("total") || lower.contains("$") || lower.contains("receipt") || lower.contains("tax") || lower.contains("amount") ->
                "RECEIPT 🧾"

            lower.contains("remind") || lower.contains("call") || lower.contains("todo") || lower.contains("at ") || lower.contains("pm") || lower.contains("am") ->
                "STICKY_NOTE 📝"

            lower.contains("menu") || lower.contains("price") || lower.contains("dish") ->
                "MENU 📖"

            text.length > 20 ->
                "DOCUMENT / TEXT 📄"

            else ->
                "OBJECT / SCENE 👁"
        }
    }
}

@Composable
fun VisionScreen(
    orchestrator: AiOrchestrator,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf<VisionScanResult?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var aiResponse by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            hasCameraPermission = true
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FridayBackground)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text(text = "‹ Back", color = FridayCyan)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "FRIDAY Advanced Vision 👁",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 16.dp)
            )
        }

        if (hasCameraPermission) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(24.dp))
            ) {
                CameraPreview(
                    context = context,
                    lifecycleOwner = lifecycleOwner,
                    onVisionScan = { result ->
                        if (!isProcessing && result.detectedText.isNotBlank()) {
                            scanResult = result
                        }
                    }
                )

                // Top Perceptual Hash Badge
                scanResult?.let { res ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xCC080B10))
                            .border(1.dp, FridayCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Column {
                            Text(
                                text = "CATEGORY: ${res.category}",
                                color = FridayCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "pHash: ${res.perceptualHash}",
                                color = Color.LightGray,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Overlay Controls
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color(0xEE080B10))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isProcessing) "Neural Processing..." else "Point camera at food, labels, or text",
                        color = FridayCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    scanResult?.let { res ->
                        if (!isProcessing) {
                            Text(
                                text = res.detectedText.take(120) + if (res.detectedText.length > 120) "..." else "",
                                color = Color.White,
                                fontSize = 12.sp,
                                maxLines = 3
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Button(
                                    onClick = {
                                        isProcessing = true
                                        val prompt = """
                                            I am looking at this through my camera:
                                            - Category: ${res.category}
                                            - Visual Fingerprint (pHash): ${res.perceptualHash}
                                            - Extracted Content:
                                            ${res.detectedText}

                                            Please analyze it and automatically take actions (log a meal, set a reminder, or save a task).
                                        """.trimIndent()

                                        scope.launch {
                                            val result = orchestrator.sendMessage(prompt, section = "vision")
                                            aiResponse = result
                                            isProcessing = false
                                            scanResult = null
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = FridayCyan)
                                ) {
                                    Text("Process & Log", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (isProcessing) {
                    Box(modifier = Modifier.fillMaxSize().background(Color(0x88000000)), contentAlignment = Alignment.Center) {
                        FridayOrb(state = OrbState.THINKING, modifier = Modifier.size(80.dp))
                    }
                }
            }
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Camera permission required for Vision.", color = Color.White)
            }
        }

        // Result Card
        aiResponse?.let { resp ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF11161D))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("FRIDAY Vision Analysis", color = FridayCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(resp, color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    onVisionScan: (VisionScanResult) -> Unit
) {
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val textRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(executor) { imageProxy ->
                            processImageProxy(textRecognizer, imageProxy, onVisionScan)
                        }
                    }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalyzer
                    )
                } catch (exc: Exception) {
                    Log.e("FRIDAY_VISION", "Camera lifecycle binding failed", exc)
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            textRecognizer.close()
        }
    }
}

@OptIn(ExperimentalGetImage::class)
private fun processImageProxy(
    recognizer: TextRecognizer,
    imageProxy: ImageProxy,
    onVisionScan: (VisionScanResult) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        // Generate Perceptual Hash (dHash) from frame
        val pHash = try {
            val bitmap = imageProxyToBitmap(imageProxy)
            if (bitmap != null) PerceptualHasher.computeDHash(bitmap) else "0000000000000000"
        } catch (e: Exception) {
            "0000000000000000"
        }

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val rawText = visionText.text
                val category = PerceptualHasher.classifyScene(rawText)

                onVisionScan(
                    VisionScanResult(
                        detectedText = rawText,
                        category = category,
                        perceptualHash = pHash,
                        confidence = 0.95f
                    )
                )
            }
            .addOnFailureListener { e ->
                Log.e("FRIDAY_VISION", "Text recognition failed", e)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}

private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
    return try {
        val plane = imageProxy.planes[0]
        val buffer = plane.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val yuvImage = YuvImage(bytes, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 80, out)
        val jpegBytes = out.toByteArray()
        BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
    } catch (e: Exception) {
        null
    }
}

package com.example.imagerec

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Size
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var tvResult: TextView

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var classifier: LiteClassifier? = null

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else Toast.makeText(this, "Cần quyền CAMERA", Toast.LENGTH_LONG).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        tvResult = findViewById(R.id.tvResult)

        initClassifier()
        askCameraPermission()
    }

    private fun askCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> startCamera()
            else -> requestPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun initClassifier() {
        try {
            classifier = LiteClassifier(this, maxResults = 3, numThreads = 4)
            tvResult.text = "Model loaded (Interpreter)"
        } catch (e: Exception) {
            tvResult.text = "Không load được model.tflite: ${e.message}"
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setTargetResolution(Size(1280, 720))
                .build()

            analysis.setAnalyzer(cameraExecutor) { imageProxy -> analyze(imageProxy) }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                Toast.makeText(this, "Bind camera lỗi: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyze(imageProxy: ImageProxy) {
        try {
            val plane = imageProxy.planes[0]
            val buffer = plane.buffer
            val width = imageProxy.width
            val height = imageProxy.height

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            buffer.rewind()
            bitmap.copyPixelsFromBuffer(buffer)

            val rotated = rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees.toFloat())

            classifier?.let { clf ->
                val results = clf.classify(rotated)
                if (results.isNotEmpty()) {
                    val text = buildString {
                        results.forEachIndexed { i, (label, score) ->
                            appendLine("#${i+1} $label - ${String.format("%.2f", score * 100)}%")
                        }
                    }
                    runOnUiThread { tvResult.text = text }
                }
            }
        } catch (e: Exception) {
            runOnUiThread { tvResult.text = "Lỗi phân loại: ${e.message}" }
        } finally {
            imageProxy.close()
        }
    }

    private fun rotateBitmap(src: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return src
        val m = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        if (rotated != src) src.recycle()
        return rotated
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        classifier?.close()
    }
}

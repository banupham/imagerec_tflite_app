package com.example.imagerec

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import org.tensorflow.lite.support.metadata.MetadataExtractor
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.MappedByteBuffer

class LiteClassifier(context: Context, private val maxResults: Int = 3, numThreads: Int = 4) {
    private val modelBuffer: MappedByteBuffer = FileUtil.loadMappedFile(context, "model.tflite")
    private val interpreter: Interpreter
    private val inputDataType: DataType
    private val inputShape: IntArray
    private val imageProcessor: ImageProcessor
    private val labels: List<String>

    init {
        val opts = Interpreter.Options().apply { setNumThreads(numThreads) }
        interpreter = Interpreter(modelBuffer, opts)

        val inputTensor = interpreter.getInputTensor(0)
        inputShape = inputTensor.shape() // [1,h,w,3]
        inputDataType = inputTensor.dataType()
        val targetH = inputShape.getOrNull(1) ?: 224
        val targetW = inputShape.getOrNull(2) ?: 224

        imageProcessor = if (inputDataType == DataType.FLOAT32) {
            ImageProcessor.Builder()
                .add(ResizeOp(targetH, targetW, ResizeOp.ResizeMethod.BILINEAR))
                .add(NormalizeOp(0f, 255f)) // scale 0..1
                .build()
        } else {
            ImageProcessor.Builder()
                .add(ResizeOp(targetH, targetW, ResizeOp.ResizeMethod.BILINEAR))
                .build()
        }

        labels = loadLabelsFromMetadata(modelBuffer) ?: runCatching {
            FileUtil.loadLabels(context, "labels.txt")
        }.getOrElse { listOf() }
    }

    private fun loadLabelsFromMetadata(buffer: MappedByteBuffer): List<String>? =
        try {
            val meta = MetadataExtractor(buffer)
            val names = meta.associatedFileNames ?: return null
            val labelFile = names.firstOrNull { it.contains("label", ignoreCase = true) } ?: return null
            val ins = meta.loadAssociatedFile(labelFile)
            BufferedReader(InputStreamReader(ins)).readLines()
        } catch (_: Exception) { null }

    fun classify(bitmap: Bitmap): List<Pair<String, Float>> {
        val tensorImage = TensorImage(inputDataType).apply { load(bitmap) }
        val input = imageProcessor.process(tensorImage)

        val outTensor = interpreter.getOutputTensor(0)
        val outShape = outTensor.shape() // [1, N]
        val outBuf = TensorBuffer.createFixedSize(outShape, outTensor.dataType())
        interpreter.run(input.buffer, outBuf.buffer.rewind())

        val scores = outBuf.floatArray
        return scores.mapIndexed { i, s -> i to s }
            .sortedByDescending { it.second }
            .take(maxResults)
            .map { (idx, sc) -> (labels.getOrNull(idx) ?: "ID_$idx") to sc }
    }

    fun close() = interpreter.close()
}

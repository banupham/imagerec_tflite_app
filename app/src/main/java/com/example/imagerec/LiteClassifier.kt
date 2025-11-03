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
import java.nio.MappedByteBuffer
import kotlin.math.exp

class LiteClassifier(
    context: Context,
    private val maxResults: Int = 3,
    numThreads: Int = 4,
    private val minScore: Float = 0.20f   // ngưỡng lọc 20%
) {
    private val modelBuffer: MappedByteBuffer = FileUtil.loadMappedFile(context, "model.tflite")
    private val interpreter: Interpreter
    private val inputShape: IntArray
    private val inputType: DataType
    private val imageProcessor: ImageProcessor
    private val labels: List<String>

    init {
        val opts = Interpreter.Options().apply { setNumThreads(numThreads) }
        interpreter = Interpreter(modelBuffer, opts)

        val inTensor = interpreter.getInputTensor(0)
        inputShape = inTensor.shape()        // [1, H, W, 3]
        inputType  = inTensor.dataType()
        val h = inputShape.getOrNull(1) ?: 224
        val w = inputShape.getOrNull(2) ?: 224

        // Hầu hết model TFHub cần scale về 0..1 (FLOAT32). Nếu là UINT8 thì không Normalize.
        imageProcessor = if (inputType == DataType.FLOAT32) {
            ImageProcessor.Builder()
                .add(ResizeOp(h, w, ResizeOp.ResizeMethod.BILINEAR))
                .add(NormalizeOp(0f, 255f))  // 0..1
                .build()
        } else {
            ImageProcessor.Builder()
                .add(ResizeOp(h, w, ResizeOp.ResizeMethod.BILINEAR))
                .build()
        }

        labels = try { FileUtil.loadLabels(context, "labels.txt") } catch (_: Exception) { emptyList() }
    }

    fun classify(bitmap: Bitmap): List<Pair<String, Float>> {
        val ti = TensorImage(inputType).apply { load(bitmap) }
        val input = imageProcessor.process(ti)

        val outTensor = interpreter.getOutputTensor(0)       // [1, N]
        val outBuf = TensorBuffer.createFixedSize(outTensor.shape(), outTensor.dataType())
        interpreter.run(input.buffer, outBuf.buffer.rewind())

        // Nhiều model xuất logits -> cần softmax
        val raw = outBuf.floatArray
        val probs = softmax(raw)

        return probs.mapIndexed { i, p -> i to p }
            .sortedByDescending { it.second }
            .filter { it.second >= minScore }
            .take(maxResults)
            .map { (i, p) -> (labels.getOrNull(i) ?: "ID_$i") to p }
    }

    private fun softmax(x: FloatArray): FloatArray {
        if (x.isEmpty()) return x
        val m = x.maxOrNull() ?: 0f
        val exps = FloatArray(x.size) { exp((x[it] - m).toDouble()).toFloat() }
        val sum = exps.sum()
        if (sum == 0f || sum.isNaN()) return FloatArray(x.size) { 0f }
        return FloatArray(x.size) { exps[it] / sum }
    }

    fun close() = interpreter.close()
}

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

class LiteClassifier(context: Context, private val maxResults: Int = 3, numThreads: Int = 4) {
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

        imageProcessor = if (inputType == DataType.FLOAT32) {
            ImageProcessor.Builder()
                .add(ResizeOp(h, w, ResizeOp.ResizeMethod.BILINEAR))
                .add(NormalizeOp(0f, 255f)) // 0..1
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

        val scores = outBuf.floatArray
        return scores.mapIndexed { i, s -> i to s }
            .sortedByDescending { it.second }
            .take(maxResults)
            .map { (i, s) -> (labels.getOrNull(i) ?: "ID_$i") to s }
    }

    fun close() = interpreter.close()
}

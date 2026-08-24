// GATE STUB — org.tensorflow.lite.
package org.tensorflow.lite

import java.io.File
import java.nio.ByteBuffer

enum class DataType {
    FLOAT32,
    INT32,
    UINT8,
    INT64,
    STRING,
    BOOL,
    INT8,
    BYTE
}

interface Delegate : AutoCloseable {
    override fun close()
}

class Tensor {
    fun shape(): IntArray = IntArray(0)
    fun dataType(): DataType = DataType.FLOAT32
    fun numElements(): Int = 0
    fun numBytes(): Int = 0
    fun quantizationParams(): Tensor.QuantizationParams = QuantizationParams(0f, 0)

    class QuantizationParams(val scale: Float, val zeroPoint: Int)
}

class Interpreter : AutoCloseable {

    constructor(modelFile: File)
    constructor(modelFile: File, options: Options)
    constructor(byteBuffer: ByteBuffer)
    constructor(byteBuffer: ByteBuffer, options: Options)

    class Options {
        fun addDelegate(delegate: Delegate): Options = this
        fun setNumThreads(numThreads: Int): Options = this
        fun setUseNNAPI(useNNAPI: Boolean): Options = this
    }

    fun run(input: Any, output: Any) {}
    fun getInputTensor(inputIndex: Int): Tensor = Tensor()
    fun getOutputTensor(outputIndex: Int): Tensor = Tensor()
    fun getInputTensorCount(): Int = 1
    fun getOutputTensorCount(): Int = 1

    override fun close() {}
}

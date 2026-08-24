// GATE STUB — org.tensorflow.lite.gpu.
package org.tensorflow.lite.gpu

import org.tensorflow.lite.Delegate

class CompatibilityList {
    val isDelegateSupportedOnThisDevice: Boolean
        get() = false

    fun bestOptionsForThisDevice(): GpuDelegateFactory.Options = GpuDelegateFactory.Options()
}

class GpuDelegate : Delegate {
    constructor()
    constructor(options: GpuDelegateFactory.Options)

    override fun close() {}
}

object GpuDelegateFactory {

    class Options {
        var inferencePreference: Int = INFERENCE_PREFERENCE_FAST_SINGLE_ANSWER
        var isPrecisionLossAllowed: Boolean = false

        fun setPrecisionLossAllowed(precisionLossAllowed: Boolean): Options = this
        fun setInferencePreference(preference: Int): Options = this
        fun setQuantizedModelsAllowed(quantizedModelsAllowed: Boolean): Options = this

        companion object {
            const val INFERENCE_PREFERENCE_FAST_SINGLE_ANSWER: Int = 0
            const val INFERENCE_PREFERENCE_SUSTAINED_SPEED: Int = 1
        }
    }
}

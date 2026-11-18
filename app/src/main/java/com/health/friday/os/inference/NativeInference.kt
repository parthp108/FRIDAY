package com.health.friday.os.inference

object NativeInference {
    init {
        System.loadLibrary("friday_memory_manager")
    }

    external fun loadModelMmap(filepath: String): Boolean
    external fun setIdleState()
    external fun setActiveState()
}
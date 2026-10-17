package com.dzynecode.app

/** Small thread-safe bridge between Python's stdout/input and the app console. */
object PythonBridge {
    @Volatile
    var outputHandler: ((String, Boolean) -> Unit)? = null

    @Volatile
    var inputHandler: ((String) -> String)? = null

    @JvmStatic
    fun write(text: String, isError: Boolean) {
        outputHandler?.invoke(text, isError)
    }

    @JvmStatic
    fun readInput(prompt: String): String {
        return inputHandler?.invoke(prompt) ?: ""
    }
}

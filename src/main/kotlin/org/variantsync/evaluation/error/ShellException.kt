package org.variantsync.evaluation.error

import java.util.function.Consumer

/**
 * Custom Exception for representing errors caused by shell commands being executed.
 */
class ShellException : Exception {
    @JvmField
    val output: List<String>

    constructor(e: Exception) : super(e) {
        output = ArrayList()
    }

    constructor(output: List<String>) : super(convert(output)) {
        this.output = output
    }

    companion object {
        private fun convert(output: Collection<String>): String {
            val sb = StringBuilder()
            output.forEach(Consumer { l: String -> sb.append(l).append(System.lineSeparator()) })
            return sb.toString()
        }
    }
}
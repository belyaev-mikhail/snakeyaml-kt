package org.yaml.snakeyaml.reader

import appendCodePoint
import org.yaml.snakeyaml.error.YAMLException

class ReaderException(val name: String, val position: Int, val codePoint: Int, message: String?) :
    YAMLException(message) {

    override fun toString(): String {
        val s = StringBuilder().appendCodePoint(codePoint)
        return """
               unacceptable code point '$s' (0x${codePoint.toString(16).uppercase()}) $message
               in "$name", position $position
               """.trimIndent()
    }
}

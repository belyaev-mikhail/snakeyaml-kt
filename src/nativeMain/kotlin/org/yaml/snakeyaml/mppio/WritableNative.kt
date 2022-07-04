package org.yaml.snakeyaml.mppio

import org.yaml.snakeyaml.mppio.appendCharsFallback

actual fun <A: Appendable> A.appendChars(charArray: CharArray): A {
    when (this) {
        is StringBuilder -> append(charArray)
        else -> appendCharsFallback(this, charArray)
    }
    return this
}
actual fun <A: Appendable> A.appendChars(charArray: CharArray, offset: Int, limit: Int): A {
    appendCharsFallback(this, charArray)
    return this
}

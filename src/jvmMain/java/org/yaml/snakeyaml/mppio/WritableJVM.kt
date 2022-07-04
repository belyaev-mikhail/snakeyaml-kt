package org.yaml.snakeyaml.mppio

import java.io.Writer
import java.nio.CharBuffer

actual fun <A: Appendable> A.appendChars(charArray: CharArray): A {
    when (this) {
        is java.lang.StringBuilder -> this.append(charArray)
        is java.lang.StringBuffer -> this.append(charArray)
        is Writer -> write(charArray)
        is CharBuffer -> put(charArray)
        else -> appendCharsFallback(this, charArray)
    }
    return this
}
actual fun <A: Appendable> A.appendChars(charArray: CharArray, offset: Int, limit: Int): A {
    when (this) {
        is java.lang.StringBuilder -> this.append(charArray, offset, limit)
        is java.lang.StringBuffer -> this.append(charArray, offset, limit)
        is Writer -> write(charArray, offset, limit)
        is CharBuffer -> put(charArray, offset, limit)
        else -> appendCharsFallback(this, charArray, offset, limit)
    }
    return this
}

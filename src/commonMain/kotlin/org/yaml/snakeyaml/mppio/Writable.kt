package org.yaml.snakeyaml.mppio

expect fun <A: Appendable> A.appendChars(charArray: CharArray): A
expect fun <A: Appendable> A.appendChars(charArray: CharArray, offset: Int, limit: Int): A

internal fun appendCharsFallback(ap: Appendable, charArray: CharArray) {
    for (ch in charArray) ap.append(ch)
}

internal fun appendCharsFallback(ap: Appendable, charArray: CharArray, offset: Int, limit: Int) {
    for (i in offset until (offset + limit)) {
        ap.append(charArray[i])
    }
}

package org.yaml.snakeyaml.mppio

expect fun String.codePointAt(index: Int): Int
expect fun CharArray.codePointAt(index: Int): Int
expect fun StringBuilder.appendCodePoint(cp: Int): StringBuilder

expect fun Char.Companion.charCount(codePoint: Int): Int
expect fun Char.Companion.toString(codePoint: Int): String
expect fun Char.Companion.toCodePoint(low: Char, high: Char?): Int
expect fun String.Companion.fromCodePoints(codePoints: IntArray, offset: Int, limit: Int): String

fun StringBuilder.appendCodePoints(cp: IntArray): StringBuilder {
    for (c in cp) append(c)
    return this
}

val s = run {
    val ss = ByteArray(1) {1}
    "".encodeToByteArray()
}

fun Char.Companion.isDecimalDigit(codePoint: Int) = codePoint in ('0'.code)..('9'.code)
fun Char.Companion.isSupplementaryCodePoint(codePoint: Int) = codePoint in 0x10000 until 0x10FFFF

internal object CodepointsPortable {

    private inline fun codePointFromChars(first: Char, second: Char?): Int {
        val firstCode = first.code
        if ( // check if it's the start of a surrogate pair
            firstCode in 0xD800..0xDBFF && // high surrogate
            second != null // there is a next code unit
        ) {
            val secondCode = second.code
            if (secondCode in 0xDC00..0xDFFF) { // low surrogate
                // https://mathiasbynens.be/notes/javascript-encoding#surrogate-formulae
                return (firstCode - 0xD800) * 0x400 + secondCode - 0xDC00 + 0x10000
            }
        }
        return firstCode
    }

    private fun codePointFromIterator(iterator: Iterator<Char>): Int {
        val firstCode = iterator.next().code
        if ( // check if it's the start of a surrogate pair
            firstCode in 0xD800..0xDBFF && // high surrogate
            iterator.hasNext() // there is a next code unit
        ) {
            val secondCode = iterator.next().code
            if (secondCode in 0xDC00..0xDFFF) { // low surrogate
                // https://mathiasbynens.be/notes/javascript-encoding#surrogate-formulae
                return (firstCode - 0xD800) * 0x400 + secondCode - 0xDC00 + 0x10000
            }
        }
        return firstCode
    }

    private inline fun forEachCharInCodePoint(codePoint: Int, body: (Char) -> Unit) {
        var codePoint = codePoint
        require(codePoint < 0x10FFFF)
        if (codePoint <= 0xFFFF) { // BMP code point
            body(codePoint.toChar())
        } else { // Astral code point; split in surrogate halves
            // https://mathiasbynens.be/notes/javascript-encoding#surrogate-formulae
            codePoint -= 0x10000
            body(Char((codePoint shr 10) + 0xD800))
            body(Char((codePoint % 0x400) + 0xDC00))
        }
    }

    private fun toCodePointsFallback(chars: CharArray): List<Int> {
        val iterator = chars.iterator()
        val result = mutableListOf<Int>()
        while (iterator.hasNext()) result += codePointFromIterator(iterator)
        return result
    }

    private fun codePointAtFallback(s: String, index: Int): Int {
        val string = s
        val size = string.length
        if (index < 0 || index >= size) {
            throw IndexOutOfBoundsException("String.codePointAt")
        }
        return codePointFromChars(string[index], string.getOrNull(index + 1))
    }

    private fun appendCodePointsFallback(stringBuilder: StringBuilder, vararg arguments: Int): StringBuilder {
        for (codePoint in arguments)
            forEachCharInCodePoint(codePoint) {
                stringBuilder.append(it)
            }
        return stringBuilder
    }

    /* to code points */
    fun String.codePointAt(index: Int): Int = codePointFromChars(get(index), getOrNull(index + 1))
    fun CharArray.codePointAt(index: Int): Int = codePointFromChars(get(index), getOrNull(index + 1))
    fun Char.Companion.toCodePoint(low: Char, high: Char?): Int = codePointFromChars(low, high)

    /* from code points */
    fun StringBuilder.appendCodePoint(cp: Int): StringBuilder {
        forEachCharInCodePoint(cp) { append(it) }
        return this
    }
    fun StringBuilder.appendCodePoints(cp: IntArray): StringBuilder {
        cp.forEach { code ->
            forEachCharInCodePoint(code) {
                append(it)
            }
        }
        return this
    }

    fun Char.Companion.charCount(codePoint: Int): Int {
        var i = 0
        forEachCharInCodePoint(codePoint) { i++ }
        return i
    }
    fun Char.Companion.toString(codePoint: Int): String =
        StringBuilder().appendCodePoint(codePoint).toString()

    fun String.Companion.fromCodePoints(codePoints: IntArray, offset: Int, limit: Int): String {
        val sb = StringBuilder()
        for (ix in offset until (offset + limit)) {
            forEachCharInCodePoint(codePoints[ix]) {
                sb.append(it)
            }
        }
        return sb.toString()
    }
}



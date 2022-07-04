package org.yaml.snakeyaml.mppio

actual fun String.codePointAt(index: Int): Int =
    js("""String.prototype.codePointAt""").call(this, index).unsafeCast<Int>()
actual fun CharArray.codePointAt(index: Int): Int =
    with (CodepointsPortable) { codePointAt(index) }
actual fun StringBuilder.appendCodePoint(cp: Int): StringBuilder =
    append(Char.toString(cp))

actual fun Char.Companion.charCount(codePoint: Int): Int =
    Char.toString(codePoint).length
actual fun Char.Companion.toString(codePoint: Int): String =
    js("String.fromCodePoint").call("", codePoint).unsafeCast<String>()
actual fun Char.Companion.toCodePoint(low: Char, high: Char?): Int = when {
    high == null -> low.code
    else -> charArrayOf(low, high).codePointAt(0)
}
actual fun String.Companion.fromCodePoints(codePoints: IntArray, offset: Int, limit: Int): String {
    val lm = codePoints.copyOfRange(offset, offset + limit)
    return js("String.fromCodePoint").apply("", codePoints).unsafeCast<String>()
}

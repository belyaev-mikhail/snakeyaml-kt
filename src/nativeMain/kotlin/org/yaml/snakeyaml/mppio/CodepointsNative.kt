package org.yaml.snakeyaml.mppio

actual fun String.codePointAt(index: Int): Int = with (CodepointsPortable) {
    codePointAt(index)
}
actual fun CharArray.codePointAt(index: Int): Int = with (CodepointsPortable) {
    codePointAt(index)
}
actual fun StringBuilder.appendCodePoint(cp: Int): StringBuilder = with (CodepointsPortable) {
    appendCodePoint(cp)
}
actual fun Char.Companion.charCount(codePoint: Int): Int = with (CodepointsPortable) {
    charCount(codePoint)
}
actual fun Char.Companion.toString(codePoint: Int): String = with (CodepointsPortable) {
    toString(codePoint)
}
actual fun Char.Companion.toCodePoint(low: Char, high: Char?): Int = with (CodepointsPortable) {
    toCodePoint(low, high)
}
actual fun String.Companion.fromCodePoints(codePoints: IntArray, offset: Int, limit: Int) = with (CodepointsPortable) {
    fromCodePoints(codePoints, offset, limit)
}

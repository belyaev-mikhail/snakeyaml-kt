package org.yaml.snakeyaml.mppio

import org.yaml.snakeyaml.mppio.CodepointsPortable.codePointAt
import java.lang.String as JString
import java.lang.Character as JChar
import java.lang.StringBuilder as JStringBuilder

actual fun String.codePointAt(index: Int): Int = (this as JString).codePointAt(index)
actual fun CharArray.codePointAt(index: Int): Int = JChar.codePointAt(this, index)
actual fun StringBuilder.appendCodePoint(cp: Int): StringBuilder =
    (this as JStringBuilder).appendCodePoint(cp)

actual fun Char.Companion.charCount(codePoint: Int): Int = JChar.charCount(codePoint)
actual fun Char.Companion.toString(codePoint: Int): String = JChar.toString(codePoint)
actual fun Char.Companion.toCodePoint(low: Char, high: Char?): Int = with(CodepointsPortable) {
    toCodePoint(low, high)
}
actual fun String.Companion.fromCodePoints(codePoints: IntArray, offset: Int, limit: Int): String =
    JString(codePoints, offset, limit) as String

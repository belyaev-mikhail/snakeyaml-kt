/**
 * Copyright (c) 2008, SnakeYAML
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.yaml.snakeyaml.error

import org.yaml.snakeyaml.scanner.Constant
import java.io.Serializable

/**
 * It's just a record and its only use is producing nice error messages. Parser
 * does not use it for any other purposes.
 */
class Mark(
    val name: String,
    /**
     * starts with 0
     * @return character number
     */
    val index: Int,
    /**
     * starts with 0
     * @return line number
     */
    val line: Int,
    /**
     * starts with 0
     * @return column number
     */
    val column: Int, val buffer: IntArray, val pointer: Int
) : Serializable {

    constructor(name: String, index: Int, line: Int, column: Int, str: CharArray, pointer: Int) : this(
        name,
        index,
        line,
        column,
        toCodePoints(str),
        pointer
    ) {
    }

    /*
     * Existed in older versions but replaced with {@code char[]}-based constructor.
     * Restored in v1.22 for backwards compatibility.
     * @deprecated Since restored in v1.22.  Use {@link Mark#Mark(String, int, int, int, char[], int)}.
     */
    @Deprecated("")
    constructor(name: String, index: Int, line: Int, column: Int, buffer: String, pointer: Int) : this(
        name,
        index,
        line,
        column,
        buffer.toCharArray(),
        pointer
    ) {
    }

    private fun isLineBreak(c: Int): Boolean {
        return Constant.Companion.NULL_OR_LINEBR.has(c)
    }

    @JvmOverloads
    fun get_snippet(indent: Int = 4, max_length: Int = 75): String {
        val half = max_length / 2f - 1f
        var start = pointer
        var head = ""
        while (start > 0 && !isLineBreak(buffer[start - 1])) {
            start -= 1
            if (pointer - start > half) {
                head = " ... "
                start += 5
                break
            }
        }
        var tail = ""
        var end = pointer
        while (end < buffer.size && !isLineBreak(buffer[end])) {
            end += 1
            if (end - pointer > half) {
                tail = " ... "
                end -= 5
                break
            }
        }
        val result = StringBuilder()
        for (i in 0 until indent) {
            result.append(" ")
        }
        result.append(head)
        for (i in start until end) {
            result.appendCodePoint(buffer[i])
        }
        result.append(tail)
        result.append("\n")
        for (i in 0 until indent + pointer - start + head.length) {
            result.append(" ")
        }
        result.append("^")
        return result.toString()
    }

    override fun toString(): String {
        val snippet = get_snippet()
        val builder = StringBuilder(" in ")
        builder.append(name)
        builder.append(", line ")
        builder.append(line + 1)
        builder.append(", column ")
        builder.append(column + 1)
        builder.append(":\n")
        builder.append(snippet)
        return builder.toString()
    }

    companion object {
        private fun toCodePoints(str: CharArray): IntArray {
            val codePoints = IntArray(Character.codePointCount(str, 0, str.size))
            var i = 0
            var c = 0
            while (i < str.size) {
                val cp = Character.codePointAt(str, i)
                codePoints[c] = cp
                i += Character.charCount(cp)
                c++
            }
            return codePoints
        }
    }
}
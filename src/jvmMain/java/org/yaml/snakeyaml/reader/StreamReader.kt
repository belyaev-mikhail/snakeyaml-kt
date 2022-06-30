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
package org.yaml.snakeyaml.reader

import org.yaml.snakeyaml.error.Mark
import org.yaml.snakeyaml.error.YAMLException
import org.yaml.snakeyaml.scanner.Constant
import java.io.*
import java.util.*

/**
 * Reader: checks if code points are in allowed range. Returns '\0' when end of
 * data has been reached.
 */
class StreamReader(reader: Reader) {
    private var name = "'reader'"
    private val stream: Reader

    /**
     * Read data (as a moving window for input stream)
     */
    private var dataWindow: IntArray

    /**
     * Real length of the data in dataWindow
     */
    private var dataLength: Int

    /**
     * The variable points to the current position in the data array
     */
    private var pointer = 0
    private var eof: Boolean
    /**
     * @return current position as number (in characters) from the beginning of the stream
     */
    /**
     * index is only required to implement 1024 key length restriction
     * http://yaml.org/spec/1.1/#simple key/
     * It must count code points, but it counts characters (to be fixed)
     */
    var index = 0 // in code points
        private set
    var line = 0
        private set
    var column = 0 //in code points
        private set
    private val buffer // temp buffer for one read operation (to avoid
            : CharArray

    constructor(stream: String?) : this(StringReader(stream)) {
        name = "'string'"
    }

    init {
        dataWindow = IntArray(0)
        dataLength = 0
        stream = reader
        eof = false
        buffer = CharArray(BUFFER_SIZE)
    }

    val mark: Mark
        get() = Mark(name, index, line, column, dataWindow, pointer)

    /**
     * read the next length characters and move the pointer.
     * if the last character is high surrogate one more character will be read
     *
     * @param length amount of characters to move forward
     */
    @JvmOverloads
    fun forward(length: Int = 1) {
        var i = 0
        while (i < length && ensureEnoughData()) {
            val c = dataWindow[pointer++]
            index++
            if (Constant.Companion.LINEBR.has(c) || c == '\r'.code && ensureEnoughData() && dataWindow[pointer] != '\n'.code) {
                line++
                column = 0
            } else if (c != 0xFEFF) {
                column++
            }
            i++
        }
    }

    fun peek(): Int {
        return if (ensureEnoughData()) dataWindow[pointer] else 0
    }

    /**
     * Peek the next index-th code point
     *
     * @param index to peek
     * @return the next index-th code point
     */
    fun peek(index: Int): Int {
        return if (ensureEnoughData(index)) dataWindow[pointer + index] else 0
    }

    /**
     * peek the next length code points
     *
     * @param length amount of the characters to peek
     * @return the next length code points
     */
    fun prefix(length: Int): String {
        return if (length == 0) {
            ""
        } else if (ensureEnoughData(length)) {
            String(dataWindow, pointer, length)
        } else {
            String(
                dataWindow, pointer,
                Math.min(length, dataLength - pointer)
            )
        }
    }

    /**
     * prefix(length) immediately followed by forward(length)
     * @param length amount of characters to get
     * @return the next length code points
     */
    fun prefixForward(length: Int): String {
        val prefix = prefix(length)
        pointer += length
        index += length
        // prefix never contains new line characters
        column += length
        return prefix
    }

    private fun ensureEnoughData(size: Int = 0): Boolean {
        if (!eof && pointer + size >= dataLength) {
            update()
        }
        return pointer + size < dataLength
    }

    private fun update() {
        try {
            var read = stream.read(buffer, 0, BUFFER_SIZE - 1)
            if (read > 0) {
                var cpIndex = dataLength - pointer
                dataWindow = Arrays.copyOfRange(dataWindow, pointer, dataLength + read)
                if (Character.isHighSurrogate(buffer[read - 1])) {
                    if (stream.read(buffer, read, 1) == -1) {
                        eof = true
                    } else {
                        read++
                    }
                }
                var nonPrintable = ' '.code
                var i = 0
                while (i < read) {
                    val codePoint = Character.codePointAt(buffer, i)
                    dataWindow[cpIndex] = codePoint
                    if (isPrintable(codePoint)) {
                        i += Character.charCount(codePoint)
                    } else {
                        nonPrintable = codePoint
                        i = read
                    }
                    cpIndex++
                }
                dataLength = cpIndex
                pointer = 0
                if (nonPrintable != ' '.code) {
                    throw ReaderException(
                        name, cpIndex - 1, nonPrintable,
                        "special characters are not allowed"
                    )
                }
            } else {
                eof = true
            }
        } catch (ioe: IOException) {
            throw YAMLException(ioe)
        }
    }

    companion object {
        // creating the array in stack)
        private const val BUFFER_SIZE = 1025
        fun isPrintable(data: String): Boolean {
            val length = data.length
            var offset = 0
            while (offset < length) {
                val codePoint = data.codePointAt(offset)
                if (!isPrintable(codePoint)) {
                    return false
                }
                offset += Character.charCount(codePoint)
            }
            return true
        }

        fun isPrintable(c: Int): Boolean {
            return c >= 0x20 && c <= 0x7E || c == 0x9 || c == 0xA || c == 0xD || c == 0x85 || c >= 0xA0 && c <= 0xD7FF || c >= 0xE000 && c <= 0xFFFD || c >= 0x10000 && c <= 0x10FFFF
        }
    }
}
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
package org.yaml.snakeyaml.scanner

import org.yaml.snakeyaml.mppio.*
import kotlin.jvm.JvmField

class Constant private constructor(content: String) {
    private val content: String
    var contains = BooleanArray(128) { false }
    var noASCII = false

    init {
        val sb = StringBuilder()
        for (i in 0 until content.length) {
            val c = content.codePointAt(i)
            if (c < 128) contains[c] = true else sb.appendCodePoint(c)
        }
        if (sb.length > 0) {
            noASCII = true
            this.content = sb.toString()
        } else this.content = content
    }

    fun has(c: Int): Boolean {
        return if (c < 128) contains[c] else noASCII && content.indexOf(c.toChar(), 0) != -1
    }

    fun hasNo(c: Int): Boolean {
        return !has(c)
    }

    fun has(c: Int, additional: String): Boolean {
        return has(c) || additional.indexOf(c.toChar(), 0) != -1
    }

    fun hasNo(c: Int, additional: String): Boolean {
        return !has(c, additional)
    }

    companion object {
        private const val ALPHA_S = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-_"
        private const val LINEBR_S = "\n\u0085\u2028\u2029"
        private const val FULL_LINEBR_S = "\r" + LINEBR_S
        private const val NULL_OR_LINEBR_S = "\u0000" + FULL_LINEBR_S
        private const val NULL_BL_LINEBR_S = " " + NULL_OR_LINEBR_S
        private const val NULL_BL_T_LINEBR_S = "\t" + NULL_BL_LINEBR_S
        private const val NULL_BL_T_S = "\u0000 \t"
        private const val URI_CHARS_S = ALPHA_S + "-;/?:@&=+$,_.!~*\'()[]%"
        @JvmField
        val LINEBR = Constant(LINEBR_S)
        val NULL_OR_LINEBR = Constant(NULL_OR_LINEBR_S)
        val NULL_BL_LINEBR = Constant(NULL_BL_LINEBR_S)
        val NULL_BL_T_LINEBR = Constant(NULL_BL_T_LINEBR_S)
        val NULL_BL_T = Constant(NULL_BL_T_S)
        val URI_CHARS = Constant(URI_CHARS_S)
        val ALPHA = Constant(ALPHA_S)
    }
}

operator fun Constant.contains(char: Char): Boolean = has(char.code)
operator fun Constant.contains(char: Int): Boolean = has(char)

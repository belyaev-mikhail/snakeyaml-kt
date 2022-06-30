/* Copyright (c) 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.yaml.snakeyaml.external.com.google.gdata.util.common.base

import java.lang.IllegalArgumentException

/**
 * A `UnicodeEscaper` that escapes some set of Java characters using the
 * URI percent encoding scheme. The set of safe characters (those which remain
 * unescaped) can be specified on construction.
 *
 *
 *
 * For details on escaping URIs for use in web pages, see section 2.4 of [RFC 3986](http://www.ietf.org/rfc/rfc3986.txt).
 *
 *
 *
 * In most cases this class should not need to be used directly. If you have no
 * special requirements for escaping your URIs, you should use either
 * [CharEscapers.uriEscaper] or [CharEscapers.uriEscaper].
 *
 *
 *
 * When encoding a String, the following rules apply:
 *
 *  * The alphanumeric characters "a" through "z", "A" through "Z" and "0"
 * through "9" remain the same.
 *  * Any additionally specified safe characters remain the same.
 *  * If `plusForSpace` was specified, the space character " " is
 * converted into a plus sign "+".
 *  * All other characters are converted into one or more bytes using UTF-8
 * encoding and each byte is then represented by the 3-character string "%XY",
 * where "XY" is the two-digit, uppercase, hexadecimal representation of the
 * byte value.
 *
 *
 *
 *
 * RFC 2396 specifies the set of unreserved characters as "-", "_", ".", "!",
 * "~", "*", "'", "(" and ")". It goes on to state:
 *
 *
 *
 * *Unreserved characters can be escaped without changing the semantics of the
 * URI, but this should not be done unless the URI is being used in a context
 * that does not allow the unescaped character to appear.*
 *
 *
 *
 * For performance reasons the only currently supported character encoding of
 * this class is UTF-8.
 *
 *
 *
 * **Note**: This escaper produces uppercase hexidecimal sequences. From [RFC 3986](http://www.ietf.org/rfc/rfc3986.txt):<br></br>
 * *"URI producers and normalizers should use uppercase hexadecimal digits for
 * all percent-encodings."*
 *
 *
 */
class PercentEscaper(safeChars: String, plusForSpace: Boolean) : UnicodeEscaper() {
    /**
     * If true we should convert space to the `+` character.
     */
    private val plusForSpace: Boolean

    /**
     * An array of flags where for any `char c` if `safeOctets[c]`
     * is true then `c` should remain unmodified in the output. If
     * `c > safeOctets.length` then it should be escaped.
     */
    private val safeOctets: BooleanArray

    /**
     * Constructs a URI escaper with the specified safe characters and optional
     * handling of the space character.
     *
     * @param safeChars
     * a non null string specifying additional safe characters for
     * this escaper (the ranges 0..9, a..z and A..Z are always safe
     * and should not be specified here)
     * @param plusForSpace
     * true if ASCII space should be escaped to `+` rather than
     * `%20`
     * @throws IllegalArgumentException
     * if any of the parameters were invalid
     */
    init {
        // Avoid any misunderstandings about the behavior of this escaper
        require(!safeChars.matches(".*[0-9A-Za-z].*")) {
            ("Alphanumeric characters are always 'safe' and should not be "
                    + "explicitly specified")
        }
        // Avoid ambiguous parameters. Safe characters are never modified so if
        // space is a safe character then setting plusForSpace is meaningless.
        require(!(plusForSpace && safeChars.contains(" "))) { "plusForSpace cannot be specified when space is a 'safe' character" }
        require(!safeChars.contains("%")) { "The '%' character cannot be specified as 'safe'" }
        this.plusForSpace = plusForSpace
        safeOctets = createSafeOctets(safeChars)
    }

    /*
     * Overridden for performance. For unescaped strings this improved the
     * performance of the uri escaper from ~760ns to ~400ns as measured by
     * {@link CharEscapersBenchmark}.
     */
    override fun nextEscapeIndex(csq: CharSequence, index: Int, end: Int): Int {
        var index = index
        while (index < end) {
            val c = csq[index]
            if (c.code >= safeOctets.size || !safeOctets[c.code]) {
                break
            }
            index++
        }
        return index
    }

    /*
     * Overridden for performance. For unescaped strings this improved the
     * performance of the uri escaper from ~400ns to ~170ns as measured by
     * {@link CharEscapersBenchmark}.
     */
    override fun escape(s: String): String? {
        val slen = s.length
        for (index in 0 until slen) {
            val c = s[index]
            if (c.code >= safeOctets.size || !safeOctets[c.code]) {
                return escapeSlow(s, index)
            }
        }
        return s
    }

    /**
     * Escapes the given Unicode code point in UTF-8.
     */
    override fun escape(cp: Int): CharArray? {
        // We should never get negative values here but if we do it will throw
        // an
        // IndexOutOfBoundsException, so at least it will get spotted.
        var cp = cp
        return if (cp < safeOctets.size && safeOctets[cp]) {
            null
        } else if (cp == ' '.code && plusForSpace) {
            URI_ESCAPED_SPACE
        } else if (cp <= 0x7F) {
            // Single byte UTF-8 characters
            // Start with "%--" and fill in the blanks
            val dest = CharArray(3)
            dest[0] = '%'
            dest[2] =
                UPPER_HEX_DIGITS[cp and 0xF]
            dest[1] =
                UPPER_HEX_DIGITS[cp ushr 4]
            dest
        } else if (cp <= 0x7ff) {
            // Two byte UTF-8 characters [cp >= 0x80 && cp <= 0x7ff]
            // Start with "%--%--" and fill in the blanks
            val dest = CharArray(6)
            dest[0] = '%'
            dest[3] = '%'
            dest[5] =
                UPPER_HEX_DIGITS[cp and 0xF]
            cp = cp ushr 4
            dest[4] =
                UPPER_HEX_DIGITS[0x8 or (cp and 0x3)]
            cp = cp ushr 2
            dest[2] =
                UPPER_HEX_DIGITS[cp and 0xF]
            cp = cp ushr 4
            dest[1] =
                UPPER_HEX_DIGITS[0xC or cp]
            dest
        } else if (cp <= 0xffff) {
            // Three byte UTF-8 characters [cp >= 0x800 && cp <= 0xffff]
            // Start with "%E-%--%--" and fill in the blanks
            val dest = CharArray(9)
            dest[0] = '%'
            dest[1] = 'E'
            dest[3] = '%'
            dest[6] = '%'
            dest[8] =
                UPPER_HEX_DIGITS[cp and 0xF]
            cp = cp ushr 4
            dest[7] =
                UPPER_HEX_DIGITS[0x8 or (cp and 0x3)]
            cp = cp ushr 2
            dest[5] =
                UPPER_HEX_DIGITS[cp and 0xF]
            cp = cp ushr 4
            dest[4] =
                UPPER_HEX_DIGITS[0x8 or (cp and 0x3)]
            cp = cp ushr 2
            dest[2] =
                UPPER_HEX_DIGITS[cp]
            dest
        } else if (cp <= 0x10ffff) {
            val dest = CharArray(12)
            // Four byte UTF-8 characters [cp >= 0xffff && cp <= 0x10ffff]
            // Start with "%F-%--%--%--" and fill in the blanks
            dest[0] = '%'
            dest[1] = 'F'
            dest[3] = '%'
            dest[6] = '%'
            dest[9] = '%'
            dest[11] =
                UPPER_HEX_DIGITS[cp and 0xF]
            cp = cp ushr 4
            dest[10] =
                UPPER_HEX_DIGITS[0x8 or (cp and 0x3)]
            cp = cp ushr 2
            dest[8] =
                UPPER_HEX_DIGITS[cp and 0xF]
            cp = cp ushr 4
            dest[7] =
                UPPER_HEX_DIGITS[0x8 or (cp and 0x3)]
            cp = cp ushr 2
            dest[5] =
                UPPER_HEX_DIGITS[cp and 0xF]
            cp = cp ushr 4
            dest[4] =
                UPPER_HEX_DIGITS[0x8 or (cp and 0x3)]
            cp = cp ushr 2
            dest[2] =
                UPPER_HEX_DIGITS[cp and 0x7]
            dest
        } else {
            // If this ever happens it is due to bug in UnicodeEscaper, not bad
            // input.
            throw IllegalArgumentException("Invalid unicode character value $cp")
        }
    }

    companion object {
        /**
         * A string of safe characters that mimics the behavior of
         * [java.net.URLEncoder].
         *
         */
        const val SAFECHARS_URLENCODER = "-_.*"

        /**
         * A string of characters that do not need to be encoded when used in URI
         * path segments, as specified in RFC 3986. Note that some of these
         * characters do need to be escaped when used in other parts of the URI.
         */
        const val SAFEPATHCHARS_URLENCODER = "-_.!~*'()@:$&,;="

        /**
         * A string of characters that do not need to be encoded when used in URI
         * query strings, as specified in RFC 3986. Note that some of these
         * characters do need to be escaped when used in other parts of the URI.
         */
        const val SAFEQUERYSTRINGCHARS_URLENCODER = "-_.!~*'()@:$,;/?:"

        // In some uri escapers spaces are escaped to '+'
        private val URI_ESCAPED_SPACE = charArrayOf('+')
        private val UPPER_HEX_DIGITS = "0123456789ABCDEF".toCharArray()

        /**
         * Creates a boolean[] with entries corresponding to the character values
         * for 0-9, A-Z, a-z and those specified in safeChars set to true. The array
         * is as small as is required to hold the given character information.
         */
        private fun createSafeOctets(safeChars: String): BooleanArray {
            var maxChar = 'z'.code
            val safeCharArray = safeChars.toCharArray()
            for (c in safeCharArray) {
                maxChar = Math.max(c.code, maxChar)
            }
            val octets = BooleanArray(maxChar + 1)
            run {
                var c = '0'.code
                while (c <= '9'.code) {
                    octets[c] = true
                    c++
                }
            }
            run {
                var c = 'A'.code
                while (c <= 'Z'.code) {
                    octets[c] = true
                    c++
                }
            }
            var c = 'a'.code
            while (c <= 'z'.code) {
                octets[c] = true
                c++
            }
            for (c in safeCharArray) {
                octets[c.code] = true
            }
            return octets
        }
    }
}
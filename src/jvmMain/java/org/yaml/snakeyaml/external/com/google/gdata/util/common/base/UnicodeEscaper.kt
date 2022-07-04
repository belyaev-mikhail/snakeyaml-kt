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

import java.io.IOException

/**
 * An [Escaper] that converts literal text into a format safe for
 * inclusion in a particular context (such as an XML document). Typically (but
 * not always), the inverse process of "unescaping" the text is performed
 * automatically by the relevant parser.
 *
 *
 *
 * For example, an XML escaper would convert the literal string
 * `"Foo<Bar>"` into `"Foo&lt;Bar&gt;"` to prevent `"<Bar>"`
 * from being confused with an XML tag. When the resulting XML document is
 * parsed, the parser API will return this text as the original literal string
 * `"Foo<Bar>"`.
 *
 *
 *
 * **Note:** This class is similar to [CharEscaper] but with one very
 * important difference. A CharEscaper can only process Java [UTF16](http://en.wikipedia.org/wiki/UTF-16) characters in isolation
 * and may not cope when it encounters surrogate pairs. This class facilitates
 * the correct escaping of all Unicode characters.
 *
 *
 *
 * As there are important reasons, including potential security issues, to
 * handle Unicode correctly if you are considering implementing a new escaper
 * you should favor using UnicodeEscaper wherever possible.
 *
 *
 *
 * A `UnicodeEscaper` instance is required to be stateless, and safe when
 * used concurrently by multiple threads.
 *
 *
 *
 * Several popular escapers are defined as constants in the class
 * [CharEscapers]. To create your own escapers extend this class and
 * implement the [.escape] method.
 *
 *
 */
abstract class UnicodeEscaper : Escaper {
    /**
     * Returns the escaped form of the given Unicode code point, or `null`
     * if this code point does not need to be escaped. When called as part of an
     * escaping operation, the given code point is guaranteed to be in the range
     * `0 <= cp <= Character#MAX_CODE_POINT`.
     *
     *
     *
     * If an empty array is returned, this effectively strips the input
     * character from the resulting text.
     *
     *
     *
     * If the character does not need to be escaped, this method should return
     * `null`, rather than an array containing the character
     * representation of the code point. This enables the escaping algorithm to
     * perform more efficiently.
     *
     *
     *
     * If the implementation of this method cannot correctly handle a particular
     * code point then it should either throw an appropriate runtime exception
     * or return a suitable replacement character. It must never silently
     * discard invalid input as this may constitute a security risk.
     *
     * @param cp
     * the Unicode code point to escape if necessary
     * @return the replacement characters, or `null` if no escaping was
     * needed
     */
    protected abstract fun escape(cp: Int): CharArray?

    /**
     * Scans a sub-sequence of characters from a given [CharSequence],
     * returning the index of the next character that requires escaping.
     *
     *
     *
     * **Note:** When implementing an escaper, it is a good idea to override
     * this method for efficiency. The base class implementation determines
     * successive Unicode code points and invokes [.escape] for each
     * of them. If the semantics of your escaper are such that code points in
     * the supplementary range are either all escaped or all unescaped, this
     * method can be implemented more efficiently using
     * [CharSequence.charAt].
     *
     *
     *
     * Note however that if your escaper does not escape characters in the
     * supplementary range, you should either continue to validate the
     * correctness of any surrogate characters encountered or provide a clear
     * warning to users that your escaper does not validate its input.
     *
     *
     *
     * See [PercentEscaper] for an example.
     *
     * @param csq
     * a sequence of characters
     * @param start
     * the index of the first character to be scanned
     * @param end
     * the index immediately after the last character to be scanned
     * @throws IllegalArgumentException
     * if the scanned sub-sequence of `csq` contains invalid
     * surrogate pairs
     */
    protected open fun nextEscapeIndex(csq: CharSequence, start: Int, end: Int): Int {
        var index = start
        while (index < end) {
            val cp = codePointAt(csq, index, end)
            if (cp < 0 || escape(cp) != null) {
                break
            }
            index += if (Character.isSupplementaryCodePoint(cp)) 2 else 1
        }
        return index
    }

    /**
     * Returns the escaped form of a given literal string.
     *
     *
     *
     * If you are escaping input in arbitrary successive chunks, then it is not
     * generally safe to use this method. If an input string ends with an
     * unmatched high surrogate character, then this method will throw
     * [IllegalArgumentException]. You should either ensure your input is
     * valid [UTF-16](http://en.wikipedia.org/wiki/UTF-16) before
     * calling this method or use an escaped [Appendable] (as returned by
     * [.escape]) which can cope with arbitrarily split input.
     *
     *
     *
     * **Note:** When implementing an escaper it is a good idea to override
     * this method for efficiency by inlining the implementation of
     * [.nextEscapeIndex] directly. Doing this for
     * [PercentEscaper] more than doubled the performance for unescaped
     * strings (as measured by [CharEscapersBenchmark]).
     *
     * @param string
     * the literal string to be escaped
     * @return the escaped form of `string`
     * @throws NullPointerException
     * if `string` is null
     * @throws IllegalArgumentException
     * if invalid surrogate characters are encountered
     */
    override fun escape(string: String): String {
        val end = string.length
        val index = nextEscapeIndex(string, 0, end)
        return if (index == end) string else escapeSlow(string, index)
    }

    /**
     * Returns the escaped form of a given literal string, starting at the given
     * index. This method is called by the [.escape] method when
     * it discovers that escaping is required. It is protected to allow
     * subclasses to override the fastpath escaping function to inline their
     * escaping test. See [CharEscaperBuilder] for an example usage.
     *
     *
     *
     * This method is not reentrant and may only be invoked by the top level
     * [.escape] method.
     *
     * @param s
     * the literal string to be escaped
     * @param index
     * the index to start escaping from
     * @return the escaped form of `string`
     * @throws NullPointerException
     * if `string` is null
     * @throws IllegalArgumentException
     * if invalid surrogate characters are encountered
     */
    protected fun escapeSlow(s: String, index: Int): String {
        var index = index
        val end = s.length

        // Get a destination buffer and setup some loop variables.
        var dest = DEST_TL.get()
        var destIndex = 0
        var unescapedChunkStart = 0
        while (index < end) {
            val cp = codePointAt(s, index, end)
            require(cp >= 0) { "Trailing high surrogate at end of input" }
            val escaped = escape(cp)
            if (escaped != null) {
                val charsSkipped = index - unescapedChunkStart

                // This is the size needed to add the replacement, not the full
                // size needed by the string. We only regrow when we absolutely
                // must.
                val sizeNeeded = destIndex + charsSkipped + escaped.size
                if (dest.size < sizeNeeded) {
                    val destLength = sizeNeeded + (end - index) + DEST_PAD
                    dest = growBuffer(dest, destIndex, destLength)
                }
                // If we have skipped any characters, we need to copy them now.
                if (charsSkipped > 0) {
                    s.toCharArray(dest, destIndex, unescapedChunkStart, index)
                    destIndex += charsSkipped
                }
                if (escaped.size > 0) {
                    System.arraycopy(escaped, 0, dest, destIndex, escaped.size)
                    destIndex += escaped.size
                }
            }
            unescapedChunkStart = index + if (Character.isSupplementaryCodePoint(cp)) 2 else 1
            index = nextEscapeIndex(s, unescapedChunkStart, end)
        }

        // Process trailing unescaped characters - no need to account for
        // escaped
        // length or padding the allocation.
        val charsSkipped = end - unescapedChunkStart
        if (charsSkipped > 0) {
            val endIndex = destIndex + charsSkipped
            if (dest.size < endIndex) {
                dest = growBuffer(dest, destIndex, endIndex)
            }
            s.toCharArray(dest, destIndex, unescapedChunkStart, end)
            destIndex = endIndex
        }
        return String(dest, 0, destIndex)
    }

    /**
     * Returns an `Appendable` instance which automatically escapes all
     * text appended to it before passing the resulting text to an underlying
     * `Appendable`.
     *
     *
     *
     * Unlike [.escape] it is permitted to append arbitrarily
     * split input to this Appendable, including input that is split over a
     * surrogate pair. In this case the pending high surrogate character will
     * not be processed until the corresponding low surrogate is appended. This
     * means that a trailing high surrogate character at the end of the input
     * cannot be detected and will be silently ignored. This is unavoidable
     * since the Appendable interface has no `close()` method, and it is
     * impossible to determine when the last characters have been appended.
     *
     *
     *
     * The methods of the returned object will propagate any exceptions thrown
     * by the underlying `Appendable`.
     *
     *
     *
     * For well formed [UTF-16](http://en.wikipedia.org/wiki/UTF-16)
     * the escaping behavior is identical to that of [.escape] and
     * the following code is equivalent to (but much slower than)
     * `escaper.escape(string)`:
     *
     * <pre>
     * {
     * &#064;code
     * StringBuilder sb = new StringBuilder();
     * escaper.escape(sb).append(string);
     * return sb.toString();
     * }
    </pre> *
     *
     * @param out
     * the underlying `Appendable` to append escaped output to
     * @return an `Appendable` which passes text to `out` after
     * escaping it
     * @throws NullPointerException
     * if `out` is null
     * @throws IllegalArgumentException
     * if invalid surrogate characters are encountered
     */
    override fun escape(out: Appendable): Appendable {
        assert(out != null)
        return object : Appendable {
            var pendingHighSurrogate = -1
            val decodedChars = CharArray(2)
            @Throws(IOException::class)
            override fun append(csq: CharSequence): Appendable {
                return append(csq, 0, csq.length)
            }

            @Throws(IOException::class)
            override fun append(csq: CharSequence, start: Int, end: Int): Appendable {
                var index = start
                if (index < end) {
                    // This is a little subtle: index must never reference the
                    // middle of a
                    // surrogate pair but unescapedChunkStart can. The first
                    // time we enter
                    // the loop below it is possible that index !=
                    // unescapedChunkStart.
                    var unescapedChunkStart = index
                    if (pendingHighSurrogate != -1) {
                        // Our last append operation ended halfway through a
                        // surrogate pair
                        // so we have to do some extra work first.
                        val c = csq[index++]
                        require(Character.isLowSurrogate(c)) { "Expected low surrogate character but got $c" }
                        val escaped = escape(
                            Character.toCodePoint(
                                pendingHighSurrogate.toChar(),
                                c
                            )
                        )
                        if (escaped != null) {
                            // Emit the escaped character and adjust
                            // unescapedChunkStart to
                            // skip the low surrogate we have consumed.
                            outputChars(escaped, escaped.size)
                            unescapedChunkStart += 1
                        } else {
                            // Emit pending high surrogate (unescaped) but do
                            // not modify
                            // unescapedChunkStart as we must still emit the low
                            // surrogate.
                            out.append(pendingHighSurrogate.toChar())
                        }
                        pendingHighSurrogate = -1
                    }
                    while (true) {
                        // Find and append the next subsequence of unescaped
                        // characters.
                        index = nextEscapeIndex(csq, index, end)
                        if (index > unescapedChunkStart) {
                            out.append(csq, unescapedChunkStart, index)
                        }
                        if (index == end) {
                            break
                        }
                        // If we are not finished, calculate the next code
                        // point.
                        val cp = codePointAt(csq, index, end)
                        if (cp < 0) {
                            // Our sequence ended half way through a surrogate
                            // pair so just
                            // record the state and exit.
                            pendingHighSurrogate = -cp
                            break
                        }
                        // Escape the code point and output the characters.
                        val escaped = escape(cp)
                        if (escaped != null) {
                            outputChars(escaped, escaped.size)
                        } else {
                            // This shouldn't really happen if nextEscapeIndex
                            // is correct but
                            // we should cope with false positives.
                            val len = Character.toChars(cp, decodedChars, 0)
                            outputChars(decodedChars, len)
                        }
                        // Update our index past the escaped character and
                        // continue.
                        index += if (Character.isSupplementaryCodePoint(cp)) 2 else 1
                        unescapedChunkStart = index
                    }
                }
                return this
            }

            @Throws(IOException::class)
            override fun append(c: Char): Appendable {
                if (pendingHighSurrogate != -1) {
                    // Our last append operation ended halfway through a
                    // surrogate pair
                    // so we have to do some extra work first.
                    require(Character.isLowSurrogate(c)) {
                        ("Expected low surrogate character but got '" + c + "' with value "
                                + c.code)
                    }
                    val escaped = escape(Character.toCodePoint(pendingHighSurrogate.toChar(), c))
                    if (escaped != null) {
                        outputChars(escaped, escaped.size)
                    } else {
                        out.append(pendingHighSurrogate.toChar())
                        out.append(c)
                    }
                    pendingHighSurrogate = -1
                } else if (Character.isHighSurrogate(c)) {
                    // This is the start of a (split) surrogate pair.
                    pendingHighSurrogate = c.code
                } else {
                    require(!Character.isLowSurrogate(c)) {
                        ("Unexpected low surrogate character '"
                                + c + "' with value " + c.code)
                    }
                    // This is a normal (non surrogate) char.
                    val escaped = escape(c.code)
                    if (escaped != null) {
                        outputChars(escaped, escaped.size)
                    } else {
                        out.append(c)
                    }
                }
                return this
            }

            @Throws(IOException::class)
            private fun outputChars(chars: CharArray, len: Int) {
                for (n in 0 until len) {
                    out.append(chars[n])
                }
            }
        }
    }

    companion object {
        /** The amount of padding (chars) to use when growing the escape buffer.  */
        private const val DEST_PAD = 32

        /**
         * Returns the Unicode code point of the character at the given index.
         *
         *
         *
         * Unlike [Character.codePointAt] or
         * [String.codePointAt] this method will never fail silently when
         * encountering an invalid surrogate pair.
         *
         *
         *
         * The behaviour of this method is as follows:
         *
         *  1. If `index >= end`, [IndexOutOfBoundsException] is thrown.
         *  1. **If the character at the specified index is not a surrogate, it is
         * returned.**
         *  1. If the first character was a high surrogate value, then an attempt is
         * made to read the next character.
         *
         *  1. **If the end of the sequence was reached, the negated value of the
         * trailing high surrogate is returned.**
         *  1. **If the next character was a valid low surrogate, the code point
         * value of the high/low surrogate pair is returned.**
         *  1. If the next character was not a low surrogate value, then
         * [IllegalArgumentException] is thrown.
         *
         *  1. If the first character was a low surrogate value,
         * [IllegalArgumentException] is thrown.
         *
         *
         * @param seq
         * the sequence of characters from which to decode the code point
         * @param index
         * the index of the first character to decode
         * @param end
         * the index beyond the last valid character to decode
         * @return the Unicode code point for the given index or the negated value
         * of the trailing high surrogate character at the end of the
         * sequence
         */
        protected fun codePointAt(seq: CharSequence, index: Int, end: Int): Int {
            var index = index
            if (index < end) {
                val c1 = seq[index++]
                return if (c1 < Character.MIN_HIGH_SURROGATE || c1 > Character.MAX_LOW_SURROGATE) {
                    // Fast path (first test is probably all we need to do)
                    c1.toInt()
                } else if (c1 <= Character.MAX_HIGH_SURROGATE) {
                    // If the high surrogate was the last character, return its
                    // inverse
                    if (index == end) {
                        return -(c1.code)
                    }
                    // Otherwise look for the low surrogate following it
                    val c2 = seq[index]
                    if (Character.isLowSurrogate(c2)) {
                        return Character.toCodePoint(c1, c2)
                    }
                    throw IllegalArgumentException(
                        "Expected low surrogate but got char '" + c2
                                + "' with value " + c2.code + " at index " + index
                    )
                } else {
                    throw IllegalArgumentException(
                        "Unexpected low surrogate character '" + c1
                                + "' with value " + c1.code + " at index " + (index - 1)
                    )
                }
            }
            throw IndexOutOfBoundsException("Index exceeds specified range")
        }

        /**
         * Helper method to grow the character buffer as needed, this only happens
         * once in a while so it's ok if it's in a method call. If the index passed
         * in is 0 then no copying will be done.
         */
        private fun growBuffer(dest: CharArray, index: Int, size: Int): CharArray {
            val copy = CharArray(size)
            if (index > 0) {
                System.arraycopy(dest, 0, copy, 0, index)
            }
            return copy
        }

        /**
         * A thread-local destination buffer to keep us from creating new buffers.
         * The starting size is 1024 characters. If we grow past this we don't put
         * it back in the threadlocal, we just keep going and grow as needed.
         */
        private val DEST_TL: ThreadLocal<CharArray> = object : ThreadLocal<CharArray>() {
            override fun initialValue(): CharArray {
                return CharArray(1024)
            }
        }
    }
}

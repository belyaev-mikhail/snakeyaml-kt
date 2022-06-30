// Copyright 2003-2010 Christian d'Heureuse, Inventec Informatik AG, Zurich, Switzerland
// www.source-code.biz, www.inventec.ch/chdh
//
// This module is multi-licensed and may be used under the terms
// of any of the following licenses:
//
//  EPL, Eclipse Public License, V1.0 or later, http://www.eclipse.org/legal
//  LGPL, GNU Lesser General Public License, V2.1 or later, http://www.gnu.org/licenses/lgpl.html
//  GPL, GNU General Public License, V2 or later, http://www.gnu.org/licenses/gpl.html
//  AL, Apache License, V2.0 or later, http://www.apache.org/licenses
//  BSD, BSD License, http://www.opensource.org/licenses/bsd-license.php
//
// Please contact the author if you need another license.
// This module is provided "as is", without warranties of any kind.
package org.yaml.snakeyaml.external.biz.base64Coder

/**
 * A Base64 encoder/decoder.
 *
 *
 *
 * This class is used to encode and decode data in Base64 format as described in
 * RFC 1521.
 *
 *
 *
 * Project home page: [www.
 * source-code.biz/base64coder/java](http://www.source-code.biz/base64coder/java/)<br></br>
 * Author: Christian d'Heureuse, Inventec Informatik AG, Zurich, Switzerland<br></br>
 * Multi-licensed: EPL / LGPL / GPL / AL / BSD.
 */
object Base64Coder {
    // The line separator string of the operating system.
    private val systemLineSeparator = System.getProperty("line.separator")

    // Mapping table from 6-bit nibbles to Base64 characters.
    private val map1 = CharArray(64)

    init {
        val i = 0
        run {
            val c = 'A'
            while (org.yaml.snakeyaml.external.biz.base64Coder.c <= 'Z') {
                map1[org.yaml.snakeyaml.external.biz.base64Coder.i++] = org.yaml.snakeyaml.external.biz.base64Coder.c
                org.yaml.snakeyaml.external.biz.base64Coder.c++
            }
        }
        run {
            val c = 'a'
            while (org.yaml.snakeyaml.external.biz.base64Coder.c <= 'z') {
                map1[org.yaml.snakeyaml.external.biz.base64Coder.i++] = org.yaml.snakeyaml.external.biz.base64Coder.c
                org.yaml.snakeyaml.external.biz.base64Coder.c++
            }
        }
        val c = '0'
        while (org.yaml.snakeyaml.external.biz.base64Coder.c <= '9') {
            map1[org.yaml.snakeyaml.external.biz.base64Coder.i++] = org.yaml.snakeyaml.external.biz.base64Coder.c
            org.yaml.snakeyaml.external.biz.base64Coder.c++
        }
        map1[org.yaml.snakeyaml.external.biz.base64Coder.i++] = '+'
        map1[org.yaml.snakeyaml.external.biz.base64Coder.i++] = '/'
    }

    // Mapping table from Base64 characters to 6-bit nibbles.
    private val map2 = ByteArray(128)

    init {
        for (i in map2.indices) map2[i] = -1
        for (i in 0..63) map2[map1[i].code] = i.toByte()
    }

    /**
     * Encodes a string into Base64 format. No blanks or line breaks are
     * inserted.
     *
     * @param s
     * A String to be encoded.
     * @return A String containing the Base64 encoded data.
     */
    fun encodeString(s: String): String {
        return String(encode(s.toByteArray()))
    }
    /**
     * Encodes a byte array into Base 64 format and breaks the output into
     * lines.
     *
     * @param in
     * An array containing the data bytes to be encoded.
     * @param iOff
     * Offset of the first byte in `in` to be processed.
     * @param iLen
     * Number of bytes to be processed in `in`, starting
     * at `iOff`.
     * @param lineLen
     * Line length for the output data. Should be a multiple of 4.
     * @param lineSeparator
     * The line separator to be used to separate the output lines.
     * @return A String containing the Base64 encoded data, broken into lines.
     */
    /**
     * Encodes a byte array into Base 64 format and breaks the output into lines
     * of 76 characters. This method is compatible with
     * `sun.misc.BASE64Encoder.encodeBuffer(byte[])`.
     *
     * @param in
     * An array containing the data bytes to be encoded.
     * @return A String containing the Base64 encoded data, broken into lines.
     */
    @JvmOverloads
    fun encodeLines(
        `in`: ByteArray, iOff: Int = 0, iLen: Int = `in`.length, lineLen: Int = 76,
        lineSeparator: String = systemLineSeparator
    ): String {
        val blockLen = lineLen * 3 / 4
        require(blockLen > 0)
        val lines = (iLen + blockLen - 1) / blockLen
        val bufLen = (iLen + 2) / 3 * 4 + lines * lineSeparator.length
        val buf = StringBuilder(bufLen)
        var ip = 0
        while (ip < iLen) {
            val l = Math.min(iLen - ip, blockLen)
            buf.append(encode(`in`, iOff + ip, l))
            buf.append(lineSeparator)
            ip += l
        }
        return buf.toString()
    }

    /**
     * Encodes a byte array into Base64 format. No blanks or line breaks are
     * inserted in the output.
     *
     * @param in
     * An array containing the data bytes to be encoded.
     * @param iLen
     * Number of bytes to process in `in`.
     * @return A character array containing the Base64 encoded data.
     */
    fun encode(`in`: ByteArray, iLen: Int): CharArray {
        return encode(`in`, 0, iLen)
    }
    /**
     * Encodes a byte array into Base64 format. No blanks or line breaks are
     * inserted in the output.
     *
     * @param in
     * An array containing the data bytes to be encoded.
     * @param iOff
     * Offset of the first byte in `in` to be processed.
     * @param iLen
     * Number of bytes to process in `in`, starting at
     * `iOff`.
     * @return A character array containing the Base64 encoded data.
     */
    /**
     * Encodes a byte array into Base64 format. No blanks or line breaks are
     * inserted in the output.
     *
     * @param in
     * An array containing the data bytes to be encoded.
     * @return A character array containing the Base64 encoded data.
     */
    @JvmOverloads
    fun encode(`in`: ByteArray, iOff: Int = 0, iLen: Int = `in`.length): CharArray {
        val oDataLen = (iLen * 4 + 2) / 3 // output length without padding
        val oLen = (iLen + 2) / 3 * 4 // output length including padding
        val out = CharArray(oLen)
        var ip = iOff
        val iEnd = iOff + iLen
        var op = 0
        while (ip < iEnd) {
            val i0 = `in`[ip++].toInt() and 0xff
            val i1 = if (ip < iEnd) `in`[ip++].toInt() and 0xff else 0
            val i2 = if (ip < iEnd) `in`[ip++].toInt() and 0xff else 0
            val o0 = i0 ushr 2
            val o1 = i0 and 3 shl 4 or (i1 ushr 4)
            val o2 = i1 and 0xf shl 2 or (i2 ushr 6)
            val o3 = i2 and 0x3F
            out[op++] = map1[o0]
            out[op++] = map1[o1]
            out[op] = if (op < oDataLen) map1[o2] else '='
            op++
            out[op] = if (op < oDataLen) map1[o3] else '='
            op++
        }
        return out
    }

    /**
     * Decodes a string from Base64 format. No blanks or line breaks are allowed
     * within the Base64 encoded input data.
     *
     * @param s
     * A Base64 String to be decoded.
     * @return A String containing the decoded data.
     * @throws IllegalArgumentException
     * If the input is not valid Base64 encoded data.
     */
    fun decodeString(s: String): String {
        return String(decode(s))
    }

    /**
     * Decodes a byte array from Base64 format and ignores line separators, tabs
     * and blanks. CR, LF, Tab and Space characters are ignored in the input
     * data. This method is compatible with
     * `sun.misc.BASE64Decoder.decodeBuffer(String)`.
     *
     * @param s
     * A Base64 String to be decoded.
     * @return An array containing the decoded data bytes.
     * @throws IllegalArgumentException
     * If the input is not valid Base64 encoded data.
     */
    fun decodeLines(s: String): ByteArray {
        val buf = CharArray(s.length)
        var p = 0
        for (ip in 0 until s.length) {
            val c = s[ip]
            if (c != ' ' && c != '\r' && c != '\n' && c != '\t') buf[p++] = c
        }
        return decode(buf, 0, p)
    }

    /**
     * Decodes a byte array from Base64 format. No blanks or line breaks are
     * allowed within the Base64 encoded input data.
     *
     * @param s
     * A Base64 String to be decoded.
     * @return An array containing the decoded data bytes.
     * @throws IllegalArgumentException
     * If the input is not valid Base64 encoded data.
     */
    fun decode(s: String): ByteArray {
        return decode(s.toCharArray())
    }
    /**
     * Decodes a byte array from Base64 format. No blanks or line breaks are
     * allowed within the Base64 encoded input data.
     *
     * @param in
     * A character array containing the Base64 encoded data.
     * @param iOff
     * Offset of the first character in `in` to be
     * processed.
     * @param iLen
     * Number of characters to process in `in`, starting
     * at `iOff`.
     * @return An array containing the decoded data bytes.
     * @throws IllegalArgumentException
     * If the input is not valid Base64 encoded data.
     */
    /**
     * Decodes a byte array from Base64 format. No blanks or line breaks are
     * allowed within the Base64 encoded input data.
     *
     * @param in
     * A character array containing the Base64 encoded data.
     * @return An array containing the decoded data bytes.
     * @throws IllegalArgumentException
     * If the input is not valid Base64 encoded data.
     */
    @JvmOverloads
    fun decode(`in`: CharArray, iOff: Int = 0, iLen: Int = `in`.length): ByteArray {
        var iLen = iLen
        require(iLen % 4 == 0) { "Length of Base64 encoded input string is not a multiple of 4." }
        while (iLen > 0 && `in`[iOff + iLen - 1] == '=') iLen--
        val oLen = iLen * 3 / 4
        val out = ByteArray(oLen)
        var ip = iOff
        val iEnd = iOff + iLen
        var op = 0
        while (ip < iEnd) {
            val i0 = `in`[ip++].code
            val i1 = `in`[ip++].code
            val i2 = if (ip < iEnd) `in`[ip++].toInt() else 'A'.code
            val i3 = if (ip < iEnd) `in`[ip++].toInt() else 'A'.code
            require((i0 > 127 || i1 > 127 || i2 > 127 || i3) <= 127) { "Illegal character in Base64 encoded data." }
            val b0 = map2[i0].toInt()
            val b1 = map2[i1].toInt()
            val b2 = map2[i2].toInt()
            val b3 = map2[i3].toInt()
            require((b0 < 0 || b1 < 0 || b2 < 0 || b3) >= 0) { "Illegal character in Base64 encoded data." }
            val o0 = b0 shl 2 or (b1 ushr 4)
            val o1 = b1 and 0xf shl 4 or (b2 ushr 2)
            val o2 = b2 and 3 shl 6 or b3
            out[op++] = o0.toByte()
            if (op < oLen) out[op++] = o1.toByte()
            if (op < oLen) out[op++] = o2.toByte()
        }
        return out
    }
} // end class Base64Coder

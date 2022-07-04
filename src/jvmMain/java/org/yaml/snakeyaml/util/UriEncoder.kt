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
package org.yaml.snakeyaml.util

import org.yaml.snakeyaml.error.YAMLException
import org.yaml.snakeyaml.external.com.google.gdata.util.common.base.Escaper
import org.yaml.snakeyaml.external.com.google.gdata.util.common.base.PercentEscaper
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

object UriEncoder {
    private val UTF8Decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)

    // Include the [] chars to the SAFEPATHCHARS_URLENCODER to avoid
    // its escape as required by spec. See
    // http://yaml.org/spec/1.1/#escaping%20in%20URI/
    private val SAFE_CHARS: String = PercentEscaper.Companion.SAFEPATHCHARS_URLENCODER + "[]/"
    private val escaper: Escaper = PercentEscaper(SAFE_CHARS, false)

    /**
     * Escape special characters with '%'
     * @param uri URI to be escaped
     * @return encoded URI
     */
    @JvmStatic
    fun encode(uri: String): String? {
        return escaper.escape(uri)
    }

    /**
     * Decode '%'-escaped characters. Decoding fails in case of invalid UTF-8
     * @param buff data to decode
     * @return decoded data
     * @throws CharacterCodingException if cannot be decoded
     */
    @Throws(CharacterCodingException::class)
    @JvmStatic
    fun decode(buff: ByteBuffer): String {
        val chars = UTF8Decoder.decode(buff)
        return chars.toString()
    }

    fun decode(buff: String): String {
        return try {
            URLDecoder.decode(buff, "UTF-8")
        } catch (e: UnsupportedEncodingException) {
            throw YAMLException(e)
        }
    }
}

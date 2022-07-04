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

import junit.framework.TestCase
import org.yaml.snakeyaml.util.UriEncoder.decode
import org.yaml.snakeyaml.util.UriEncoder.encode
import java.nio.ByteBuffer

class UriEncoderTest : TestCase() {
    fun testEncode() {
        assertEquals("Acad%C3%A9mico", encode("Académico"))
        assertEquals(
            "Check http://yaml.org/spec/1.1/#escaping%20in%20URI/", "[]",
            encode("[]")
        )
    }

    @Throws(CharacterCodingException::class)
    fun testDecode() {
        val buff = ByteBuffer.allocate(10)
        buff.put(0x34.toByte())
        buff.put(0x35.toByte())
        buff.flip()
        assertEquals("45", decode(buff))
    }

    @Throws(CharacterCodingException::class)
    fun testFailDecode() {
        val buff = ByteBuffer.allocate(10)
        buff.put(0x34.toByte())
        buff.put(0xC1.toByte())
        buff.flip()
        try {
            decode(buff)
            fail("Invalid UTF-8 must not be accepted.")
        } catch (e: Exception) {
            assertEquals("Input length = 1", e.message)
        }
    }
}

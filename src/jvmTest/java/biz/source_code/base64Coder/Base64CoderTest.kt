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
package biz.source_code.base64Coder

import junit.framework.TestCase
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder.decode
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder.encode
import java.io.UnsupportedEncodingException

class Base64CoderTest : TestCase() {
    @Throws(UnsupportedEncodingException::class)
    fun testDecode() {
        check("Aladdin:open sesame", "QWxhZGRpbjpvcGVuIHNlc2FtZQ==")
        check("a", "YQ==")
        check("aa", "YWE=")
        check("a=", "YT0=")
        check("", "")
    }

    @Throws(UnsupportedEncodingException::class)
    fun testFailure1() {
        try {
            decode("YQ=".toCharArray())
            fail()
        } catch (e: Exception) {
            assertEquals(
                "Length of Base64 encoded input string is not a multiple of 4.",
                e.message
            )
        }
    }

    @Throws(UnsupportedEncodingException::class)
    fun testFailure2() {
        checkInvalid("\tWE=")
        checkInvalid("Y\tE=")
        checkInvalid("YW\t=")
        checkInvalid("YWE\t")
        checkInvalid("©WE=")
        checkInvalid("Y©E=")
        checkInvalid("YW©=")
        checkInvalid("YWE©")
    }

    private fun checkInvalid(encoded: String) {
        try {
            decode(encoded.toCharArray())
            fail("Illegal chanracter.")
        } catch (e: Exception) {
            assertEquals("Illegal character in Base64 encoded data.", e.message)
        }
    }

    @Throws(UnsupportedEncodingException::class)
    private fun check(text: String, encoded: String) {
        val s1 = encode(text.toByteArray(Charsets.UTF_8))
        val t1 = String(s1)
        assertEquals(encoded, t1)
        val s2 = decode(encoded.toCharArray())
        val t2 = String(s2, Charsets.UTF_8)
        assertEquals(text, t2)
    }
}

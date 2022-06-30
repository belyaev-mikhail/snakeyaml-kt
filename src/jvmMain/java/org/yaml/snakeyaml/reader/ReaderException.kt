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

import org.yaml.snakeyaml.error.YAMLException
import java.util.*

class ReaderException(val name: String, val position: Int, val codePoint: Int, message: String?) :
    YAMLException(message) {

    override fun toString(): String {
        val s = String(Character.toChars(codePoint))
        return """
               unacceptable code point '$s' (0x${
            Integer.toHexString(codePoint).uppercase(Locale.getDefault())
        }) $message
               in "$name", position $position
               """.trimIndent()
    }

    companion object {
        private const val serialVersionUID = 8710781187529689083L
    }
}
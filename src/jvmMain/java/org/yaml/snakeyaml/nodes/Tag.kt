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
package org.yaml.snakeyaml.nodes

import org.yaml.snakeyaml.error.YAMLException
import org.yaml.snakeyaml.util.UriEncoder
import java.math.BigDecimal
import java.math.BigInteger
import java.net.URI
import java.util.*

class Tag {
    val value: String
    var isSecondary = false // see http://www.yaml.org/refcard.html
        private set

    constructor(tag: String?) {
        if (tag == null) {
            throw NullPointerException("Tag must be provided.")
        } else require(tag.length != 0) { "Tag must not be empty." }
            require(tag.trim { it <= ' ' }.length == tag.length) { "Tag must not contain leading or trailing spaces." }
        value = UriEncoder.encode(tag)!!
        isSecondary = !tag.startsWith(PREFIX)
    }

    constructor(clazz: Class<out Any?>?) {
        if (clazz == null) {
            throw NullPointerException("Class for tag must be provided.")
        }
        value = PREFIX + UriEncoder.encode(clazz.name)
    }

    /**
     * @param uri
     * - URI to be encoded as tag value
     */
    @Deprecated(
        """- it will be removed
      """
    )
    constructor(uri: URI?) {
        if (uri == null) {
            throw NullPointerException("URI for tag must be provided.")
        }
        value = uri.toASCIIString()
    }

    fun startsWith(prefix: String?): Boolean {
        return value.startsWith(prefix!!)
    }

    val className: String?
        get() {
            if (!value.startsWith(PREFIX)) {
                throw YAMLException("Invalid tag: $value")
            }
            return UriEncoder.decode(value.substring(PREFIX.length))
        }

    override fun toString(): String {
        return value
    }

    override fun equals(obj: Any?): Boolean {
        return if (obj is Tag) {
            value == obj.value
        } else false
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    /**
     * Java has more then 1 class compatible with a language-independent tag (!!int, !!float, !!timestamp etc)
     *
     * @param clazz
     * - Class to check compatibility
     * @return true when the Class can be represented by this language-independent tag
     */
    fun isCompatible(clazz: Class<*>): Boolean {
        val set = COMPATIBILITY_MAP!![this]
        return set?.contains(clazz) ?: false
    }

    /**
     * Check whether this tag matches the global tag for the Class
     *
     * @param clazz
     * - Class to check
     * @return true when the this tag can be used as a global tag for the Class
     */
    fun matches(clazz: Class<out Any?>?): Boolean {
        return value == PREFIX + clazz!!.name
    }

    companion object {
        const val PREFIX = "tag:yaml.org,2002:"
        val YAML = Tag(PREFIX + "yaml")
        val MERGE = Tag(PREFIX + "merge")
        val SET = Tag(PREFIX + "set")
        val PAIRS = Tag(PREFIX + "pairs")
        val OMAP = Tag(PREFIX + "omap")
        val BINARY = Tag(PREFIX + "binary")
        val INT = Tag(PREFIX + "int")
        val FLOAT = Tag(PREFIX + "float")
        val TIMESTAMP = Tag(PREFIX + "timestamp")
        val BOOL = Tag(PREFIX + "bool")
        val NULL = Tag(PREFIX + "null")
        val STR = Tag(PREFIX + "str")
        val SEQ = Tag(PREFIX + "seq")
        val MAP = Tag(PREFIX + "map")

        // For use to indicate a DUMMY node that contains comments, when there is no other (empty document)
        val COMMENT = Tag(PREFIX + "comment")
        protected val COMPATIBILITY_MAP: MutableMap<Tag, Set<Class<*>>>

        init {
            COMPATIBILITY_MAP = HashMap()
            val floatSet: MutableSet<Class<*>> = HashSet()
            floatSet.add(Double::class.java)
            floatSet.add(Float::class.java)
            floatSet.add(BigDecimal::class.java)
            COMPATIBILITY_MAP[FLOAT] = floatSet
            //
            val intSet: MutableSet<Class<*>> = HashSet()
            intSet.add(Int::class.java)
            intSet.add(Long::class.java)
            intSet.add(BigInteger::class.java)
            COMPATIBILITY_MAP[INT] = intSet
            //
            val timestampSet: MutableSet<Class<*>> = HashSet()
            timestampSet.add(Date::class.java)

            // java.sql is a separate module since jigsaw was introduced in java9
            try {
                timestampSet.add(Class.forName("java.sql.Date"))
                timestampSet.add(Class.forName("java.sql.Timestamp"))
            } catch (ignored: ClassNotFoundException) {
                // ignore - we are running in a module path without java.sql
            }
            COMPATIBILITY_MAP[TIMESTAMP] = timestampSet
        }
    }
}
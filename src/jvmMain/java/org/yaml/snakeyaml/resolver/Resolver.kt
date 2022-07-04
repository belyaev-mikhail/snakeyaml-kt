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
package org.yaml.snakeyaml.resolver

import org.yaml.snakeyaml.nodes.NodeId
import org.yaml.snakeyaml.nodes.Tag
import java.util.regex.Pattern

/**
 * Resolver tries to detect a type by content (when the tag is implicit)
 */
open class Resolver {
    protected var yamlImplicitResolvers: MutableMap<Char?, MutableList<ResolverTuple>> = HashMap()
    protected open fun addImplicitResolvers() {
        addImplicitResolver(Tag.Companion.BOOL, BOOL, "yYnNtTfFoO", 10)
        /*
         * INT must be before FLOAT because the regular expression for FLOAT
         * matches INT (see issue 130)
         * http://code.google.com/p/snakeyaml/issues/detail?id=130
         */addImplicitResolver(Tag.Companion.INT, INT, "-+0123456789")
        addImplicitResolver(Tag.Companion.FLOAT, FLOAT, "-+0123456789.")
        addImplicitResolver(Tag.Companion.MERGE, MERGE, "<", 10)
        addImplicitResolver(Tag.Companion.NULL, NULL, "~nN\u0000", 10)
        addImplicitResolver(Tag.Companion.NULL, EMPTY, null, 10)
        addImplicitResolver(Tag.Companion.TIMESTAMP, TIMESTAMP, "0123456789", 50)
        // The following implicit resolver is only for documentation purposes.
        // It cannot work because plain scalars cannot start with '!', '&', or '*'.
        addImplicitResolver(Tag.Companion.YAML, YAML, "!&*", 10)
    }

    init {
        addImplicitResolvers()
    }

    fun addImplicitResolver(tag: Tag, regexp: Pattern, first: String?) {
        addImplicitResolver(tag, regexp, first, 1024)
    }

    fun addImplicitResolver(tag: Tag, regexp: Pattern, first: String?, limit: Int) {
        if (first == null) {
            var curr = yamlImplicitResolvers[null]
            if (curr == null) {
                curr = ArrayList()
                yamlImplicitResolvers[null] = curr
            }
            curr.add(ResolverTuple(tag, regexp, limit))
        } else {
            val chrs = first.toCharArray()
            var i = 0
            val j = chrs.size
            while (i < j) {
                var theC = Character.valueOf(chrs[i])
                if (theC.code == 0) {
                    // special case: for null
                    theC = null
                }
                var curr = yamlImplicitResolvers[theC]
                if (curr == null) {
                    curr = ArrayList()
                    yamlImplicitResolvers[theC] = curr
                }
                curr.add(ResolverTuple(tag, regexp, limit))
                i++
            }
        }
    }

    fun resolve(kind: NodeId, value: String?, implicit: Boolean): Tag {
        if (kind == NodeId.scalar && implicit) {
            value!!
            val resolvers: List<ResolverTuple>?
            resolvers = if (value.length == 0) {
                yamlImplicitResolvers['\u0000']
            } else {
                yamlImplicitResolvers[value[0]]
            }
            if (resolvers != null) {
                for (v in resolvers) {
                    val tag = v.tag
                    val regexp = v.regexp
                    if (value.length <= v.limit && regexp!!.matcher(value).matches()) {
                        return tag
                    }
                }
            }
            if (yamlImplicitResolvers.containsKey(null)) {
                // check null resolver
                for (v in yamlImplicitResolvers[null]!!) {
                    val tag = v.tag
                    val regexp = v.regexp
                    if (value.length <= v.limit && regexp!!.matcher(value).matches()) {
                        return tag
                    }
                }
            }
        }
        return when (kind) {
            NodeId.scalar -> Tag.Companion.STR
            NodeId.sequence -> Tag.Companion.SEQ
            else -> Tag.Companion.MAP
        }
    }

    companion object {
        @JvmField
        val BOOL = Pattern
            .compile("^(?:yes|Yes|YES|no|No|NO|true|True|TRUE|false|False|FALSE|on|On|ON|off|Off|OFF)$")

        /**
         * The regular expression is taken from the 1.2 specification but '_'s are
         * added to keep backwards compatibility
         */
        @JvmField
        val FLOAT = Pattern
            .compile(
                "^(" +
                        "[-+]?(?:[0-9][0-9_]*)\\.[0-9_]*(?:[eE][-+]?[0-9]+)?" +  // (base 10)
                        "|[-+]?(?:[0-9][0-9_]*)(?:[eE][-+]?[0-9]+)" +  // (base 10, scientific notation without .)
                        "|[-+]?\\.[0-9_]+(?:[eE][-+]?[0-9]+)?" +  // (base 10, starting with .)
                        "|[-+]?[0-9][0-9_]*(?::[0-5]?[0-9])+\\.[0-9_]*" +  // (base 60)
                        "|[-+]?\\.(?:inf|Inf|INF)" +
                        "|\\.(?:nan|NaN|NAN)" +
                        ")$"
            )
        @JvmField
        val INT = Pattern
            .compile(
                "^(?:" +
                        "[-+]?0b_*[0-1]+[0-1_]*" +  // (base 2)
                        "|[-+]?0_*[0-7]+[0-7_]*" +  // (base 8)
                        "|[-+]?(?:0|[1-9][0-9_]*)" +  // (base 10)
                        "|[-+]?0x_*[0-9a-fA-F]+[0-9a-fA-F_]*" +  // (base 16)
                        "|[-+]?[1-9][0-9_]*(?::[0-5]?[0-9])+" +  // (base 60)
                        ")$"
            )
        @JvmField
        val MERGE = Pattern.compile("^(?:<<)$")
        @JvmField
        val NULL = Pattern.compile("^(?:~|null|Null|NULL| )$")
        @JvmField
        val EMPTY = Pattern.compile("^$")
        @JvmField
        val TIMESTAMP = Pattern
            .compile("^(?:[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]|[0-9][0-9][0-9][0-9]-[0-9][0-9]?-[0-9][0-9]?(?:[Tt]|[ \t]+)[0-9][0-9]?:[0-9][0-9]:[0-9][0-9](?:\\.[0-9]*)?(?:[ \t]*(?:Z|[-+][0-9][0-9]?(?::[0-9][0-9])?))?)$")
        val VALUE = Pattern.compile("^(?:=)$")
        val YAML = Pattern.compile("^(?:!|&|\\*)$")
    }
}

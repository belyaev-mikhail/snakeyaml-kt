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
package org.yaml.snakeyaml.constructor

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.error.YAMLException
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder
import org.yaml.snakeyaml.nodes.*
import java.math.BigInteger
import java.util.*
import java.util.regex.Pattern

/**
 * Construct standard Java classes
 */
open class SafeConstructor @JvmOverloads constructor(loadingConfig: LoaderOptions? = LoaderOptions()) : BaseConstructor(
    loadingConfig!!
) {
    protected fun flattenMapping(node: MappingNode, forceStringKeys: Boolean = false) {
        // perform merging only on nodes containing merge node(s)
        processDuplicateKeys(node, forceStringKeys)
        if (node.isMerged) {
            node.value = mergeNode(
                node, true, HashMap(),
                ArrayList(), forceStringKeys
            )
        }
    }

    protected fun processDuplicateKeys(node: MappingNode, forceStringKeys: Boolean = false) {
        val nodeValue = node.value
        val keys: MutableMap<Any?, Int> = HashMap(nodeValue.size)
        val toRemove = TreeSet<Int>()
        var i = 0
        for (tuple in nodeValue) {
            val keyNode = tuple.keyNode
            if (keyNode.tag != Tag.MERGE) {
                if (forceStringKeys) {
                    if (keyNode is ScalarNode) {
                        keyNode.setType(String::class.java)
                        keyNode.setTag(Tag.STR)
                    } else {
                        throw YAMLException(
                            "Keys must be scalars but found: $keyNode"
                        )
                    }
                }
                val key = constructObject(keyNode)
                if (key != null && !forceStringKeys) {
                    try {
                        key.hashCode() // check circular dependencies
                    } catch (e: Exception) {
                        throw ConstructorException(
                            "while constructing a mapping",
                            node.startMark, "found unacceptable key $key",
                            tuple.keyNode.startMark, e
                        )
                    }
                }
                val prevIndex = keys.put(key, i)
                if (prevIndex != null) {
                    if (!isAllowDuplicateKeys) {
                        throw DuplicateKeyException(
                            node.startMark, key,
                            tuple.keyNode.startMark
                        )
                    }
                    toRemove.add(prevIndex)
                }
            }
            i = i + 1
        }
        val indices2remove = toRemove.descendingIterator()
        while (indices2remove.hasNext()) {
            nodeValue.removeAt(indices2remove.next().toInt())
        }
    }

    /**
     * Does merge for supplied mapping node.
     *
     * @param node
     * where to merge
     * @param isPreffered
     * true if keys of node should take precedence over others...
     * @param key2index
     * maps already merged keys to index from values
     * @param values
     * collects merged NodeTuple
     * @return list of the merged NodeTuple (to be set as value for the
     * MappingNode)
     */
    private fun mergeNode(
        node: MappingNode, isPreffered: Boolean,
        key2index: MutableMap<Any?, Int>, values: MutableList<NodeTuple>, forceStringKeys: Boolean
    ): List<NodeTuple> {
        val iter = node.value.iterator()
        while (iter.hasNext()) {
            val nodeTuple = iter.next()
            val keyNode = nodeTuple.keyNode
            val valueNode = nodeTuple.valueNode
            if (keyNode.tag == Tag.MERGE) {
                iter.remove()
                when (valueNode.nodeId) {
                    NodeId.mapping -> {
                        val mn = valueNode as MappingNode
                        mergeNode(mn, false, key2index, values, forceStringKeys)
                    }
                    NodeId.sequence -> {
                        val sn = valueNode as SequenceNode
                        val vals = sn.value
                        for (subnode in vals) {
                            if (subnode !is MappingNode) {
                                throw ConstructorException(
                                    "while constructing a mapping",
                                    node.startMark, "expected a mapping for merging, but found "
                                            + subnode.nodeId,
                                    subnode.startMark
                                )
                            }
                            mergeNode(subnode, false, key2index, values, forceStringKeys)
                        }
                    }
                    else -> throw ConstructorException(
                        "while constructing a mapping",
                        node.startMark, "expected a mapping or list of mappings for merging, but found "
                                + valueNode.nodeId,
                        valueNode.startMark
                    )
                }
            } else {
                // we need to construct keys to avoid duplications
                if (forceStringKeys) {
                    if (keyNode is ScalarNode) {
                        keyNode.setType(String::class.java)
                        keyNode.setTag(Tag.STR)
                    } else {
                        throw YAMLException("Keys must be scalars but found: $keyNode")
                    }
                }
                val key = constructObject(keyNode)
                if (!key2index.containsKey(key)) { // 1st time merging key
                    values.add(nodeTuple)
                    // keep track where tuple for the key is
                    key2index[key] = values.size - 1
                } else if (isPreffered) { // there is value for the key, but we
                    // need to override it
                    // change value for the key using saved position
                    values[key2index[key]!!] = nodeTuple
                }
            }
        }
        return values
    }

    override fun constructMapping2ndStep(node: MappingNode, mapping: MutableMap<Any?, Any?>) {
        flattenMapping(node)
        super.constructMapping2ndStep(node, mapping)
    }

    override fun constructSet2ndStep(node: MappingNode, set: MutableSet<Any?>) {
        flattenMapping(node)
        super.constructSet2ndStep(node, set)
    }

    inner class ConstructYamlNull : AbstractConstruct() {
        override fun construct(node: Node?): Any? {
            if (node != null) constructScalar((node as ScalarNode?)!!)
            return null
        }
    }

    inner class ConstructYamlBool : AbstractConstruct() {
        override fun construct(node: Node?): Any? {
            return BOOL_VALUES[constructScalar((node as ScalarNode?)!!).lowercase(Locale.getDefault())]
        }
    }

    inner class ConstructYamlInt : AbstractConstruct() {
        override fun construct(node: Node?): Any? {
            var value = constructScalar((node as ScalarNode?)!!).replace("_".toRegex(), "")
            if (value.isEmpty()) {
                throw ConstructorException(
                    "while constructing an int",
                    node!!.startMark, "found empty value",
                    node.startMark
                )
            }
            var sign = +1
            val first = value[0]
            if (first == '-') {
                sign = -1
                value = value.substring(1)
            } else if (first == '+') {
                value = value.substring(1)
            }
            var base = 10
            if ("0" == value) {
                return Integer.valueOf(0)
            } else if (value.startsWith("0b")) {
                value = value.substring(2)
                base = 2
            } else if (value.startsWith("0x")) {
                value = value.substring(2)
                base = 16
            } else if (value.startsWith("0")) {
                value = value.substring(1)
                base = 8
            } else if (value.indexOf(':') != -1) {
                val digits = value.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                var bes = 1
                var `val` = 0
                var i = 0
                val j = digits.size
                while (i < j) {
                    `val` += (digits[j - i - 1].toLong() * bes).toInt()
                    bes *= 60
                    i++
                }
                return createNumber(sign, `val`.toString(), 10)
            } else {
                return createNumber(sign, value, 10)
            }
            return createNumber(sign, value, base)
        }
    }

    private fun createNumber(sign: Int, number: String, radix: Int): Number {
        var number: String? = number
        val len = number?.length ?: 0
        if (sign < 0) {
            number = "-$number"
        }
        val maxArr = if (radix < RADIX_MAX.size) RADIX_MAX[radix] else null
        if (maxArr != null) {
            val gtInt = len > maxArr[0]
            if (gtInt) {
                return if (len > maxArr[1]) {
                    BigInteger(number, radix)
                } else createLongOrBigInteger(number, radix)
            }
        }
        val result: Number
        result = try {
            Integer.valueOf(number, radix)
        } catch (e: NumberFormatException) {
            createLongOrBigInteger(number, radix)
        }
        return result
    }

    inner class ConstructYamlFloat : AbstractConstruct() {
        override fun construct(node: Node?): Any? {
            var value = constructScalar((node as ScalarNode?)!!).replace("_".toRegex(), "")
            if (value.isEmpty()) {
                throw ConstructorException(
                    "while constructing a float",
                    node!!.startMark, "found empty value",
                    node.startMark
                )
            }
            var sign = +1
            val first = value[0]
            if (first == '-') {
                sign = -1
                value = value.substring(1)
            } else if (first == '+') {
                value = value.substring(1)
            }
            val valLower = value.lowercase(Locale.getDefault())
            return if (".inf" == valLower) {
                java.lang.Double
                    .valueOf(if (sign == -1) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY)
            } else if (".nan" == valLower) {
                java.lang.Double.valueOf(Double.NaN)
            } else if (value.indexOf(':') != -1) {
                val digits = value.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                var bes = 1
                var `val` = 0.0
                var i = 0
                val j = digits.size
                while (i < j) {
                    `val` += digits[j - i - 1].toDouble() * bes
                    bes *= 60
                    i++
                }
                java.lang.Double.valueOf(sign * `val`)
            } else {
                val d = java.lang.Double.valueOf(value)
                java.lang.Double.valueOf(d.toDouble() * sign)
            }
        }
    }

    inner class ConstructYamlBinary : AbstractConstruct() {
        override fun construct(node: Node?): Any? {
            // Ignore white spaces for base64 encoded scalar
            val noWhiteSpaces =
                constructScalar((node as ScalarNode?)!!).replace(
                    "\\s".toRegex(),
                    ""
                )
            return Base64Coder.decode(noWhiteSpaces.toCharArray())
        }
    }

    init {
        yamlConstructors[Tag.NULL] = ConstructYamlNull()
        yamlConstructors[Tag.BOOL] = ConstructYamlBool()
        yamlConstructors[Tag.INT] =
            ConstructYamlInt()
        yamlConstructors[Tag.FLOAT] = ConstructYamlFloat()
        yamlConstructors[Tag.BINARY] =
            ConstructYamlBinary()
        yamlConstructors[Tag.TIMESTAMP] =
            ConstructYamlTimestamp()
        yamlConstructors[Tag.OMAP] = ConstructYamlOmap()
        yamlConstructors[Tag.PAIRS] = ConstructYamlPairs()
        yamlConstructors[Tag.SET] = ConstructYamlSet()
        yamlConstructors[Tag.STR] = ConstructYamlStr()
        yamlConstructors[Tag.SEQ] = ConstructYamlSeq()
        yamlConstructors[Tag.MAP] = ConstructYamlMap()
        yamlConstructors[null] = undefinedConstructor
        yamlClassConstructors[NodeId.scalar] =
            undefinedConstructor
        yamlClassConstructors[NodeId.sequence] =
            undefinedConstructor
        yamlClassConstructors[NodeId.mapping] =
            undefinedConstructor
    }

    class ConstructYamlTimestamp : AbstractConstruct() {
        var calendar: Calendar? = null
            private set

        override fun construct(node: Node?): Any? {
            val scalar = node as ScalarNode?
            val nodeValue = scalar!!.value
            var match = YMD_REGEXP.matcher(nodeValue)
            return if (match.matches()) {
                val year_s = match.group(1)
                val month_s = match.group(2)
                val day_s = match.group(3)
                calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                calendar!!.clear()
                calendar!!.set(Calendar.YEAR, year_s.toInt())
                // Java's months are zero-based...
                calendar!!.set(Calendar.MONTH, month_s.toInt() - 1) // x
                calendar!!.set(Calendar.DAY_OF_MONTH, day_s.toInt())
                calendar!!.getTime()
            } else {
                match = TIMESTAMP_REGEXP.matcher(nodeValue)
                if (!match.matches()) {
                    throw YAMLException("Unexpected timestamp: $nodeValue")
                }
                val year_s = match.group(1)
                val month_s = match.group(2)
                val day_s = match.group(3)
                val hour_s = match.group(4)
                val min_s = match.group(5)
                // seconds and milliseconds
                var seconds = match.group(6)
                val millis = match.group(7)
                if (millis != null) {
                    seconds = "$seconds.$millis"
                }
                val fractions = seconds.toDouble()
                val sec_s = Math.round(Math.floor(fractions)).toInt()
                val usec = Math.round((fractions - sec_s) * 1000).toInt()
                // timezone
                val timezoneh_s = match.group(8)
                val timezonem_s = match.group(9)
                val timeZone: TimeZone
                timeZone = if (timezoneh_s != null) {
                    val time = if (timezonem_s != null) ":$timezonem_s" else "00"
                    TimeZone.getTimeZone("GMT$timezoneh_s$time")
                } else {
                    // no time zone provided
                    TimeZone.getTimeZone("UTC")
                }
                calendar = Calendar.getInstance(timeZone)
                calendar!!.set(Calendar.YEAR, year_s.toInt())
                // Java's months are zero-based...
                calendar!!.set(Calendar.MONTH, month_s.toInt() - 1)
                calendar!!.set(Calendar.DAY_OF_MONTH, day_s.toInt())
                calendar!!.set(Calendar.HOUR_OF_DAY, hour_s.toInt())
                calendar!!.set(Calendar.MINUTE, min_s.toInt())
                calendar!!.set(Calendar.SECOND, sec_s)
                calendar!!.set(Calendar.MILLISECOND, usec)
                calendar!!.getTime()
            }
        }
    }

    inner class ConstructYamlOmap : AbstractConstruct() {
        override fun construct(node: Node?): Any? {
            // Note: we do not check for duplicate keys, because it's too
            // CPU-expensive.
            val omap: MutableMap<Any?, Any?> = LinkedHashMap()
            if (node !is SequenceNode) {
                throw ConstructorException(
                    "while constructing an ordered map",
                    node!!.startMark, "expected a sequence, but found " + node.nodeId,
                    node.startMark
                )
            }
            for (subnode in node.value) {
                if (subnode !is MappingNode) {
                    throw ConstructorException(
                        "while constructing an ordered map",
                        node.startMark,
                        "expected a mapping of length 1, but found " + subnode.nodeId,
                        subnode.startMark
                    )
                }
                val mnode = subnode
                if (mnode.value.size != 1) {
                    throw ConstructorException(
                        "while constructing an ordered map",
                        node.startMark, "expected a single mapping item, but found "
                                + mnode.value.size + " items",
                        mnode.startMark
                    )
                }
                val keyNode = mnode.value[0].keyNode
                val valueNode = mnode.value[0].valueNode
                val key = constructObject(keyNode)
                val value = constructObject(valueNode)
                omap[key] = value
            }
            return omap
        }
    }

    inner class ConstructYamlPairs : AbstractConstruct() {
        override fun construct(node: Node?): Any? {
            // Note: we do not check for duplicate keys, because it's too
            // CPU-expensive.
            if (node !is SequenceNode) {
                throw ConstructorException(
                    "while constructing pairs", node!!.startMark,
                    "expected a sequence, but found " + node.nodeId, node.startMark
                )
            }
            val snode = node
            val pairs: MutableList<Array<Any?>> = ArrayList(snode.value.size)
            for (subnode in snode.value) {
                if (subnode !is MappingNode) {
                    throw ConstructorException(
                        "while constructingpairs", node.startMark,
                        "expected a mapping of length 1, but found " + subnode.nodeId,
                        subnode.startMark
                    )
                }
                val mnode = subnode
                if (mnode.value.size != 1) {
                    throw ConstructorException(
                        "while constructing pairs", node.startMark,
                        "expected a single mapping item, but found " + mnode.value.size
                                + " items",
                        mnode.startMark
                    )
                }
                val keyNode = mnode.value[0].keyNode
                val valueNode = mnode.value[0].valueNode
                val key = constructObject(keyNode)
                val value = constructObject(valueNode)
                pairs.add(arrayOf(key, value))
            }
            return pairs
        }
    }

    inner class ConstructYamlSet : Construct {
        override fun construct(node: Node?): Any? {
            return if (node!!.isTwoStepsConstruction) {
                if (constructedObjects.containsKey(node)) constructedObjects[node] else createDefaultSet((node as MappingNode?)!!.value.size)
            } else {
                constructSet((node as MappingNode?)!!)
            }
        }

        override fun construct2ndStep(node: Node?, `object`: Any?) {
            if (node!!.isTwoStepsConstruction) {
                constructSet2ndStep((node as MappingNode?)!!, (`object` as MutableSet<Any?>?)!!)
            } else {
                throw YAMLException("Unexpected recursive set structure. Node: $node")
            }
        }
    }

    inner class ConstructYamlStr : AbstractConstruct() {
        override fun construct(node: Node?): Any? {
            return constructScalar((node as ScalarNode?)!!)
        }
    }

    inner class ConstructYamlSeq : Construct {
        override fun construct(node: Node?): Any? {
            val seqNode = node as SequenceNode?
            return if (node!!.isTwoStepsConstruction) {
                newList(seqNode!!)
            } else {
                constructSequence(seqNode!!)
            }
        }

        override fun construct2ndStep(node: Node?, data: Any?) {
            if (node!!.isTwoStepsConstruction) {
                constructSequenceStep2((node as SequenceNode?)!!, data as MutableList<Any?>)
            } else {
                throw YAMLException("Unexpected recursive sequence structure. Node: $node")
            }
        }
    }

    inner class ConstructYamlMap : Construct {
        override fun construct(node: Node?): Any? {
            val mnode = node as MappingNode?
            return if (node!!.isTwoStepsConstruction) {
                createDefaultMap(mnode!!.value.size)
            } else {
                constructMapping(mnode!!)
            }
        }

        override fun construct2ndStep(node: Node?, `object`: Any?) {
            if (node!!.isTwoStepsConstruction) {
                constructMapping2ndStep((node as MappingNode?)!!, (`object` as MutableMap<Any?, Any?>?)!!)
            } else {
                throw YAMLException("Unexpected recursive mapping structure. Node: $node")
            }
        }
    }

    class ConstructUndefined : AbstractConstruct() {
        override fun construct(node: Node?): Any? {
            throw ConstructorException(
                null, null,
                "could not determine a constructor for the tag " + node!!.tag,
                node.startMark
            )
        }
    }

    companion object {
        val undefinedConstructor = ConstructUndefined()
        private val BOOL_VALUES: MutableMap<String, Boolean> = HashMap()

        init {
            BOOL_VALUES["yes"] = java.lang.Boolean.TRUE
            BOOL_VALUES["no"] = java.lang.Boolean.FALSE
            BOOL_VALUES["true"] = java.lang.Boolean.TRUE
            BOOL_VALUES["false"] = java.lang.Boolean.FALSE
            BOOL_VALUES["on"] = java.lang.Boolean.TRUE
            BOOL_VALUES["off"] = java.lang.Boolean.FALSE
        }

        private val RADIX_MAX = Array(17) { IntArray(2) }

        init {
            val radixList = intArrayOf(2, 8, 10, 16)
            for (radix in radixList) {
                RADIX_MAX[radix] = intArrayOf(maxLen(Int.MAX_VALUE, radix), maxLen(Long.MAX_VALUE, radix))
            }
        }

        private fun maxLen(max: Int, radix: Int): Int {
            return Integer.toString(max, radix).length
        }

        private fun maxLen(max: Long, radix: Int): Int {
            return java.lang.Long.toString(max, radix).length
        }

        protected fun createLongOrBigInteger(number: String?, radix: Int): Number {
            return try {
                java.lang.Long.valueOf(number, radix)
            } catch (e1: NumberFormatException) {
                BigInteger(number, radix)
            }
        }

        private val TIMESTAMP_REGEXP = Pattern.compile(
            "^([0-9][0-9][0-9][0-9])-([0-9][0-9]?)-([0-9][0-9]?)(?:(?:[Tt]|[ \t]+)([0-9][0-9]?):([0-9][0-9]):([0-9][0-9])(?:\\.([0-9]*))?(?:[ \t]*(?:Z|([-+][0-9][0-9]?)(?::([0-9][0-9])?)?))?)?$"
        )
        private val YMD_REGEXP = Pattern
            .compile("^([0-9][0-9][0-9][0-9])-([0-9][0-9]?)-([0-9][0-9]?)$")
    }
}
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
package org.yaml.snakeyaml.representer

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.introspector.PropertyUtils
import org.yaml.snakeyaml.nodes.*
import java.util.*

/**
 * Represent basic YAML structures: scalar, sequence, mapping
 */
abstract class BaseRepresenter {
    @JvmField
    protected val representers: MutableMap<Class<*>?, Represent> = HashMap()

    /**
     * in Java 'null' is not a type. So we have to keep the null representer
     * separately otherwise it will coincide with the default representer which
     * is stored with the key null.
     */
    @JvmField
    protected var nullRepresenter: Represent? = null

    // the order is important (map can be also a sequence of key-values)
    @JvmField
    protected val multiRepresenters: MutableMap<Class<*>?, Represent> = LinkedHashMap()
    private var defaultScalarStyle__: DumperOptions.ScalarStyle? = null
    var defaultScalarStyle: DumperOptions.ScalarStyle
        set(defaultStyle: DumperOptions.ScalarStyle) {
            defaultScalarStyle__ = defaultStyle
        }
        get(): DumperOptions.ScalarStyle {
            return defaultScalarStyle__ ?: DumperOptions.ScalarStyle.PLAIN
        }

    var defaultFlowStyle = DumperOptions.FlowStyle.AUTO
    protected val representedObjects: MutableMap<Any?, Node> = object : IdentityHashMap<Any, Node>() {
        override fun put(key: Any, value: Node): Node? {
            return super.put(key, AnchorNode(value))
        }
    }
    protected var objectToRepresent: Any? = null
    open var propertyUtils: PropertyUtils? = null
        get() {
            if (field == null) {
                field = PropertyUtils()
            }
            return field
        }
        set(propertyUtils) {
            field = propertyUtils
            isExplicitPropertyUtils = true
        }
    var isExplicitPropertyUtils = false
        private set

    fun represent(data: Any?): Node? {
        val node = representData(data)
        representedObjects.clear()
        objectToRepresent = null
        return node
    }

    protected fun representData(data: Any?): Node? {
        objectToRepresent = data
        // check for identity
        if (representedObjects.containsKey(objectToRepresent)) {
            return representedObjects[objectToRepresent]
        }
        // }
        // check for null first
        if (data == null) {
            return nullRepresenter!!.representData(null)
        }
        // check the same class
        val node: Node
        val clazz: Class<*> = data.javaClass
        if (representers.containsKey(clazz)) {
            val representer = representers[clazz]
            node = representer!!.representData(data)!!
        } else {
            // check the parents
            for (repr in multiRepresenters.keys) {
                if (repr != null && repr.isInstance(data)) {
                    val representer = multiRepresenters[repr]
                    node = representer!!.representData(data)!!
                    return node
                }
            }

            // check defaults
            node = if (multiRepresenters.containsKey(null)) {
                val representer = multiRepresenters[null]
                representer!!.representData(data)!!
            } else {
                val representer = representers[null]
                representer!!.representData(data)!!
            }
        }
        return node
    }

    protected fun representScalar(
        tag: Tag,
        value: String,
        style: DumperOptions.ScalarStyle?
    ): Node = ScalarNode(tag, value, null, null, style ?: defaultScalarStyle)

    protected fun representScalar(tag: Tag, value: String): Node = representScalar(tag, value, null)

    protected fun representSequence(tag: Tag, sequence: Iterable<*>, flowStyle: DumperOptions.FlowStyle): Node {
        var size = 10 // default for ArrayList
        if (sequence is List<*>) {
            size = sequence.size
        }
        val value: MutableList<Node> = ArrayList(size)
        val node = SequenceNode(tag, value, flowStyle)
        representedObjects[objectToRepresent] = node
        var bestStyle = DumperOptions.FlowStyle.FLOW
        for (item in sequence) {
            val nodeItem = representData(item)
            if (!(nodeItem is ScalarNode && nodeItem.isPlain)) {
                bestStyle = DumperOptions.FlowStyle.BLOCK
            }
            value.add(nodeItem!!)
        }
        if (flowStyle == DumperOptions.FlowStyle.AUTO) {
            if (defaultFlowStyle != DumperOptions.FlowStyle.AUTO) {
                node.flowStyle = defaultFlowStyle
            } else {
                node.flowStyle = bestStyle
            }
        }
        return node
    }

    protected fun representMapping(tag: Tag, mapping: Map<*, *>, flowStyle: DumperOptions.FlowStyle): Node {
        val value: MutableList<NodeTuple> = ArrayList(mapping.size)
        val node = MappingNode(tag, value, flowStyle)
        representedObjects[objectToRepresent] = node
        var bestStyle = DumperOptions.FlowStyle.FLOW
        for ((key, value1) in mapping) {
            val nodeKey = representData(key)
            val nodeValue = representData(value1)
            if (!(nodeKey is ScalarNode && nodeKey.isPlain)) {
                bestStyle = DumperOptions.FlowStyle.BLOCK
            }
            if (!(nodeValue is ScalarNode && nodeValue.isPlain)) {
                bestStyle = DumperOptions.FlowStyle.BLOCK
            }
            value.add(NodeTuple(nodeKey, nodeValue))
        }
        if (flowStyle == DumperOptions.FlowStyle.AUTO) {
            if (defaultFlowStyle != DumperOptions.FlowStyle.AUTO) {
                node.flowStyle = defaultFlowStyle
            } else {
                node.flowStyle = bestStyle
            }
        }
        return node
    }


}

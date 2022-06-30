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

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.DumperOptions.FlowStyle.Companion.fromBoolean
import org.yaml.snakeyaml.error.Mark

/**
 * Represents a map.
 *
 *
 * A map is a collection of unsorted key-value pairs.
 *
 */
class MappingNode(
    tag: Tag?, resolved: Boolean, value: List<NodeTuple>, startMark: Mark?,
    endMark: Mark?, flowStyle: DumperOptions.FlowStyle?
) : CollectionNode<NodeTuple?>(tag, startMark, endMark, flowStyle) {
    /**
     * Returns the entries of this map.
     *
     * @return List of entries.
     */
    override var value: List<NodeTuple> = value
    /**
     * @return true if map contains merge node
     */
    /**
     * @param merged
     * - true if map contains merge node
     */
    var isMerged = false

    init {
        this.isResolved = resolved
    }

    constructor(tag: Tag?, value: List<NodeTuple>, flowStyle: DumperOptions.FlowStyle?) : this(
        tag,
        true,
        value,
        null,
        null,
        flowStyle
    ) {
    }

    /*
     * Existed in older versions but replaced with {@link DumperOptions.FlowStyle}-based constructor.
     * Restored in v1.22 for backwards compatibility.
     * @deprecated Since restored in v1.22.  Use {@link MappingNode#MappingNode(Tag, boolean, List, Mark, Mark, org.yaml.snakeyaml.DumperOptions.FlowStyle) }.
     */
    @Deprecated("")
    constructor(
        tag: Tag?, resolved: Boolean, value: List<NodeTuple>, startMark: Mark?,
        endMark: Mark?, flowStyle: Boolean?
    ) : this(tag, resolved, value, startMark, endMark, fromBoolean(flowStyle)) {
    }

    /*
     * Existed in older versions but replaced with {@link DumperOptions.FlowStyle}-based constructor.
     * Restored in v1.22 for backwards compatibility.
     * @deprecated Since restored in v1.22.  Use {@link MappingNode#MappingNode(Tag, List, org.yaml.snakeyaml.DumperOptions.FlowStyle) }.
     */
    @Deprecated("")
    constructor(tag: Tag?, value: List<NodeTuple>, flowStyle: Boolean?) : this(tag, value, fromBoolean(flowStyle)) {
    }

    override val nodeId: NodeId
        get() = NodeId.mapping

    fun setOnlyKeyType(keyType: Class<out Any>) {
        for (nodes in value) {
            nodes.keyNode.type = keyType
        }
    }

    fun setTypes(keyType: Class<out Any>, valueType: Class<out Any>) {
        for (nodes in value) {
            nodes.valueNode.type = valueType
            nodes.keyNode.type = keyType
        }
    }

    override fun toString(): String {
        val values: String
        val buf = StringBuilder()
        for (node in value) {
            buf.append("{ key=")
            buf.append(node.keyNode)
            buf.append("; value=")
            if (node.valueNode is CollectionNode<*>) {
                // to avoid overflow in case of recursive structures
                buf.append(System.identityHashCode(node.valueNode))
            } else {
                buf.append(node.toString())
            }
            buf.append(" }")
        }
        values = buf.toString()
        return "<" + this.javaClass.name + " (tag=" + tag + ", values=" + values + ")>"
    }
}
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
import org.yaml.snakeyaml.error.Mark

/**
 * Represents a sequence.
 *
 *
 * A sequence is a ordered collection of nodes.
 *
 */
class SequenceNode(
    tag: Tag, resolved: Boolean, value: MutableList<Node>, startMark: Mark?, endMark: Mark?,
    flowStyle: DumperOptions.FlowStyle?
) : CollectionNode<Node>(tag, startMark, endMark, flowStyle) {
    /**
     * Returns the elements in this sequence.
     *
     * @return Nodes in the specified order.
     */
    override val value: MutableList<Node>

    init {
        if (value == null) {
            throw NullPointerException("value in a Node is required.")
        }
        this.value = value
        this.isResolved = resolved
    }

    constructor(tag: Tag, value: MutableList<Node>, flowStyle: DumperOptions.FlowStyle?) : this(
        tag,
        true,
        value,
        null,
        null,
        flowStyle
    ) {
    }

    /*
     * Existed in older versions but replaced with {@link DumperOptions.SequenceStyle}-based constructor.
     * Restored in v1.22 for backwards compatibility.
     * @deprecated Since restored in v1.22.  Use {@link SequenceNode#SequenceNode(Tag, List<Node>, org.yaml.snakeyaml.DumperOptions.FlowStyle) }.
     */
    @Deprecated("")
    constructor(tag: Tag, value: MutableList<Node>, style: Boolean?) : this(
        tag,
        value,
        DumperOptions.FlowStyle.Companion.fromBoolean(style)
    ) {
    }

    /*
     * Existed in older versions but replaced with {@link DumperOptions.SequenceStyle}-based constructor.
     * Restored in v1.22 for backwards compatibility.
     * @deprecated Since restored in v1.22.  Use {@link SequenceNode#SequenceNode(Tag, boolean, List<Node>, Mark, Mark, org.yaml.snakeyaml.DumperOptions.FlowStyle) }.
     */
    @Deprecated("")
    constructor(
        tag: Tag, resolved: Boolean, value: MutableList<Node>, startMark: Mark?, endMark: Mark?,
        style: Boolean?
    ) : this(tag, resolved, value, startMark, endMark, DumperOptions.FlowStyle.Companion.fromBoolean(style)) {
    }

    override val nodeId: NodeId
        get() = NodeId.sequence

    fun setListType(listType: Class<out Any?>) {
        for (node in value) {
            node.type = listType
        }
    }

    override fun toString(): String {
        return ("<" + this.javaClass.name + " (tag=" + tag + ", value=" + value
                + ")>")
    }
}

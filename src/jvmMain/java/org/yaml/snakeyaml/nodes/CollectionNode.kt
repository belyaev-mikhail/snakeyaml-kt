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
 * Base class for the two collection types [mapping][MappingNode] and
 * [collection][SequenceNode].
 */
abstract class CollectionNode<T>(tag: Tag?, startMark: Mark?, endMark: Mark?, flowStyle: DumperOptions.FlowStyle?) :
    Node(tag, startMark, endMark) {
    /**
     * Serialization style of this collection.
     *
     * @return `true` for flow style, `false` for block
     * style.
     */
    var flowStyle: DumperOptions.FlowStyle? = null
        set(flowStyle) {
            if (flowStyle == null) throw NullPointerException("Flow style must be provided.")
            field = flowStyle
        }

    init {
        this.flowStyle = flowStyle
    }

    /*
     * Existed in older versions but replaced with {@link DumperOptions.FlowStyle}-based constructor.
     * Restored in v1.22 for backwards compatibility.
     * @deprecated Since restored in v1.22.  Use {@link CollectionNode#CollectionNode(Tag, Mark, Mark, org.yaml.snakeyaml.DumperOptions.FlowStyle) }.
     */
    @Deprecated("")
    constructor(tag: Tag?, startMark: Mark?, endMark: Mark?, flowStyle: Boolean?) : this(
        tag,
        startMark,
        endMark,
        fromBoolean(flowStyle)
    ) {
    }

    /**
     * Returns the elements in this sequence.
     *
     * @return Nodes in the specified order.
     */
    abstract val value: List<T>

    /*
     * Existed in older versions but replaced with {@link DumperOptions.FlowStyle}-based method.
     * Restored in v1.26 for backwards compatibility.
     * @deprecated Since restored in v1.26.  Use {@link CollectionNode#setFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle) }.
     */
    @Deprecated("")
    fun setFlowStyle(flowStyle: Boolean?) {
        this.flowStyle = fromBoolean(flowStyle)
    }

    override var endMark: Mark?
        get() = super.endMark
        set(endMark) {
            this.endMark = endMark
        }
}
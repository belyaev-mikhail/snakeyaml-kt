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
 * Represents a scalar node.
 *
 *
 * Scalar nodes form the leaves in the node graph.
 *
 */
class ScalarNode(
    tag: Tag, resolved: Boolean, value: String, startMark: Mark?, endMark: Mark?,
    style: DumperOptions.ScalarStyle?
) : Node(tag, startMark, endMark) {
    /**
     * Get scalar style of this node.
     *
     * @see org.yaml.snakeyaml.events.ScalarEvent
     *
     * @see [Chapter 9. Scalar
     * Styles](http://yaml.org/spec/1.1/.id903915)
     *
     * @return style of this scalar node
     */
    val scalarStyle: DumperOptions.ScalarStyle

    /**
     * Value of this scalar.
     *
     * @return Scalar's value.
     */
    val value: String

    constructor(tag: Tag, value: String, startMark: Mark?, endMark: Mark?, style: DumperOptions.ScalarStyle?) : this(
        tag,
        true,
        value,
        startMark,
        endMark,
        style
    ) {
    }

    init {
        if (value == null) {
            throw NullPointerException("value in a Node is required.")
        }
        this.value = value
        if (style == null) throw NullPointerException("Scalar style must be provided.")
        scalarStyle = style
        this.isResolved = resolved
    }

    /*
     * Existed in older versions but replaced with {@link DumperOptions.ScalarStyle}-based constructor.
     * Restored in v1.22 for backwards compatibility.
     * @deprecated Since restored in v1.22.  Use {@link ScalarNode#ScalarNode(Tag, String, Mark, Mark, org.yaml.snakeyaml.DumperOptions.ScalarStyle) }.
     */
    @Deprecated("")
    constructor(tag: Tag, value: String, startMark: Mark?, endMark: Mark?, style: Char?) : this(
        tag,
        value,
        startMark,
        endMark,
        DumperOptions.ScalarStyle.Companion.createStyle(style)
    ) {
    }

    /*
     * Existed in older versions but replaced with {@link DumperOptions.ScalarStyle}-based constructor.
     * Restored in v1.22 for backwards compatibility.
     * @deprecated Since restored in v1.22.  Use {@link ScalarNode#ScalarNode(Tag, boolean, String, Mark, Mark, org.yaml.snakeyaml.DumperOptions.ScalarStyle) }.
     */
    @Deprecated("")
    constructor(
        tag: Tag, resolved: Boolean, value: String, startMark: Mark?, endMark: Mark?,
        style: Char?
    ) : this(tag, resolved, value, startMark, endMark, DumperOptions.ScalarStyle.Companion.createStyle(style)) {
    }

    /**
     * Get scalar style of this node.
     *
     * @see org.yaml.snakeyaml.events.ScalarEvent
     *
     * @see [Chapter 9. Scalar
     * Styles](http://yaml.org/spec/1.1/.id903915)
     *
     * @return style of this scalar node
     */
    @Deprecated("use getScalarStyle instead")
    fun getStyle(): Char? {
        return scalarStyle.char
    }

    override val nodeId: NodeId
        get() = NodeId.scalar

    override fun toString(): String {
        return ("<" + this.javaClass.name + " (tag=" + tag + ", value=" + value
                + ")>")
    }

    val isPlain: Boolean
        get() = scalarStyle == DumperOptions.ScalarStyle.PLAIN
}

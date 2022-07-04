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

import org.yaml.snakeyaml.comments.CommentLine
import org.yaml.snakeyaml.error.Mark

/**
 * Base class for all nodes.
 *
 *
 * The nodes form the node-graph described in the [YAML Specification](http://yaml.org/spec/1.1/).
 *
 *
 *
 * While loading, the node graph is usually created by the
 * [org.yaml.snakeyaml.composer.Composer], and later transformed into
 * application specific Java classes by the classes from the
 * [org.yaml.snakeyaml.constructor] package.
 *
 */
abstract class Node(var tag: Tag, val startMark: Mark?, endMark: Mark?) {
    open var endMark: Mark? = endMark
        protected set

    var type: Class<out Any?> = Any::class.javaObjectType
        set(type: Class<out Any?>) {
            if (!type.isAssignableFrom(this.type)) {
                field = type
            }
        }

    /**
     * Indicates if this node must be constructed in two steps.
     *
     *
     * Two-step construction is required whenever a node is a child (direct or
     * indirect) of it self. That is, if a recursive structure is build using
     * anchors and aliases.
     *
     *
     *
     * Set by [org.yaml.snakeyaml.composer.Composer], used during the
     * construction process.
     *
     *
     *
     * Only relevant during loading.
     *
     *
     * @return `true` if the node is self referenced.
     */
    var isTwoStepsConstruction: Boolean
    var anchor: String? = null

    /**
     * The ordered list of in-line comments. The first of which appears at the end of the line respresent by this node.
     * The rest are in the following lines, indented per the Spec to indicate they are continuation of the inline comment.
     *
     * @return the comment line list.
     */
    var inLineComments: List<CommentLine>?

    /**
     * The ordered list of blank lines and block comments (full line) that appear before this node.
     *
     * @return the comment line list.
     */
    var blockComments: List<CommentLine>?

    /**
     * The ordered list of blank lines and block comments (full line) that appear AFTER this node.
     *
     *
     * NOTE: these comment should occur only in the last node in a document, when walking the node tree "in order"
     *
     * @return the comment line list.
     */
    // End Comments are only on the last node in a document
    var endComments: List<CommentLine>?
    /**
     * Indicates if the tag was added by
     * [org.yaml.snakeyaml.resolver.Resolver].
     *
     * @return true if the tag of this node was resolved
     *
     */
    /**
     * true when the tag is assigned by the resolver
     */
    @get:Deprecated("Since v1.22.  Absent in immediately prior versions, but present previously.  Restored deprecated for backwards compatibility.")
    var isResolved: Boolean
        protected set
    var useClassConstructor: Boolean?

    init {
        isTwoStepsConstruction = false
        isResolved = true
        useClassConstructor = null
        inLineComments = null
        blockComments = null
        endComments = null
    }

    /**
     * Tag of this node.
     *
     *
     * Every node has a tag assigned. The tag is either local or global.
     *
     * @return Tag of this node.
     */


    /**
     * For error reporting.
     *
     * @see "class variable 'id' in PyYAML"
     *
     * @return scalar, sequence, mapping
     */
    abstract val nodeId: NodeId


    /**
     * Node is only equal to itself
     */
    override fun equals(obj: Any?): Boolean {
        return super.equals(obj)
    }



    override fun hashCode(): Int {
        return super.hashCode()
    }

    fun useClassConstructor(): Boolean {
        return if (useClassConstructor == null) {
            if (!tag.isSecondary && isResolved && Any::class.javaObjectType != type
                && tag != Tag.NULL
            ) {
                true
            } else tag.isCompatible(type)
        } else useClassConstructor!!
    }
}

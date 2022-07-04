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
package org.yaml.snakeyaml.composer

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.comments.CommentEventsCollector
import org.yaml.snakeyaml.comments.CommentLine
import org.yaml.snakeyaml.comments.CommentType
import org.yaml.snakeyaml.error.YAMLException
import org.yaml.snakeyaml.events.*
import org.yaml.snakeyaml.nodes.*
import org.yaml.snakeyaml.parser.Parser
import org.yaml.snakeyaml.resolver.Resolver

/**
 * Creates a node graph from parser events.
 *
 *
 * Corresponds to the 'Compose' step as described in chapter 3.1 of the
 * [YAML Specification](http://yaml.org/spec/1.1/).
 *
 */
open class Composer @JvmOverloads constructor(
    protected val parser: Parser,
    private val resolver: Resolver,
    loadingConfig: LoaderOptions = LoaderOptions()
) {
    private val anchors: MutableMap<String?, Node>
    private val recursiveNodes: MutableSet<Node?>
    private var nonScalarAliasesCount = 0
    private val loadingConfig: LoaderOptions
    private val blockCommentsCollector: CommentEventsCollector
    private val inlineCommentsCollector: CommentEventsCollector

    // keep the nesting of collections inside other collections
    private var nestingDepth = 0
    private val nestingDepthLimit: Int

    init {
        anchors = HashMap()
        recursiveNodes = HashSet()
        this.loadingConfig = loadingConfig
        blockCommentsCollector = CommentEventsCollector(
            parser,
            CommentType.BLANK_LINE, CommentType.BLOCK
        )
        inlineCommentsCollector = CommentEventsCollector(
            parser,
            CommentType.IN_LINE
        )
        nestingDepthLimit = loadingConfig.nestingDepthLimit
    }

    /**
     * Checks if further documents are available.
     *
     * @return `true` if there is at least one more document.
     */
    fun checkNode(): Boolean {
        // Drop the STREAM-START event.
        if (parser.checkEvent(Event.ID.StreamStart)) {
            parser.event
        }
        // If there are more documents available?
        return !parser.checkEvent(Event.ID.StreamEnd)
    }// Collect inter-document start comments
    // Drop the DOCUMENT-START event.
    // Compose the root node.
    // Drop the DOCUMENT-END event.
    /**
     * Reads and composes the next document.
     *
     * @return The root node of the document or `null` if no more documents are available.
     */
    val node: Node?
        get() {
            // Collect inter-document start comments
            blockCommentsCollector.collectEvents()
            if (parser.checkEvent(Event.ID.StreamEnd)) {
                val commentLines = blockCommentsCollector.consume()
                val startMark = commentLines[0].startMark
                val children = mutableListOf<NodeTuple>()
                val node: Node =
                    MappingNode(Tag.Companion.COMMENT, false, children, startMark, null, DumperOptions.FlowStyle.BLOCK)
                node.blockComments = commentLines
                return node
            }
            // Drop the DOCUMENT-START event.
            parser.event
            // Compose the root node.
            val node = composeNode(null)
            // Drop the DOCUMENT-END event.
            blockCommentsCollector.collectEvents()
            if (!blockCommentsCollector.isEmpty) {
                node.endComments = blockCommentsCollector.consume()
            }
            parser.event
            anchors.clear()
            recursiveNodes.clear()
            return node
        }// Drop the STREAM-START event.
    // Compose a document if the stream is not empty.
    // Ensure that the stream contains no more documents.
    // Drop the STREAM-END event.
    /**
     * Reads a document from a source that contains only one document.
     *
     *
     * If the stream contains more than one document an exception is thrown.
     *
     *
     * @return The root node of the document or `null` if no document
     * is available.
     */
    open val singleNode: Node?
        get() {
            // Drop the STREAM-START event.
            parser.event
            // Compose a document if the stream is not empty.
            var document: Node? = null
            if (!parser.checkEvent(Event.ID.StreamEnd)) {
                document = node
            }
            // Ensure that the stream contains no more documents.
            if (!parser.checkEvent(Event.ID.StreamEnd)) {
                val event = parser.event
                val contextMark = document?.startMark
                throw ComposerException(
                    "expected a single document in the stream",
                    contextMark, "but found another document", event!!.startMark
                )
            }
            // Drop the STREAM-END event.
            parser.event
            return document
        }

    private fun composeNode(parent: Node?): Node {
        blockCommentsCollector.collectEvents()
        if (parent != null) recursiveNodes.add(parent)
        val node: Node?
        if (parser.checkEvent(Event.ID.Alias)) {
            val event = parser.event as AliasEvent
            val anchor = event.anchor
            if (!anchors.containsKey(anchor)) {
                throw ComposerException(
                    null, null, "found undefined alias $anchor",
                    event.startMark
                )
            }
            node = anchors[anchor]!!
            if (node !is ScalarNode) {
                nonScalarAliasesCount++
                if (nonScalarAliasesCount > loadingConfig.maxAliasesForCollections) {
                    throw YAMLException("Number of aliases for non-scalar nodes exceeds the specified max=" + loadingConfig.maxAliasesForCollections)
                }
            }
            if (recursiveNodes.remove(node)) {
                node.isTwoStepsConstruction = true
            }
            // drop comments, they can not be supported here
            blockCommentsCollector.consume()
            inlineCommentsCollector.collectEvents().consume()
        } else {
            val event = parser.peekEvent() as NodeEvent
            val anchor = event.anchor
            increaseNestingDepth()
            // the check for duplicate anchors has been removed (issue 174)
            node = if (parser.checkEvent(Event.ID.Scalar)) {
                composeScalarNode(anchor, blockCommentsCollector.consume())
            } else if (parser.checkEvent(Event.ID.SequenceStart)) {
                composeSequenceNode(anchor)
            } else {
                composeMappingNode(anchor)
            }
            decreaseNestingDepth()
        }
        recursiveNodes.remove(parent)
        return node
    }

    protected fun composeScalarNode(anchor: String?, blockComments: List<CommentLine>): Node {
        val ev = parser.event as ScalarEvent
        val tag = ev.tag
        var resolved = false
        val nodeTag: Tag?
        if (tag == null || tag == "!") {
            nodeTag = resolver.resolve(
                NodeId.scalar, ev.value,
                ev.implicit.canOmitTagInPlainScalar()
            )
            resolved = true
        } else {
            nodeTag = Tag(tag)
        }
        val node: Node = ScalarNode(
            nodeTag, resolved, ev.value, ev.startMark,
            ev.endMark, ev.scalarStyle
        )
        if (anchor != null) {
            node.anchor = anchor
            anchors[anchor] = node
        }
        node.blockComments = blockComments
        node.inLineComments = inlineCommentsCollector.collectEvents().consume()
        return node
    }

    protected fun composeSequenceNode(anchor: String?): Node {
        val startEvent = parser.event as SequenceStartEvent
        val tag = startEvent.tag
        val nodeTag: Tag?
        var resolved = false
        if (tag == null || tag == "!") {
            nodeTag = resolver.resolve(NodeId.sequence, null, startEvent.implicit)
            resolved = true
        } else {
            nodeTag = Tag(tag)
        }
        val children = ArrayList<Node>()
        val node = SequenceNode(
            nodeTag, resolved, children, startEvent.startMark,
            null, startEvent.flowStyle
        )
        if (startEvent.isFlow) {
            node.blockComments = blockCommentsCollector.consume()
        }
        if (anchor != null) {
            node.anchor = anchor
            anchors[anchor] = node
        }
        while (!parser.checkEvent(Event.ID.SequenceEnd)) {
            blockCommentsCollector.collectEvents()
            if (parser.checkEvent(Event.ID.SequenceEnd)) {
                break
            }
            children.add(composeNode(node))
        }
        if (startEvent.isFlow) {
            node.inLineComments = inlineCommentsCollector.collectEvents().consume()
        }
        val endEvent = parser.event!!
        node.endMark = endEvent.endMark
        inlineCommentsCollector.collectEvents()
        if (!inlineCommentsCollector.isEmpty) {
            node.inLineComments = inlineCommentsCollector.consume()
        }
        return node
    }

    protected fun composeMappingNode(anchor: String?): Node {
        val startEvent = parser.event as MappingStartEvent
        val tag = startEvent.tag
        val nodeTag: Tag?
        var resolved = false
        if (tag == null || tag == "!") {
            nodeTag = resolver.resolve(NodeId.mapping, null, startEvent.implicit)
            resolved = true
        } else {
            nodeTag = Tag(tag)
        }
        val children: MutableList<NodeTuple> = ArrayList()
        val node = MappingNode(
            nodeTag, resolved, children, startEvent.startMark,
            null, startEvent.flowStyle
        )
        if (startEvent.isFlow) {
            node.blockComments = blockCommentsCollector.consume()
        }
        if (anchor != null) {
            node.anchor = anchor
            anchors[anchor] = node
        }
        while (!parser.checkEvent(Event.ID.MappingEnd)) {
            blockCommentsCollector.collectEvents()
            if (parser.checkEvent(Event.ID.MappingEnd)) {
                break
            }
            composeMappingChildren(children, node)
        }
        if (startEvent.isFlow) {
            node.inLineComments = inlineCommentsCollector.collectEvents().consume()
        }
        val endEvent = parser.event!!
        node.endMark = endEvent.endMark
        inlineCommentsCollector.collectEvents()
        if (!inlineCommentsCollector.isEmpty) {
            node.inLineComments = inlineCommentsCollector.consume()
        }
        return node
    }

    protected fun composeMappingChildren(children: MutableList<NodeTuple>, node: MappingNode) {
        val itemKey = composeKeyNode(node)
        if (itemKey!!.tag == Tag.Companion.MERGE) {
            node.isMerged = true
        }
        val itemValue = composeValueNode(node)
        children.add(NodeTuple(itemKey, itemValue))
    }

    protected fun composeKeyNode(node: MappingNode?): Node? {
        return composeNode(node)
    }

    protected fun composeValueNode(node: MappingNode?): Node? {
        return composeNode(node)
    }

    /**
     * Increase nesting depth and fail when it exceeds the denied limit
     */
    private fun increaseNestingDepth() {
        if (nestingDepth > nestingDepthLimit) {
            throw YAMLException("Nesting Depth exceeded max $nestingDepthLimit")
        }
        nestingDepth++
    }

    /**
     * Indicate that the collection is finished and the nesting is decreased
     */
    private fun decreaseNestingDepth() {
        if (nestingDepth > 0) {
            nestingDepth--
        } else {
            throw YAMLException("Nesting Depth cannot be negative")
        }
    }
}

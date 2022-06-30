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
package org.yaml.snakeyaml.serializer

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.comments.CommentLine
import org.yaml.snakeyaml.emitter.Emitable
import org.yaml.snakeyaml.events.*
import org.yaml.snakeyaml.nodes.*
import org.yaml.snakeyaml.resolver.Resolver
import java.io.IOException

class Serializer(private val emitter: Emitable, private val resolver: Resolver, opts: DumperOptions, rootTag: Tag?) {
    private val explicitStart: Boolean
    private val explicitEnd: Boolean
    private var useVersion: DumperOptions.Version? = null
    private val useTags: Map<String?, String?>?
    private val serializedNodes: MutableSet<Node?>
    private val anchors: MutableMap<Node?, String?>
    private val anchorGenerator: AnchorGenerator
    private var closed: Boolean?
    private val explicitRoot: Tag?

    init {
        explicitStart = opts.isExplicitStart
        explicitEnd = opts.isExplicitEnd
        if (opts.version != null) {
            useVersion = opts.version
        }
        useTags = opts.tags
        serializedNodes = HashSet()
        anchors = HashMap()
        anchorGenerator = opts.anchorGenerator
        closed = null
        explicitRoot = rootTag
    }

    @Throws(IOException::class)
    fun open() {
        if (closed == null) {
            emitter.emit(StreamStartEvent(null, null))
            closed = java.lang.Boolean.FALSE
        } else if (java.lang.Boolean.TRUE == closed) {
            throw SerializerException("serializer is closed")
        } else {
            throw SerializerException("serializer is already opened")
        }
    }

    @Throws(IOException::class)
    fun close() {
        if (closed == null) {
            throw SerializerException("serializer is not opened")
        } else if (java.lang.Boolean.TRUE != closed) {
            emitter.emit(StreamEndEvent(null, null))
            closed = java.lang.Boolean.TRUE
            // release unused resources
            serializedNodes.clear()
            anchors.clear()
        }
    }

    @Throws(IOException::class)
    fun serialize(node: Node) {
        if (closed == null) {
            throw SerializerException("serializer is not opened")
        } else if (closed) {
            throw SerializerException("serializer is closed")
        }
        emitter.emit(
            DocumentStartEvent(
                null, null, explicitStart, useVersion,
                useTags
            )
        )
        anchorNode(node)
        if (explicitRoot != null) {
            node.tag = explicitRoot
        }
        serializeNode(node, null)
        emitter.emit(DocumentEndEvent(null, null, explicitEnd))
        serializedNodes.clear()
        anchors.clear()
    }

    private fun anchorNode(node: Node?) {
        var node = node
        if (node.getNodeId() == NodeId.anchor) {
            node = (node as AnchorNode?).getRealNode()
        }
        if (anchors.containsKey(node)) {
            var anchor = anchors[node]
            if (null == anchor) {
                anchor = anchorGenerator.nextAnchor(node)
                anchors[node] = anchor
            }
        } else {
            anchors[node] = if (node.getAnchor() != null) anchorGenerator.nextAnchor(node) else null
            when (node.getNodeId()) {
                NodeId.sequence -> {
                    val seqNode = node as SequenceNode?
                    val list = seqNode.getValue()
                    for (item in list!!) {
                        anchorNode(item)
                    }
                }
                NodeId.mapping -> {
                    val mnode = node as MappingNode?
                    val map = mnode.getValue()
                    for (`object` in map!!) {
                        val key = `object`.keyNode
                        val value = `object`.valueNode
                        anchorNode(key)
                        anchorNode(value)
                    }
                }
            }
        }
    }

    // parent Node is not used but might be used in the future
    @Throws(IOException::class)
    private fun serializeNode(node: Node?, parent: Node?) {
        var node = node
        if (node.getNodeId() == NodeId.anchor) {
            node = (node as AnchorNode?).getRealNode()
        }
        val tAlias = anchors[node]
        if (serializedNodes.contains(node)) {
            emitter.emit(AliasEvent(tAlias, null, null))
        } else {
            serializedNodes.add(node)
            when (node.getNodeId()) {
                NodeId.scalar -> {
                    val scalarNode = node as ScalarNode?
                    serializeComments(node.getBlockComments())
                    val detectedTag = resolver.resolve(NodeId.scalar, scalarNode.getValue(), true)
                    val defaultTag = resolver.resolve(NodeId.scalar, scalarNode.getValue(), false)
                    val tuple = ImplicitTuple(
                        node!!.tag == detectedTag, node
                            .getTag() == defaultTag
                    )
                    val event = ScalarEvent(
                        tAlias, node.tag.value, tuple,
                        scalarNode.getValue(), null, null, scalarNode.getScalarStyle()
                    )
                    emitter.emit(event)
                    serializeComments(node.getInLineComments())
                    serializeComments(node.getEndComments())
                }
                NodeId.sequence -> {
                    val seqNode = node as SequenceNode?
                    serializeComments(node.getBlockComments())
                    val implicitS = node!!.tag == resolver.resolve(
                        NodeId.sequence,
                        null, true
                    )
                    emitter.emit(
                        SequenceStartEvent(
                            tAlias, node.tag.value,
                            implicitS, null, null, seqNode.getFlowStyle()
                        )
                    )
                    val list = seqNode.getValue()
                    for (item in list!!) {
                        serializeNode(item, node)
                    }
                    emitter.emit(SequenceEndEvent(null, null))
                    serializeComments(node.getInLineComments())
                    serializeComments(node.getEndComments())
                }
                else -> {
                    serializeComments(node.getBlockComments())
                    val implicitTag = resolver.resolve(NodeId.mapping, null, true)
                    val implicitM = node!!.tag == implicitTag
                    val mnode = node as MappingNode?
                    val map = mnode.getValue()
                    if (mnode!!.tag !== Tag.Companion.COMMENT) {
                        emitter.emit(
                            MappingStartEvent(
                                tAlias, mnode!!.tag.value, implicitM, null, null,
                                mnode.flowStyle
                            )
                        )
                        for (row in map!!) {
                            val key = row.keyNode
                            val value = row.valueNode
                            serializeNode(key, mnode)
                            serializeNode(value, mnode)
                        }
                        emitter.emit(MappingEndEvent(null, null))
                        serializeComments(node.getInLineComments())
                        serializeComments(node.getEndComments())
                    }
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun serializeComments(comments: List<CommentLine?>?) {
        if (comments == null) {
            return
        }
        for (line in comments) {
            val commentEvent = CommentEvent(
                line!!.commentType, line.value, line.startMark,
                line.endMark
            )
            emitter.emit(commentEvent)
        }
    }
}
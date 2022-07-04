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
package org.yaml.snakeyaml.parser

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.error.Mark
import org.yaml.snakeyaml.error.YAMLException
import org.yaml.snakeyaml.events.*
import org.yaml.snakeyaml.reader.StreamReader
import org.yaml.snakeyaml.scanner.Scanner
import org.yaml.snakeyaml.scanner.ScannerImpl
import org.yaml.snakeyaml.tagPrefix
import org.yaml.snakeyaml.tokens.*
import org.yaml.snakeyaml.util.ArrayStack
import java.util.*

/**
 * <pre>
 * # The following YAML grammar is LL(1) and is parsed by a recursive descent
 * parser.
 * stream            ::= STREAM-START implicit_document? explicit_document* STREAM-END
 * implicit_document ::= block_node DOCUMENT-END*
 * explicit_document ::= DIRECTIVE* DOCUMENT-START block_node? DOCUMENT-END*
 * block_node_or_indentless_sequence ::=
 * ALIAS
 * | properties (block_content | indentless_block_sequence)?
 * | block_content
 * | indentless_block_sequence
 * block_node        ::= ALIAS
 * | properties block_content?
 * | block_content
 * flow_node         ::= ALIAS
 * | properties flow_content?
 * | flow_content
 * properties        ::= TAG ANCHOR? | ANCHOR TAG?
 * block_content     ::= block_collection | flow_collection | SCALAR
 * flow_content      ::= flow_collection | SCALAR
 * block_collection  ::= block_sequence | block_mapping
 * flow_collection   ::= flow_sequence | flow_mapping
 * block_sequence    ::= BLOCK-SEQUENCE-START (BLOCK-ENTRY block_node?)* BLOCK-END
 * indentless_sequence   ::= (BLOCK-ENTRY block_node?)+
 * block_mapping     ::= BLOCK-MAPPING_START
 * ((KEY block_node_or_indentless_sequence?)?
 * (VALUE block_node_or_indentless_sequence?)?)*
 * BLOCK-END
 * flow_sequence     ::= FLOW-SEQUENCE-START
 * (flow_sequence_entry FLOW-ENTRY)*
 * flow_sequence_entry?
 * FLOW-SEQUENCE-END
 * flow_sequence_entry   ::= flow_node | KEY flow_node? (VALUE flow_node?)?
 * flow_mapping      ::= FLOW-MAPPING-START
 * (flow_mapping_entry FLOW-ENTRY)*
 * flow_mapping_entry?
 * FLOW-MAPPING-END
 * flow_mapping_entry    ::= flow_node | KEY flow_node? (VALUE flow_node?)?
 * FIRST sets:
 * stream: { STREAM-START }
 * explicit_document: { DIRECTIVE DOCUMENT-START }
 * implicit_document: FIRST(block_node)
 * block_node: { ALIAS TAG ANCHOR SCALAR BLOCK-SEQUENCE-START BLOCK-MAPPING-START FLOW-SEQUENCE-START FLOW-MAPPING-START }
 * flow_node: { ALIAS ANCHOR TAG SCALAR FLOW-SEQUENCE-START FLOW-MAPPING-START }
 * block_content: { BLOCK-SEQUENCE-START BLOCK-MAPPING-START FLOW-SEQUENCE-START FLOW-MAPPING-START SCALAR }
 * flow_content: { FLOW-SEQUENCE-START FLOW-MAPPING-START SCALAR }
 * block_collection: { BLOCK-SEQUENCE-START BLOCK-MAPPING-START }
 * flow_collection: { FLOW-SEQUENCE-START FLOW-MAPPING-START }
 * block_sequence: { BLOCK-SEQUENCE-START }
 * block_mapping: { BLOCK-MAPPING-START }
 * block_node_or_indentless_sequence: { ALIAS ANCHOR TAG SCALAR BLOCK-SEQUENCE-START BLOCK-MAPPING-START FLOW-SEQUENCE-START FLOW-MAPPING-START BLOCK-ENTRY }
 * indentless_sequence: { ENTRY }
 * flow_collection: { FLOW-SEQUENCE-START FLOW-MAPPING-START }
 * flow_sequence: { FLOW-SEQUENCE-START }
 * flow_mapping: { FLOW-MAPPING-START }
 * flow_sequence_entry: { ALIAS ANCHOR TAG SCALAR FLOW-SEQUENCE-START FLOW-MAPPING-START KEY }
 * flow_mapping_entry: { ALIAS ANCHOR TAG SCALAR FLOW-SEQUENCE-START FLOW-MAPPING-START KEY }
</pre> *
 *
 * Since writing a recursive-descendant parser is a straightforward task, we do
 * not give many comments here.
 */
class ParserImpl(protected val scanner: Scanner) : Parser {
    private var currentEvent: Event? = null
    private val states: ArrayStack<Production>
    private val marks: ArrayStack<Mark>
    private var state: Production?
    private var directives: VersionTagsTuple

    constructor(reader: StreamReader) : this(ScannerImpl(reader)) {}
    constructor(
        reader: StreamReader,
        parseComments: Boolean
    ) : this(ScannerImpl(reader).setParseComments(parseComments)) {
    }

    init {
        directives = VersionTagsTuple(null, HashMap(DEFAULT_TAGS))
        states = ArrayStack(100)
        marks = ArrayStack(10)
        state = ParseStreamStart()
    }

    /**
     * Check the type of the next event.
     */
    override fun checkEvent(choice: Event.ID): Boolean {
        peekEvent()
        return currentEvent != null && currentEvent!!.`is`(choice)
    }

    /**
     * Get the next event.
     */
    override fun peekEvent(): Event? {
        if (currentEvent == null) {
            if (state != null) {
                currentEvent = state!!.produce()
            }
        }
        return currentEvent
    }

    /**
     * Get the next event and proceed further.
     */
    override val event: Event?
        get() {
            peekEvent()
            val value = currentEvent
            currentEvent = null
            return value
        }

    private fun produceCommentEvent(token: CommentToken): CommentEvent {
        val startMark = token.startMark
        val endMark = token.endMark
        val value = token.value
        val type = token.commentType

        // state = state, that no change in state
        return CommentEvent(type, value, startMark, endMark)
    }

    /**
     * <pre>
     * stream    ::= STREAM-START implicit_document? explicit_document* STREAM-END
     * implicit_document ::= block_node DOCUMENT-END*
     * explicit_document ::= DIRECTIVE* DOCUMENT-START block_node? DOCUMENT-END*
    </pre> *
     */
    private inner class ParseStreamStart : Production {
        override fun produce(): Event {
            // Parse the stream start.
            val token = scanner.token as StreamStartToken
            val event: Event = StreamStartEvent(token.startMark, token.endMark)
            // Prepare the next state.
            state = ParseImplicitDocumentStart()
            return event
        }
    }

    private inner class ParseImplicitDocumentStart : Production {
        override fun produce(): Event {
            // Parse an implicit document.
            if (scanner.checkToken(Token.ID.Comment)) {
                state = ParseImplicitDocumentStart()
                return produceCommentEvent((scanner.token as CommentToken))
            }
            if (!scanner.checkToken(Token.ID.Directive, Token.ID.DocumentStart, Token.ID.StreamEnd)) {
                val token = scanner.peekToken()
                val startMark = token.startMark
                val event: Event = DocumentStartEvent(
                    startMark,
                    startMark,
                    false,
                    null,
                    null
                )
                // Prepare the next state.
                states.push(ParseDocumentEnd())
                state = ParseBlockNode()
                return event
            }
            return ParseDocumentStart().produce()
        }
    }

    private inner class ParseDocumentStart : Production {
        override fun produce(): Event {
            // Parse any extra document end indicators.
            while (scanner.checkToken(Token.ID.DocumentEnd)) {
                scanner.token
            }
            // Parse an explicit document.
            val event: Event
            if (!scanner.checkToken(Token.ID.StreamEnd)) {
                var token = scanner.peekToken()
                val startMark = token.startMark
                val tuple = processDirectives()
                while (scanner.checkToken(Token.ID.Comment)) {
                    // TODO: till we figure out what todo with the comments
                    scanner.token
                }
                if (!scanner.checkToken(Token.ID.StreamEnd)) {
                    if (!scanner.checkToken(Token.ID.DocumentStart)) {
                        throw ParserException(
                            null, null, "expected '<document start>', but found '"
                                    + scanner.peekToken().tokenId + "'", scanner.peekToken().startMark
                        )
                    }
                    token = scanner.token
                    val endMark = token.endMark
                    event = DocumentStartEvent(
                        startMark, endMark, true, tuple.version,
                        tuple.tags
                    )
                    states.push(ParseDocumentEnd())
                    state = ParseDocumentContent()
                    return event
                }
            }
            // Parse the end of the stream.
            val token = scanner.token as StreamEndToken
            event = StreamEndEvent(token.startMark, token.endMark)
            if (!states.isEmpty) {
                throw YAMLException("Unexpected end of stream. States left: $states")
            }
            if (!marks.isEmpty) {
                throw YAMLException("Unexpected end of stream. Marks left: $marks")
            }
            state = null
            return event
        }
    }

    private inner class ParseDocumentEnd : Production {
        override fun produce(): Event {
            // Parse the document end.
            var token = scanner.peekToken()
            val startMark = token.startMark
            var endMark = startMark
            var explicit = false
            if (scanner.checkToken(Token.ID.DocumentEnd)) {
                token = scanner.token
                endMark = token.endMark
                explicit = true
            }
            val event: Event = DocumentEndEvent(startMark, endMark, explicit)
            // Prepare the next state.
            state = ParseDocumentStart()
            return event
        }
    }

    private inner class ParseDocumentContent : Production {
        override fun produce(): Event {
            if (scanner.checkToken(Token.ID.Comment)) {
                state = ParseDocumentContent()
                return produceCommentEvent((scanner.token as CommentToken))
            }
            if (scanner.checkToken(
                    Token.ID.Directive, Token.ID.DocumentStart,
                    Token.ID.DocumentEnd, Token.ID.StreamEnd
                )
            ) {
                val event = processEmptyScalar(scanner.peekToken().startMark)
                state = states.pop()
                return event
            }
            return ParseBlockNode().produce()
        }
    }

    /**
     * https://yaml.org/spec/1.1/#id898785 says "If the document specifies no directives,
     * it is parsed using the same settings as the previous document.
     * If the document does specify any directives,
     * all directives of previous documents, if any, are ignored."
     * TODO the last statement is not respected (as in PyYAML, to work the same)
     * @return directives to be applied for the current document
     */
    private fun processDirectives(): VersionTagsTuple {
        val tagHandles = HashMap(directives.tags)
        for (key in DEFAULT_TAGS.keys) {
            tagHandles.remove(key)
        }
        // keep only added tag handlers
        directives = VersionTagsTuple(null, tagHandles)
        while (scanner.checkToken(Token.ID.Directive)) {
            val token = scanner.token as DirectiveToken<*>
            if (token.name == "YAML") {
                if (directives.version != null) {
                    throw ParserException(
                        null, null, "found duplicate YAML directive",
                        token.startMark
                    )
                }
                val value = token.value as List<Int?>
                val major = value[0]
                if (major != 1) {
                    throw ParserException(
                        null, null,
                        "found incompatible YAML document (version 1.* is required)",
                        token.startMark
                    )
                }
                val minor = value[1]
                directives = when (minor) {
                    0 -> VersionTagsTuple(DumperOptions.Version.V1_0, tagHandles)
                    else -> VersionTagsTuple(DumperOptions.Version.V1_1, tagHandles)
                }
            } else if (token.name == "TAG") {
                val value = token.value as List<String?>
                val handle = value[0]
                val prefix = value[1]
                if (tagHandles.containsKey(handle)) {
                    throw ParserException(
                        null, null, "duplicate tag handle $handle",
                        token.startMark
                    )
                }
                tagHandles[handle] = prefix
            }
        }
        var detectedTagHandles = HashMap<String, String>()
        if (!tagHandles.isEmpty()) {
            // copy from tagHandles
            detectedTagHandles = HashMap(tagHandles)
        }
        // add default tag handlers to resolve tags
        for (key in DEFAULT_TAGS.keys) {
            // do not overwrite re-defined tags
            if (!tagHandles.containsKey(key)) {
                tagHandles[key] = DEFAULT_TAGS[key]
            }
        }
        // data for the events (no default tags added)
        return VersionTagsTuple(directives.version, detectedTagHandles)
    }

    /**
     * <pre>
     * block_node_or_indentless_sequence ::= ALIAS
     * | properties (block_content | indentless_block_sequence)?
     * | block_content
     * | indentless_block_sequence
     * block_node    ::= ALIAS
     * | properties block_content?
     * | block_content
     * flow_node     ::= ALIAS
     * | properties flow_content?
     * | flow_content
     * properties    ::= TAG ANCHOR? | ANCHOR TAG?
     * block_content     ::= block_collection | flow_collection | SCALAR
     * flow_content      ::= flow_collection | SCALAR
     * block_collection  ::= block_sequence | block_mapping
     * flow_collection   ::= flow_sequence | flow_mapping
    </pre> *
     */
    private inner class ParseBlockNode : Production {
        override fun produce(): Event {
            return parseNode(true, false)
        }
    }

    private fun parseFlowNode(): Event {
        return parseNode(false, false)
    }

    private fun parseBlockNodeOrIndentlessSequence(): Event {
        return parseNode(true, true)
    }

    private fun parseNode(block: Boolean, indentlessSequence: Boolean): Event {
        var event: Event?
        var startMark: Mark? = null
        var endMark: Mark? = null
        var tagMark: Mark? = null
        if (scanner.checkToken(Token.ID.Alias)) {
            val token = scanner.token as AliasToken
            event = AliasEvent(token.value, token.startMark, token.endMark)
            state = states.pop()
        } else {
            var anchor: String? = null
            var tagTokenTag: TagTuple? = null
            if (scanner.checkToken(Token.ID.Anchor)) {
                val token = scanner.token as AnchorToken
                startMark = token.startMark
                endMark = token.endMark
                anchor = token.value
                if (scanner.checkToken(Token.ID.Tag)) {
                    val tagToken = scanner.token as TagToken
                    tagMark = tagToken.startMark
                    endMark = tagToken.endMark
                    tagTokenTag = tagToken.value
                }
            } else if (scanner.checkToken(Token.ID.Tag)) {
                val tagToken = scanner.token as TagToken
                startMark = tagToken.startMark
                tagMark = startMark
                endMark = tagToken.endMark
                tagTokenTag = tagToken.value
                if (scanner.checkToken(Token.ID.Anchor)) {
                    val token = scanner.token as AnchorToken
                    endMark = token.endMark
                    anchor = token.value
                }
            }
            var tag: String? = null
            if (tagTokenTag != null) {
                val handle = tagTokenTag.handle
                val suffix = tagTokenTag.suffix
                tag = if (handle != null) {
                    if (!directives.tags.containsKey(handle)) {
                        throw ParserException(
                            "while parsing a node", startMark,
                            "found undefined tag handle $handle", tagMark
                        )
                    }
                    directives.tags[handle] + suffix
                } else {
                    suffix
                }
            }
            if (startMark == null) {
                startMark = scanner.peekToken().startMark
                endMark = startMark
            }
            event = null
            val implicit = tag == null || tag == "!"
            if (indentlessSequence && scanner.checkToken(Token.ID.BlockEntry)) {
                endMark = scanner.peekToken().endMark
                event = SequenceStartEvent(
                    anchor, tag, implicit, startMark, endMark,
                    DumperOptions.FlowStyle.BLOCK
                )
                state = ParseIndentlessSequenceEntryKey()
            } else {
                if (scanner.checkToken(Token.ID.Scalar)) {
                    val token = scanner.token as ScalarToken
                    endMark = token.endMark
                    val implicitValues: ImplicitTuple
                    implicitValues = if (token.plain && tag == null || "!" == tag) {
                        ImplicitTuple(true, false)
                    } else if (tag == null) {
                        ImplicitTuple(false, true)
                    } else {
                        ImplicitTuple(false, false)
                    }
                    event = ScalarEvent(
                        anchor, tag, implicitValues, token.value,
                        startMark, endMark, token.style
                    )
                    state = states.pop()
                } else if (scanner.checkToken(Token.ID.FlowSequenceStart)) {
                    endMark = scanner.peekToken().endMark
                    event = SequenceStartEvent(
                        anchor, tag, implicit, startMark, endMark,
                        DumperOptions.FlowStyle.FLOW
                    )
                    state = ParseFlowSequenceFirstEntry()
                } else if (scanner.checkToken(Token.ID.FlowMappingStart)) {
                    endMark = scanner.peekToken().endMark
                    event = MappingStartEvent(
                        anchor, tag, implicit, startMark, endMark,
                        DumperOptions.FlowStyle.FLOW
                    )
                    state = ParseFlowMappingFirstKey()
                } else if (block && scanner.checkToken(Token.ID.BlockSequenceStart)) {
                    endMark = scanner.peekToken().startMark
                    event = SequenceStartEvent(
                        anchor, tag, implicit, startMark, endMark,
                        DumperOptions.FlowStyle.BLOCK
                    )
                    state = ParseBlockSequenceFirstEntry()
                } else if (block && scanner.checkToken(Token.ID.BlockMappingStart)) {
                    endMark = scanner.peekToken().startMark
                    event = MappingStartEvent(
                        anchor, tag, implicit, startMark, endMark,
                        DumperOptions.FlowStyle.BLOCK
                    )
                    state = ParseBlockMappingFirstKey()
                } else if (anchor != null || tag != null) {
                    // Empty scalars are allowed even if a tag or an anchor is
                    // specified.
                    event = ScalarEvent(
                        anchor, tag, ImplicitTuple(implicit, false), "",
                        startMark, endMark, DumperOptions.ScalarStyle.PLAIN
                    )
                    state = states.pop()
                } else {
                    val token = scanner.peekToken()
                    throw ParserException(
                        "while parsing a " + (if (block) "block" else "flow") + " node", startMark,
                        "expected the node content, but found '" + token.tokenId + "'",
                        token.startMark
                    )
                }
            }
        }
        return event!!
    }

    // block_sequence ::= BLOCK-SEQUENCE-START (BLOCK-ENTRY block_node?)*
    // BLOCK-END
    private inner class ParseBlockSequenceFirstEntry : Production {
        override fun produce(): Event {
            val token = scanner.token
            marks.push(token.startMark)
            return ParseBlockSequenceEntryKey().produce()
        }
    }

    private inner class ParseBlockSequenceEntryKey : Production {
        override fun produce(): Event {
            if (scanner.checkToken(Token.ID.Comment)) {
                state = ParseBlockSequenceEntryKey()
                return produceCommentEvent((scanner.token as CommentToken))
            }
            if (scanner.checkToken(Token.ID.BlockEntry)) {
                val token = scanner.token as BlockEntryToken
                return ParseBlockSequenceEntryValue(token).produce()
            }
            if (!scanner.checkToken(Token.ID.BlockEnd)) {
                val token = scanner.peekToken()
                throw ParserException(
                    "while parsing a block collection", marks.pop(),
                    "expected <block end>, but found '" + token.tokenId + "'",
                    token.startMark
                )
            }
            val token = scanner.token
            val event: Event = SequenceEndEvent(token.startMark, token.endMark)
            state = states.pop()
            marks.pop()
            return event
        }
    }

    private inner class ParseBlockSequenceEntryValue(var token: BlockEntryToken) : Production {
        override fun produce(): Event {
            if (scanner.checkToken(Token.ID.Comment)) {
                state = ParseBlockSequenceEntryValue(token)
                return produceCommentEvent((scanner.token as CommentToken))
            }
            return if (!scanner.checkToken(
                    Token.ID.BlockEntry,
                    Token.ID.BlockEnd
                )
            ) {
                states.push(ParseBlockSequenceEntryKey())
                ParseBlockNode().produce()
            } else {
                state = ParseBlockSequenceEntryKey()
                processEmptyScalar(token.endMark)
            }
        }
    }

    // indentless_sequence ::= (BLOCK-ENTRY block_node?)+
    private inner class ParseIndentlessSequenceEntryKey : Production {
        override fun produce(): Event {
            if (scanner.checkToken(Token.ID.Comment)) {
                state = ParseIndentlessSequenceEntryKey()
                return produceCommentEvent((scanner.token as CommentToken))
            }
            if (scanner.checkToken(Token.ID.BlockEntry)) {
                val token = scanner.token as BlockEntryToken
                return ParseIndentlessSequenceEntryValue(token).produce()
            }
            val token = scanner.peekToken()
            val event: Event = SequenceEndEvent(token.startMark, token.endMark)
            state = states.pop()
            return event
        }
    }

    private inner class ParseIndentlessSequenceEntryValue(var token: BlockEntryToken) : Production {
        override fun produce(): Event {
            if (scanner.checkToken(Token.ID.Comment)) {
                state = ParseIndentlessSequenceEntryValue(token)
                return produceCommentEvent((scanner.token as CommentToken))
            }
            return if (!scanner.checkToken(
                    Token.ID.BlockEntry,
                    Token.ID.Key,
                    Token.ID.Value,
                    Token.ID.BlockEnd
                )
            ) {
                states.push(ParseIndentlessSequenceEntryKey())
                ParseBlockNode().produce()
            } else {
                state = ParseIndentlessSequenceEntryKey()
                processEmptyScalar(token.endMark)
            }
        }
    }

    private inner class ParseBlockMappingFirstKey : Production {
        override fun produce(): Event {
            val token = scanner.token
            marks.push(token.startMark)
            return ParseBlockMappingKey().produce()
        }
    }

    private inner class ParseBlockMappingKey : Production {
        override fun produce(): Event {
            if (scanner.checkToken(Token.ID.Comment)) {
                state = ParseBlockMappingKey()
                return produceCommentEvent((scanner.token as CommentToken))
            }
            if (scanner.checkToken(Token.ID.Key)) {
                val token = scanner.token
                return if (!scanner.checkToken(
                        Token.ID.Key,
                        Token.ID.Value,
                        Token.ID.BlockEnd
                    )
                ) {
                    states.push(ParseBlockMappingValue())
                    parseBlockNodeOrIndentlessSequence()
                } else {
                    state = ParseBlockMappingValue()
                    processEmptyScalar(token.endMark)
                }
            }
            if (!scanner.checkToken(Token.ID.BlockEnd)) {
                val token = scanner.peekToken()
                throw ParserException(
                    "while parsing a block mapping", marks.pop(),
                    "expected <block end>, but found '" + token.tokenId + "'",
                    token.startMark
                )
            }
            val token = scanner.token
            val event: Event = MappingEndEvent(token.startMark, token.endMark)
            state = states.pop()
            marks.pop()
            return event
        }
    }

    private inner class ParseBlockMappingValue : Production {
        override fun produce(): Event {
            if (scanner.checkToken(Token.ID.Value)) {
                val token = scanner.token
                return if (scanner.checkToken(Token.ID.Comment)) {
                    state = ParseBlockMappingValueComment()
                    state!!.produce()
                } else if (!scanner.checkToken(
                        Token.ID.Key,
                        Token.ID.Value,
                        Token.ID.BlockEnd
                    )
                ) {
                    states.push(ParseBlockMappingKey())
                    parseBlockNodeOrIndentlessSequence()
                } else {
                    state = ParseBlockMappingKey()
                    processEmptyScalar(token.endMark)
                }
            } else if (scanner.checkToken(Token.ID.Scalar)) {
                states.push(ParseBlockMappingKey())
                return parseBlockNodeOrIndentlessSequence()
            }
            state = ParseBlockMappingKey()
            val token = scanner.peekToken()
            return processEmptyScalar(token.startMark)
        }
    }

    private inner class ParseBlockMappingValueComment : Production {
        var tokens: MutableList<CommentToken> = ArrayDeque()
        override fun produce(): Event {
            return if (scanner.checkToken(Token.ID.Comment)) {
                tokens.add(scanner.token as CommentToken)
                produce()
            } else if (!scanner.checkToken(
                    Token.ID.Key,
                    Token.ID.Value,
                    Token.ID.BlockEnd
                )
            ) {
                if (!tokens.isEmpty()) {
                    return produceCommentEvent(tokens.removeAt(0)!!)
                }
                states.push(ParseBlockMappingKey())
                parseBlockNodeOrIndentlessSequence()
            } else {
                state = ParseBlockMappingValueCommentList(tokens)
                processEmptyScalar(scanner.peekToken().startMark)
            }
        }
    }

    private inner class ParseBlockMappingValueCommentList(tokens: MutableList<CommentToken>) : Production {
        var tokens: MutableList<CommentToken>

        init {
            this.tokens = tokens
        }

        override fun produce(): Event {
            return if (!tokens.isEmpty()) {
                produceCommentEvent(tokens.removeAt(0))
            } else ParseBlockMappingKey().produce()
        }
    }

    /**
     * <pre>
     * flow_sequence     ::= FLOW-SEQUENCE-START
     * (flow_sequence_entry FLOW-ENTRY)*
     * flow_sequence_entry?
     * FLOW-SEQUENCE-END
     * flow_sequence_entry   ::= flow_node | KEY flow_node? (VALUE flow_node?)?
     * Note that while production rules for both flow_sequence_entry and
     * flow_mapping_entry are equal, their interpretations are different.
     * For `flow_sequence_entry`, the part `KEY flow_node? (VALUE flow_node?)?`
     * generate an inline mapping (set syntax).
    </pre> *
     */
    private inner class ParseFlowSequenceFirstEntry : Production {
        override fun produce(): Event {
            val token = scanner.token
            marks.push(token.startMark)
            return ParseFlowSequenceEntry(true).produce()
        }
    }

    private inner class ParseFlowSequenceEntry(private val first: Boolean) : Production {
        override fun produce(): Event {
            if (scanner.checkToken(Token.ID.Comment)) {
                state = ParseFlowSequenceEntry(first)
                return produceCommentEvent((scanner.token as CommentToken))
            }
            if (!scanner.checkToken(Token.ID.FlowSequenceEnd)) {
                if (!first) {
                    if (scanner.checkToken(Token.ID.FlowEntry)) {
                        scanner.token
                        if (scanner.checkToken(Token.ID.Comment)) {
                            state = ParseFlowSequenceEntry(true)
                            return produceCommentEvent((scanner.token as CommentToken))
                        }
                    } else {
                        val token = scanner.peekToken()
                        throw ParserException(
                            "while parsing a flow sequence", marks.pop(),
                            "expected ',' or ']', but got " + token.tokenId,
                            token.startMark
                        )
                    }
                }
                if (scanner.checkToken(Token.ID.Key)) {
                    val token = scanner.peekToken()
                    val event: Event = MappingStartEvent(
                        null, null, true, token.startMark,
                        token.endMark, DumperOptions.FlowStyle.FLOW
                    )
                    state = ParseFlowSequenceEntryMappingKey()
                    return event
                } else if (!scanner.checkToken(Token.ID.FlowSequenceEnd)) {
                    states.push(ParseFlowSequenceEntry(false))
                    return parseFlowNode()
                }
            }
            val token = scanner.token
            val event: Event = SequenceEndEvent(token.startMark, token.endMark)
            if (!scanner.checkToken(Token.ID.Comment)) {
                state = states.pop()
            } else {
                state = ParseFlowEndComment()
            }
            marks.pop()
            return event
        }
    }

    private inner class ParseFlowEndComment : Production {
        override fun produce(): Event {
            val event: Event = produceCommentEvent((scanner.token as CommentToken))
            if (!scanner.checkToken(Token.ID.Comment)) {
                state = states.pop()
            }
            return event
        }
    }

    private inner class ParseFlowSequenceEntryMappingKey : Production {
        override fun produce(): Event {
            val token = scanner.token
            return if (!scanner.checkToken(
                    Token.ID.Value,
                    Token.ID.FlowEntry,
                    Token.ID.FlowSequenceEnd
                )
            ) {
                states.push(ParseFlowSequenceEntryMappingValue())
                parseFlowNode()
            } else {
                state = ParseFlowSequenceEntryMappingValue()
                processEmptyScalar(token.endMark)
            }
        }
    }

    private inner class ParseFlowSequenceEntryMappingValue : Production {
        override fun produce(): Event {
            return if (scanner.checkToken(Token.ID.Value)) {
                val token = scanner.token
                if (!scanner.checkToken(
                        Token.ID.FlowEntry,
                        Token.ID.FlowSequenceEnd
                    )
                ) {
                    states.push(ParseFlowSequenceEntryMappingEnd())
                    parseFlowNode()
                } else {
                    state = ParseFlowSequenceEntryMappingEnd()
                    processEmptyScalar(token.endMark)
                }
            } else {
                state = ParseFlowSequenceEntryMappingEnd()
                val token = scanner.peekToken()
                processEmptyScalar(token.startMark)
            }
        }
    }

    private inner class ParseFlowSequenceEntryMappingEnd : Production {
        override fun produce(): Event {
            state = ParseFlowSequenceEntry(false)
            val token = scanner.peekToken()
            return MappingEndEvent(token.startMark, token.endMark)
        }
    }

    /**
     * <pre>
     * flow_mapping  ::= FLOW-MAPPING-START
     * (flow_mapping_entry FLOW-ENTRY)*
     * flow_mapping_entry?
     * FLOW-MAPPING-END
     * flow_mapping_entry    ::= flow_node | KEY flow_node? (VALUE flow_node?)?
    </pre> *
     */
    private inner class ParseFlowMappingFirstKey : Production {
        override fun produce(): Event {
            val token = scanner.token
            marks.push(token.startMark)
            return ParseFlowMappingKey(true).produce()
        }
    }

    private inner class ParseFlowMappingKey(private val first: Boolean) : Production {
        override fun produce(): Event {
            if (scanner.checkToken(Token.ID.Comment)) {
                state = ParseFlowMappingKey(first)
                return produceCommentEvent((scanner.token as CommentToken))
            }
            if (!scanner.checkToken(Token.ID.FlowMappingEnd)) {
                if (!first) {
                    if (scanner.checkToken(Token.ID.FlowEntry)) {
                        scanner.token
                        if (scanner.checkToken(Token.ID.Comment)) {
                            state = ParseFlowMappingKey(true)
                            return produceCommentEvent((scanner.token as CommentToken))
                        }
                    } else {
                        val token = scanner.peekToken()
                        throw ParserException(
                            "while parsing a flow mapping", marks.pop(),
                            "expected ',' or '}', but got " + token.tokenId,
                            token.startMark
                        )
                    }
                }
                if (scanner.checkToken(Token.ID.Key)) {
                    val token = scanner.token
                    return if (!scanner.checkToken(
                            Token.ID.Value, Token.ID.FlowEntry,
                            Token.ID.FlowMappingEnd
                        )
                    ) {
                        states.push(ParseFlowMappingValue())
                        parseFlowNode()
                    } else {
                        state = ParseFlowMappingValue()
                        processEmptyScalar(token.endMark)
                    }
                } else if (!scanner.checkToken(Token.ID.FlowMappingEnd)) {
                    states.push(ParseFlowMappingEmptyValue())
                    return parseFlowNode()
                }
            }
            val token = scanner.token
            val event: Event = MappingEndEvent(token.startMark, token.endMark)
            marks.pop()
            if (!scanner.checkToken(Token.ID.Comment)) {
                state = states.pop()
            } else {
                state = ParseFlowEndComment()
            }
            return event
        }
    }

    private inner class ParseFlowMappingValue : Production {
        override fun produce(): Event {
            return if (scanner.checkToken(Token.ID.Value)) {
                val token = scanner.token
                if (!scanner.checkToken(
                        Token.ID.FlowEntry,
                        Token.ID.FlowMappingEnd
                    )
                ) {
                    states.push(ParseFlowMappingKey(false))
                    parseFlowNode()
                } else {
                    state = ParseFlowMappingKey(false)
                    processEmptyScalar(token.endMark)
                }
            } else {
                state = ParseFlowMappingKey(false)
                val token = scanner.peekToken()
                processEmptyScalar(token.startMark)
            }
        }
    }

    private inner class ParseFlowMappingEmptyValue : Production {
        override fun produce(): Event {
            state = ParseFlowMappingKey(false)
            return processEmptyScalar(scanner.peekToken().startMark)
        }
    }

    /**
     * <pre>
     * block_mapping     ::= BLOCK-MAPPING_START
     * ((KEY block_node_or_indentless_sequence?)?
     * (VALUE block_node_or_indentless_sequence?)?)*
     * BLOCK-END
    </pre> *
     */
    private fun processEmptyScalar(mark: Mark): Event {
        return ScalarEvent(null, null, ImplicitTuple(true, false), "", mark, mark, DumperOptions.ScalarStyle.PLAIN)
    }

    companion object {
        private val DEFAULT_TAGS: MutableMap<String, String> = HashMap()

        init {
            DEFAULT_TAGS["!"] = "!"
            DEFAULT_TAGS["!!"] = tagPrefix
        }
    }
}

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
package org.yaml.snakeyaml.emitter

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.comments.CommentEventsCollector
import org.yaml.snakeyaml.comments.CommentLine
import org.yaml.snakeyaml.comments.CommentType
import org.yaml.snakeyaml.error.YAMLException
import org.yaml.snakeyaml.events.*
import org.yaml.snakeyaml.nodes.Tag
import org.yaml.snakeyaml.reader.StreamReader
import org.yaml.snakeyaml.scanner.Constant
import org.yaml.snakeyaml.util.ArrayStack
import java.io.IOException
import java.io.Writer
import java.util.*
import java.util.regex.Pattern

/**
 * <pre>
 * Emitter expects events obeying the following grammar:
 * stream ::= STREAM-START document* STREAM-END
 * document ::= DOCUMENT-START node DOCUMENT-END
 * node ::= SCALAR | sequence | mapping
 * sequence ::= SEQUENCE-START node* SEQUENCE-END
 * mapping ::= MAPPING-START (node node)* MAPPING-END
</pre> *
 */
class Emitter(// The stream should have the methods `write` and possibly `flush`.
    private val stream: Writer, opts: DumperOptions
) : Emitable {
    // Encoding is defined by Writer (cannot be overridden by STREAM-START.)
    // private Charset encoding;
    // Emitter is a state machine with a stack of states to handle nested
    // structures.
    private val states: ArrayStack<EmitterState>
    private var state: EmitterState?

    // Current event and the event queue.
    private val events: Queue<Event>
    private var event: Event?

    // The current indentation level and the stack of previous indents.
    private val indents: ArrayStack<Int>
    private var indent: Int?

    // Flow level.
    private var flowLevel: Int

    // Contexts.
    private var rootContext = false
    private var mappingContext: Boolean
    private var simpleKeyContext: Boolean

    //
    // Characteristics of the last emitted character:
    // - current position.
    // - is it a whitespace?
    // - is it an indention character
    // (indentation space, '-', '?', or ':')?
    // private int line; this variable is not used
    private var column: Int
    private var whitespace: Boolean
    private var indention: Boolean
    private var openEnded: Boolean

    // Formatting details.
    private val canonical: Boolean

    // pretty print flow by adding extra line breaks
    private val prettyFlow: Boolean
    private val allowUnicode: Boolean
    private var bestIndent: Int
    private val indicatorIndent: Int
    private val indentWithIndicator: Boolean
    private var bestWidth: Int
    private val bestLineBreak: CharArray
    private val splitLines: Boolean
    private val maxSimpleKeyLength: Int
    private val emitComments: Boolean

    // Tag prefixes.
    private var tagPrefixes: MutableMap<String?, String?>

    // Prepared anchor and tag.
    private var preparedAnchor: String?
    private var preparedTag: String?

    // Scalar analysis and style.
    private var analysis: ScalarAnalysis?
    private var style: DumperOptions.ScalarStyle?

    // Comment processing
    private val blockCommentsCollector: CommentEventsCollector
    private val inlineCommentsCollector: CommentEventsCollector
    @Throws(IOException::class)
    override fun emit(event: Event) {
        events.add(event)
        while (!needMoreEvents()) {
            this.event = events.poll()
            state!!.expect()
            this.event = null
        }
    }

    // In some cases, we wait for a few next events before emitting.
    private fun needMoreEvents(): Boolean {
        if (events.isEmpty()) {
            return true
        }
        val iter: Iterator<Event> = events.iterator()
        var event = iter.next() // FIXME why without check ???
        while (event is CommentEvent) {
            if (!iter.hasNext()) {
                return true
            }
            event = iter.next()
        }
        if (event is DocumentStartEvent) {
            return needEvents(iter, 1)
        } else if (event is SequenceStartEvent) {
            return needEvents(iter, 2)
        } else if (event is MappingStartEvent) {
            return needEvents(iter, 3)
        } else if (event is StreamStartEvent) {
            return needEvents(iter, 2)
        } else if (event is StreamEndEvent) {
            return false
        } else if (emitComments) {
            return needEvents(iter, 1)
        }
        return false
    }

    private fun needEvents(iter: Iterator<Event>, count: Int): Boolean {
        var level = 0
        var actualCount = 0
        while (iter.hasNext()) {
            val event = iter.next()
            if (event is CommentEvent) {
                continue
            }
            actualCount++
            if (event is DocumentStartEvent || event is CollectionStartEvent) {
                level++
            } else if (event is DocumentEndEvent || event is CollectionEndEvent) {
                level--
            } else if (event is StreamEndEvent) {
                level = -1
            }
            if (level < 0) {
                return false
            }
        }
        return actualCount < count
    }

    private fun increaseIndent(flow: Boolean, indentless: Boolean) {
        indents.push(indent!!)
        if (indent == null) {
            indent = if (flow) {
                bestIndent
            } else {
                0
            }
        } else if (!indentless) {
            indent = indent!! + bestIndent
        }
    }

    // States
    // Stream handlers.
    private inner class ExpectStreamStart : EmitterState {
        @Throws(IOException::class)
        override fun expect() {
            if (event is StreamStartEvent) {
                writeStreamStart()
                state = ExpectFirstDocumentStart()
            } else {
                throw EmitterException("expected StreamStartEvent, but got $event")
            }
        }
    }

    private inner class ExpectNothing : EmitterState {
        @Throws(IOException::class)
        override fun expect() {
            throw EmitterException("expecting nothing, but got $event")
        }
    }

    // Document handlers.
    private inner class ExpectFirstDocumentStart : EmitterState {
        @Throws(IOException::class)
        override fun expect() {
            ExpectDocumentStart(true).expect()
        }
    }

    private inner class ExpectDocumentStart(private val first: Boolean) : EmitterState {
        @Throws(IOException::class)
        override fun expect() {
            if (event is DocumentStartEvent) {
                val ev = event as DocumentStartEvent
                if ((ev.version != null || ev.tags != null) && openEnded) {
                    writeIndicator("...", true, false, false)
                    writeIndent()
                }
                if (ev.version != null) {
                    val versionText = prepareVersion(ev.version)
                    writeVersionDirective(versionText)
                }
                tagPrefixes = LinkedHashMap(DEFAULT_TAG_PREFIXES)
                if (ev.tags != null) {
                    val handles: Set<String?> = TreeSet(ev.tags.keys)
                    for (handle in handles) {
                        val prefix = ev.tags[handle]
                        tagPrefixes[prefix] = handle
                        val handleText = prepareTagHandle(handle)
                        val prefixText = prepareTagPrefix(prefix)
                        writeTagDirective(handleText, prefixText)
                    }
                }
                val implicit =
                    (first && !ev.explicit && !canonical && ev.version == null && (ev.tags == null || ev.tags.isEmpty())
                            && !checkEmptyDocument())
                if (!implicit) {
                    writeIndent()
                    writeIndicator("---", true, false, false)
                    if (canonical) {
                        writeIndent()
                    }
                }
                state = ExpectDocumentRoot()
            } else if (event is StreamEndEvent) {
                writeStreamEnd()
                state = ExpectNothing()
            } else if (event is CommentEvent) {
                blockCommentsCollector.collectEvents(event)
                writeBlockComment()
                // state = state; remains unchanged
            } else {
                throw EmitterException("expected DocumentStartEvent, but got $event")
            }
        }
    }

    private inner class ExpectDocumentEnd : EmitterState {
        @Throws(IOException::class)
        override fun expect() {
            event = blockCommentsCollector.collectEventsAndPoll(event)
            writeBlockComment()
            if (event is DocumentEndEvent) {
                writeIndent()
                if ((event as DocumentEndEvent).explicit) {
                    writeIndicator("...", true, false, false)
                    writeIndent()
                }
                flushStream()
                state = ExpectDocumentStart(false)
            } else {
                throw EmitterException("expected DocumentEndEvent, but got $event")
            }
        }
    }

    private inner class ExpectDocumentRoot : EmitterState {
        @Throws(IOException::class)
        override fun expect() {
            event = blockCommentsCollector.collectEventsAndPoll(event)
            if (!blockCommentsCollector.isEmpty) {
                writeBlockComment()
                if (event is DocumentEndEvent) {
                    ExpectDocumentEnd().expect()
                    return
                }
            }
            states.push(ExpectDocumentEnd())
            expectNode(true, false, false)
        }
    }

    // Node handlers.
    @Throws(IOException::class)
    private fun expectNode(root: Boolean, mapping: Boolean, simpleKey: Boolean) {
        rootContext = root
        mappingContext = mapping
        simpleKeyContext = simpleKey
        if (event is AliasEvent) {
            expectAlias()
        } else if (event is ScalarEvent || event is CollectionStartEvent) {
            processAnchor("&")
            processTag()
            if (event is ScalarEvent) {
                expectScalar()
            } else if (event is SequenceStartEvent) {
                if (flowLevel != 0 || canonical || (event as SequenceStartEvent).isFlow
                    || checkEmptySequence()
                ) {
                    expectFlowSequence()
                } else {
                    expectBlockSequence()
                }
            } else { // MappingStartEvent
                if (flowLevel != 0 || canonical || (event as MappingStartEvent).isFlow
                    || checkEmptyMapping()
                ) {
                    expectFlowMapping()
                } else {
                    expectBlockMapping()
                }
            }
        } else {
            throw EmitterException("expected NodeEvent, but got $event")
        }
    }

    @Throws(IOException::class)
    private fun expectAlias() {
        if (event !is AliasEvent) {
            throw EmitterException("Alias must be provided")
        }
        processAnchor("*")
        state = states.pop()
    }

    @Throws(IOException::class)
    private fun expectScalar() {
        increaseIndent(true, false)
        processScalar()
        indent = indents.pop()
        state = states.pop()
    }

    // Flow sequence handlers.
    @Throws(IOException::class)
    private fun expectFlowSequence() {
        writeIndicator("[", true, true, false)
        flowLevel++
        increaseIndent(true, false)
        if (prettyFlow) {
            writeIndent()
        }
        state = ExpectFirstFlowSequenceItem()
    }

    private inner class ExpectFirstFlowSequenceItem : EmitterState {
        @Throws(IOException::class)
        override fun expect() {
            if (event is SequenceEndEvent) {
                indent = indents.pop()
                flowLevel--
                writeIndicator("]", false, false, false)
                inlineCommentsCollector.collectEvents()
                writeInlineComments()
                state = states.pop()
            } else if (event is CommentEvent) {
                blockCommentsCollector.collectEvents(event)
                writeBlockComment()
            } else {
                if (canonical || column > bestWidth && splitLines || prettyFlow) {
                    writeIndent()
                }
                states.push(ExpectFlowSequenceItem())
                expectNode(false, false, false)
                event = inlineCommentsCollector.collectEvents(event)
                writeInlineComments()
            }
        }
    }

    private inner class ExpectFlowSequenceItem : EmitterState {
        @Throws(IOException::class)
        override fun expect() {
            if (event is SequenceEndEvent) {
                indent = indents.pop()
                flowLevel--
                if (canonical) {
                    writeIndicator(",", false, false, false)
                    writeIndent()
                } else if (prettyFlow) {
                    writeIndent()
                }
                writeIndicator("]", false, false, false)
                inlineCommentsCollector.collectEvents()
                writeInlineComments()
                if (prettyFlow) {
                    writeIndent()
                }
                state = states.pop()
            } else if (event is CommentEvent) {
                event = blockCommentsCollector.collectEvents(event)
            } else {
                writeIndicator(",", false, false, false)
                writeBlockComment()
                if (canonical || column > bestWidth && splitLines || prettyFlow) {
                    writeIndent()
                }
                states.push(ExpectFlowSequenceItem())
                expectNode(false, false, false)
                event = inlineCommentsCollector.collectEvents(event)
                writeInlineComments()
            }
        }
    }

    // Flow mapping handlers.
    @Throws(IOException::class)
    private fun expectFlowMapping() {
        writeIndicator("{", true, true, false)
        flowLevel++
        increaseIndent(true, false)
        if (prettyFlow) {
            writeIndent()
        }
        state = ExpectFirstFlowMappingKey()
    }

    private inner class ExpectFirstFlowMappingKey : EmitterState {
        @Throws(IOException::class)
        override fun expect() {
            event = blockCommentsCollector.collectEventsAndPoll(event)
            writeBlockComment()
            if (event is MappingEndEvent) {
                indent = indents.pop()
                flowLevel--
                writeIndicator("}", false, false, false)
                inlineCommentsCollector.collectEvents()
                writeInlineComments()
                state = states.pop()
            } else {
                if (canonical || column > bestWidth && splitLines || prettyFlow) {
                    writeIndent()
                }
                if (!canonical && checkSimpleKey()) {
                    states.push(ExpectFlowMappingSimpleValue())
                    expectNode(false, true, true)
                } else {
                    writeIndicator("?", true, false, false)
                    states.push(ExpectFlowMappingValue())
                    expectNode(false, true, false)
                }
            }
        }
    }

    private inner class ExpectFlowMappingKey : EmitterState {
        @Throws(IOException::class)
        override fun expect() {
            if (event is MappingEndEvent) {
                indent = indents.pop()
                flowLevel--
                if (canonical) {
                    writeIndicator(",", false, false, false)
                    writeIndent()
                }
                if (prettyFlow) {
                    writeIndent()
                }
                writeIndicator("}", false, false, false)
                inlineCommentsCollector.collectEvents()
                writeInlineComments()
                state = states.pop()
            } else {
                writeIndicator(",", false, false, false)
                event = blockCommentsCollector.collectEventsAndPoll(event)
                writeBlockComment()
                if (canonical || column > bestWidth && splitLines || prettyFlow) {
                    writeIndent()
                }
                if (!canonical && checkSimpleKey()) {
                    states.push(ExpectFlowMappingSimpleValue())
                    expectNode(false, true, true)
                } else {
                    writeIndicator("?", true, false, false)
                    states.push(ExpectFlowMappingValue())
                    expectNode(false, true, false)
                }
            }
        }
    }

    private inner class ExpectFlowMappingSimpleValue : EmitterState {
        @Throws(IOException::class)
        override fun expect() {
            writeIndicator(":", false, false, false)
            event = inlineCommentsCollector.collectEventsAndPoll(event)
            writeInlineComments()
            states.push(ExpectFlowMappingKey())
            expectNode(false, true, false)
            inlineCommentsCollector.collectEvents(event)
            writeInlineComments()
        }
    }

    private inner class ExpectFlowMappingValue : EmitterState {
        @Throws(IOException::class)
        override fun expect() {
            if (canonical || column > bestWidth || prettyFlow) {
                writeIndent()
            }
            writeIndicator(":", true, false, false)
            event = inlineCommentsCollector.collectEventsAndPoll(event)
            writeInlineComments()
            states.push(ExpectFlowMappingKey())
            expectNode(false, true, false)
            inlineCommentsCollector.collectEvents(event)
            writeInlineComments()
        }
    }

    // Block sequence handlers.
    @Throws(IOException::class)
    private fun expectBlockSequence() {
        val indentless = mappingContext && !indention
        increaseIndent(false, indentless)
        state = ExpectFirstBlockSequenceItem()
    }

    private inner class ExpectFirstBlockSequenceItem : EmitterState {
        @Throws(IOException::class)
        override fun expect() {
            ExpectBlockSequenceItem(true).expect()
        }
    }

    private inner class ExpectBlockSequenceItem(private val first: Boolean) : EmitterState {
        @Throws(IOException::class)
        override fun expect() {
            if (!first && event is SequenceEndEvent) {
                indent = indents.pop()
                state = states.pop()
            } else if (event is CommentEvent) {
                blockCommentsCollector.collectEvents(event)
            } else {
                writeIndent()
                if (!indentWithIndicator || first) {
                    writeWhitespace(indicatorIndent)
                }
                writeIndicator("-", true, false, true)
                if (indentWithIndicator && first) {
                    indent = indent!! + indicatorIndent
                }
                if (!blockCommentsCollector.isEmpty) {
                    increaseIndent(false, false)
                    writeBlockComment()
                    if (event is ScalarEvent) {
                        analysis = analyzeScalar((event as ScalarEvent).value)
                        if (!analysis.isEmpty()) {
                            writeIndent()
                        }
                    }
                    indent = indents.pop()
                }
                states.push(ExpectBlockSequenceItem(false))
                expectNode(false, false, false)
                inlineCommentsCollector.collectEvents()
                writeInlineComments()
            }
        }
    }

    // Block mapping handlers.
    @Throws(IOException::class)
    private fun expectBlockMapping() {
        increaseIndent(false, false)
        state = ExpectFirstBlockMappingKey()
    }

    private inner class ExpectFirstBlockMappingKey : EmitterState {
        @Throws(IOException::class)
        override fun expect() {
            ExpectBlockMappingKey(true).expect()
        }
    }

    private inner class ExpectBlockMappingKey(private val first: Boolean) : EmitterState {
        @Throws(IOException::class)
        override fun expect() {
            event = blockCommentsCollector.collectEventsAndPoll(event)
            writeBlockComment()
            if (!first && event is MappingEndEvent) {
                indent = indents.pop()
                state = states.pop()
            } else {
                writeIndent()
                if (checkSimpleKey()) {
                    states.push(ExpectBlockMappingSimpleValue())
                    expectNode(false, true, true)
                } else {
                    writeIndicator("?", true, false, true)
                    states.push(ExpectBlockMappingValue())
                    expectNode(false, true, false)
                }
            }
        }
    }

    private fun isFoldedOrLiteral(event: Event): Boolean {
        if (!event.`is`(Event.ID.Scalar)) {
            return false
        }
        val scalarEvent = event as ScalarEvent
        val style = scalarEvent.scalarStyle
        return style === DumperOptions.ScalarStyle.FOLDED || style === DumperOptions.ScalarStyle.LITERAL
    }

    private inner class ExpectBlockMappingSimpleValue : EmitterState {
        @Throws(IOException::class)
        override fun expect() {
            writeIndicator(":", false, false, false)
            event = inlineCommentsCollector.collectEventsAndPoll(event)
            if (!isFoldedOrLiteral(event!!)) {
                if (writeInlineComments()) {
                    increaseIndent(true, false)
                    writeIndent()
                    indent = indents.pop()
                }
            }
            event = blockCommentsCollector.collectEventsAndPoll(event)
            if (!blockCommentsCollector.isEmpty) {
                increaseIndent(true, false)
                writeBlockComment()
                writeIndent()
                indent = indents.pop()
            }
            states.push(ExpectBlockMappingKey(false))
            expectNode(false, true, false)
            inlineCommentsCollector.collectEvents()
            writeInlineComments()
        }
    }

    private inner class ExpectBlockMappingValue : EmitterState {
        @Throws(IOException::class)
        override fun expect() {
            writeIndent()
            writeIndicator(":", true, false, true)
            event = inlineCommentsCollector.collectEventsAndPoll(event)
            writeInlineComments()
            event = blockCommentsCollector.collectEventsAndPoll(event)
            writeBlockComment()
            states.push(ExpectBlockMappingKey(false))
            expectNode(false, true, false)
            inlineCommentsCollector.collectEvents(event)
            writeInlineComments()
        }
    }

    // Checkers.
    private fun checkEmptySequence(): Boolean {
        return event is SequenceStartEvent && !events.isEmpty() && events.peek() is SequenceEndEvent
    }

    private fun checkEmptyMapping(): Boolean {
        return event is MappingStartEvent && !events.isEmpty() && events.peek() is MappingEndEvent
    }

    private fun checkEmptyDocument(): Boolean {
        if (event !is DocumentStartEvent || events.isEmpty()) {
            return false
        }
        val event = events.peek()
        if (event is ScalarEvent) {
            val e = event
            return e.anchor == null && e.tag == null && e.implicit != null && e
                .value.length == 0
        }
        return false
    }

    private fun checkSimpleKey(): Boolean {
        var length = 0
        if (event is NodeEvent && (event as NodeEvent).anchor != null) {
            if (preparedAnchor == null) {
                preparedAnchor = prepareAnchor((event as NodeEvent).anchor)
            }
            length += preparedAnchor!!.length
        }
        var tag: String? = null
        if (event is ScalarEvent) {
            tag = (event as ScalarEvent).tag
        } else if (event is CollectionStartEvent) {
            tag = (event as CollectionStartEvent).tag
        }
        if (tag != null) {
            if (preparedTag == null) {
                preparedTag = prepareTag(tag)
            }
            length += preparedTag!!.length
        }
        if (event is ScalarEvent) {
            if (analysis == null) {
                analysis = analyzeScalar((event as ScalarEvent).value)
            }
            length += analysis.getScalar().length
        }
        return length < maxSimpleKeyLength && (event is AliasEvent || event is ScalarEvent && !analysis.isEmpty() && !analysis!!.isMultiline
                || checkEmptySequence() || checkEmptyMapping())
    }

    // Anchor, Tag, and Scalar processors.
    @Throws(IOException::class)
    private fun processAnchor(indicator: String) {
        val ev = event as NodeEvent?
        if (ev.getAnchor() == null) {
            preparedAnchor = null
            return
        }
        if (preparedAnchor == null) {
            preparedAnchor = prepareAnchor(ev.getAnchor())
        }
        writeIndicator(indicator + preparedAnchor, true, false, false)
        preparedAnchor = null
    }

    @Throws(IOException::class)
    private fun processTag() {
        var tag: String? = null
        if (event is ScalarEvent) {
            val ev = event as ScalarEvent
            tag = ev.tag
            if (style == null) {
                style = chooseScalarStyle()
            }
            if ((!canonical || tag == null) && (style == null && ev.implicit
                    .canOmitTagInPlainScalar() || style != null) && ev.implicit
                    .canOmitTagInNonPlainScalar()
            ) {
                preparedTag = null
                return
            }
            if (ev.implicit!!.canOmitTagInPlainScalar() && tag == null) {
                tag = "!"
                preparedTag = null
            }
        } else {
            val ev = event as CollectionStartEvent?
            tag = ev.getTag()
            if ((!canonical || tag == null) && ev.getImplicit()) {
                preparedTag = null
                return
            }
        }
        if (tag == null) {
            throw EmitterException("tag is not specified")
        }
        if (preparedTag == null) {
            preparedTag = prepareTag(tag)
        }
        writeIndicator(preparedTag, true, false, false)
        preparedTag = null
    }

    private fun chooseScalarStyle(): DumperOptions.ScalarStyle? {
        val ev = event as ScalarEvent?
        if (analysis == null) {
            analysis = analyzeScalar(ev.getValue())
        }
        if (!ev!!.isPlain && ev.scalarStyle === DumperOptions.ScalarStyle.DOUBLE_QUOTED || canonical) {
            return DumperOptions.ScalarStyle.DOUBLE_QUOTED
        }
        if (ev.isPlain && ev.implicit!!.canOmitTagInPlainScalar()) {
            if (!(simpleKeyContext && (analysis.isEmpty() || analysis!!.isMultiline)) && (flowLevel != 0 && analysis!!.isAllowFlowPlain || flowLevel) == 0 && analysis!!.isAllowBlockPlain) {
                return null
            }
        }
        if (!ev.isPlain && (ev.scalarStyle === DumperOptions.ScalarStyle.LITERAL || ev.scalarStyle === DumperOptions.ScalarStyle.FOLDED)) {
            if (flowLevel == 0 && !simpleKeyContext && analysis!!.isAllowBlock) {
                return ev.scalarStyle
            }
        }
        if (ev.isPlain || ev.scalarStyle === DumperOptions.ScalarStyle.SINGLE_QUOTED) {
            if (analysis!!.isAllowSingleQuoted && !(simpleKeyContext && analysis!!.isMultiline)) {
                return DumperOptions.ScalarStyle.SINGLE_QUOTED
            }
        }
        return DumperOptions.ScalarStyle.DOUBLE_QUOTED
    }

    @Throws(IOException::class)
    private fun processScalar() {
        val ev = event as ScalarEvent?
        if (analysis == null) {
            analysis = analyzeScalar(ev.getValue())
        }
        if (style == null) {
            style = chooseScalarStyle()
        }
        val split = !simpleKeyContext && splitLines
        if (style == null) {
            writePlain(analysis.getScalar(), split)
        } else {
            when (style) {
                DumperOptions.ScalarStyle.DOUBLE_QUOTED -> writeDoubleQuoted(analysis.getScalar(), split)
                DumperOptions.ScalarStyle.SINGLE_QUOTED -> writeSingleQuoted(analysis.getScalar(), split)
                DumperOptions.ScalarStyle.FOLDED -> writeFolded(analysis.getScalar(), split)
                DumperOptions.ScalarStyle.LITERAL -> writeLiteral(analysis.getScalar())
                else -> throw YAMLException("Unexpected style: $style")
            }
        }
        analysis = null
        style = null
    }

    // Analyzers.
    private fun prepareVersion(version: DumperOptions.Version?): String {
        if (version!!.major() != 1) {
            throw EmitterException("unsupported YAML version: $version")
        }
        return version.representation
    }

    init {
        // The stream should have the methods `write` and possibly `flush`.
        // Emitter is a state machine with a stack of states to handle nested
        // structures.
        states = ArrayStack(100)
        state = ExpectStreamStart()
        // Current event and the event queue.
        events = ArrayDeque(100)
        event = null
        // The current indentation level and the stack of previous indents.
        indents = ArrayStack(10)
        indent = null
        // Flow level.
        flowLevel = 0
        // Contexts.
        mappingContext = false
        simpleKeyContext = false

        //
        // Characteristics of the last emitted character:
        // - current position.
        // - is it a whitespace?
        // - is it an indention character
        // (indentation space, '-', '?', or ':')?
        column = 0
        whitespace = true
        indention = true

        // Whether the document requires an explicit document indicator
        openEnded = false

        // Formatting details.
        canonical = opts.isCanonical
        prettyFlow = opts.isPrettyFlow
        allowUnicode = opts.isAllowUnicode
        bestIndent = 2
        if (opts.indent > MIN_INDENT && opts.indent < MAX_INDENT) {
            bestIndent = opts.indent
        }
        indicatorIndent = opts.indicatorIndent
        indentWithIndicator = opts.indentWithIndicator
        bestWidth = 80
        if (opts.width > bestIndent * 2) {
            bestWidth = opts.width
        }
        bestLineBreak = opts.lineBreak.string.toCharArray()
        splitLines = opts.splitLines
        maxSimpleKeyLength = opts.maxSimpleKeyLength
        emitComments = opts.isProcessComments

        // Tag prefixes.
        tagPrefixes = LinkedHashMap()

        // Prepared anchor and tag.
        preparedAnchor = null
        preparedTag = null

        // Scalar analysis and style.
        analysis = null
        style = null

        // Comment processing
        blockCommentsCollector = CommentEventsCollector(
            events,
            CommentType.BLANK_LINE, CommentType.BLOCK
        )
        inlineCommentsCollector = CommentEventsCollector(
            events,
            CommentType.IN_LINE
        )
    }

    private fun prepareTagHandle(handle: String?): String? {
        if (handle!!.length == 0) {
            throw EmitterException("tag handle must not be empty")
        } else if (handle[0] != '!' || handle[handle.length - 1] != '!') {
            throw EmitterException("tag handle must start and end with '!': $handle")
        } else if ("!" != handle && !HANDLE_FORMAT.matcher(handle).matches()) {
            throw EmitterException("invalid character in the tag handle: $handle")
        }
        return handle
    }

    private fun prepareTagPrefix(prefix: String?): String {
        if (prefix!!.length == 0) {
            throw EmitterException("tag prefix must not be empty")
        }
        val chunks = StringBuilder()
        val start = 0
        var end = 0
        if (prefix[0] == '!') {
            end = 1
        }
        while (end < prefix.length) {
            end++
        }
        if (start < end) {
            chunks.append(prefix, start, end)
        }
        return chunks.toString()
    }

    private fun prepareTag(tag: String): String {
        if (tag.length == 0) {
            throw EmitterException("tag must not be empty")
        }
        if ("!" == tag) {
            return tag
        }
        var handle: String? = null
        var suffix = tag
        // shall the tag prefixes be sorted as in PyYAML?
        for (prefix in tagPrefixes.keys) {
            if (tag.startsWith(prefix!!) && ("!" == prefix || prefix.length < tag.length)) {
                handle = prefix
            }
        }
        if (handle != null) {
            suffix = tag.substring(handle.length)
            handle = tagPrefixes[handle]
        }
        val end = suffix.length
        val suffixText = if (end > 0) suffix.substring(0, end) else ""
        return if (handle != null) {
            handle + suffixText
        } else "!<$suffixText>"
    }

    private fun analyzeScalar(scalar: String?): ScalarAnalysis {
        // Empty scalar is a special case.
        if (scalar!!.length == 0) {
            return ScalarAnalysis(scalar, true, false, false, true, true, false)
        }
        // Indicators and special characters.
        var blockIndicators = false
        var flowIndicators = false
        var lineBreaks = false
        var specialCharacters = false

        // Important whitespace combinations.
        var leadingSpace = false
        var leadingBreak = false
        var trailingSpace = false
        var trailingBreak = false
        var breakSpace = false
        var spaceBreak = false

        // Check document indicators.
        if (scalar.startsWith("---") || scalar.startsWith("...")) {
            blockIndicators = true
            flowIndicators = true
        }
        // First character or preceded by a whitespace.
        var preceededByWhitespace = true
        var followedByWhitespace = scalar.length == 1 || Constant.Companion.NULL_BL_T_LINEBR.has(
            scalar.codePointAt(1)
        )
        // The previous character is a space.
        var previousSpace = false

        // The previous character is a break.
        var previousBreak = false
        var index = 0
        while (index < scalar.length) {
            val c = scalar.codePointAt(index)
            // Check for indicators.
            if (index == 0) {
                // Leading indicators are special characters.
                if ("#,[]{}&*!|>'\"%@`".indexOf(c.toChar()) != -1) {
                    flowIndicators = true
                    blockIndicators = true
                }
                if (c == '?'.code || c == ':'.code) {
                    flowIndicators = true
                    if (followedByWhitespace) {
                        blockIndicators = true
                    }
                }
                if (c == '-'.code && followedByWhitespace) {
                    flowIndicators = true
                    blockIndicators = true
                }
            } else {
                // Some indicators cannot appear within a scalar as well.
                if (",?[]{}".indexOf(c.toChar()) != -1) {
                    flowIndicators = true
                }
                if (c == ':'.code) {
                    flowIndicators = true
                    if (followedByWhitespace) {
                        blockIndicators = true
                    }
                }
                if (c == '#'.code && preceededByWhitespace) {
                    flowIndicators = true
                    blockIndicators = true
                }
            }
            // Check for line breaks, special, and unicode characters.
            val isLineBreak: Boolean = Constant.Companion.LINEBR.has(c)
            if (isLineBreak) {
                lineBreaks = true
            }
            if (!(c == '\n'.code || 0x20 <= c && c <= 0x7E)) {
                if (c == 0x85 || c >= 0xA0 && c <= 0xD7FF || c >= 0xE000 && c <= 0xFFFD || c >= 0x10000 && c <= 0x10FFFF) {
                    // unicode is used
                    if (!allowUnicode) {
                        specialCharacters = true
                    }
                } else {
                    specialCharacters = true
                }
            }
            // Detect important whitespace combinations.
            if (c == ' '.code) {
                if (index == 0) {
                    leadingSpace = true
                }
                if (index == scalar.length - 1) {
                    trailingSpace = true
                }
                if (previousBreak) {
                    breakSpace = true
                }
                previousSpace = true
                previousBreak = false
            } else if (isLineBreak) {
                if (index == 0) {
                    leadingBreak = true
                }
                if (index == scalar.length - 1) {
                    trailingBreak = true
                }
                if (previousSpace) {
                    spaceBreak = true
                }
                previousSpace = false
                previousBreak = true
            } else {
                previousSpace = false
                previousBreak = false
            }

            // Prepare for the next character.
            index += Character.charCount(c)
            preceededByWhitespace = Constant.Companion.NULL_BL_T.has(c) || isLineBreak
            followedByWhitespace = true
            if (index + 1 < scalar.length) {
                val nextIndex = index + Character.charCount(scalar.codePointAt(index))
                if (nextIndex < scalar.length) {
                    followedByWhitespace =
                        Constant.Companion.NULL_BL_T.has(scalar.codePointAt(nextIndex)) || isLineBreak
                }
            }
        }
        // Let's decide what styles are allowed.
        var allowFlowPlain = true
        var allowBlockPlain = true
        var allowSingleQuoted = true
        var allowBlock = true
        // Leading and trailing whitespaces are bad for plain scalars.
        if (leadingSpace || leadingBreak || trailingSpace || trailingBreak) {
            allowBlockPlain = false
            allowFlowPlain = allowBlockPlain
        }
        // We do not permit trailing spaces for block scalars.
        if (trailingSpace) {
            allowBlock = false
        }
        // Spaces at the beginning of a new line are only acceptable for block
        // scalars.
        if (breakSpace) {
            allowSingleQuoted = false
            allowBlockPlain = allowSingleQuoted
            allowFlowPlain = allowBlockPlain
        }
        // Spaces followed by breaks, as well as special character are only
        // allowed for double quoted scalars.
        if (spaceBreak || specialCharacters) {
            allowBlock = false
            allowSingleQuoted = allowBlock
            allowBlockPlain = allowSingleQuoted
            allowFlowPlain = allowBlockPlain
        }
        // Although the plain scalar writer supports breaks, we never emit
        // multiline plain scalars in the flow context.
        if (lineBreaks) {
            allowFlowPlain = false
        }
        // Flow indicators are forbidden for flow plain scalars.
        if (flowIndicators) {
            allowFlowPlain = false
        }
        // Block indicators are forbidden for block plain scalars.
        if (blockIndicators) {
            allowBlockPlain = false
        }
        return ScalarAnalysis(
            scalar, false, lineBreaks, allowFlowPlain, allowBlockPlain,
            allowSingleQuoted, allowBlock
        )
    }

    // Writers.
    @Throws(IOException::class)
    fun flushStream() {
        stream.flush()
    }

    fun writeStreamStart() {
        // BOM is written by Writer.
    }

    @Throws(IOException::class)
    fun writeStreamEnd() {
        flushStream()
    }

    @Throws(IOException::class)
    fun writeIndicator(
        indicator: String?, needWhitespace: Boolean, whitespace: Boolean,
        indentation: Boolean
    ) {
        if (!this.whitespace && needWhitespace) {
            column++
            stream.write(SPACE)
        }
        this.whitespace = whitespace
        indention = indention && indentation
        column += indicator!!.length
        openEnded = false
        stream.write(indicator)
    }

    @Throws(IOException::class)
    fun writeIndent() {
        val indent: Int
        indent = if (this.indent != null) {
            this.indent!!
        } else {
            0
        }
        if (!indention || column > indent || column == indent && !whitespace) {
            writeLineBreak(null)
        }
        writeWhitespace(indent - column)
    }

    @Throws(IOException::class)
    private fun writeWhitespace(length: Int) {
        if (length <= 0) {
            return
        }
        whitespace = true
        val data = CharArray(length)
        for (i in data.indices) {
            data[i] = ' '
        }
        column += length
        stream.write(data)
    }

    @Throws(IOException::class)
    private fun writeLineBreak(data: String?) {
        whitespace = true
        indention = true
        column = 0
        if (data == null) {
            stream.write(bestLineBreak)
        } else {
            stream.write(data)
        }
    }

    @Throws(IOException::class)
    fun writeVersionDirective(versionText: String?) {
        stream.write("%YAML ")
        stream.write(versionText)
        writeLineBreak(null)
    }

    @Throws(IOException::class)
    fun writeTagDirective(handleText: String?, prefixText: String?) {
        // XXX: not sure 4 invocations better then StringBuilders created by str
        // + str
        stream.write("%TAG ")
        stream.write(handleText)
        stream.write(SPACE)
        stream.write(prefixText)
        writeLineBreak(null)
    }

    // Scalar streams.
    @Throws(IOException::class)
    private fun writeSingleQuoted(text: String?, split: Boolean) {
        writeIndicator("'", true, false, false)
        var spaces = false
        var breaks = false
        var start = 0
        var end = 0
        var ch: Char
        while (end <= text!!.length) {
            ch = 0.toChar()
            if (end < text.length) {
                ch = text[end]
            }
            if (spaces) {
                if (ch.code == 0 || ch != ' ') {
                    if (start + 1 == end && column > bestWidth && split && start != 0 && end != text.length) {
                        writeIndent()
                    } else {
                        val len = end - start
                        column += len
                        stream.write(text, start, len)
                    }
                    start = end
                }
            } else if (breaks) {
                if (ch.code == 0 || Constant.Companion.LINEBR.hasNo(ch.code)) {
                    if (text[start] == '\n') {
                        writeLineBreak(null)
                    }
                    val data = text.substring(start, end)
                    for (br in data.toCharArray()) {
                        if (br == '\n') {
                            writeLineBreak(null)
                        } else {
                            writeLineBreak(br.toString())
                        }
                    }
                    writeIndent()
                    start = end
                }
            } else {
                if (Constant.Companion.LINEBR.has(ch.code, "\u0000 '")) {
                    if (start < end) {
                        val len = end - start
                        column += len
                        stream.write(text, start, len)
                        start = end
                    }
                }
            }
            if (ch == '\'') {
                column += 2
                stream.write("''")
                start = end + 1
            }
            if (ch.code != 0) {
                spaces = ch == ' '
                breaks = Constant.Companion.LINEBR.has(ch.code)
            }
            end++
        }
        writeIndicator("'", false, false, false)
    }

    @Throws(IOException::class)
    private fun writeDoubleQuoted(text: String?, split: Boolean) {
        writeIndicator("\"", true, false, false)
        var start = 0
        var end = 0
        while (end <= text!!.length) {
            var ch: Char? = null
            if (end < text.length) {
                ch = text[end]
            }
            if (ch == null || "\"\\\u0085\u2028\u2029\uFEFF".indexOf(ch) != -1 || !('\u0020' <= ch && ch <= '\u007E')) {
                if (start < end) {
                    val len = end - start
                    column += len
                    stream.write(text, start, len)
                    start = end
                }
                if (ch != null) {
                    var data: String
                    data = if (ESCAPE_REPLACEMENTS.containsKey(ch)) {
                        "\\" + ESCAPE_REPLACEMENTS[ch]
                    } else if (!allowUnicode || !StreamReader.Companion.isPrintable(ch.code)) {
                        // if !allowUnicode or the character is not printable,
                        // we must encode it
                        if (ch <= '\u00FF') {
                            val s = "0" + Integer.toString(ch.code, 16)
                            "\\x" + s.substring(s.length - 2)
                        } else if (ch >= '\uD800' && ch <= '\uDBFF') {
                            if (end + 1 < text.length) {
                                val ch2 = text[++end]
                                val s = "000" + java.lang.Long.toHexString(Character.toCodePoint(ch, ch2).toLong())
                                "\\U" + s.substring(s.length - 8)
                            } else {
                                val s = "000" + Integer.toString(ch.code, 16)
                                "\\u" + s.substring(s.length - 4)
                            }
                        } else {
                            val s = "000" + Integer.toString(ch.code, 16)
                            "\\u" + s.substring(s.length - 4)
                        }
                    } else {
                        ch.toString()
                    }
                    column += data.length
                    stream.write(data)
                    start = end + 1
                }
            }
            if (0 < end && end < text.length - 1 && (ch == ' ' || start >= end) && column + (end - start) > bestWidth && split) {
                var data: String
                data = if (start >= end) {
                    "\\"
                } else {
                    text.substring(start, end) + "\\"
                }
                if (start < end) {
                    start = end
                }
                column += data.length
                stream.write(data)
                writeIndent()
                whitespace = false
                indention = false
                if (text[start] == ' ') {
                    data = "\\"
                    column += data.length
                    stream.write(data)
                }
            }
            end += 1
        }
        writeIndicator("\"", false, false, false)
    }

    @Throws(IOException::class)
    private fun writeCommentLines(commentLines: List<CommentLine>): Boolean {
        var wroteComment = false
        if (emitComments) {
            var indentColumns = 0
            var firstComment = true
            for (commentLine in commentLines) {
                if (commentLine.commentType !== CommentType.BLANK_LINE) {
                    if (firstComment) {
                        firstComment = false
                        writeIndicator("#", commentLine.commentType === CommentType.IN_LINE, false, false)
                        indentColumns = if (column > 0) column - 1 else 0
                    } else {
                        writeWhitespace(indentColumns)
                        writeIndicator("#", false, false, false)
                    }
                    stream.write(commentLine.value)
                    writeLineBreak(null)
                } else {
                    writeLineBreak(null)
                    writeIndent()
                }
                wroteComment = true
            }
        }
        return wroteComment
    }

    @Throws(IOException::class)
    private fun writeBlockComment() {
        if (!blockCommentsCollector.isEmpty) {
            writeIndent()
            writeCommentLines(blockCommentsCollector.consume())
        }
    }

    @Throws(IOException::class)
    private fun writeInlineComments(): Boolean {
        return writeCommentLines(inlineCommentsCollector.consume())
    }

    private fun determineBlockHints(text: String?): String {
        val hints = StringBuilder()
        if (Constant.Companion.LINEBR.has(text!![0].code, " ")) {
            hints.append(bestIndent)
        }
        val ch1 = text[text.length - 1]
        if (Constant.Companion.LINEBR.hasNo(ch1.code)) {
            hints.append("-")
        } else if (text.length == 1 || Constant.Companion.LINEBR.has(text[text.length - 2].code)) {
            hints.append("+")
        }
        return hints.toString()
    }

    @Throws(IOException::class)
    fun writeFolded(text: String?, split: Boolean) {
        val hints = determineBlockHints(text)
        writeIndicator(">$hints", true, false, false)
        if (hints.length > 0 && hints[hints.length - 1] == '+') {
            openEnded = true
        }
        if (!writeInlineComments()) {
            writeLineBreak(null)
        }
        var leadingSpace = true
        var spaces = false
        var breaks = true
        var start = 0
        var end = 0
        while (end <= text!!.length) {
            var ch = 0.toChar()
            if (end < text.length) {
                ch = text[end]
            }
            if (breaks) {
                if (ch.code == 0 || Constant.Companion.LINEBR.hasNo(ch.code)) {
                    if (!leadingSpace && ch.code != 0 && ch != ' ' && text[start] == '\n') {
                        writeLineBreak(null)
                    }
                    leadingSpace = ch == ' '
                    val data = text.substring(start, end)
                    for (br in data.toCharArray()) {
                        if (br == '\n') {
                            writeLineBreak(null)
                        } else {
                            writeLineBreak(br.toString())
                        }
                    }
                    if (ch.code != 0) {
                        writeIndent()
                    }
                    start = end
                }
            } else if (spaces) {
                if (ch != ' ') {
                    if (start + 1 == end && column > bestWidth && split) {
                        writeIndent()
                    } else {
                        val len = end - start
                        column += len
                        stream.write(text, start, len)
                    }
                    start = end
                }
            } else {
                if (Constant.Companion.LINEBR.has(ch.code, "\u0000 ")) {
                    val len = end - start
                    column += len
                    stream.write(text, start, len)
                    if (ch.code == 0) {
                        writeLineBreak(null)
                    }
                    start = end
                }
            }
            if (ch.code != 0) {
                breaks = Constant.Companion.LINEBR.has(ch.code)
                spaces = ch == ' '
            }
            end++
        }
    }

    @Throws(IOException::class)
    fun writeLiteral(text: String?) {
        val hints = determineBlockHints(text)
        writeIndicator("|$hints", true, false, false)
        if (hints.length > 0 && hints[hints.length - 1] == '+') {
            openEnded = true
        }
        if (!writeInlineComments()) {
            writeLineBreak(null)
        }
        var breaks = true
        var start = 0
        var end = 0
        while (end <= text!!.length) {
            var ch = 0.toChar()
            if (end < text.length) {
                ch = text[end]
            }
            if (breaks) {
                if (ch.code == 0 || Constant.Companion.LINEBR.hasNo(ch.code)) {
                    val data = text.substring(start, end)
                    for (br in data.toCharArray()) {
                        if (br == '\n') {
                            writeLineBreak(null)
                        } else {
                            writeLineBreak(br.toString())
                        }
                    }
                    if (ch.code != 0) {
                        writeIndent()
                    }
                    start = end
                }
            } else {
                if (ch.code == 0 || Constant.Companion.LINEBR.has(ch.code)) {
                    stream.write(text, start, end - start)
                    if (ch.code == 0) {
                        writeLineBreak(null)
                    }
                    start = end
                }
            }
            if (ch.code != 0) {
                breaks = Constant.Companion.LINEBR.has(ch.code)
            }
            end++
        }
    }

    @Throws(IOException::class)
    fun writePlain(text: String?, split: Boolean) {
        if (rootContext) {
            openEnded = true
        }
        if (text!!.length == 0) {
            return
        }
        if (!whitespace) {
            column++
            stream.write(SPACE)
        }
        whitespace = false
        indention = false
        var spaces = false
        var breaks = false
        var start = 0
        var end = 0
        while (end <= text.length) {
            var ch = 0.toChar()
            if (end < text.length) {
                ch = text[end]
            }
            if (spaces) {
                if (ch != ' ') {
                    if (start + 1 == end && column > bestWidth && split) {
                        writeIndent()
                        whitespace = false
                        indention = false
                    } else {
                        val len = end - start
                        column += len
                        stream.write(text, start, len)
                    }
                    start = end
                }
            } else if (breaks) {
                if (Constant.Companion.LINEBR.hasNo(ch.code)) {
                    if (text[start] == '\n') {
                        writeLineBreak(null)
                    }
                    val data = text.substring(start, end)
                    for (br in data.toCharArray()) {
                        if (br == '\n') {
                            writeLineBreak(null)
                        } else {
                            writeLineBreak(br.toString())
                        }
                    }
                    writeIndent()
                    whitespace = false
                    indention = false
                    start = end
                }
            } else {
                if (Constant.Companion.LINEBR.has(ch.code, "\u0000 ")) {
                    val len = end - start
                    column += len
                    stream.write(text, start, len)
                    start = end
                }
            }
            if (ch.code != 0) {
                spaces = ch == ' '
                breaks = Constant.Companion.LINEBR.has(ch.code)
            }
            end++
        }
    }

    companion object {
        const val MIN_INDENT = 1
        const val MAX_INDENT = 10
        private val SPACE = charArrayOf(' ')
        private val SPACES_PATTERN = Pattern.compile("\\s")
        private val INVALID_ANCHOR: MutableSet<Char?> = HashSet<Any?>()

        init {
            INVALID_ANCHOR.add('[')
            INVALID_ANCHOR.add(']')
            INVALID_ANCHOR.add('{')
            INVALID_ANCHOR.add('}')
            INVALID_ANCHOR.add(',')
            INVALID_ANCHOR.add('*')
            INVALID_ANCHOR.add('&')
        }

        private val ESCAPE_REPLACEMENTS: MutableMap<Char, String> = HashMap()

        init {
            ESCAPE_REPLACEMENTS['\u0000'] = "0"
            ESCAPE_REPLACEMENTS['\u0007'] = "a"
            ESCAPE_REPLACEMENTS['\u0008'] = "b"
            ESCAPE_REPLACEMENTS['\u0009'] = "t"
            ESCAPE_REPLACEMENTS['\n'] = "n"
            ESCAPE_REPLACEMENTS['\u000B'] = "v"
            ESCAPE_REPLACEMENTS['\u000C'] = "f"
            ESCAPE_REPLACEMENTS['\r'] = "r"
            ESCAPE_REPLACEMENTS['\u001B'] = "e"
            ESCAPE_REPLACEMENTS['"'] = "\""
            ESCAPE_REPLACEMENTS['\\'] = "\\"
            ESCAPE_REPLACEMENTS['\u0085'] = "N"
            ESCAPE_REPLACEMENTS['\u00A0'] = "_"
            ESCAPE_REPLACEMENTS['\u2028'] = "L"
            ESCAPE_REPLACEMENTS['\u2029'] = "P"
        }

        private val DEFAULT_TAG_PREFIXES: MutableMap<String?, String?> = LinkedHashMap()

        init {
            DEFAULT_TAG_PREFIXES["!"] = "!"
            DEFAULT_TAG_PREFIXES[Tag.Companion.PREFIX] = "!!"
        }

        private val HANDLE_FORMAT = Pattern.compile("^![-_\\w]*!$")
        fun prepareAnchor(anchor: String?): String? {
            if (anchor!!.length == 0) {
                throw EmitterException("anchor must not be empty")
            }
            for (invalid in INVALID_ANCHOR) {
                if (anchor.indexOf(invalid!!) > -1) {
                    throw EmitterException("Invalid character '$invalid' in the anchor: $anchor")
                }
            }
            val matcher = SPACES_PATTERN.matcher(anchor)
            if (matcher.find()) {
                throw EmitterException("Anchor may not contain spaces: $anchor")
            }
            return anchor
        }
    }
}
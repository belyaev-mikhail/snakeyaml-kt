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
package org.yaml.snakeyaml.scanner

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.comments.CommentType
import org.yaml.snakeyaml.error.Mark
import org.yaml.snakeyaml.error.YAMLException
import org.yaml.snakeyaml.mppio.CodepointsPortable.appendCodePoint
import org.yaml.snakeyaml.mppio.CodepointsPortable.toString
import org.yaml.snakeyaml.mppio.charCount
import org.yaml.snakeyaml.mppio.isDecimalDigit
import org.yaml.snakeyaml.mppio.isSupplementaryCodePoint
import org.yaml.snakeyaml.reader.StreamReader
import org.yaml.snakeyaml.scanner.Constant.Companion.NULL_BL_T_LINEBR
import org.yaml.snakeyaml.tokens.*
import org.yaml.snakeyaml.util.ArrayStack
import org.yaml.snakeyaml.util.UriEncoder
import kotlin.jvm.JvmField

/**
 * <pre>
 * Scanner produces tokens of the following types:
 * STREAM-START
 * STREAM-END
 * COMMENT
 * DIRECTIVE(name, value)
 * DOCUMENT-START
 * DOCUMENT-END
 * BLOCK-SEQUENCE-START
 * BLOCK-MAPPING-START
 * BLOCK-END
 * FLOW-SEQUENCE-START
 * FLOW-MAPPING-START
 * FLOW-SEQUENCE-END
 * FLOW-MAPPING-END
 * BLOCK-ENTRY
 * FLOW-ENTRY
 * KEY
 * VALUE
 * ALIAS(value)
 * ANCHOR(value)
 * TAG(value)
 * SCALAR(value, plain, style)
 * Read comments in the Scanner code for more details.
</pre> *
 */
class ScannerImpl(private val reader: StreamReader) : Scanner {
    // Had we reached the end of the stream?
    private var done = false

    // The number of unclosed '{' and '['. `flow_level == 0` means block context.
    private var flowLevel = 0

    // List of processed tokens that are not yet emitted.
    private val tokens: MutableList<Token>

    // The last added token
    private var lastToken: Token? = null

    // Number of tokens that were emitted through the `getToken()` method.
    private var tokensTaken = 0

    // The current indentation level.
    private var indent = -1

    // Past indentation levels.
    private val indents: ArrayStack<Int>

    // A flag that indicates if comments should be parsed
    var isParseComments = false
        private set
    // Variables related to simple keys treatment. See PyYAML.
    /**
     * <pre>
     * A simple key is a key that is not denoted by the '?' indicator.
     * Example of simple keys:
     * ---
     * block simple key: value
     * ? not a simple key:
     * : { flow simple key: value }
     * We emit the KEY token before all keys, so when we find a potential
     * simple key, we try to locate the corresponding ':' indicator.
     * Simple keys should be limited to a single line and 1024 characters.
     *
     * Can a simple key start at the current position? A simple key may
     * start:
     * - at the beginning of the line, not counting indentation spaces
     * (in block context),
     * - after '{', '[', ',' (in the flow context),
     * - after '?', ':', '-' (in the block context).
     * In the block context, this flag also signifies if a block collection
     * may start at the current position.
    </pre> *
     */
    private var allowSimpleKey = true

    /*
     * Keep track of possible simple keys. This is a dictionary. The key is
     * `flow_level`; there can be no more than one possible simple key for each
     * level. The value is a SimpleKey record: (token_number, required, index,
     * line, column, mark) A simple key may start with ALIAS, ANCHOR, TAG,
     * SCALAR(flow), '[', or '{' tokens.
     */
    private val possibleSimpleKeys: MutableMap<Int, SimpleKey>

    init {
        tokens = ArrayList(100)
        indents = ArrayStack(10)
        // The order in possibleSimpleKeys is kept for nextPossibleSimpleKey()
        possibleSimpleKeys = LinkedHashMap()
        fetchStreamStart() // Add the STREAM-START token.
    }

    /**
     * Set the scanner to ignore comments or parse them as a `CommentToken`.
     *
     * @param parseComments `true` to parse; `false` to ignore
     */
    fun setParseComments(parseComments: Boolean): ScannerImpl {
        isParseComments = parseComments
        return this
    }

    /**
     * Check whether the next token is one of the given types.
     */
    override fun checkToken(vararg choices: Token.ID): Boolean {
        while (needMoreTokens()) {
            fetchMoreTokens()
        }
        if (!tokens.isEmpty()) {
            if (choices.size == 0) {
                return true
            }
            // since profiler puts this method on top (it is used a lot), we
            // should not use 'foreach' here because of the performance reasons
            val first = tokens[0].tokenId
            for (i in choices.indices) {
                if (first == choices[i]) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Return the next token, but do not delete it from the queue.
     */
    override fun peekToken(): Token {
        while (needMoreTokens()) {
            fetchMoreTokens()
        }
        return tokens[0]
    }

    /**
     * Return the next token, removing it from the queue.
     */
    override val token: Token
        get() {
            tokensTaken++
            return tokens.removeAt(0)
        }

    // Private methods.
    private fun addToken(token: Token) {
        lastToken = token
        tokens.add(token)
    }

    private fun addToken(index: Int, token: Token) {
        if (index == tokens.size) {
            lastToken = token
        }
        tokens.add(index, token)
    }

    private fun addAllTokens(tokens: List<Token>) {
        lastToken = tokens[tokens.size - 1]
        this.tokens.addAll(tokens)
    }

    /**
     * Returns true if more tokens should be scanned.
     */
    private fun needMoreTokens(): Boolean {
        // If we are done, we do not require more tokens.
        if (done) {
            return false
        }
        // If we aren't done, but we have no tokens, we need to scan more.
        if (tokens.isEmpty()) {
            return true
        }
        // The current token may be a potential simple key, so we
        // need to look further.
        stalePossibleSimpleKeys()
        return nextPossibleSimpleKey() == tokensTaken
    }

    /**
     * Fetch one or more tokens from the StreamReader.
     */
    private fun fetchMoreTokens() {
        // Eat whitespaces and process comments until we reach the next token.
        scanToNextToken()
        // Remove obsolete possible simple keys.
        stalePossibleSimpleKeys()
        // Compare the current indentation and column. It may add some tokens
        // and decrease the current indentation level.
        unwindIndent(reader.column)
        // Peek the next code point, to decide what the next group of tokens
        // will look like.
        val c = reader.peek()
        when (c) {
            '\u0000'.code -> {
                // Is it the end of stream?
                fetchStreamEnd()
                return
            }
            '%'.code ->             // Is it a directive?
                if (checkDirective()) {
                    fetchDirective()
                    return
                }
            '-'.code ->             // Is it the document start?
                if (checkDocumentStart()) {
                    fetchDocumentStart()
                    return
                    // Is it the block entry indicator?
                } else if (checkBlockEntry()) {
                    fetchBlockEntry()
                    return
                }
            '.'.code ->             // Is it the document end?
                if (checkDocumentEnd()) {
                    fetchDocumentEnd()
                    return
                }
            '['.code -> {
                // Is it the flow sequence start indicator?
                fetchFlowSequenceStart()
                return
            }
            '{'.code -> {
                // Is it the flow mapping start indicator?
                fetchFlowMappingStart()
                return
            }
            ']'.code -> {
                // Is it the flow sequence end indicator?
                fetchFlowSequenceEnd()
                return
            }
            '}'.code -> {
                // Is it the flow mapping end indicator?
                fetchFlowMappingEnd()
                return
            }
            ','.code -> {
                // Is it the flow entry indicator?
                fetchFlowEntry()
                return
            }
            '?'.code ->             // Is it the key indicator?
                if (checkKey()) {
                    fetchKey()
                    return
                }
            ':'.code ->             // Is it the value indicator?
                if (checkValue()) {
                    fetchValue()
                    return
                }
            '*'.code -> {
                // Is it an alias?
                fetchAlias()
                return
            }
            '&'.code -> {
                // Is it an anchor?
                fetchAnchor()
                return
            }
            '!'.code -> {
                // Is it a tag?
                fetchTag()
                return
            }
            '|'.code ->             // Is it a literal scalar?
                if (flowLevel == 0) {
                    fetchLiteral()
                    return
                }
            '>'.code ->             // Is it a folded scalar?
                if (flowLevel == 0) {
                    fetchFolded()
                    return
                }
            '\''.code -> {
                // Is it a single quoted scalar?
                fetchSingle()
                return
            }
            '"'.code -> {
                // Is it a double quoted scalar?
                fetchDouble()
                return
            }
        }
        // It must be a plain scalar then.
        if (checkPlain()) {
            fetchPlain()
            return
        }
        // No? It's an error. Let's produce a nice error message.We do this by
        // converting escaped characters into their escape sequences. This is a
        // backwards use of the ESCAPE_REPLACEMENTS map.
        var chRepresentation = escapeChar(Char.toString(c))
        if (c == '\t'.code) chRepresentation += "(TAB)"
        val text = "found character '$chRepresentation' that cannot start any token. (Do not use $chRepresentation for indentation)"
        throw ScannerException(
            "while scanning for the next token", null, text,
            reader.mark
        )
    }

    /**
     * This is implemented in CharConstants in SnakeYAML Engine
     */
    private fun escapeChar(chRepresentation: String): String {
        for (s in ESCAPE_REPLACEMENTS.keys) {
            val v = ESCAPE_REPLACEMENTS[s]
            if (v == chRepresentation) {
                return "\\" + s // ' ' -> '\t'
            }
        }
        return chRepresentation
    }
    // Simple keys treatment.
    /**
     * Return the number of the nearest possible simple key. Actually we don't
     * need to loop through the whole dictionary.
     */
    private fun nextPossibleSimpleKey(): Int {
        /*
         * the implementation is not as in PyYAML. Because
         * this.possibleSimpleKeys is ordered we can simply take the first key
         */
        return if (!possibleSimpleKeys.isEmpty()) {
            possibleSimpleKeys.values.iterator().next().tokenNumber
        } else -1
    }

    /**
     * <pre>
     * Remove entries that are no longer possible simple keys. According to
     * the YAML specification, simple keys
     * - should be limited to a single line,
     * - should be no longer than 1024 characters.
     * Disabling this procedure will allow simple keys of any length and
     * height (may cause problems if indentation is broken though).
    </pre> *
     */
    private fun stalePossibleSimpleKeys() {
        if (!possibleSimpleKeys.isEmpty()) {
            val iterator = possibleSimpleKeys.values.iterator()
            while (iterator
                    .hasNext()
            ) {
                val key = iterator.next()
                if (key.line != reader.line || reader.index - key.index > 1024) {
                    // If the key is not on the same line as the current
                    // position OR the difference in column between the token
                    // start and the current position is more than the maximum
                    // simple key length, then this cannot be a simple key.
                    if (key.isRequired) {
                        // If the key was required, this implies an error
                        // condition.
                        throw ScannerException(
                            "while scanning a simple key", key.mark,
                            "could not find expected ':'", reader.mark
                        )
                    }
                    iterator.remove()
                }
            }
        }
    }

    /**
     * The next token may start a simple key. We check if it's possible and save
     * its position. This function is called for ALIAS, ANCHOR, TAG,
     * SCALAR(flow), '[', and '{'.
     */
    private fun savePossibleSimpleKey() {
        // The next token may start a simple key. We check if it's possible
        // and save its position. This function is called for
        // ALIAS, ANCHOR, TAG, SCALAR(flow), '[', and '{'.

        // Check if a simple key is required at the current position.
        // A simple key is required if this position is the root flowLevel, AND
        // the current indentation level is the same as the last indent-level.
        val required = flowLevel == 0 && indent == reader.column
        if (allowSimpleKey || !required) {
            // A simple key is required only if it is the first token in the
            // current line. Therefore it is always allowed.
        } else {
            throw YAMLException(
                "A simple key is required only if it is the first token in the current line"
            )
        }

        // The next token might be a simple key. Let's save it's number and
        // position.
        if (allowSimpleKey) {
            removePossibleSimpleKey()
            val tokenNumber = tokensTaken + tokens.size
            val key = SimpleKey(
                tokenNumber, required, reader.index,
                reader.line, reader.column, reader.mark
            )
            possibleSimpleKeys[flowLevel] = key
        }
    }

    /**
     * Remove the saved possible key position at the current flow level.
     */
    private fun removePossibleSimpleKey() {
        val key = possibleSimpleKeys.remove(flowLevel)
        if (key != null && key.isRequired) {
            throw ScannerException(
                "while scanning a simple key", key.mark,
                "could not find expected ':'", reader.mark
            )
        }
    }
    // Indentation functions.
    /**
     * * Handle implicitly ending multiple levels of block nodes by decreased
     * indentation. This function becomes important on lines 4 and 7 of this
     * example:
     *
     * <pre>
     * 1) book one:
     * 2)   part one:
     * 3)     chapter one
     * 4)   part two:
     * 5)     chapter one
     * 6)     chapter two
     * 7) book two:
    </pre> *
     *
     * In flow context, tokens should respect indentation. Actually the
     * condition should be `self.indent &gt;= column` according to the spec. But
     * this condition will prohibit intuitively correct constructions such as
     * key : { }
     */
    private fun unwindIndent(col: Int) {
        // In the flow context, indentation is ignored. We make the scanner less
        // restrictive than specification requires.
        if (flowLevel != 0) {
            return
        }

        // In block context, we may need to issue the BLOCK-END tokens.
        while (indent > col) {
            val mark = reader.mark
            indent = indents.pop()
            addToken(BlockEndToken(mark, mark))
        }
    }

    /**
     * Check if we need to increase indentation.
     */
    private fun addIndent(column: Int): Boolean {
        if (indent < column) {
            indents.push(indent)
            indent = column
            return true
        }
        return false
    }
    // Fetchers.
    /**
     * We always add STREAM-START as the first token and STREAM-END as the last
     * token.
     */
    private fun fetchStreamStart() {
        // Read the token.
        val mark = reader.mark

        // Add STREAM-START.
        val token: Token = StreamStartToken(mark, mark)
        addToken(token)
    }

    private fun fetchStreamEnd() {
        // Set the current indentation to -1.
        unwindIndent(-1)

        // Reset simple keys.
        removePossibleSimpleKey()
        allowSimpleKey = false
        possibleSimpleKeys.clear()

        // Read the token.
        val mark = reader.mark

        // Add STREAM-END.
        val token: Token = StreamEndToken(mark, mark)
        addToken(token)

        // The stream is finished.
        done = true
    }

    /**
     * Fetch a YAML directive. Directives are presentation details that are
     * interpreted as instructions to the processor. YAML defines two kinds of
     * directives, YAML and TAG; all other types are reserved for future use.
     *
     * @see [3.2.3.4. Directives](http://www.yaml.org/spec/1.1/.id864824)
     */
    private fun fetchDirective() {
        // Set the current indentation to -1.
        unwindIndent(-1)

        // Reset simple keys.
        removePossibleSimpleKey()
        allowSimpleKey = false

        // Scan and add DIRECTIVE.
        val tok = scanDirective()
        addAllTokens(tok)
    }

    /**
     * Fetch a document-start token ("---").
     */
    private fun fetchDocumentStart() {
        fetchDocumentIndicator(true)
    }

    /**
     * Fetch a document-end token ("...").
     */
    private fun fetchDocumentEnd() {
        fetchDocumentIndicator(false)
    }

    /**
     * Fetch a document indicator, either "---" for "document-start", or else
     * "..." for "document-end. The type is chosen by the given boolean.
     */
    private fun fetchDocumentIndicator(isDocumentStart: Boolean) {
        // Set the current indentation to -1.
        unwindIndent(-1)

        // Reset simple keys. Note that there could not be a block collection
        // after '---'.
        removePossibleSimpleKey()
        allowSimpleKey = false

        // Add DOCUMENT-START or DOCUMENT-END.
        val startMark = reader.mark
        reader.forward(3)
        val endMark = reader.mark
        val token: Token
        token = if (isDocumentStart) {
            DocumentStartToken(startMark, endMark)
        } else {
            DocumentEndToken(startMark, endMark)
        }
        addToken(token)
    }

    private fun fetchFlowSequenceStart() {
        fetchFlowCollectionStart(false)
    }

    private fun fetchFlowMappingStart() {
        fetchFlowCollectionStart(true)
    }

    /**
     * Fetch a flow-style collection start, which is either a sequence or a
     * mapping. The type is determined by the given boolean.
     *
     * A flow-style collection is in a format similar to JSON. Sequences are
     * started by '[' and ended by ']'; mappings are started by '{' and ended by
     * '}'.
     *
     * @see [3.2.3.1. Node Styles](http://www.yaml.org/spec/1.1/.id863975)
     *
     *
     * @param isMappingStart
     */
    private fun fetchFlowCollectionStart(isMappingStart: Boolean) {
        // '[' and '{' may start a simple key.
        savePossibleSimpleKey()

        // Increase the flow level.
        flowLevel++

        // Simple keys are allowed after '[' and '{'.
        allowSimpleKey = true

        // Add FLOW-SEQUENCE-START or FLOW-MAPPING-START.
        val startMark = reader.mark
        reader.forward(1)
        val endMark = reader.mark
        val token: Token
        token = if (isMappingStart) {
            FlowMappingStartToken(startMark, endMark)
        } else {
            FlowSequenceStartToken(startMark, endMark)
        }
        addToken(token)
    }

    private fun fetchFlowSequenceEnd() {
        fetchFlowCollectionEnd(false)
    }

    private fun fetchFlowMappingEnd() {
        fetchFlowCollectionEnd(true)
    }

    /**
     * Fetch a flow-style collection end, which is either a sequence or a
     * mapping. The type is determined by the given boolean.
     *
     * A flow-style collection is in a format similar to JSON. Sequences are
     * started by '[' and ended by ']'; mappings are started by '{' and ended by
     * '}'.
     *
     * @see [3.2.3.1. Node Styles](http://www.yaml.org/spec/1.1/.id863975)
     */
    private fun fetchFlowCollectionEnd(isMappingEnd: Boolean) {
        // Reset possible simple key on the current level.
        removePossibleSimpleKey()

        // Decrease the flow level.
        flowLevel--

        // No simple keys after ']' or '}'.
        allowSimpleKey = false

        // Add FLOW-SEQUENCE-END or FLOW-MAPPING-END.
        val startMark = reader.mark
        reader.forward()
        val endMark = reader.mark
        val token: Token
        token = if (isMappingEnd) {
            FlowMappingEndToken(startMark, endMark)
        } else {
            FlowSequenceEndToken(startMark, endMark)
        }
        addToken(token)
    }

    /**
     * Fetch an entry in the flow style. Flow-style entries occur either
     * immediately after the start of a collection, or else after a comma.
     *
     * @see [3.2.3.1. Node Styles](http://www.yaml.org/spec/1.1/.id863975)
     */
    private fun fetchFlowEntry() {
        // Simple keys are allowed after ','.
        allowSimpleKey = true

        // Reset possible simple key on the current level.
        removePossibleSimpleKey()

        // Add FLOW-ENTRY.
        val startMark = reader.mark
        reader.forward()
        val endMark = reader.mark
        val token: Token = FlowEntryToken(startMark, endMark)
        addToken(token)
    }

    /**
     * Fetch an entry in the block style.
     *
     * @see [3.2.3.1. Node Styles](http://www.yaml.org/spec/1.1/.id863975)
     */
    private fun fetchBlockEntry() {
        // Block context needs additional checks.
        if (flowLevel == 0) {
            // Are we allowed to start a new entry?
            if (!allowSimpleKey) {
                throw ScannerException(
                    null, null, "sequence entries are not allowed here",
                    reader.mark
                )
            }

            // We may need to add BLOCK-SEQUENCE-START.
            if (addIndent(reader.column)) {
                val mark = reader.mark
                addToken(BlockSequenceStartToken(mark, mark))
            }
        } else {
            // It's an error for the block entry to occur in the flow
            // context,but we let the parser detect this.
        }
        // Simple keys are allowed after '-'.
        allowSimpleKey = true

        // Reset possible simple key on the current level.
        removePossibleSimpleKey()

        // Add BLOCK-ENTRY.
        val startMark = reader.mark
        reader.forward()
        val endMark = reader.mark
        val token: Token = BlockEntryToken(startMark, endMark)
        addToken(token)
    }

    /**
     * Fetch a key in a block-style mapping.
     *
     * @see [3.2.3.1. Node Styles](http://www.yaml.org/spec/1.1/.id863975)
     */
    private fun fetchKey() {
        // Block context needs additional checks.
        if (flowLevel == 0) {
            // Are we allowed to start a key (not necessary a simple)?
            if (!allowSimpleKey) {
                throw ScannerException(
                    null, null, "mapping keys are not allowed here",
                    reader.mark
                )
            }
            // We may need to add BLOCK-MAPPING-START.
            if (addIndent(reader.column)) {
                val mark = reader.mark
                addToken(BlockMappingStartToken(mark, mark))
            }
        }
        // Simple keys are allowed after '?' in the block context.
        allowSimpleKey = flowLevel == 0

        // Reset possible simple key on the current level.
        removePossibleSimpleKey()

        // Add KEY.
        val startMark = reader.mark
        reader.forward()
        val endMark = reader.mark
        val token: Token = KeyToken(startMark, endMark)
        addToken(token)
    }

    /**
     * Fetch a value in a block-style mapping.
     *
     * @see [3.2.3.1. Node Styles](http://www.yaml.org/spec/1.1/.id863975)
     */
    private fun fetchValue() {
        // Do we determine a simple key?
        val key = possibleSimpleKeys.remove(flowLevel)
        if (key != null) {
            // Add KEY.
            addToken(key.tokenNumber - tokensTaken, KeyToken(key.mark, key.mark))

            // If this key starts a new block mapping, we need to add
            // BLOCK-MAPPING-START.
            if (flowLevel == 0) {
                if (addIndent(key.column)) {
                    addToken(
                        key.tokenNumber - tokensTaken,
                        BlockMappingStartToken(key.mark, key.mark)
                    )
                }
            }
            // There cannot be two simple keys one after another.
            allowSimpleKey = false
        } else {
            // It must be a part of a complex key.
            // Block context needs additional checks. Do we really need them?
            // They will be caught by the parser anyway.
            if (flowLevel == 0) {

                // We are allowed to start a complex value if and only if we can
                // start a simple key.
                if (!allowSimpleKey) {
                    throw ScannerException(
                        null, null, "mapping values are not allowed here",
                        reader.mark
                    )
                }
            }

            // If this value starts a new block mapping, we need to add
            // BLOCK-MAPPING-START. It will be detected as an error later by
            // the parser.
            if (flowLevel == 0) {
                if (addIndent(reader.column)) {
                    val mark = reader.mark
                    addToken(BlockMappingStartToken(mark, mark))
                }
            }

            // Simple keys are allowed after ':' in the block context.
            allowSimpleKey = flowLevel == 0

            // Reset possible simple key on the current level.
            removePossibleSimpleKey()
        }
        // Add VALUE.
        val startMark = reader.mark
        reader.forward()
        val endMark = reader.mark
        val token: Token = ValueToken(startMark, endMark)
        addToken(token)
    }

    /**
     * Fetch an alias, which is a reference to an anchor. Aliases take the
     * format:
     *
     * <pre>
     * *(anchor name)
    </pre> *
     *
     * @see [3.2.2.2. Anchors and Aliases](http://www.yaml.org/spec/1.1/.id863390)
     */
    private fun fetchAlias() {
        // ALIAS could be a simple key.
        savePossibleSimpleKey()

        // No simple keys after ALIAS.
        allowSimpleKey = false

        // Scan and add ALIAS.
        val tok = scanAnchor(false)
        addToken(tok)
    }

    /**
     * Fetch an anchor. Anchors take the form:
     *
     * <pre>
     * &(anchor name)
    </pre> *
     *
     * @see [3.2.2.2. Anchors and Aliases](http://www.yaml.org/spec/1.1/.id863390)
     */
    private fun fetchAnchor() {
        // ANCHOR could start a simple key.
        savePossibleSimpleKey()

        // No simple keys after ANCHOR.
        allowSimpleKey = false

        // Scan and add ANCHOR.
        val tok = scanAnchor(true)
        addToken(tok)
    }

    /**
     * Fetch a tag. Tags take a complex form.
     *
     * @see [3.2.1.2. Tags](http://www.yaml.org/spec/1.1/.id861700)
     */
    private fun fetchTag() {
        // TAG could start a simple key.
        savePossibleSimpleKey()

        // No simple keys after TAG.
        allowSimpleKey = false

        // Scan and add TAG.
        val tok = scanTag()
        addToken(tok)
    }

    /**
     * Fetch a literal scalar, denoted with a vertical-bar. This is the type
     * best used for source code and other content, such as binary data, which
     * must be included verbatim.
     *
     * @see [3.2.3.1. Node Styles](http://www.yaml.org/spec/1.1/.id863975)
     */
    private fun fetchLiteral() {
        fetchBlockScalar('|')
    }

    /**
     * Fetch a folded scalar, denoted with a greater-than sign. This is the type
     * best used for long content, such as the text of a chapter or description.
     *
     * @see [3.2.3.1. Node Styles](http://www.yaml.org/spec/1.1/.id863975)
     */
    private fun fetchFolded() {
        fetchBlockScalar('>')
    }

    /**
     * Fetch a block scalar (literal or folded).
     *
     * @see [3.2.3.1. Node Styles](http://www.yaml.org/spec/1.1/.id863975)
     *
     *
     * @param style
     */
    private fun fetchBlockScalar(style: Char) {
        // A simple key may follow a block scalar.
        allowSimpleKey = true

        // Reset possible simple key on the current level.
        removePossibleSimpleKey()

        // Scan and add SCALAR.
        val tok = scanBlockScalar(style)
        addAllTokens(tok)
    }

    /**
     * Fetch a single-quoted (') scalar.
     */
    private fun fetchSingle() {
        fetchFlowScalar('\'')
    }

    /**
     * Fetch a double-quoted (") scalar.
     */
    private fun fetchDouble() {
        fetchFlowScalar('"')
    }

    /**
     * Fetch a flow scalar (single- or double-quoted).
     *
     * @see [3.2.3.1. Node Styles](http://www.yaml.org/spec/1.1/.id863975)
     *
     *
     * @param style
     */
    private fun fetchFlowScalar(style: Char) {
        // A flow scalar could be a simple key.
        savePossibleSimpleKey()

        // No simple keys after flow scalars.
        allowSimpleKey = false

        // Scan and add SCALAR.
        val tok = scanFlowScalar(style)
        addToken(tok)
    }

    /**
     * Fetch a plain scalar.
     */
    private fun fetchPlain() {
        // A plain scalar could be a simple key.
        savePossibleSimpleKey()

        // No simple keys after plain scalars. But note that `scan_plain` will
        // change this flag if the scan is finished at the beginning of the
        // line.
        allowSimpleKey = false

        // Scan and add SCALAR. May change `allow_simple_key`.
        val tok = scanPlain()
        addToken(tok)
    }
    // Checkers.
    /**
     * Returns true if the next thing on the reader is a directive, given that
     * the leading '%' has already been checked.
     *
     * @see [3.2.3.4. Directives](http://www.yaml.org/spec/1.1/.id864824)
     */
    private fun checkDirective(): Boolean {
        // DIRECTIVE: ^ '%' ...
        // The '%' indicator is already checked.
        return reader.column == 0
    }

    /**
     * Returns true if the next thing on the reader is a document-start ("---").
     * A document-start is always followed immediately by a new line.
     */
    private fun checkDocumentStart(): Boolean {
        // DOCUMENT-START: ^ '---' (' '|'\n')
        if (reader.column == 0) {
            if ("---" == reader.prefix(3) && NULL_BL_T_LINEBR.has(reader.peek(3))) {
                return true
            }
        }
        return false
    }

    /**
     * Returns true if the next thing on the reader is a document-end ("..."). A
     * document-end is always followed immediately by a new line.
     */
    private fun checkDocumentEnd(): Boolean {
        // DOCUMENT-END: ^ '...' (' '|'\n')
        if (reader.column == 0) {
            if ("..." == reader.prefix(3) && NULL_BL_T_LINEBR.has(reader.peek(3))) {
                return true
            }
        }
        return false
    }

    /**
     * Returns true if the next thing on the reader is a block token.
     */
    private fun checkBlockEntry(): Boolean {
        // BLOCK-ENTRY: '-' (' '|'\n')
        return NULL_BL_T_LINEBR.has(reader.peek(1))
    }

    /**
     * Returns true if the next thing on the reader is a key token.
     */
    private fun checkKey(): Boolean {
        // KEY(flow context): '?'
        return if (flowLevel != 0) {
            true
        } else {
            // KEY(block context): '?' (' '|'\n')
            NULL_BL_T_LINEBR.has(reader.peek(1))
        }
    }

    /**
     * Returns true if the next thing on the reader is a value token.
     */
    private fun checkValue(): Boolean {
        // VALUE(flow context): ':'
        return if (flowLevel != 0) {
            true
        } else {
            // VALUE(block context): ':' (' '|'\n')
            NULL_BL_T_LINEBR.has(reader.peek(1))
        }
    }

    /**
     * Returns true if the next thing on the reader is a plain token.
     */
    private fun checkPlain(): Boolean {
        /**
         * <pre>
         * A plain scalar may start with any non-space character except:
         * '-', '?', ':', ',', '[', ']', '{', '}',
         * '#', '&amp;', '*', '!', '|', '&gt;', '\'', '\&quot;',
         * '%', '@', '`'.
         *
         * It may also start with
         * '-', '?', ':'
         * if it is followed by a non-space character.
         *
         * Note that we limit the last rule to the block context (except the
         * '-' character) because we want the flow context to be space
         * independent.
        </pre> *
         */
        val c = reader.peek()
        // If the next char is NOT one of the forbidden chars above or
        // whitespace, then this is the start of a plain scalar.
        // If the next char is NOT one of the forbidden chars above or
        // whitespace, then this is the start of a plain scalar.
        return NULL_BL_T_LINEBR.hasNo(
            c,
            "-?:,[]{}#&*!|>\'\"%@`"
        ) || NULL_BL_T_LINEBR.hasNo(reader.peek(1)) && (c == '-'.code || flowLevel == 0 && "?:"
            .indexOf(c.toChar()) != -1)
    }
    // Scanners.
    /**
     * <pre>
     * We ignore spaces, line breaks and comments.
     * If we find a line break in the block context, we set the flag
     * `allow_simple_key` on.
     * The byte order mark is stripped if it's the first character in the
     * stream. We do not yet support BOM inside the stream as the
     * specification requires. Any such mark will be considered as a part
     * of the document.
     * TODO: We need to make tab handling rules more sane. A good rule is
     * Tabs cannot precede tokens
     * BLOCK-SEQUENCE-START, BLOCK-MAPPING-START, BLOCK-END,
     * KEY(block), VALUE(block), BLOCK-ENTRY
     * So the checking code is
     * if &lt;TAB&gt;:
     * self.allow_simple_keys = False
     * We also need to add the check for `allow_simple_keys == True` to
     * `unwind_indent` before issuing BLOCK-END.
     * Scanners for block, flow, and plain scalars need to be modified.
    </pre> *
     */
    private fun scanToNextToken() {
        // If there is a byte order mark (BOM) at the beginning of the stream,
        // forward past it.
        if (reader.index == 0 && reader.peek() == 0xFEFF) {
            reader.forward()
        }
        var found = false
        var inlineStartColumn = -1
        while (!found) {
            val startMark = reader.mark
            val columnBeforeComment = reader.column
            var commentSeen = false
            var ff = 0
            // Peek ahead until we find the first non-space character, then
            // move forward directly to that character.
            while (reader.peek(ff) == ' '.code) {
                ff++
            }
            if (ff > 0) {
                reader.forward(ff)
            }
            // If the character we have skipped forward to is a comment (#),
            // then peek ahead until we find the next end of line. YAML
            // comments are from a # to the next new-line. We then forward
            // past the comment.
            if (reader.peek() == '#'.code) {
                commentSeen = true
                var type: CommentType
                if (columnBeforeComment != 0 && !(lastToken != null && lastToken!!.tokenId == Token.ID.BlockEntry)) {
                    type = CommentType.IN_LINE
                    inlineStartColumn = reader.column
                } else if (inlineStartColumn == reader.column) {
                    type = CommentType.IN_LINE
                } else {
                    inlineStartColumn = -1
                    type = CommentType.BLOCK
                }
                val token = scanComment(type)
                if (isParseComments) {
                    addToken(token)
                }
            }
            // If we scanned a line break, then (depending on flow level),
            // simple keys may be allowed.
            val breaks = scanLineBreak()
            if (breaks.length != 0) { // found a line-break
                if (isParseComments && !commentSeen) {
                    if (columnBeforeComment == 0) {
                        val endMark = reader.mark
                        addToken(CommentToken(CommentType.BLANK_LINE, breaks, startMark, endMark))
                    }
                }
                if (flowLevel == 0) {
                    // Simple keys are allowed at flow-level 0 after a line
                    // break
                    allowSimpleKey = true
                }
            } else {
                found = true
            }
        }
    }

    private fun scanComment(type: CommentType): CommentToken {
        // See the specification for details.
        val startMark = reader.mark
        reader.forward()
        var length = 0
        while (Constant.NULL_OR_LINEBR.hasNo(reader.peek(length))) {
            length++
        }
        val value = reader.prefixForward(length)
        val endMark = reader.mark
        return CommentToken(type, value!!, startMark, endMark)
    }

    private fun scanDirective(): List<Token> {
        // See the specification for details.
        val startMark = reader.mark
        val endMark: Mark
        reader.forward()
        val name = scanDirectiveName(startMark)
        var value: List<*>? = null
        if ("YAML" == name) {
            value = scanYamlDirectiveValue(startMark)
            endMark = reader.mark
        } else if ("TAG" == name) {
            value = scanTagDirectiveValue(startMark)
            endMark = reader.mark
        } else {
            endMark = reader.mark
            var ff = 0
            while (Constant.NULL_OR_LINEBR.hasNo(reader.peek(ff))) {
                ff++
            }
            if (ff > 0) {
                reader.forward(ff)
            }
        }
        val commentToken = scanDirectiveIgnoredLine(startMark)
        val token: DirectiveToken<*> = DirectiveToken(name, value, startMark, endMark)
        return makeTokenList(token, commentToken)
    }

    /**
     * Scan a directive name. Directive names are a series of non-space
     * characters.
     *
     * @see [7.1. Directives](http://www.yaml.org/spec/1.1/.id895217)
     */
    private fun scanDirectiveName(startMark: Mark): String? {
        // See the specification for details.
        var length = 0
        // A Directive-name is a sequence of alphanumeric characters
        // (a-z,A-Z,0-9). We scan until we find something that isn't.
        // FIXME this disagrees with the specification.
        var c = reader.peek(length)
        while (Constant.ALPHA.has(c)) {
            length++
            c = reader.peek(length)
        }
        // If the name would be empty, an error occurs.
        if (length == 0) {
            val s = Char.toString(c)
            throw ScannerException(
                "while scanning a directive", startMark,
                "expected alphabetic or numeric character, but found " + s + "(" + c
                        + ")", reader.mark
            )
        }
        val value = reader.prefixForward(length)
        c = reader.peek()
        if (Constant.NULL_BL_LINEBR.hasNo(c)) {
            val s = Char.toString(c)
            throw ScannerException(
                "while scanning a directive", startMark,
                "expected alphabetic or numeric character, but found " + s + "(" + c
                        + ")", reader.mark
            )
        }
        return value
    }

    private fun scanYamlDirectiveValue(startMark: Mark): List<Int> {
        // See the specification for details.
        while (reader.peek() == ' '.code) {
            reader.forward()
        }
        val major = scanYamlDirectiveNumber(startMark)
        var c = reader.peek()
        if (c != '.'.code) {
            val s = Char.toString(c)
            throw ScannerException(
                "while scanning a directive", startMark,
                "expected a digit or '.', but found " + s + "("
                        + c + ")", reader.mark
            )
        }
        reader.forward()
        val minor = scanYamlDirectiveNumber(startMark)
        c = reader.peek()
        if (Constant.NULL_BL_LINEBR.hasNo(c)) {
            val s = Char.toString(c)
            throw ScannerException(
                "while scanning a directive", startMark,
                "expected a digit or ' ', but found " + s + "("
                        + c + ")", reader.mark
            )
        }
        val result: MutableList<Int> = ArrayList(2)
        result.add(major)
        result.add(minor)
        return result
    }

    /**
     * Read a %YAML directive number: this is either the major or the minor
     * part. Stop reading at a non-digit character (usually either '.' or '\n').
     *
     * @see [7.1.1. “YAML” Directive](http://www.yaml.org/spec/1.1/.id895631)
     *
     * @see [](http://www.yaml.org/spec/1.1/.ns-dec-digit)
     */
    private fun scanYamlDirectiveNumber(startMark: Mark): Int {
        // See the specification for details.
        val c = reader.peek()
        if (!Char.isDecimalDigit(c)) {
            val s = Char.toString(c)
            throw ScannerException(
                "while scanning a directive", startMark,
                "expected a digit, but found $s($c)", reader.mark
            )
        }
        var length = 0
        while (Char.isDecimalDigit(reader.peek(length))) {
            length++
        }
        return reader.prefixForward(length).toInt()
    }

    /**
     *
     *
     * Read a %TAG directive value:
     *
     * <pre>
     * s-ignored-space+ c-tag-handle s-ignored-space+ ns-tag-prefix s-l-comments
    </pre> *
     *
     *
     *
     * @see [7.1.2. “TAG” Directive](http://www.yaml.org/spec/1.1/.id896044)
     */
    private fun scanTagDirectiveValue(startMark: Mark): List<String?> {
        // See the specification for details.
        while (reader.peek() == ' '.code) {
            reader.forward()
        }
        val handle = scanTagDirectiveHandle(startMark)
        while (reader.peek() == ' '.code) {
            reader.forward()
        }
        val prefix = scanTagDirectivePrefix(startMark)
        val result: MutableList<String?> = ArrayList(2)
        result.add(handle)
        result.add(prefix)
        return result
    }

    /**
     * Scan a %TAG directive's handle. This is YAML's c-tag-handle.
     *
     * @see [7.1.2.2. Tag Handles](http://www.yaml.org/spec/1.1/.id896876)
     *
     * @param startMark - beginning of the handle
     * @return scanned handle
     */
    private fun scanTagDirectiveHandle(startMark: Mark): String? {
        // See the specification for details.
        val value = scanTagHandle("directive", startMark)
        val c = reader.peek()
        if (c != ' '.code) {
            val s = Char.toString(c)
            throw ScannerException(
                "while scanning a directive", startMark,
                "expected ' ', but found $s($c)", reader.mark
            )
        }
        return value
    }

    /**
     * Scan a %TAG directive's prefix. This is YAML's ns-tag-prefix.
     *
     * @see [](http://www.yaml.org/spec/1.1/.ns-tag-prefix)
     */
    private fun scanTagDirectivePrefix(startMark: Mark): String {
        // See the specification for details.
        val value = scanTagUri("directive", startMark)
        val c = reader.peek()
        if (Constant.NULL_BL_LINEBR.hasNo(c)) {
            val s = Char.toString(c)
            throw ScannerException(
                "while scanning a directive", startMark,
                "expected ' ', but found $s($c)",
                reader.mark
            )
        }
        return value
    }

    private fun scanDirectiveIgnoredLine(startMark: Mark): CommentToken? {
        // See the specification for details.
        while (reader.peek() == ' '.code) {
            reader.forward()
        }
        var commentToken: CommentToken? = null
        if (reader.peek() == '#'.code) {
            val comment = scanComment(CommentType.IN_LINE)
            if (isParseComments) {
                commentToken = comment
            }
        }
        val c = reader.peek()
        val lineBreak = scanLineBreak()
        if (lineBreak.length == 0 && c != '\u0000'.code) {
            val s = Char.toString(c)
            throw ScannerException(
                "while scanning a directive", startMark,
                "expected a comment or a line break, but found $s($c)",
                reader.mark
            )
        }
        return commentToken
    }

    /**
     * <pre>
     * The YAML 1.1 specification does not restrict characters for anchors and
     * aliases. This may lead to problems.
     * see https://bitbucket.org/snakeyaml/snakeyaml/issues/485/alias-names-are-too-permissive-compared-to
     * This implementation tries to follow https://github.com/yaml/yaml-spec/blob/master/rfc/RFC-0003.md
    </pre> *
     */
    private fun scanAnchor(isAnchor: Boolean): Token {
        val startMark = reader.mark
        val indicator = reader.peek()
        val name = if (indicator == '*'.code) "alias" else "anchor"
        reader.forward()
        var length = 0
        var c = reader.peek(length)
        while (NULL_BL_T_LINEBR.hasNo(c, ":,[]{}/.*&")) {
            length++
            c = reader.peek(length)
        }
        if (length == 0) {
            val s = Char.toString(c)
            throw ScannerException(
                "while scanning an $name", startMark,
                "unexpected character found $s($c)", reader.mark
            )
        }
        val value = reader.prefixForward(length)
        c = reader.peek()
        if (NULL_BL_T_LINEBR.hasNo(c, "?:,]}%@`")) {
            val s = Char.toString(c)
            throw ScannerException(
                "while scanning an $name", startMark,
                "unexpected character found $s($c)", reader.mark
            )
        }
        val endMark = reader.mark
        val tok: Token
        tok = if (isAnchor) {
            AnchorToken(value, startMark, endMark)
        } else {
            AliasToken(value, startMark, endMark)
        }
        return tok
    }

    /**
     *
     *
     * Scan a Tag property. A Tag property may be specified in one of three
     * ways: c-verbatim-tag, c-ns-shorthand-tag, or c-ns-non-specific-tag
     *
     *
     *
     *
     * c-verbatim-tag takes the form !&lt;ns-uri-char+&gt; and must be delivered
     * verbatim (as-is) to the application. In particular, verbatim tags are not
     * subject to tag resolution.
     *
     *
     *
     *
     * c-ns-shorthand-tag is a valid tag handle followed by a non-empty suffix.
     * If the tag handle is a c-primary-tag-handle ('!') then the suffix must
     * have all exclamation marks properly URI-escaped (%21); otherwise, the
     * string will look like a named tag handle: !foo!bar would be interpreted
     * as (handle="!foo!", suffix="bar").
     *
     *
     *
     *
     * c-ns-non-specific-tag is always a lone '!'; this is only useful for plain
     * scalars, where its specification means that the scalar MUST be resolved
     * to have type tag:yaml.org,2002:str.
     *
     *
     * TODO SnakeYaml incorrectly ignores c-ns-non-specific-tag right now.
     *
     * @see [8.2. Node Tags](http://www.yaml.org/spec/1.1/.id900262)
     *
     * TODO Note that this method does not enforce rules about local versus
     * global tags!
     */
    private fun scanTag(): Token {
        // See the specification for details.
        val startMark = reader.mark
        // Determine the type of tag property based on the first character
        // encountered
        var c = reader.peek(1)
        var handle: String? = null
        var suffix: String? = null
        // Verbatim tag! (c-verbatim-tag)
        if (c == '<'.code) {
            // Skip the exclamation mark and &gt;, then read the tag suffix (as
            // a URI).
            reader.forward(2)
            suffix = scanTagUri("tag", startMark)
            c = reader.peek()
            if (c != '>'.code) {
                // If there are any characters between the end of the tag-suffix
                // URI and the closing &gt;, then an error has occurred.
                val s = Char.toString(c)
                throw ScannerException(
                    "while scanning a tag", startMark,
                    "expected '>', but found '" + s + "' (" + c
                            + ")", reader.mark
                )
            }
            reader.forward()
        } else if (NULL_BL_T_LINEBR.has(c)) {
            // A NUL, blank, tab, or line-break means that this was a
            // c-ns-non-specific tag.
            suffix = "!"
            reader.forward()
        } else {
            // Any other character implies c-ns-shorthand-tag type.

            // Look ahead in the stream to determine whether this tag property
            // is of the form !foo or !foo!bar.
            var length = 1
            var useHandle = false
            while (Constant.NULL_BL_LINEBR.hasNo(c)) {
                if (c == '!'.code) {
                    useHandle = true
                    break
                }
                length++
                c = reader.peek(length)
            }
            // If we need to use a handle, scan it in; otherwise, the handle is
            // presumed to be '!'.
            if (useHandle) {
                handle = scanTagHandle("tag", startMark)
            } else {
                handle = "!"
                reader.forward()
            }
            suffix = scanTagUri("tag", startMark)
        }
        c = reader.peek()
        // Check that the next character is allowed to follow a tag-property;
        // if it is not, raise the error.
        if (Constant.NULL_BL_LINEBR.hasNo(c)) {
            val s = Char.toString(c)
            throw ScannerException(
                "while scanning a tag", startMark,
                "expected ' ', but found '$s' ($c)", reader.mark
            )
        }
        val value = TagTuple(handle, suffix)
        val endMark = reader.mark
        return TagToken(value, startMark, endMark)
    }

    private fun scanBlockScalar(style: Char): List<Token> {
        // See the specification for details.
        val folded: Boolean
        // Depending on the given style, we determine whether the scalar is
        // folded ('>') or literal ('|')
        folded = if (style == '>') {
            true
        } else {
            false
        }
        val chunks = StringBuilder()
        val startMark = reader.mark
        // Scan the header.
        reader.forward()
        val chompi = scanBlockScalarIndicators(startMark)
        val increment: Int = chompi.increment
        val commentToken = scanBlockScalarIgnoredLine(startMark)

        // Determine the indentation level and go to the first non-empty line.
        var minIndent = indent + 1
        if (minIndent < 1) {
            minIndent = 1
        }
        var breaks: String
        val maxIndent: Int
        val indent: Int
        var endMark: Mark
        if (increment == -1) {
            val brme = scanBlockScalarIndentation()
            breaks = brme[0] as String
            maxIndent = (brme[1] as Int).toInt()
            endMark = brme[2] as Mark
            indent = maxOf(minIndent, maxIndent)
        } else {
            indent = minIndent + increment - 1
            val brme = scanBlockScalarBreaks(indent)
            breaks = brme[0] as String
            endMark = brme[1] as Mark
        }
        var lineBreak = ""

        // Scan the inner part of the block scalar.
        while (reader.column == indent && reader.peek() != '\u0000'.code) {
            chunks.append(breaks)
            val leadingNonSpace = " \t".indexOf(reader.peek().toChar()) == -1
            var length = 0
            while (Constant.NULL_OR_LINEBR.hasNo(reader.peek(length))) {
                length++
            }
            chunks.append(reader.prefixForward(length))
            lineBreak = scanLineBreak()
            val brme = scanBlockScalarBreaks(indent)
            breaks = brme[0] as String
            endMark = brme[1] as Mark
            if (reader.column == indent && reader.peek() != '\u0000'.code) {

                // Unfortunately, folding rules are ambiguous.
                //
                // This is the folding according to the specification:
                if (folded && "\n" == lineBreak && leadingNonSpace && " \t".indexOf(reader.peek().toChar()) == -1) {
                    if (breaks.length == 0) {
                        chunks.append(" ")
                    }
                } else {
                    chunks.append(lineBreak)
                }
                // Clark Evans's interpretation (also in the spec examples) not
                // imported from PyYAML
            } else {
                break
            }
        }
        // Chomp the tail.
        if (chompi.chompTailIsNotFalse()) {
            chunks.append(lineBreak)
        }
        if (chompi.chompTailIsTrue()) {
            chunks.append(breaks)
        }
        // We are done.
        val scalarToken = ScalarToken(
            chunks.toString(),
            false,
            startMark,
            endMark,
            DumperOptions.ScalarStyle.createStyle(style)
        )
        return makeTokenList(commentToken, scalarToken)
    }

    /**
     * Scan a block scalar indicator. The block scalar indicator includes two
     * optional components, which may appear in either order.
     *
     * A block indentation indicator is a non-zero digit describing the
     * indentation level of the block scalar to follow. This indentation is an
     * additional number of spaces relative to the current indentation level.
     *
     * A block chomping indicator is a + or -, selecting the chomping mode away
     * from the default (clip) to either -(strip) or +(keep).
     *
     * @see [5.3. Indicator Characters](http://www.yaml.org/spec/1.1/.id868988)
     *
     * @see [9.2.2. Block Indentation Indicator](http://www.yaml.org/spec/1.1/.id927035)
     *
     * @see [9.2.3. Block Chomping Indicator](http://www.yaml.org/spec/1.1/.id927557)
     */
    private fun scanBlockScalarIndicators(startMark: Mark): Chomping {
        // See the specification for details.
        var chomping: Boolean? = null
        var increment = -1
        var c = reader.peek()
        if (c == '-'.code || c == '+'.code) {
            chomping = c == '+'.code
            reader.forward()
            c = reader.peek()
            if (Char.isDecimalDigit(c)) {
                val s = Char.toString(c)
                increment = s.toInt()
                if (increment == 0) {
                    throw ScannerException(
                        "while scanning a block scalar", startMark,
                        "expected indentation indicator in the range 1-9, but found 0",
                        reader.mark
                    )
                }
                reader.forward()
            }
        } else if (Char.isDecimalDigit(c)) {
            val s = Char.toString(c)
            increment = s.toInt()
            if (increment == 0) {
                throw ScannerException(
                    "while scanning a block scalar", startMark,
                    "expected indentation indicator in the range 1-9, but found 0",
                    reader.mark
                )
            }
            reader.forward()
            c = reader.peek()
            if (c == '-'.code || c == '+'.code) {
                chomping = c == '+'.code
                reader.forward()
            }
        }
        c = reader.peek()
        if (Constant.NULL_BL_LINEBR.hasNo(c)) {
            val s = Char.toString(c)
            throw ScannerException(
                "while scanning a block scalar", startMark,
                "expected chomping or indentation indicators, but found " + s + "("
                        + c + ")", reader.mark
            )
        }
        return Chomping(chomping, increment)
    }

    /**
     * Scan to the end of the line after a block scalar has been scanned; the
     * only things that are permitted at this time are comments and spaces.
     */
    private fun scanBlockScalarIgnoredLine(startMark: Mark): CommentToken? {
        // See the specification for details.

        // Forward past any number of trailing spaces
        while (reader.peek() == ' '.code) {
            reader.forward()
        }

        // If a comment occurs, scan to just before the end of line.
        var commentToken: CommentToken? = null
        if (reader.peek() == '#'.code) {
            commentToken = scanComment(CommentType.IN_LINE)
        }
        // If the next character is not a null or line break, an error has
        // occurred.
        val c = reader.peek()
        val lineBreak = scanLineBreak()
        if (lineBreak.length == 0 && c != '\u0000'.code) {
            val s = Char.toString(c)
            throw ScannerException(
                "while scanning a block scalar", startMark,
                "expected a comment or a line break, but found " + s + "("
                        + c + ")", reader.mark
            )
        }
        return commentToken
    }

    /**
     * Scans for the indentation of a block scalar implicitly. This mechanism is
     * used only if the block did not explicitly state an indentation to be
     * used.
     *
     * @see [9.2.2. Block Indentation Indicator](http://www.yaml.org/spec/1.1/.id927035)
     */
    private fun scanBlockScalarIndentation(): Array<Any> {
        // See the specification for details.
        val chunks = StringBuilder()
        var maxIndent = 0
        var endMark = reader.mark
        // Look ahead some number of lines until the first non-blank character
        // occurs; the determined indentation will be the maximum number of
        // leading spaces on any of these lines.
        while (Constant.LINEBR.has(reader.peek(), " \r")) {
            if (reader.peek() != ' '.code) {
                // If the character isn't a space, it must be some kind of
                // line-break; scan the line break and track it.
                chunks.append(scanLineBreak())
                endMark = reader.mark
            } else {
                // If the character is a space, move forward to the next
                // character; if we surpass our previous maximum for indent
                // level, update that too.
                reader.forward()
                if (reader.column > maxIndent) {
                    maxIndent = reader.column
                }
            }
        }
        // Pass several results back together.
        return arrayOf(chunks.toString(), maxIndent, endMark)
    }

    private fun scanBlockScalarBreaks(indent: Int): Array<Any> {
        // See the specification for details.
        val chunks = StringBuilder()
        var endMark = reader.mark
        var col = reader.column
        // Scan for up to the expected indentation-level of spaces, then move
        // forward past that amount.
        while (col < indent && reader.peek() == ' '.code) {
            reader.forward()
            col++
        }

        // Consume one or more line breaks followed by any amount of spaces,
        // until we find something that isn't a line-break.
        var lineBreak: String? = null
        while (scanLineBreak().also { lineBreak = it }.length != 0) {
            chunks.append(lineBreak)
            endMark = reader.mark
            // Scan past up to (indent) spaces on the next line, then forward
            // past them.
            col = reader.column
            while (col < indent && reader.peek() == ' '.code) {
                reader.forward()
                col++
            }
        }
        // Return both the assembled intervening string and the end-mark.
        return arrayOf(chunks.toString(), endMark)
    }

    /**
     * Scan a flow-style scalar. Flow scalars are presented in one of two forms;
     * first, a flow scalar may be a double-quoted string; second, a flow scalar
     * may be a single-quoted string.
     *
     * @see [9.1. Flow Scalar Styles](https://yaml.org/spec/1.1/.id904158) style/syntax
     *
     * <pre>
     * See the specification for details.
     * Note that we loose indentation rules for quoted scalars. Quoted
     * scalars don't need to adhere indentation because &quot; and ' clearly
     * mark the beginning and the end of them. Therefore we are less
     * restrictive then the specification requires. We only need to check
     * that document separators are not included in scalars.
    </pre> *
     */
    private fun scanFlowScalar(style: Char): Token {
        val _double: Boolean
        // The style will be either single- or double-quoted; we determine this
        // by the first character in the entry (supplied)
        _double = if (style == '"') {
            true
        } else {
            false
        }
        val chunks = StringBuilder()
        val startMark = reader.mark
        val quote = reader.peek()
        reader.forward()
        chunks.append(scanFlowScalarNonSpaces(_double, startMark))
        while (reader.peek() != quote) {
            chunks.append(scanFlowScalarSpaces(startMark))
            chunks.append(scanFlowScalarNonSpaces(_double, startMark))
        }
        reader.forward()
        val endMark = reader.mark
        return ScalarToken(
            chunks.toString(),
            false,
            startMark,
            endMark,
            DumperOptions.ScalarStyle.createStyle(style)
        )
    }

    /**
     * Scan some number of flow-scalar non-space characters.
     */
    private fun scanFlowScalarNonSpaces(doubleQuoted: Boolean, startMark: Mark): String {
        // See the specification for details.
        val chunks = StringBuilder()
        while (true) {
            // Scan through any number of characters which are not: NUL, blank,
            // tabs, line breaks, single-quotes, double-quotes, or backslashes.
            var length = 0
            while (NULL_BL_T_LINEBR.hasNo(reader.peek(length), "\'\"\\")) {
                length++
            }
            if (length != 0) {
                chunks.append(reader.prefixForward(length))
            }
            // Depending on our quoting-type, the characters ', " and \ have
            // differing meanings.
            var c = reader.peek()
            if (!doubleQuoted && c == '\''.code && reader.peek(1) == '\''.code) {
                chunks.append("'")
                reader.forward(2)
            } else if ((doubleQuoted && c == '\''.code || !doubleQuoted) && "\"\\".indexOf(c.toChar()) != -1) {
                chunks.appendCodePoint(c)
                reader.forward()
            } else if (doubleQuoted && c == '\\'.code) {
                reader.forward()
                c = reader.peek()
                if (!Char.isSupplementaryCodePoint(c) && ESCAPE_REPLACEMENTS.containsKey((c.toChar()))) {
                    // The character is one of the single-replacement
                    // types; these are replaced with a literal character
                    // from the mapping.
                    chunks.append(ESCAPE_REPLACEMENTS[(c.toChar())])
                    reader.forward()
                } else if (!Char.isSupplementaryCodePoint(c) && ESCAPE_CODES.containsKey((c.toChar()))) {
                    // The character is a multi-digit escape sequence, with
                    // length defined by the value in the ESCAPE_CODES map.
                    length = ESCAPE_CODES[(c.toChar())]!!
                        .toInt()
                    reader.forward()
                    val hex = reader.prefix(length)
                    if (NOT_HEXA.find(hex) != null) {
                        throw ScannerException(
                            "while scanning a double-quoted scalar",
                            startMark, "expected escape sequence of " + length
                                    + " hexadecimal numbers, but found: " + hex,
                            reader.mark
                        )
                    }
                    val decimal = hex.toInt(16)
                    val unicode = Char.toString(decimal)
                    chunks.append(unicode)
                    reader.forward(length)
                } else if (scanLineBreak().length != 0) {
                    chunks.append(scanFlowScalarBreaks(startMark))
                } else {
                    val s = Char.toString(c)
                    throw ScannerException(
                        "while scanning a double-quoted scalar", startMark,
                        "found unknown escape character $s($c)",
                        reader.mark
                    )
                }
            } else {
                return chunks.toString()
            }
        }
    }

    private fun scanFlowScalarSpaces(startMark: Mark): String {
        // See the specification for details.
        val chunks = StringBuilder()
        var length = 0
        // Scan through any number of whitespace (space, tab) characters,
        // consuming them.
        while (" \t".indexOf(reader.peek(length).toChar()) != -1) {
            length++
        }
        val whitespaces = reader.prefixForward(length)
        val c = reader.peek()
        if (c == '\u0000'.code) {
            // A flow scalar cannot end with an end-of-stream
            throw ScannerException(
                "while scanning a quoted scalar", startMark,
                "found unexpected end of stream", reader.mark
            )
        }
        // If we encounter a line break, scan it into our assembled string...
        val lineBreak = scanLineBreak()
        if (lineBreak.length != 0) {
            val breaks = scanFlowScalarBreaks(startMark)
            if ("\n" != lineBreak) {
                chunks.append(lineBreak)
            } else if (breaks.length == 0) {
                chunks.append(" ")
            }
            chunks.append(breaks)
        } else {
            chunks.append(whitespaces)
        }
        return chunks.toString()
    }

    private fun scanFlowScalarBreaks(startMark: Mark): String {
        // See the specification for details.
        val chunks = StringBuilder()
        while (true) {
            // Instead of checking indentation, we check for document
            // separators.
            val prefix = reader.prefix(3)
            if ("---" == prefix || "..." == prefix
                && NULL_BL_T_LINEBR.has(reader.peek(3))
            ) {
                throw ScannerException(
                    "while scanning a quoted scalar", startMark,
                    "found unexpected document separator", reader.mark
                )
            }
            // Scan past any number of spaces and tabs, ignoring them
            while (" \t".indexOf(reader.peek().toChar()) != -1) {
                reader.forward()
            }
            // If we stopped at a line break, add that; otherwise, return the
            // assembled set of scalar breaks.
            val lineBreak = scanLineBreak()
            if (lineBreak.length != 0) {
                chunks.append(lineBreak)
            } else {
                return chunks.toString()
            }
        }
    }

    private operator fun String.contains(charCode: Int) = contains(charCode.toChar())

    /**
     * Scan a plain scalar.
     *
     * <pre>
     * See the specification for details.
     * We add an additional restriction for the flow context:
     * plain scalars in the flow context cannot contain ',', ':' and '?'.
     * We also keep track of the `allow_simple_key` flag here.
     * Indentation rules are loosed for the flow context.
    </pre> *
     */
    private fun scanPlain(): Token {
        val chunks = StringBuilder()
        val startMark = reader.mark
        var endMark = startMark
        val indent = indent + 1
        var spaces: String? = ""
        while (true) {
            var c: Int
            var length = 0
            // A comment indicates the end of the scalar.
            if (reader.peek() == '#'.code) {
                break
            }
            while (true) {
                c = reader.peek(length)
                if (c in NULL_BL_T_LINEBR
                    || c == ':'.code && NULL_BL_T_LINEBR.has(reader.peek(length + 1), if (flowLevel != 0) ",[]{}" else "")
                    || flowLevel != 0 && c.toChar() in ",?[]{}"
                ) {
                    break
                }
                length++
            }
            if (length == 0) {
                break
            }
            allowSimpleKey = false
            chunks.append(spaces)
            chunks.append(reader.prefixForward(length))
            endMark = reader.mark
            spaces = scanPlainSpaces()
            // System.out.printf("spaces[%s]\n", spaces);

            if (spaces!!.length == 0 || reader.peek() == '#'.code || flowLevel == 0 && reader.column < indent) {
                break
            }
        }
        return ScalarToken(chunks.toString(), startMark, endMark, true)
    }

    // Helper for scanPlainSpaces method when comments are enabled.
    // The ensures that blank lines and comments following a multi-line plain token are not swallowed up
    private fun atEndOfPlain(): Boolean {
        // peak ahead to find end of whitespaces and the column at which it occurs
        var wsLength = 0
        var wsColumn = reader.column
        run {
            var c: Int
            while (reader.peek(wsLength)
                    .also { c = it } != '\u0000'.code && NULL_BL_T_LINEBR.has(c)
            ) {
                wsLength++
                if (!Constant.LINEBR.has(c) && (c != '\r'.code || reader.peek(wsLength + 1) != '\n'.code) && c != 0xFEFF) {
                    wsColumn++
                } else {
                    wsColumn = 0
                }
            }
        }

        // if we see, a comment or end of string or change decrease in indent, we are done
        // Do not chomp end of lines and blanks, they will be handled by the main loop.
        if (reader.peek(wsLength) == '#'.code || reader.peek(wsLength + 1) == '\u0000'.code || flowLevel == 0 && wsColumn < indent) {
            return true
        }

        // if we see, after the space, a key-value followed by a ':', we are done
        // Do not chomp end of lines and blanks, they will be handled by the main loop.
        if (flowLevel == 0) {
            var c: Int
            var extra = 1
            while (reader.peek(wsLength + extra).also { c = it } != 0 && !NULL_BL_T_LINEBR.has(c)) {
                if (c == ':'.code && NULL_BL_T_LINEBR.has(reader.peek(wsLength + extra + 1))) {
                    return true
                }
                extra++
            }
        }

        // None of the above so safe to chomp the spaces.
        return false
    }

    /**
     * See the specification for details. SnakeYAML and libyaml allow tabs
     * inside plain scalar
     */
    private fun scanPlainSpaces(): String? {
        var length = 0
        while (reader.peek(length) == ' '.code || reader.peek(length) == '\t'.code) {
            length++
        }
        val whitespaces = reader.prefixForward(length)
        val lineBreak = scanLineBreak()
        if (lineBreak.length != 0) {
            allowSimpleKey = true
            var prefix = reader.prefix(3)
            if ("---" == prefix || "..." == prefix && NULL_BL_T_LINEBR.has(
                    reader.peek(3)
                )
            ) {
                return ""
            }
            if (isParseComments && atEndOfPlain()) {
                return ""
            }
            val breaks = StringBuilder()
            while (true) {
                if (reader.peek() == ' '.code) {
                    reader.forward()
                } else {
                    val lb = scanLineBreak()
                    if (lb.length != 0) {
                        breaks.append(lb)
                        prefix = reader.prefix(3)
                        if ("---" == prefix || "..." == prefix && NULL_BL_T_LINEBR.has(
                                reader.peek(3)
                            )
                        ) {
                            return ""
                        }
                    } else {
                        break
                    }
                }
            }
            if ("\n" != lineBreak) {
                return lineBreak + breaks
            } else if (breaks.length == 0) {
                return " "
            }
            return breaks.toString()
        }
        return whitespaces
    }

    /**
     *
     *
     * Scan a Tag handle. A Tag handle takes one of three forms:
     *
     * <pre>
     * "!" (c-primary-tag-handle)
     * "!!" (ns-secondary-tag-handle)
     * "!(name)!" (c-named-tag-handle)
    </pre> *
     *
     * Where (name) must be formatted as an ns-word-char.
     *
     *
     * @see [](http://www.yaml.org/spec/1.1/.c-tag-handle)
     *
     * @see [](http://www.yaml.org/spec/1.1/.ns-word-char)
     *
     * <pre>
     * See the specification for details.
     * For some strange reasons, the specification does not allow '_' in
     * tag handles. I have allowed it anyway.
    </pre> *
     */
    private fun scanTagHandle(name: String, startMark: Mark): String? {
        var c = reader.peek()
        if (c != '!'.code) {
            val s = Char.toString(c)
            throw ScannerException(
                "while scanning a $name", startMark,
                "expected '!', but found $s($c)", reader.mark
            )
        }
        // Look for the next '!' in the stream, stopping if we hit a
        // non-word-character. If the first character is a space, then the
        // tag-handle is a c-primary-tag-handle ('!').
        var length = 1
        c = reader.peek(length)
        if (c != ' '.code) {
            // Scan through 0+ alphabetic characters.
            // FIXME According to the specification, these should be
            // ns-word-char only, which prohibits '_'. This might be a
            // candidate for a configuration option.
            while (Constant.ALPHA.has(c)) {
                length++
                c = reader.peek(length)
            }
            // Found the next non-word-char. If this is not a space and not an
            // '!', then this is an error, as the tag-handle was specified as:
            // !(name) or similar; the trailing '!' is missing.
            if (c != '!'.code) {
                reader.forward(length)
                val s = Char.toString(c)
                throw ScannerException(
                    "while scanning a $name", startMark,
                    "expected '!', but found $s($c)", reader.mark
                )
            }
            length++
        }
        return reader.prefixForward(length)
    }

    /**
     *
     *
     * Scan a Tag URI. This scanning is valid for both local and global tag
     * directives, because both appear to be valid URIs as far as scanning is
     * concerned. The difference may be distinguished later, in parsing. This
     * method will scan for ns-uri-char*, which covers both cases.
     *
     *
     *
     *
     * This method performs no verification that the scanned URI conforms to any
     * particular kind of URI specification.
     *
     *
     * @see [](http://www.yaml.org/spec/1.1/.ns-uri-char)
     */
    private fun scanTagUri(name: String, startMark: Mark): String {
        // See the specification for details.
        // Note: we do not check if URI is well-formed.
        val chunks = StringBuilder()
        // Scan through accepted URI characters, which includes the standard
        // URI characters, plus the start-escape character ('%'). When we get
        // to a start-escape, scan the escaped sequence, then return.
        var length = 0
        var c = reader.peek(length)
        while (Constant.URI_CHARS.has(c)) {
            if (c == '%'.code) {
                chunks.append(reader.prefixForward(length))
                length = 0
                chunks.append(scanUriEscapes(name, startMark))
            } else {
                length++
            }
            c = reader.peek(length)
        }
        // Consume the last "chunk", which would not otherwise be consumed by
        // the loop above.
        if (length != 0) {
            chunks.append(reader.prefixForward(length))
        }
        if (chunks.length == 0) {
            // If no URI was found, an error has occurred.
            val s = Char.toString(c)
            throw ScannerException(
                "while scanning a $name", startMark,
                "expected URI, but found $s($c)", reader.mark
            )
        }
        return chunks.toString()
    }

    /**
     *
     *
     * Scan a sequence of %-escaped URI escape codes and convert them into a
     * String representing the unescaped values.
     *
     *
     * FIXME This method fails for more than 256 bytes' worth of URI-encoded
     * characters in a row. Is this possible? Is this a use-case?
     *
     * @see [section 2.4, Escaped Encoding](http://www.ietf.org/rfc/rfc2396.txt)
     */
    private fun scanUriEscapes(name: String, startMark: Mark): String {
        // First, look ahead to see how many URI-escaped characters we should
        // expect, so we can use the correct buffer size.
        var length = 1
        while (reader.peek(length * 3) == '%'.code) {
            length++
        }
        // See the specification for details.
        // URIs containing 16 and 32 bit Unicode characters are
        // encoded in UTF-8, and then each octet is written as a
        // separate character.
        val beginningMark = reader.mark
        val buff = ByteArray(length)
        var ix = 0
        while (reader.peek() == '%'.code) {
            reader.forward()
            try {
                val code = reader.prefix(2).toInt(16).toByte()
                buff[ix] = code
                ++ix
            } catch (nfe: NumberFormatException) {
                val c1 = reader.peek()
                val s1 = Char.toString(c1)
                val c2 = reader.peek(1)
                val s2 = Char.toString(c2)
                throw ScannerException(
                    "while scanning a $name", startMark,
                    "expected URI escape sequence of 2 hexadecimal numbers, but found "
                            + s1 + "(" + c1 + ") and "
                            + s2 + "(" + c2 + ")",
                    reader.mark
                )
            }
            reader.forward(2)
        }
        return try {
            buff.decodeToString(0, ix, throwOnInvalidSequence = true)
        } catch (e: CharacterCodingException) {
            throw ScannerException(
                "while scanning a $name", startMark,
                "expected URI in UTF-8: " + e.message, beginningMark
            )
        }
    }

    /**
     * Scan a line break, transforming:
     *
     * <pre>
     * '\r\n' : '\n'
     * '\r' : '\n'
     * '\n' : '\n'
     * '\x85' : '\n'
     * default : ''
    </pre> *
     */
    private fun scanLineBreak(): String {
        val c = reader.peek()
        if (c == '\r'.code || c == '\n'.code || c == '\u0085'.code) {
            if (c == '\r'.code && '\n'.code == reader.peek(1)) {
                reader.forward(2)
            } else {
                reader.forward()
            }
            return "\n"
        } else if (c == '\u2028'.code || c == '\u2029'.code) {
            reader.forward()
            return Char.toString(c)
        }
        return ""
    }

    private fun makeTokenList(vararg tokens: Token?): List<Token> {
        val tokenList: MutableList<Token> = ArrayList()
        for (ix in tokens.indices) {
            if (tokens[ix] == null) {
                continue
            }
            if (!isParseComments && tokens[ix] is CommentToken) {
                continue
            }
            tokenList.add(tokens[ix]!!)
        }
        return tokenList
    }

    /**
     * Chomping the tail may have 3 values - yes, no, not defined.
     */
    private class Chomping(private val value: Boolean?, val increment: Int) {

        fun chompTailIsNotFalse(): Boolean {
            return value == null || value
        }

        fun chompTailIsTrue(): Boolean {
            return value != null && value
        }
    }

    companion object {
        /**
         * A regular expression matching characters which are not in the hexadecimal
         * set (0-9, A-F, a-f).
         */
        private val NOT_HEXA = Regex("[^0-9A-Fa-f]")

        /**
         * A mapping from an escaped character in the input stream to the string representation
         * that they should be replaced with.
         *
         * YAML defines several common and a few uncommon escape sequences.
         *
         * @see [4.1.6.
         * Escape Sequences](http://www.yaml.org/spec/current.html.id2517668)
         */
        @JvmField
        val ESCAPE_REPLACEMENTS: MutableMap<Char, String> = HashMap()

        /**
         * A mapping from a character to a number of bytes to read-ahead for that
         * escape sequence. These escape sequences are used to handle unicode
         * escaping in the following formats, where H is a hexadecimal character:
         *
         * <pre>
         * &#92;xHH         : escaped 8-bit Unicode character
         * &#92;uHHHH       : escaped 16-bit Unicode character
         * &#92;UHHHHHHHH   : escaped 32-bit Unicode character
        </pre> *
         *
         * @see [5.6. Escape
         * Sequences](http://yaml.org/spec/1.1/current.html.id872840)
         */
        @JvmField
        val ESCAPE_CODES: MutableMap<Char, Int> = HashMap()

        init {
            // ASCII null
            ESCAPE_REPLACEMENTS['0'] = "\u0000"
            // ASCII bell
            ESCAPE_REPLACEMENTS['a'] = "\u0007"
            // ASCII backspace
            ESCAPE_REPLACEMENTS['b'] = "\u0008"
            // ASCII horizontal tab
            ESCAPE_REPLACEMENTS['t'] = "\u0009"
            // ASCII newline (line feed; &#92;n maps to 0x0A)
            ESCAPE_REPLACEMENTS['n'] = "\n"
            // ASCII vertical tab
            ESCAPE_REPLACEMENTS['v'] = "\u000B"
            // ASCII form-feed
            ESCAPE_REPLACEMENTS['f'] = "\u000C"
            // carriage-return (&#92;r maps to 0x0D)
            ESCAPE_REPLACEMENTS['r'] = "\r"
            // ASCII escape character (Esc)
            ESCAPE_REPLACEMENTS['e'] = "\u001B"
            // ASCII space
            ESCAPE_REPLACEMENTS[' '] = "\u0020"
            // ASCII double-quote
            ESCAPE_REPLACEMENTS['"'] = "\""
            // ASCII backslash
            ESCAPE_REPLACEMENTS['\\'] = "\\"
            // Unicode next line
            ESCAPE_REPLACEMENTS['N'] = "\u0085"
            // Unicode non-breaking-space
            ESCAPE_REPLACEMENTS['_'] = "\u00A0"
            // Unicode line-separator
            ESCAPE_REPLACEMENTS['L'] = "\u2028"
            // Unicode paragraph separator
            ESCAPE_REPLACEMENTS['P'] = "\u2029"

            // 8-bit Unicode
            ESCAPE_CODES['x'] = 2
            // 16-bit Unicode
            ESCAPE_CODES['u'] = 4
            // 32-bit Unicode (Supplementary characters are supported)
            ESCAPE_CODES['U'] = 8
        }
    }
}

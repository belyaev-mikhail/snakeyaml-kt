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
package org.yaml.snakeyaml

import org.yaml.snakeyaml.emitter.EmitterConstants
import org.yaml.snakeyaml.error.YAMLException
import org.yaml.snakeyaml.serializer.AnchorGenerator
import org.yaml.snakeyaml.serializer.NumberAnchorGenerator
import systemLineSeparator
import kotlin.jvm.JvmStatic

class DumperOptions {
    /**
     * YAML provides a rich set of scalar styles. Block scalar styles include
     * the literal style and the folded style; flow scalar styles include the
     * plain style and two quoted styles, the single-quoted style and the
     * double-quoted style. These styles offer a range of trade-offs between
     * expressive power and readability.
     *
     * @see [Chapter 9. Scalar
     * Styles](http://yaml.org/spec/1.1/.id903915)
     *
     * @see [2.3. Scalars](http://yaml.org/spec/1.1/.id858081)
     */
    enum class ScalarStyle(val char: Char?) {
        DOUBLE_QUOTED('"'), SINGLE_QUOTED('\''), LITERAL(
            '|'
        ),
        FOLDED('>'), PLAIN(null);

        override fun toString(): String {
            return "Scalar style: '" + char + "'"
        }

        companion object {
            @JvmStatic
            fun createStyle(style: Char?): ScalarStyle {
                return if (style == null) {
                    PLAIN
                } else {
                    when (style) {
                        '"' -> DOUBLE_QUOTED
                        '\'' -> SINGLE_QUOTED
                        '|' -> LITERAL
                        '>' -> FOLDED
                        else -> throw YAMLException("Unknown scalar style character: $style")
                    }
                }
            }
        }
    }

    /**
     * Block styles use indentation to denote nesting and scope within the
     * document. In contrast, flow styles rely on explicit indicators to denote
     * nesting and scope.
     *
     * @see [3.2.3.1.
     * Node Styles
    ](http://www.yaml.org/spec/current.html.id2509255) */
    enum class FlowStyle(val styleBoolean: Boolean?) {
        FLOW(true), BLOCK(false), AUTO(null);

        override fun toString(): String {
            return "Flow style: '$styleBoolean'"
        }

        companion object {
            /*
         * Convenience for legacy constructors that took {@link Boolean} arguments since replaced by {@link FlowStyle}.
         * Introduced in v1.22 but only to support that for backwards compatibility.
         * @deprecated Since restored in v1.22.  Use the {@link FlowStyle} constants in your code instead.
         */
            @Deprecated("")
            fun fromBoolean(flowStyle: Boolean?): FlowStyle {
                return if (flowStyle == null) AUTO else if (flowStyle) FLOW else BLOCK
            }
        }
    }

    /**
     * Platform dependent line break.
     */
    enum class LineBreak(val string: String) {
        WIN("\r\n"), MAC("\r"), UNIX("\n");

        override fun toString(): String {
            return "Line break: $name"
        }

        companion object {
            @JvmStatic
            val platformLineBreak: LineBreak
                get() {
                    val platformLineBreak = systemLineSeparator()
                    for (lb in values()) {
                        if (lb.string == platformLineBreak) {
                            return lb
                        }
                    }
                    return UNIX
                }
        }
    }

    /**
     * Specification version. Currently supported 1.0 and 1.1
     */
    enum class Version(private val version: Array<Int>) {
        V1_0(arrayOf(1, 0)), V1_1(arrayOf(1, 1));

        fun major(): Int {
            return version[0]
        }

        fun minor(): Int {
            return version[1]
        }

        val representation: String
            get() = version[0].toString() + "." + version[1]

        override fun toString(): String {
            return "Version: " + representation
        }
    }

    enum class NonPrintableStyle {
        /**
         * Transform String to binary if it contains non-printable characters
         */
        BINARY,

        /**
         * Escape non-printable characters
         */
        ESCAPE
    }

    private var defaultStyle = ScalarStyle.PLAIN
    var defaultFlowStyle = FlowStyle.AUTO
        set(defaultFlowStyle) {
            if (defaultFlowStyle == null) {
                throw NullPointerException("Use FlowStyle enum.")
            }
            field = defaultFlowStyle
        }

    /**
     * Force the emitter to produce a canonical YAML document.
     *
     * @param canonical
     * true produce canonical YAML document
     */
    var isCanonical = false

    /**
     * Specify whether to emit non-ASCII printable Unicode characters.
     * The default value is true.
     * When set to false then printable non-ASCII characters (Cyrillic, Chinese etc)
     * will be not printed but escaped (to support ASCII terminals)
     *
     * @param allowUnicode
     * if allowUnicode is false then all non-ASCII characters are
     * escaped
     */
    var isAllowUnicode = true
    /**
     * Report whether read-only JavaBean properties (the ones without setters)
     * should be included in the YAML document
     *
     * @return false when read-only JavaBean properties are not emitted
     */
    /**
     * Set to true to include read-only JavaBean properties (the ones without
     * setters) in the YAML document. By default these properties are not
     * included to be able to parse later the same JavaBean.
     *
     * @param allowReadOnlyProperties
     * - true to dump read-only JavaBean properties
     */
    var isAllowReadOnlyProperties = false
    var indent = 2
        set(indent) {
            if (indent < EmitterConstants.MIN_INDENT) {
                throw YAMLException("Indent must be at least " + EmitterConstants.MIN_INDENT)
            }
            if (indent > EmitterConstants.MAX_INDENT) {
                throw YAMLException("Indent must be at most " + EmitterConstants.MAX_INDENT)
            }
            field = indent
        }

    /**
     * Set number of white spaces to use for the sequence indicator '-'
     * @param indicatorIndent value to be used as indent
     */
    var indicatorIndent = 0
        set(indicatorIndent) {
            if (indicatorIndent < 0) {
                throw YAMLException("Indicator indent must be non-negative.")
            }
            if (indicatorIndent > EmitterConstants.MAX_INDENT - 1) {
                throw YAMLException("Indicator indent must be at most Emitter.MAX_INDENT-1: " + (EmitterConstants.MAX_INDENT - 1))
            }
            field = indicatorIndent
        }

    /**
     * Set to true to add the indent for sequences to the general indent
     * @param indentWithIndicator - true when indent for sequences is added to general
     */
    var indentWithIndicator = false

    /**
     * Specify the preferred width to emit scalars. When the scalar
     * representation takes more then the preferred with the scalar will be
     * split into a few lines. The default is 80.
     *
     * @param bestWidth
     * the preferred width for scalars.
     */
    var width = 80

    /**
     * Specify whether to split lines exceeding preferred width for
     * scalars. The default is true.
     *
     * @param splitLines
     * whether to split lines exceeding preferred width for scalars.
     */
    var splitLines = true

    /**
     * Specify the line break to separate the lines. It is platform specific:
     * Windows - "\r\n", old MacOS - "\r", Unix - "\n". The default value is the
     * one for Unix.
     * @param lineBreak to be used for the input
     */
    var lineBreak = LineBreak.UNIX
        set(lineBreak) {
            if (lineBreak == null) {
                throw NullPointerException("Specify line break.")
            }
            field = lineBreak
        }
    var isExplicitStart = false
    var isExplicitEnd = false

    /**
     * Set the timezone to be used for Date. If set to `null` UTC is
     * used.
     * @param timeZone for created Dates or null to use UTC
     */
    var timeZone: kotlinx.datetime.Timezone? = null

    /**
     * Define max key length to use simple key (without '?')
     * More info https://yaml.org/spec/1.1/#id934537
     * @param maxSimpleKeyLength - the limit after which the key gets explicit key indicator '?'
     */
    var maxSimpleKeyLength = 128
        set(maxSimpleKeyLength) {
            if (maxSimpleKeyLength > 1024) {
                throw YAMLException("The simple key must not span more than 1024 stream characters. See https://yaml.org/spec/1.1/#id934537")
            }
            field = maxSimpleKeyLength
        }

    /**
     * Set the comment processing. By default comments are ignored.
     *
     * @param processComments `true` to process; `false` to ignore
     */
    var isProcessComments = false

    /**
     * When String contains non-printable characters SnakeYAML convert it to binary data with the !!binary tag.
     * Set this to ESCAPE to keep the !!str tag and escape the non-printable chars with \\x or \\u
     * @param style ESCAPE to force SnakeYAML to keep !!str tag for non-printable data
     */
    var nonPrintableStyle = NonPrintableStyle.BINARY
    var version: Version? = null
    var tags: Map<String, String>? = null

    /**
     * Force the emitter to produce a pretty YAML document when using the flow
     * style.
     *
     * @param prettyFlow
     * true produce pretty flow YAML document
     */
    var isPrettyFlow = false
    var anchorGenerator: AnchorGenerator = NumberAnchorGenerator(0)

    /**
     * Set default style for scalars. See YAML 1.1 specification, 2.3 Scalars
     * (http://yaml.org/spec/1.1/#id858081)
     *
     * @param defaultStyle
     * set the style for all scalars
     */
    var defaultScalarStyle: ScalarStyle
        get() = defaultStyle
        set(defaultStyle) {
            if (defaultStyle == null) {
                throw NullPointerException("Use ScalarStyle enum.")
            }
            this.defaultStyle = defaultStyle
        }
}

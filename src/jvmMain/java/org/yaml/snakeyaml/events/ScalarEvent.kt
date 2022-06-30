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
package org.yaml.snakeyaml.events

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.DumperOptions.ScalarStyle.Companion.createStyle
import org.yaml.snakeyaml.error.Mark

/**
 * Marks a scalar value.
 */
class ScalarEvent(
    anchor: String?,
    /**
     * Tag of this scalar.
     *
     * @return The tag of this scalar, or `null` if no explicit tag
     * is available.
     */
    val tag: String?, // The implicit flag of a scalar event is a pair of boolean values that
    // indicate if the tag may be omitted when the scalar is emitted in a plain
    // and non-plain style correspondingly.
    private val implicit: ImplicitTuple, value: String?,
    startMark: Mark?, endMark: Mark?, style: DumperOptions.ScalarStyle?
) : NodeEvent(anchor, startMark, endMark) {

    /**
     * Style of the scalar.
     * <dl>
     * <dt>null</dt>
     * <dd>Flow Style - Plain</dd>
     * <dt>'\''</dt>
     * <dd>Flow Style - Single-Quoted</dd>
     * <dt>'"'</dt>
     * <dd>Flow Style - Double-Quoted</dd>
     * <dt>'|'</dt>
     * <dd>Block Style - Literal</dd>
     * <dt>'&gt;'</dt>
     * <dd>Block Style - Folded</dd>
    </dl> *
     *
     * @see [Kind/Style
     * Combinations](http://yaml.org/spec/1.1/.id864487)
     *
     * @return Style of the scalar.
     */
    // style flag of a scalar event indicates the style of the scalar. Possible
    // values are None, '', '\'', '"', '|', '>'
    val scalarStyle: DumperOptions.ScalarStyle

    /**
     * String representation of the value.
     *
     *
     * Without quotes and escaping.
     *
     *
     * @return Value as Unicode string.
     */
    val value: String

    init {
        if (value == null) throw NullPointerException("Value must be provided.")
        this.value = value
        if (style == null) throw NullPointerException("Style must be provided.")
        scalarStyle = style
    }

    /*
     * Existed in older versions but replaced with {@link DumperOptions.ScalarStyle}-based constructor.
     * Restored in v1.22 for backwards compatibility.
     * @deprecated Since restored in v1.22.  Use {@link ScalarEvent#ScalarEvent(String, String, ImplicitTuple, String, Mark, Mark, org.yaml.snakeyaml.DumperOptions.ScalarStyle) }.
     */
    @Deprecated("")
    constructor(
        anchor: String?, tag: String?, implicit: ImplicitTuple, value: String?,
        startMark: Mark?, endMark: Mark?, style: Char?
    ) : this(anchor, tag, implicit, value, startMark, endMark, createStyle(style)) {
    }

    /**
     * @return char which is a value behind ScalarStyle
     */
    @Deprecated(
        """use getScalarStyle()  instead
      """
    )
    fun getStyle(): Char? {
        return scalarStyle.char
    }

    fun getImplicit(): ImplicitTuple? {
        return implicit
    }

    protected override val arguments: String
        protected get() = super.arguments + ", tag=" + tag + ", " + implicit + ", value=" + value
    override val eventId: ID
        get() = ID.Scalar
    val isPlain: Boolean
        get() = scalarStyle === DumperOptions.ScalarStyle.PLAIN
}
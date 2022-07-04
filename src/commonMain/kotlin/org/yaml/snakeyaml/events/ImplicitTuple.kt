package org.yaml.snakeyaml.events

/**
 * The implicit flag of a scalar event is a pair of boolean values that indicate
 * if the tag may be omitted when the scalar is emitted in a plain and non-plain
 * style correspondingly.
 *
 * @see [Events](http://pyyaml.org/wiki/PyYAMLDocumentation.Events)
 */
class ImplicitTuple(private val plain: Boolean, private val nonPlain: Boolean) {
    /**
     * @return true when tag may be omitted when the scalar is emitted in a
     * plain style.
     */
    fun canOmitTagInPlainScalar(): Boolean {
        return plain
    }

    /**
     * @return true when tag may be omitted when the scalar is emitted in a
     * non-plain style.
     */
    fun canOmitTagInNonPlainScalar(): Boolean {
        return nonPlain
    }

    fun bothFalse(): Boolean {
        return !plain && !nonPlain
    }

    override fun toString(): String {
        return "implicit=[$plain, $nonPlain]"
    }
}

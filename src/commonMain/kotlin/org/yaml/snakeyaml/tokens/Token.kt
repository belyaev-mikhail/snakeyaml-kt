package org.yaml.snakeyaml.tokens

import org.yaml.snakeyaml.error.Mark

abstract class Token(val startMark: Mark, val endMark: Mark) {
    enum class ID(private val description: String) {
        Alias("<alias>"), Anchor("<anchor>"), BlockEnd("<block end>"), BlockEntry("-"), BlockMappingStart("<block mapping start>"), BlockSequenceStart(
            "<block sequence start>"
        ),
        Directive("<directive>"), DocumentEnd("<document end>"), DocumentStart("<document start>"), FlowEntry(","), FlowMappingEnd(
            "}"
        ),
        FlowMappingStart("{"), FlowSequenceEnd("]"), FlowSequenceStart("["), Key("?"), Scalar("<scalar>"), StreamEnd("<stream end>"), StreamStart(
            "<stream start>"
        ),
        Tag("<tag>"), Value(":"), Whitespace("<whitespace>"), Comment("#"), Error("<error>");

        override fun toString(): String {
            return description
        }
    }

    /**
     * For error reporting.
     *
     * @see "class variable 'id' in PyYAML"
     *
     * @return ID of this token
     */
    abstract val tokenId: ID
}

package org.yaml.snakeyaml.tokens

import org.yaml.snakeyaml.error.Mark

class FlowMappingStartToken(startMark: Mark, endMark: Mark) : Token(startMark, endMark) {
    override val tokenId: ID
        get() = ID.FlowMappingStart
}

package org.yaml.snakeyaml.tokens

import org.yaml.snakeyaml.error.Mark

class BlockEndToken(startMark: Mark, endMark: Mark) : Token(startMark, endMark) {
    override val tokenId: ID
        get() = ID.BlockEnd
}

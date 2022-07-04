package org.yaml.snakeyaml.tokens

import org.yaml.snakeyaml.error.Mark

class BlockEntryToken(startMark: Mark, endMark: Mark) : Token(startMark, endMark) {
    override val tokenId: ID
        get() = ID.BlockEntry
}

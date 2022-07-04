package org.yaml.snakeyaml.tokens

import org.yaml.snakeyaml.error.Mark

@Deprecated("it will be removed because it is not used")
class WhitespaceToken(startMark: Mark, endMark: Mark) : Token(startMark, endMark) {
    override val tokenId: ID
        get() = ID.Whitespace
}

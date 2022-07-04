package org.yaml.snakeyaml.tokens

import org.yaml.snakeyaml.error.Mark

class TagToken(val value: TagTuple, startMark: Mark, endMark: Mark) : Token(startMark, endMark) {

    override val tokenId: ID
        get() = ID.Tag
}

package org.yaml.snakeyaml.tokens

import org.yaml.snakeyaml.comments.CommentType
import org.yaml.snakeyaml.error.Mark

class CommentToken(type: CommentType, value: String, startMark: Mark, endMark: Mark) : Token(startMark, endMark) {
    val commentType: CommentType
    val value: String

    init {
        commentType = type
        this.value = value
    }

    override val tokenId: ID
        get() = ID.Comment
}

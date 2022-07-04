package org.yaml.snakeyaml.comments

import org.yaml.snakeyaml.error.Mark
import org.yaml.snakeyaml.events.CommentEvent

/**
 * A comment line. May be a block comment, blank line, or inline comment.
 */
class CommentLine(
    val startMark: Mark?, val endMark: Mark?,
    /**
     * Value of this comment.
     *
     * @return comment's value.
     */
    val value: String?, val commentType: CommentType?
) {

    constructor(event: CommentEvent) : this(event.startMark, event.endMark, event.value, event.commentType)

    override fun toString(): String {
        return "<" + (this::class).simpleName + " (type=" + commentType + ", value=" + value + ")>"
    }
}

package org.yaml.snakeyaml.events

import org.yaml.snakeyaml.comments.CommentType
import org.yaml.snakeyaml.error.Mark

/**
 * Marks a comment block value.
 */
class CommentEvent(type: CommentType?, value: String?, startMark: Mark?, endMark: Mark?) : Event(startMark, endMark) {
    /**
     * The comment type.
     *
     * @return the commentType.
     */
    val commentType: CommentType

    /**
     * String representation of the value.
     *
     *
     * Without quotes and escaping.
     *
     *
     * @return Value a comment line string without the leading '#' or a blank line.
     */
    val value: String

    init {
        if (type == null) throw NullPointerException("Event Type must be provided.")
        commentType = type
        if (value == null) throw NullPointerException("Value must be provided.")
        this.value = value
    }

    protected override val arguments: String
        get() = super.arguments + "type=" + commentType + ", value=" + value
    override val eventId: ID
        get() = ID.Comment
}

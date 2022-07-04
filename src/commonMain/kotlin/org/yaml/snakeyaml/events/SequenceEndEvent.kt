package org.yaml.snakeyaml.events

import org.yaml.snakeyaml.error.Mark

/**
 * Marks the end of a sequence.
 *
 * @see SequenceStartEvent
 */
class SequenceEndEvent(startMark: Mark?, endMark: Mark?) : CollectionEndEvent(startMark, endMark) {
    override val eventId: ID
        get() = ID.SequenceEnd
}

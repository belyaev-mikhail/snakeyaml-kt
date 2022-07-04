package org.yaml.snakeyaml.events

import org.yaml.snakeyaml.error.Mark

/**
 * Marks the end of a stream that might have contained multiple documents.
 *
 *
 * This event is the last event that a parser emits. Together with
 * [StreamStartEvent] (which is the first event a parser emits) they mark
 * the beginning and the end of a stream of documents.
 *
 *
 *
 * See [Event] for an exemplary output.
 *
 */
class StreamEndEvent(startMark: Mark?, endMark: Mark?) : Event(startMark, endMark) {
    override val eventId: ID
        get() = ID.StreamEnd
}

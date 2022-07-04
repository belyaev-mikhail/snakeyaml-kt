package org.yaml.snakeyaml.events

import org.yaml.snakeyaml.error.Mark

/**
 * Marks the start of a stream that might contain multiple documents.
 *
 *
 * This event is the first event that a parser emits. Together with
 * [StreamEndEvent] (which is the last event a parser emits) they mark the
 * beginning and the end of a stream of documents.
 *
 *
 *
 * See [Event] for an exemplary output.
 *
 */
class StreamStartEvent(startMark: Mark?, endMark: Mark?) : Event(startMark, endMark) {
    override val eventId: ID
        get() = ID.StreamStart
}

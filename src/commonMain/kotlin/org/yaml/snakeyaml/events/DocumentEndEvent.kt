package org.yaml.snakeyaml.events

import org.yaml.snakeyaml.error.Mark

/**
 * Marks the end of a document.
 *
 *
 * This event follows the document's content.
 *
 */
class DocumentEndEvent(startMark: Mark?, endMark: Mark?, val explicit: Boolean) : Event(startMark, endMark) {

    override val eventId: ID
        get() = ID.DocumentEnd
}

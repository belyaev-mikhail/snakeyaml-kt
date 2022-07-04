package org.yaml.snakeyaml.events

import org.yaml.snakeyaml.error.Mark

/**
 * Marks the end of a mapping node.
 *
 * @see MappingStartEvent
 */
class MappingEndEvent(startMark: Mark?, endMark: Mark?) : CollectionEndEvent(startMark, endMark) {
    override val eventId: ID
        get() = ID.MappingEnd
}

package org.yaml.snakeyaml.events

import org.yaml.snakeyaml.error.Mark

/**
 * Marks the inclusion of a previously anchored node.
 */
class AliasEvent(anchor: String?, startMark: Mark?, endMark: Mark?) : NodeEvent(anchor, startMark, endMark) {
    init {
        if (anchor == null) throw NullPointerException("anchor is not specified for alias")
    }

    override val eventId: ID
        get() = ID.Alias
}

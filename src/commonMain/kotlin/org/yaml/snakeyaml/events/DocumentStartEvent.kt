package org.yaml.snakeyaml.events

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.error.Mark

/**
 * Marks the beginning of a document.
 *
 *
 * This event followed by the document's content and a [DocumentEndEvent].
 *
 */
class DocumentStartEvent(
    startMark: Mark?, endMark: Mark?, val explicit: Boolean,
    /**
     * YAML version the document conforms to.
     *
     * @return `null`if the document has no explicit
     * `%YAML` directive. Otherwise an array with two
     * components, the major and minor part of the version (in this
     * order).
     */
    val version: DumperOptions.Version?,
    val tags: Map<String, String>?
) : Event(startMark, endMark) {
    override val eventId: ID
        get() = ID.DocumentStart
}

package org.yaml.snakeyaml.events

import org.yaml.snakeyaml.error.Mark

/**
 * Base class for the end events of the collection nodes.
 */
abstract class CollectionEndEvent(startMark: Mark?, endMark: Mark?) : Event(startMark, endMark)

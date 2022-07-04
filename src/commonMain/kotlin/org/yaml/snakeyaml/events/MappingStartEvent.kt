package org.yaml.snakeyaml.events

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.error.Mark

/**
 * Marks the beginning of a mapping node.
 *
 *
 * This event is followed by a number of key value pairs. <br></br>
 * The pairs are not in any particular order. However, the value always directly
 * follows the corresponding key. <br></br>
 * After the key value pairs follows a [MappingEndEvent].
 *
 *
 *
 * There must be an even number of node events between the start and end event.
 *
 *
 * @see MappingEndEvent
 */
class MappingStartEvent(
    anchor: String?, tag: String?, implicit: Boolean, startMark: Mark?,
    endMark: Mark?, flowStyle: DumperOptions.FlowStyle?
) : CollectionStartEvent(anchor, tag, implicit, startMark, endMark, flowStyle) {
    /*
     * Existed in older versions but replaced with {@link DumperOptions.FlowStyle}-based constructor.
     * Restored in v1.22 for backwards compatibility.
     * @deprecated Since restored in v1.22.  Use {@link MappingStartEvent#CollectionStartEvent(String, String, boolean, Mark, Mark, org.yaml.snakeyaml.DumperOptions.FlowStyle) }.
     */
    @Deprecated("")
    constructor(
        anchor: String?, tag: String?, implicit: Boolean, startMark: Mark?,
        endMark: Mark?, flowStyle: Boolean?
    ) : this(anchor, tag, implicit, startMark, endMark, DumperOptions.FlowStyle.Companion.fromBoolean(flowStyle)) {
    }

    override val eventId: ID
        get() = ID.MappingStart
}

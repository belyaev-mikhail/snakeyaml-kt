package org.yaml.snakeyaml.events

import org.yaml.snakeyaml.error.Mark

/**
 * Base class for all events that mark the beginning of a node.
 */
abstract class NodeEvent(
    /**
     * Node anchor by which this node might later be referenced by a
     * [AliasEvent].
     *
     *
     * Note that [AliasEvent]s are by it self `NodeEvent`s and
     * use this property to indicate the referenced anchor.
     *
     * @return Anchor of this node or `null` if no anchor is defined.
     */
    val anchor: String?, startMark: Mark?, endMark: Mark?
) : Event(startMark, endMark) {

    protected override val arguments: String
        get() = "anchor=$anchor"
}

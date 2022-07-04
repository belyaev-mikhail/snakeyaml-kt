package org.yaml.snakeyaml.events

import org.yaml.snakeyaml.error.Mark

/**
 * Basic unit of output from a [org.yaml.snakeyaml.parser.Parser] or input
 * of a [org.yaml.snakeyaml.emitter.Emitter].
 */
abstract class Event(val startMark: Mark?, val endMark: Mark?) {
    enum class ID {
        Alias, Comment, DocumentEnd, DocumentStart, MappingEnd, MappingStart, Scalar, SequenceEnd, SequenceStart, StreamEnd, StreamStart
    }

    override fun toString(): String {
        return "<" + (this::class.simpleName) + "(" + arguments + ")>"
    }

    /**
     * Generate human readable representation of the Event
     * @see "__repr__ for Event in PyYAML"
     *
     * @return representation fore humans
     */
    protected open val arguments: String
        protected get() = ""

    /**
     * Check if the Event is of the provided kind
     * @param id - the Event.ID enum
     * @return true then this Event of the provided type
     */
    fun `is`(id: ID): Boolean {
        return eventId == id
    }

    /**
     * Get the type (kind) if this Event
     * @return the ID of this Event
     */
    abstract val eventId: ID

    /*
     * for tests only
     */
    override fun equals(obj: Any?): Boolean {
        return if (obj is Event) {
            toString() == obj.toString()
        } else {
            false
        }
    }

    /*
     * for tests only
     */
    override fun hashCode(): Int {
        return toString().hashCode()
    }
}

/**
 * Copyright (c) 2008, SnakeYAML
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
        return "<" + this.javaClass.name + "(" + arguments + ")>"
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
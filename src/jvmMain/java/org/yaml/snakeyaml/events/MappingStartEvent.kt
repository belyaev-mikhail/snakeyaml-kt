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

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.DumperOptions.FlowStyle.Companion.fromBoolean
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
    ) : this(anchor, tag, implicit, startMark, endMark, fromBoolean(flowStyle)) {
    }

    override val eventId: ID
        get() = ID.MappingStart
}
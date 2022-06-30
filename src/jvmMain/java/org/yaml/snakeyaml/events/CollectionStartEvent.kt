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
 * Base class for the start events of the collection nodes.
 */
abstract class CollectionStartEvent(
    anchor: String?,
    /**
     * Tag of this collection.
     *
     * @return The tag of this collection, or `null` if no explicit
     * tag is available.
     */
    val tag: String?,
    /**
     * `true` if the tag can be omitted while this collection is
     * emitted.
     *
     * @return True if the tag can be omitted while this collection is emitted.
     */
    // The implicit flag of a collection start event indicates if the tag may be
    // omitted when the collection is emitted
    val implicit: Boolean, startMark: Mark?,
    endMark: Mark?, flowStyle: DumperOptions.FlowStyle?
) : NodeEvent(anchor, startMark, endMark) {

    /**
     * `true` if this collection is in flow style, `false`
     * for block style.
     *
     * @return If this collection is in flow style.
     */
    // flag indicates if a collection is block or flow
    val flowStyle: DumperOptions.FlowStyle

    init {
        if (flowStyle == null) throw NullPointerException("Flow style must be provided.")
        this.flowStyle = flowStyle
    }

    /*
     * Existed in older versions but replaced with {@link DumperOptions.FlowStyle}-based constructor.
     * Restored in v1.22 for backwards compatibility.
     * @deprecated Since restored in v1.22.  Use {@link CollectionStartEvent#CollectionStartEvent(String, String, boolean, Mark, Mark, org.yaml.snakeyaml.DumperOptions.FlowStyle) }.
     */
    @Deprecated("")
    constructor(
        anchor: String?, tag: String?, implicit: Boolean, startMark: Mark?,
        endMark: Mark?, flowStyle: Boolean?
    ) : this(anchor, tag, implicit, startMark, endMark, fromBoolean(flowStyle)) {
    }

    protected override val arguments: String
        protected get() = super.arguments + ", tag=" + tag + ", implicit=" + implicit
    val isFlow: Boolean
        get() = DumperOptions.FlowStyle.FLOW === flowStyle
}
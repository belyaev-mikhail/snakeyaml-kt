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
    /**
     * Tag shorthands as defined by the `%TAG` directive.
     *
     * @return Mapping of 'handles' to 'prefixes' (the handles include the '!'
     * characters).
     */
    val tags: Map<String?, String?>?
) : Event(startMark, endMark) {

    override val eventId: ID
        get() = ID.DocumentStart
}
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

import org.yaml.snakeyaml.comments.CommentType
import org.yaml.snakeyaml.error.Mark

/**
 * Marks a comment block value.
 */
class CommentEvent(type: CommentType?, value: String?, startMark: Mark?, endMark: Mark?) : Event(startMark, endMark) {
    /**
     * The comment type.
     *
     * @return the commentType.
     */
    val commentType: CommentType

    /**
     * String representation of the value.
     *
     *
     * Without quotes and escaping.
     *
     *
     * @return Value a comment line string without the leading '#' or a blank line.
     */
    val value: String

    init {
        if (type == null) throw NullPointerException("Event Type must be provided.")
        commentType = type
        if (value == null) throw NullPointerException("Value must be provided.")
        this.value = value
    }

    protected override val arguments: String
        protected get() = super.arguments + "type=" + commentType + ", value=" + value
    override val eventId: ID
        get() = ID.Comment
}
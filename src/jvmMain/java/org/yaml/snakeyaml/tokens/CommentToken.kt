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
package org.yaml.snakeyaml.tokens

import org.yaml.snakeyaml.comments.CommentType
import org.yaml.snakeyaml.error.Mark
import java.util.*

class CommentToken(type: CommentType, value: String?, startMark: Mark?, endMark: Mark?) : Token(startMark, endMark) {
    val commentType: CommentType
    val value: String?

    init {
        Objects.requireNonNull(type)
        commentType = type
        Objects.requireNonNull(value)
        this.value = value
    }

    override val tokenId: ID
        get() = ID.Comment
}
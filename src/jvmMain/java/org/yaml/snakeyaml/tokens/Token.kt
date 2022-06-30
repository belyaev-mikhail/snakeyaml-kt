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

import org.yaml.snakeyaml.error.Mark
import org.yaml.snakeyaml.error.YAMLException

abstract class Token(startMark: Mark?, endMark: Mark?) {
    enum class ID(private val description: String) {
        Alias("<alias>"), Anchor("<anchor>"), BlockEnd("<block end>"), BlockEntry("-"), BlockMappingStart("<block mapping start>"), BlockSequenceStart(
            "<block sequence start>"
        ),
        Directive("<directive>"), DocumentEnd("<document end>"), DocumentStart("<document start>"), FlowEntry(","), FlowMappingEnd(
            "}"
        ),
        FlowMappingStart("{"), FlowSequenceEnd("]"), FlowSequenceStart("["), Key("?"), Scalar("<scalar>"), StreamEnd("<stream end>"), StreamStart(
            "<stream start>"
        ),
        Tag("<tag>"), Value(":"), Whitespace("<whitespace>"), Comment("#"), Error("<error>");

        override fun toString(): String {
            return description
        }
    }

    val startMark: Mark
    val endMark: Mark

    init {
        if (startMark == null || endMark == null) {
            throw YAMLException("Token requires marks.")
        }
        this.startMark = startMark
        this.endMark = endMark
    }

    /**
     * For error reporting.
     *
     * @see "class variable 'id' in PyYAML"
     *
     * @return ID of this token
     */
    abstract val tokenId: ID
}
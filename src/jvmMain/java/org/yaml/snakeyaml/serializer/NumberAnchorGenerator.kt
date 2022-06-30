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
package org.yaml.snakeyaml.serializer

import org.yaml.snakeyaml.nodes.Node
import java.text.NumberFormat

class NumberAnchorGenerator(lastAnchorId: Int) : AnchorGenerator {
    private var lastAnchorId = 0

    init {
        this.lastAnchorId = lastAnchorId
    }

    override fun nextAnchor(node: Node?): String {
        lastAnchorId++
        val format = NumberFormat.getNumberInstance()
        format.minimumIntegerDigits = 3
        format.maximumFractionDigits = 0 // issue 172
        format.isGroupingUsed = false
        val anchorId = format.format(lastAnchorId.toLong())
        return "id$anchorId"
    }
}
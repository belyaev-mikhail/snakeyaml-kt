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
package org.yaml.snakeyaml.nodes

/**
 * Stores one key value pair used in a map.
 */
class NodeTuple(keyNode: Node?, valueNode: Node?) {
    /**
     * Key node.
     *
     * @return the node used as key
     */
    val keyNode: Node

    /**
     * Value node.
     *
     * @return node used as value
     */
    val valueNode: Node

    init {
        if (keyNode == null || valueNode == null) {
            throw NullPointerException("Nodes must be provided.")
        }
        this.keyNode = keyNode
        this.valueNode = valueNode
    }

    override fun toString(): String {
        return ("<NodeTuple keyNode=" + keyNode.toString() + "; valueNode=" + valueNode.toString()
                + ">")
    }
}
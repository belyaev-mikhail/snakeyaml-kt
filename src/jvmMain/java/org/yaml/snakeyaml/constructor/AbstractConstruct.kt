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
package org.yaml.snakeyaml.constructor

import org.yaml.snakeyaml.error.YAMLException
import org.yaml.snakeyaml.nodes.Node

/**
 * Because recursive structures are not very common we provide a way to save
 * some typing when extending a constructor
 */
abstract class AbstractConstruct : Construct {
    /**
     * Fail with a reminder to provide the seconds step for a recursive
     * structure
     *
     * @see org.yaml.snakeyaml.constructor.Construct.construct2ndStep
     */
    override fun construct2ndStep(node: Node, data: Any) {
        check(!node.isTwoStepsConstruction) { "Not Implemented in " + javaClass.name }
        throw YAMLException("Unexpected recursive structure for Node: $node")
    }
}

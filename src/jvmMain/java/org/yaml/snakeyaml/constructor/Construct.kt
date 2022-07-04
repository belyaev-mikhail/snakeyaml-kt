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

import org.yaml.snakeyaml.nodes.Node

/**
 * Provide a way to construct a Java instance out of the composed Node. Support
 * recursive objects if it is required. (create Native Data Structure out of
 * Node Graph)
 *
 * @see [Chapter 3. Processing YAML
 * Information](http://yaml.org/spec/1.1/.id859109)
 */
interface Construct {
    /**
     * Construct a Java instance with all the properties injected when it is
     * possible.
     *
     * @param node
     * composed Node
     * @return a complete Java instance
     */
    fun construct(node: Node?): Any?

    /**
     * Apply the second step when constructing recursive structures. Because the
     * instance is already created it can assign a reference to itself.
     *
     * @param node
     * composed Node
     * @param object
     * the instance constructed earlier by
     * `construct(Node node)` for the provided Node
     */
    fun construct2ndStep(node: Node, `object`: Any)
}

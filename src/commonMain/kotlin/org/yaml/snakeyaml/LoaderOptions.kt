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
package org.yaml.snakeyaml

class LoaderOptions {
    /**
     * Allow/Reject duplicate map keys in the YAML file.
     *
     * Default is to allow.
     *
     * YAML 1.1 is slightly vague around duplicate entries in the YAML file. The
     * best reference is [
 * 3.2.1.3. Nodes Comparison](http://www.yaml.org/spec/1.1/#id862121) where it hints that a duplicate map key is
     * an error.
     *
     * For future reference, YAML spec 1.2 is clear. The keys MUST be unique.
     * [1.3. Relation
 * to JSON](http://www.yaml.org/spec/1.2/spec.html#id2759572)
     * @param allowDuplicateKeys false to reject duplicate mapping keys
     */
    var isAllowDuplicateKeys = true

    /**
     * Wrap runtime exception to YAMLException during parsing or leave them as they are
     *
     * Default is to leave original exceptions
     *
     * @param wrappedToRootException - true to convert runtime exception to YAMLException
     */
    var isWrappedToRootException = false

    /**
     * Restrict the amount of aliases for collections (sequences and mappings)
     * to avoid https://en.wikipedia.org/wiki/Billion_laughs_attack
     * @param maxAliasesForCollections set max allowed value (50 by default)
     */
    var maxAliasesForCollections = 50 //to prevent YAML at https://en.wikipedia.org/wiki/Billion_laughs_attack

    /**
     * Allow recursive keys for mappings. By default, it is not allowed.
     * This setting only prevents the case when the key is the value. If the key is only a part of the value
     * (the value is a sequence or a mapping) then this case is not recognized and always allowed.
     * @param allowRecursiveKeys - false to disable recursive keys
     */
    var allowRecursiveKeys = false

    /**
     * Set the comment processing. By default, comments are ignored.
     *
     * @param processComments `true` to process; `false` to ignore
     */
    var isProcessComments = false

    /**
     * Disables or enables case sensitivity during construct enum constant from string value
     * Default is false.
     *
     * @param enumCaseSensitive - true to set enum case sensitive, false the reverse
     */
    var isEnumCaseSensitive = true

    /**
     * Set max depth of nested collections. When the limit is exceeded an exception is thrown.
     * Aliases/Anchors are not counted.
     * This is to prevent a DoS attack
     * @param nestingDepthLimit - depth to be accepted (50 by default)
     */
    var nestingDepthLimit = 50
}

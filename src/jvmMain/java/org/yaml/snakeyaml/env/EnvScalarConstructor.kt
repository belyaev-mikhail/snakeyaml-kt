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
package org.yaml.snakeyaml.env

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.TypeDescription
import org.yaml.snakeyaml.constructor.AbstractConstruct
import org.yaml.snakeyaml.constructor.Constructor
import org.yaml.snakeyaml.error.MissingEnvironmentVariableException
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.ScalarNode
import org.yaml.snakeyaml.nodes.Tag

/**
 * Construct scalar for format ${VARIABLE} replacing the template with the value from environment.
 * It can also be used to create JavaBeans when the all the arguments are provided.
 * @see [Variable substitution](https://bitbucket.org/snakeyaml/snakeyaml/wiki/Variable%20substitution)
 *
 * @see [Variable substitution](https://docs.docker.com/compose/compose-file/.variable-substitution)
 */
open class EnvScalarConstructor : Constructor {
    /**
     * For simple cases when no JavaBeans are needed
     */
    constructor() : super() {
        yamlConstructors[ENV_TAG] =
            ConstructEnv()
    }

    /**
     * Create EnvScalarConstructor which can create JavaBeans with variable substitution
     * @param theRoot - the class (usually JavaBean) to be constructed
     * @param moreTDs - collection of classes used by the root class
     * @param loadingConfig - configuration
     */
    constructor(
        theRoot: TypeDescription?,
        moreTDs: Collection<TypeDescription?>?,
        loadingConfig: LoaderOptions
    ) : super(theRoot, moreTDs, loadingConfig) {
        yamlConstructors[ENV_TAG] =
            ConstructEnv()
    }

    private inner class ConstructEnv : AbstractConstruct() {
        override fun construct(node: Node?): Any {
            val `val` = constructScalar((node as ScalarNode))!!
            val matcher = ENV_FORMAT.matchEntire(`val`)
            matcher!!
            val name = matcher.groups["name"]?.value!!
            val value = matcher.groups["value"]?.value
            val separator = matcher.groups["separator"]?.value
            return apply(name, separator, value ?: "", getEnv(name))
        }
    }

    /**
     * Implement the logic for missing and unset variables
     *
     * @param name        - variable name in the template
     * @param separator   - separator in the template, can be :-, -, :?, ?
     * @param value       - default value or the error in the template
     * @param environment - the value from environment for the provided variable
     * @return the value to apply in the template
     */
    fun apply(name: String, separator: String?, value: String, environment: String?): String {
        if (environment != null && !environment.isEmpty()) return environment
        // variable is either unset or empty
        if (separator != null) {
            //there is a default value or error
            if (separator == "?") {
                if (environment == null) throw MissingEnvironmentVariableException("Missing mandatory variable $name: $value")
            }
            if (separator == ":?") {
                if (environment == null) throw MissingEnvironmentVariableException("Missing mandatory variable $name: $value")
                if (environment.isEmpty()) throw MissingEnvironmentVariableException("Empty mandatory variable $name: $value")
            }
            if (separator.startsWith(":")) {
                if (environment == null || environment.isEmpty()) return value
            } else {
                if (environment == null) return value
            }
        }
        return ""
    }

    /**
     * Get value of the environment variable
     *
     * @param key - the name of the variable
     * @return value or null if not set
     */
    open fun getEnv(key: String?): String? {
        return System.getenv(key)
    }

    companion object {
        @JvmField
        val ENV_TAG = Tag("!ENV")

        // name must be a word -> \w+
        // value can be any non-space -> \S+
        @JvmField
        val ENV_FORMAT = Regex("^\\$\\{\\s*((?<name>\\w+)((?<separator>:?(-|\\?))(?<value>\\S+)?)?)\\s*\\}$")
    }
}

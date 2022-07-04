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
package org.yaml.snakeyaml.util

object EnumUtils {
    /**
     * Looks for an enumeration constant that matches the string without being case sensitive
     *
     * @param enumType - the Class object of the enum type from which to return a constant
     * @param name     - the name of the constant to return
     * @param <T>      - the enum type whose constant is to be returned
     * @return the enum constant of the specified enum type with the specified name, insensitive to case
     * @throws IllegalArgumentException – if the specified enum type has no constant with the specified name, insensitive case
    </T> */
    fun <T : Enum<T>?> findEnumInsensitiveCase(enumType: Class<T>, name: String): T {
        for (constant in enumType.enumConstants) {
            if (constant!!.name.compareTo(name, ignoreCase = true) == 0) {
                return constant
            }
        }
        throw IllegalArgumentException("No enum constant " + enumType.canonicalName + "." + name)
    }
}

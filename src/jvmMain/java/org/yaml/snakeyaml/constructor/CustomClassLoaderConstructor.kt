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

/**
 * Construct instances with a custom Class Loader.
 */
class CustomClassLoaderConstructor(theRoot: Class<out Any?>, theLoader: ClassLoader?) : Constructor(theRoot) {
    private var loader = CustomClassLoaderConstructor::class.javaObjectType.classLoader

    constructor(cLoader: ClassLoader?) : this(Any::class.javaObjectType, cLoader) {}

    init {
        if (theLoader == null) {
            throw NullPointerException("Loader must be provided.")
        }
        loader = theLoader
    }

    @Throws(ClassNotFoundException::class)
    override fun getClassForName(name: String): Class<*> {
        return Class.forName(name, true, loader)
    }
}

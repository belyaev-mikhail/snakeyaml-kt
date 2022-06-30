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
package org.yaml.snakeyaml.introspector

/**
 * A property that does not map to a real property; this is used when [ ].setSkipMissingProperties(boolean) is set to true.
 */
class MissingProperty(name: String?) : Property(name, Any::class.java) {
    override val actualTypeArguments: Array<Class<*>?>?
        get() = arrayOfNulls(0)

    /**
     * Setter does nothing.
     */
    @Throws(Exception::class)
    override fun set(`object`: Any?, value: Any?) {
    }

    override fun get(`object`: Any?): Any? {
        return `object`
    }

    override val annotations: List<Annotation?>?
        get() = emptyList<Annotation>()

    override fun <A : Annotation?> getAnnotation(annotationType: Class<A>?): A? {
        return null
    }
}
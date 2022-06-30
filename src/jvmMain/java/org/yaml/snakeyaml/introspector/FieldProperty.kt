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

import org.yaml.snakeyaml.error.YAMLException
import org.yaml.snakeyaml.util.ArrayUtils
import java.lang.reflect.Field

/**
 *
 *
 * A `FieldProperty` is a `Property` which is accessed as
 * a field, without going through accessor methods (setX, getX). The field may
 * have any scope (public, package, protected, private).
 *
 */
class FieldProperty(private val field: Field) : GenericProperty(
    field.name, field.type, field.genericType
) {
    init {
        field.isAccessible = true
    }

    @Throws(Exception::class)
    override fun set(`object`: Any?, value: Any?) {
        field[`object`] = value
    }

    override fun get(`object`: Any?): Any? {
        return try {
            field[`object`]
        } catch (e: Exception) {
            throw YAMLException(
                "Unable to access field " + field.name + " on object "
                        + `object` + " : " + e
            )
        }
    }

    override val annotations: List<Annotation?>?
        get() = ArrayUtils.toUnmodifiableList(field.getAnnotations())

    override fun <A : Annotation?> getAnnotation(annotationType: Class<A>?): A? {
        return field.getAnnotation(annotationType)
    }
}
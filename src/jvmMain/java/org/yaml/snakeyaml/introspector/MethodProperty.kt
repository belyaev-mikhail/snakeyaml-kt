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
import java.beans.PropertyDescriptor
import java.lang.reflect.Type

/**
 *
 *
 * A `MethodProperty` is a `Property` which is accessed
 * through accessor methods (setX, getX). It is possible to have a
 * `MethodProperty` which has only setter, only getter, or both. It
 * is not possible to have a `MethodProperty` which has neither
 * setter nor getter.
 *
 */
class MethodProperty(private val property: PropertyDescriptor) : GenericProperty(
    property.name, property.propertyType,
    discoverGenericType(property)
) {
    override val isReadable: Boolean
    override val isWritable: Boolean

    init {
        isReadable = property.readMethod != null
        isWritable = property.writeMethod != null
    }

    @Throws(Exception::class)
    override fun set(`object`: Any, value: Any?) {
        if (!isWritable) {
            throw YAMLException(
                "No writable property '" + name + "' on class: "
                        + `object`.javaClass.name
            )
        }
        property.writeMethod.invoke(`object`, value)
    }

    override fun get(`object`: Any): Any? {
        return try {
            property.readMethod.isAccessible = true // issue 50
            property.readMethod.invoke(`object`)
        } catch (e: Exception) {
            throw YAMLException(
                "Unable to find getter for property '" + property.name
                        + "' on object " + `object` + ":" + e
            )
        }
    }

    /**
     * Returns the annotations that are present on read and write methods of this property or empty `List` if
     * there're no annotations.
     *
     * @return the annotations that are present on this property or empty `List` if there're no annotations
     */
    override val annotations: List<Annotation?>
        get() {
            val annotations: List<Annotation?>
            annotations = if (isReadable && isWritable) {
                ArrayUtils.toUnmodifiableCompositeList(
                    property.readMethod.annotations,
                    property.writeMethod.annotations
                )
            } else if (isReadable) {
                ArrayUtils.toUnmodifiableList(property.readMethod.annotations)
            } else {
                ArrayUtils.toUnmodifiableList(property.writeMethod.annotations)
            }
            return annotations
        }

    /**
     * Returns property's annotation for the given type or `null` if it's not present. If the annotation is present
     * on both read and write methods, the annotation on read method takes precedence.
     *
     * @param annotationType the type of the annotation to be returned
     * @return property's annotation for the given type or `null` if it's not present
     */
    override fun <A : Annotation?> getAnnotation(annotationType: Class<A>): A? {
        var annotation: A? = null
        if (isReadable) {
            annotation = property.readMethod.getAnnotation(annotationType)
        }
        if (annotation == null && isWritable) {
            annotation = property.writeMethod.getAnnotation(annotationType)
        }
        return annotation
    }

    companion object {
        private fun discoverGenericType(property: PropertyDescriptor): Type? {
            val readMethod = property.readMethod
            if (readMethod != null) {
                return readMethod.genericReturnType
            }
            val writeMethod = property.writeMethod
            if (writeMethod != null) {
                val paramTypes = writeMethod.genericParameterTypes
                if (paramTypes.size > 0) {
                    return paramTypes[0]
                }
            }
            /*
         * This actually may happen if PropertyDescriptor is of type
         * IndexedPropertyDescriptor and it has only IndexedGetter/Setter. ATM
         * we simply skip type discovery.
         */return null
        }
    }
}

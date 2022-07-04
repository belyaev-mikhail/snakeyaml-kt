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
 *
 *
 * A `Property` represents a single member variable of a class,
 * possibly including its accessor methods (getX, setX). The name stored in this
 * class is the actual name of the property as given for the class, not an
 * alias.
 *
 *
 *
 *
 * Objects of this class have a total ordering which defaults to ordering based
 * on the name of the property.
 *
 */
abstract class Property(open val name: String?, open val type: Class<*>?) : Comparable<Property> {

    abstract val actualTypeArguments: Array<Class<*>>?
    override fun toString(): String {
        return name + " of " + type
    }

    override fun compareTo(o: Property): Int {
        return name!!.compareTo(o.name!!)
    }

    open val isWritable: Boolean
        get() = true
    open val isReadable: Boolean
        get() = true

    @Throws(Exception::class)
    abstract operator fun set(`object`: Any, value: Any?)
    abstract operator fun get(`object`: Any): Any?

    /**
     * Returns the annotations that are present on this property or empty `List` if there're no annotations.
     *
     * @return the annotations that are present on this property or empty `List` if there're no annotations
     */
    abstract val annotations: List<Annotation?>

    /**
     * Returns property's annotation for the given type or `null` if it's not present.
     *
     * @param annotationType the type of the annotation to be returned
     * @param <A> class of the annotation
     *
     * @return property's annotation for the given type or `null` if it's not present
    </A> */
    abstract fun <A : Annotation?> getAnnotation(annotationType: Class<A>): A?
    override fun hashCode(): Int {
        return name.hashCode() + type.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other is Property) {
            val p = other
            return name == p.name && type == p.type
        }
        return false
    }
}

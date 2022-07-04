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

import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

abstract class GenericProperty(name: String?, aClass: Class<*>?, private val genType: Type?) : Property(name, aClass) {
    private var actualClassesChecked: Boolean
    private var actualClasses: Array<Class<*>>? = null

    init {
        actualClassesChecked = genType == null
    }// XXX this check is only

    // required for IcedTea6
    // should we synchronize here ?
    override val actualTypeArguments: Array<Class<*>>?
        get() { // should we synchronize here ?
            if (!actualClassesChecked) {
                if (genType is ParameterizedType) {
                    val actualTypeArguments = genType.actualTypeArguments
                    if (actualTypeArguments.size > 0) {
                        actualClasses = arrayOfNulls<Class<*>>(actualTypeArguments.size) as Array<Class<*>>
                        for (i in actualTypeArguments.indices) {
                            if (actualTypeArguments[i] is Class<*>) {
                                actualClasses!![i] = actualTypeArguments[i] as Class<*>
                            } else if (actualTypeArguments[i] is ParameterizedType) {
                                actualClasses!![i] = (actualTypeArguments[i] as ParameterizedType)
                                    .rawType as Class<*>
                            } else if (actualTypeArguments[i] is GenericArrayType) {
                                val componentType = (actualTypeArguments[i] as GenericArrayType)
                                    .genericComponentType
                                if (componentType is Class<*>) {
                                    actualClasses!![i] = java.lang.reflect.Array.newInstance(componentType, 0)
                                        .javaClass
                                } else {
                                    actualClasses = null
                                    break
                                }
                            } else {
                                actualClasses = null
                                break
                            }
                        }
                    }
                } else if (genType is GenericArrayType) {
                    val componentType = genType.genericComponentType
                    if (componentType is Class<*>) {
                        actualClasses = arrayOf(componentType)
                    }
                } else if (genType is Class<*>) { // XXX this check is only
                    // required for IcedTea6
                    if (genType.isArray) {
                        actualClasses = arrayOfNulls<Class<*>>(1) as Array<Class<*>>
                        actualClasses!![0] = type!!.componentType
                    }
                }
                actualClassesChecked = true
            }
            return actualClasses
        }
}

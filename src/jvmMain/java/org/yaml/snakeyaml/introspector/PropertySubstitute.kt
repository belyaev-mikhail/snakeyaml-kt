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
import java.lang.reflect.*
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

// TODO: decide priorities for get/set Read/Field/Delegate Write/Field/Delegate - is FIELD on the correct place ?
class PropertySubstitute(
    name: String?, type: Class<*>?, private val readMethod: String?, private val writeMethod: String?,
    vararg params: Class<*>
) : Property(name, type) {
    var targetType: Class<*>? = null
        set(targetType: Class<*>?) {
            if (field != targetType) {
                field = targetType
                val name = this.name
                var c: Class<*>? = targetType
                while (c != null) {
                    for (f in c.declaredFields) {
                        if (f.name == name) {
                            val modifiers = f.modifiers
                            if (!Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers)) {
                                f.isAccessible = true
                                this.field = f
                            }
                            break
                        }
                    }
                    c = c.superclass
                }
                if (this.field == null && log.isLoggable(Level.FINE)) {
                    log.fine(
                        String.format(
                            "Failed to find field for %s.%s", targetType!!.name,
                            this.name
                        )
                    )
                }

                // Retrieve needed info
                if (readMethod != null) {
                    read = discoverMethod(targetType, readMethod)
                }
                if (writeMethod != null) {
                    filler = false
                    write = discoverMethod(targetType, writeMethod, this.type!!)
                    if (write == null && parameters != null) {
                        filler = true
                        write = discoverMethod(targetType, writeMethod, *(parameters!!))
                    }
                }
            }
        }

    @Transient
    private var read: Method? = null

    @Transient
    private var write: Method? = null
    private var field: Field? = null
    protected var parameters: Array<Class<*>>? = null

    internal var delegate: Property? = null
        set(delegate: Property?) {
            field = delegate
            if (writeMethod != null && write == null && !filler) {
                filler = true
                write = discoverMethod(targetType, writeMethod, *(actualTypeArguments!!))
            }
        }

    private var filler: Boolean

    override var actualTypeArguments: Array<Class<*>>?
        get() = if (parameters == null && delegate != null) {
            delegate!!.actualTypeArguments
        } else parameters
        set(args: Array<Class<*>>?) {
            if (args != null && args.size > 0) {
                parameters = args
            } else {
                parameters = null
            }
        }

    init {
        actualTypeArguments = params as Array<Class<*>>
        filler = false
    }

    constructor(name: String?, type: Class<*>?, vararg params: Class<*>) :
            this(name, type, null as String?, null as String?, *params) {}

    @Throws(Exception::class)
    override fun set(`object`: Any, value: Any?) {
        if (write != null) {
            if (!filler) {
                write!!.invoke(`object`, value)
            } else if (value != null) {
                if (value is Collection<*>) {
                    for (`val` in value) {
                        write!!.invoke(`object`, `val`)
                    }
                } else if (value is Map<*, *>) {
                    for ((key, value1) in value) {
                        write!!.invoke(`object`, key, value1)
                    }
                } else if (value.javaClass.isArray) { // TODO: maybe arrays
                    // need 2 fillers like
                    // SET(index, value)
                    // add ADD(value)
                    val len = java.lang.reflect.Array.getLength(value)
                    for (i in 0 until len) {
                        write!!.invoke(`object`, java.lang.reflect.Array.get(value, i))
                    }
                }
            }
        } else if (field != null) {
            field!![`object`] = value
        } else if (delegate != null) {
            delegate!![`object`] = value
        } else {
            log.warning("No setter/delegate for '" + this.name + "' on object " + `object`)
        }
        // TODO: maybe throw YAMLException here
    }

    override fun get(`object`: Any): Any? {
        try {
            if (read != null) {
                return read!!.invoke(`object`)
            } else if (field != null) {
                return field!![`object`]
            }
        } catch (e: Exception) {
            throw YAMLException(
                "Unable to find getter for property '" + this.name
                        + "' on object " + `object` + ":" + e
            )
        }
        if (delegate != null) {
            return delegate!![`object`]
        }
        throw YAMLException(
            "No getter or delegate for property '" + this.name + "' on object "
                    + `object`
        )
    }

    override val annotations: List<Annotation?>
        get() {
            var annotations: Array<Annotation?>? = null
            if (read != null) {
                annotations = read!!.annotations
            } else if (this.field != null) {
                annotations = this.field!!.annotations
            }
            return if (annotations != null) Arrays.asList(*annotations) else delegate!!.annotations
        }

    override fun <A : Annotation?> getAnnotation(annotationType: Class<A>): A? {
        val annotation: A?
        annotation = if (read != null) {
            read!!.getAnnotation(annotationType)
        } else if (field != null) {
            field!!.getAnnotation(annotationType)
        } else {
            delegate!!.getAnnotation(annotationType)
        }
        return annotation
    }



    private fun discoverMethod(type: Class<*>?, name: String, vararg params: Class<*>): Method? {
        var c = type
        while (c != null) {
            for (method in c.declaredMethods) {
                if (name == method.name) {
                    val parameterTypes = method.parameterTypes
                    if (parameterTypes.size != params.size) {
                        continue
                    }
                    var found = true
                    for (i in parameterTypes.indices) {
                        if (!parameterTypes[i].isAssignableFrom(params[i])) {
                            found = false
                        }
                    }
                    if (found) {
                        method.isAccessible = true
                        return method
                    }
                }
            }
            c = c.superclass
        }
        if (log.isLoggable(Level.FINE)) {
            log.fine(
                String.format(
                    "Failed to find [%s(%d args)] for %s.%s", name, params.size,
                    targetType!!.name, this.name
                )
            )
        }
        return null
    }

    override val name: String?
        get() {
            val n = super.name
            if (n != null) {
                return n
            }
            return if (delegate != null) delegate!!.name else null
        }
    override val type: Class<*>?
        get() {
            val t = super.type
            if (t != null) {
                return t
            }
            return if (delegate != null) delegate!!.type else null
        }
    override val isReadable: Boolean
        get() = read != null || this.field != null || delegate != null && delegate!!.isReadable
    override val isWritable: Boolean
        get() = write != null || this.field != null || delegate != null && delegate!!.isWritable



    companion object {
        private val log = Logger.getLogger(
            PropertySubstitute::class.javaObjectType.getPackage()
                .name
        )
    }
}

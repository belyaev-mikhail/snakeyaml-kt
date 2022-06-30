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
import org.yaml.snakeyaml.util.PlatformFeatureDetector
import java.beans.FeatureDescriptor
import java.beans.IntrospectionException
import java.beans.Introspector
import java.lang.reflect.Modifier
import java.util.*

class PropertyUtils internal constructor(private val platformFeatureDetector: PlatformFeatureDetector) {
    private val propertiesCache: MutableMap<Class<*>, Map<String?, Property>> = HashMap()
    private val readableProperties: MutableMap<Class<*>, MutableSet<Property>> = HashMap()
    private var beanAccess = BeanAccess.DEFAULT
    private var allowReadOnlyProperties = false
    private var skipMissingProperties = false

    constructor() : this(PlatformFeatureDetector()) {}

    protected fun getPropertiesMap(type: Class<*>, bAccess: BeanAccess?): Map<String?, Property> {
        if (propertiesCache.containsKey(type)) {
            return propertiesCache[type]!!
        }
        val properties: MutableMap<String?, Property> = LinkedHashMap()
        var inaccessableFieldsExist = false
        when (bAccess) {
            BeanAccess.FIELD -> {
                var c: Class<*>? = type
                while (c != null) {
                    for (field in c.declaredFields) {
                        val modifiers = field.modifiers
                        if (!Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers)
                            && !properties.containsKey(field.name)
                        ) {
                            properties[field.name] = FieldProperty(field)
                        }
                    }
                    c = c.superclass
                }
            }
            else -> {
                // add JavaBean properties
                try {
                    for (property in Introspector.getBeanInfo(type)
                        .propertyDescriptors) {
                        val readMethod = property.readMethod
                        if ((readMethod == null || readMethod.name != "getClass")
                            && !isTransient(property)
                        ) {
                            properties[property.name] = MethodProperty(property)
                        }
                    }
                } catch (e: IntrospectionException) {
                    throw YAMLException(e)
                }

                // add public fields
                var c: Class<*>? = type
                while (c != null) {
                    for (field in c.declaredFields) {
                        val modifiers = field.modifiers
                        if (!Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers)) {
                            if (Modifier.isPublic(modifiers)) {
                                properties[field.name] = FieldProperty(field)
                            } else {
                                inaccessableFieldsExist = true
                            }
                        }
                    }
                    c = c.superclass
                }
            }
        }
        if (properties.isEmpty() && inaccessableFieldsExist) {
            throw YAMLException("No JavaBean properties found in " + type.name)
        }
        propertiesCache[type] = properties
        return properties
    }

    init {

        /*
         * Android lacks much of java.beans (including the Introspector class, used here), because java.beans classes tend to rely on java.awt, which isn't
         * supported in the Android SDK. That means we have to fall back on FIELD access only when SnakeYAML is running on the Android Runtime.
         */if (platformFeatureDetector.isRunningOnAndroid) {
            beanAccess = BeanAccess.FIELD
        }
    }

    private fun isTransient(fd: FeatureDescriptor): Boolean {
        return java.lang.Boolean.TRUE == fd.getValue(TRANSIENT)
    }

    fun getProperties(type: Class<out Any>): MutableSet<Property> {
        return getProperties(type, beanAccess)
    }

    fun getProperties(type: Class<out Any>, bAccess: BeanAccess?): MutableSet<Property> {
        if (readableProperties.containsKey(type)) {
            return readableProperties[type]!!
        }
        val properties = createPropertySet(type, bAccess)
        readableProperties[type] = properties
        return properties
    }

    protected fun createPropertySet(type: Class<out Any>, bAccess: BeanAccess?): MutableSet<Property> {
        val properties: MutableSet<Property> = TreeSet()
        val props = getPropertiesMap(type, bAccess).values
        for (property in props) {
            if (property.isReadable && (allowReadOnlyProperties || property.isWritable)) {
                properties.add(property)
            }
        }
        return properties
    }

    fun getProperty(type: Class<out Any?>, name: String?): Property {
        return getProperty(type, name, beanAccess)
    }

    fun getProperty(type: Class<out Any?>, name: String?, bAccess: BeanAccess?): Property {
        val properties = getPropertiesMap(type, bAccess)
        var property = properties[name]
        if (property == null && skipMissingProperties) {
            property = MissingProperty(name)
        }
        if (property == null) {
            throw YAMLException(
                "Unable to find property '" + name + "' on class: " + type.name
            )
        }
        return property
    }

    fun setBeanAccess(beanAccess: BeanAccess) {
        require(!(platformFeatureDetector.isRunningOnAndroid && beanAccess != BeanAccess.FIELD)) { "JVM is Android - only BeanAccess.FIELD is available" }
        if (this.beanAccess != beanAccess) {
            this.beanAccess = beanAccess
            propertiesCache.clear()
            readableProperties.clear()
        }
    }

    fun setAllowReadOnlyProperties(allowReadOnlyProperties: Boolean) {
        if (this.allowReadOnlyProperties != allowReadOnlyProperties) {
            this.allowReadOnlyProperties = allowReadOnlyProperties
            readableProperties.clear()
        }
    }

    fun isAllowReadOnlyProperties(): Boolean {
        return allowReadOnlyProperties
    }

    /**
     * Skip properties that are missing during deserialization of YAML to a Java
     * object. The default is false.
     *
     * @param skipMissingProperties
     * true if missing properties should be skipped, false otherwise.
     */
    fun setSkipMissingProperties(skipMissingProperties: Boolean) {
        if (this.skipMissingProperties != skipMissingProperties) {
            this.skipMissingProperties = skipMissingProperties
            readableProperties.clear()
        }
    }

    fun isSkipMissingProperties(): Boolean {
        return skipMissingProperties
    }

    companion object {
        private const val TRANSIENT = "transient"
    }
}
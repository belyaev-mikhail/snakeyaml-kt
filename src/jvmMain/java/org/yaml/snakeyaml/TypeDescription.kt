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
package org.yaml.snakeyaml

import org.yaml.snakeyaml.error.YAMLException
import org.yaml.snakeyaml.introspector.BeanAccess
import org.yaml.snakeyaml.introspector.Property
import org.yaml.snakeyaml.introspector.PropertySubstitute
import org.yaml.snakeyaml.introspector.PropertyUtils
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.Tag
import java.util.*
import java.util.logging.Logger

/**
 * Provides additional runtime information necessary to create a custom Java
 * instance.
 *
 * In general this class is thread-safe and can be used as a singleton, the only
 * exception being the PropertyUtils field. A singleton PropertyUtils should be
 * constructed and shared between all YAML Constructors used if a singleton
 * TypeDescription is used, since Constructor sets its propertyUtils to the
 * TypeDescription that is passed to it, hence you may end up in a situation
 * when propertyUtils in TypeDescription is from different Constructor.
 */
open class TypeDescription @JvmOverloads constructor(
    /**
     * Get represented type (class)
     *
     * @return type (class) to be described.
     */
    val type: Class<out Any?>, var tag: Tag = Tag(
        type
    ), // class that implements the described type; if set, will be used as a source for constructor.
    // If not set - TypeDescription will leave instantiation of an entity to the YAML Constructor
    private var impl: Class<*>? = null
) {
    @Transient
    private var dumpProperties: MutableSet<Property>? = null

    @Transient
    var propertyUtils: PropertyUtils? = null

    @Transient
    private var delegatesChecked = false
    private var properties: MutableMap<String?, PropertySubstitute> = Collections.emptyMap()
    var excludes: MutableSet<String> = mutableSetOf<String>()
        protected set
    var includes: Array<String>? = null
        set(propNames) {
            field = if (propNames != null && propNames.size > 0) propNames else null
        }

    protected var beanAccess: BeanAccess? = null

    constructor(clazz: Class<out Any?>, tag: String?) : this(clazz, Tag(tag), null) {}
    constructor(clazz: Class<out Any?>, impl: Class<*>?) : this(clazz, Tag(clazz), impl) {}


    /**
     * Set tag to be used to dump the type (class).
     *
     * @param tag - local or global tag
     */
    @Deprecated("it will be removed because it is not used")
    fun setTag(tag: String?) {
        this.tag = Tag(tag)
    }

    /**
     * Specify that the property is a type-safe `List`.
     *
     * @param property
     * name of the JavaBean property
     * @param type
     * class of List values
     */
    @Deprecated("")
    fun putListPropertyType(property: String?, type: Class<out Any?>) {
        addPropertyParameters(property, type)
    }

    /**
     * Get class of List values for provided JavaBean property.
     *
     * @param property
     * property name
     * @return class of List values
     */
    @Deprecated("")
    fun getListPropertyType(property: String?): Class<out Any>? {
        if (properties.containsKey(property)) {
            val typeArguments = properties[property]!!.actualTypeArguments
            if (typeArguments != null && typeArguments.size > 0) {
                return typeArguments[0]
            }
        }
        return null
    }

    /**
     * Specify that the property is a type-safe `Map`.
     *
     * @param property
     * property name of this JavaBean
     * @param key
     * class of keys in Map
     * @param value
     * class of values in Map
     */
    @Deprecated("")
    fun putMapPropertyType(
        property: String?, key: Class<out Any?>,
        value: Class<out Any?>
    ) {
        addPropertyParameters(property, key, value)
    }

    /**
     * Get keys type info for this JavaBean
     *
     * @param property
     * property name of this JavaBean
     * @return class of keys in the Map
     */
    @Deprecated("")
    fun getMapKeyType(property: String?): Class<out Any>? {
        if (properties.containsKey(property)) {
            val typeArguments = properties[property]!!.actualTypeArguments
            if (typeArguments != null && typeArguments.size > 0) {
                return typeArguments[0]
            }
        }
        return null
    }

    /**
     * Get values type info for this JavaBean
     *
     * @param property
     * property name of this JavaBean
     * @return class of values in the Map
     */
    @Deprecated("")
    fun getMapValueType(property: String?): Class<out Any>? {
        if (properties.containsKey(property)) {
            val typeArguments = properties[property]!!.actualTypeArguments
            if (typeArguments != null && typeArguments.size > 1) {
                return typeArguments[1]
            }
        }
        return null
    }

    /**
     * Adds new substitute for property `pName` parameterized by
     * `classes` to this `TypeDescription`. If
     * `pName` has been added before - updates parameters with
     * `classes`.
     *
     * @param pName - parameter name
     * @param classes - parameterized by
     */
    fun addPropertyParameters(pName: String?, vararg classes: Class<*>) {
        if (!properties.containsKey(pName)) {
            substituteProperty(pName, null, null, null, *classes)
        } else {
            val pr = properties[pName]
            pr!!.actualTypeArguments = arrayOf(*classes)
        }
    }

    override fun toString(): String {
        return "TypeDescription for " + type + " (tag='" + tag + "')"
    }

    private fun checkDelegates() {
        val values = properties.values
        for (p in values) {
            try {
                p.delegate = discoverProperty(p.name)
            } catch (e: YAMLException) {
            }
        }
        delegatesChecked = true
    }

    private fun discoverProperty(name: String?): Property? {
        return if (propertyUtils != null) {
            if (beanAccess == null) {
                propertyUtils!!.getProperty(type, name)
            } else propertyUtils!!.getProperty(type, name, beanAccess!!)
        } else null
    }

    fun getProperty(name: String?): Property? {
        if (!delegatesChecked) {
            checkDelegates()
        }
        return if (properties.containsKey(name)) properties[name]!! else discoverProperty(name)
    }

    /**
     * Adds property substitute for `pName`
     *
     * @param pName
     * property name
     * @param pType
     * property type
     * @param getter
     * method name for getter
     * @param setter
     * method name for setter
     * @param argParams
     * actual types for parameterized type (List&lt;?&gt;, Map&lt;?&gt;)
     */
    fun substituteProperty(
        pName: String?, pType: Class<*>?, getter: String?, setter: String?,
        vararg argParams: Class<*>
    ) {
        substituteProperty(PropertySubstitute(pName, pType, getter, setter, *argParams))
    }

    fun substituteProperty(substitute: PropertySubstitute) {
        if (Collections.EMPTY_MAP === properties) {
            properties = LinkedHashMap()
        }
        substitute.targetType = type
        properties.put(substitute.name, substitute)
    }

    /* begin: Representer */


    fun setExcludes(vararg propNames: String) {
        excludes = propNames.toHashSet()
    }

    fun getProperties(): Set<Property>? {
        if (dumpProperties != null) {
            return dumpProperties
        }
        if (propertyUtils != null) {
            if (includes != null) {
                dumpProperties = LinkedHashSet()
                for (propertyName in includes!!) {
                    if (!excludes.contains(propertyName)) {
                        dumpProperties!!.add(getProperty(propertyName)!!)
                    }
                }
                return dumpProperties
            }
            val readableProps =
                if (beanAccess == null) propertyUtils!!.getProperties(type) else propertyUtils!!.getProperties(
                    type,
                    beanAccess!!
                )
            if (properties.isEmpty()) {
                if (excludes.isEmpty()) {
                    return readableProps.also { dumpProperties = it }
                }
                dumpProperties = LinkedHashSet()
                for (property in readableProps!!) {
                    if (!excludes.contains(property.name)) {
                        dumpProperties!!.add(property)
                    }
                }
                return dumpProperties
            }
            if (!delegatesChecked) {
                checkDelegates()
            }
            dumpProperties = LinkedHashSet()
            for (property in properties.values) {
                if (property.name !in excludes && property.isReadable) {
                    dumpProperties!!.add(property)
                }
            }
            for (property in readableProps) {
                if (property.name !in excludes) {
                    dumpProperties!!.add(property)
                }
            }
            return dumpProperties
        }
        return null
    }

    /* end: Representer */ /*------------ Maybe something useful to override :) ---------*/
    fun setupPropertyType(key: String?, valueNode: Node?): Boolean {
        return false
    }

    @Throws(Exception::class)
    fun setProperty(targetBean: Any?, propertyName: String?, value: Any?): Boolean {
        return false
    }

    /**
     * This method should be overridden for TypeDescription implementations that are supposed to implement
     * instantiation logic that is different from default one as implemented in YAML constructors.
     * Note that even if you override this method, default filling of fields with
     * variables from parsed YAML will still occur later.
     * @param node - node to construct the instance from
     * @return new instance
     */
    open fun newInstance(node: Node?): Any? {
        if (impl != null) {
            try {
                val c = impl!!.getDeclaredConstructor()
                c.isAccessible = true
                return c.newInstance()
            } catch (e: Exception) {
                log.fine(e.localizedMessage)
                impl = null
            }
        }
        return null
    }

    open fun newInstance(propertyName: String?, node: Node?): Any? {
        return null
    }

    /**
     * Is invoked after entity is filled with values from deserialized YAML
     * @param obj - deserialized entity
     * @return postprocessed deserialized entity
     */
    open fun finalizeConstruction(obj: Any?): Any? {
        return obj
    }

    companion object {
        private val log = Logger
            .getLogger(TypeDescription::class.javaObjectType.getPackage().name)
    }
}

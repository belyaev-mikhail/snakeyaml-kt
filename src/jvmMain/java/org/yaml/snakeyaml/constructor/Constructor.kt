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

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.TypeDescription
import org.yaml.snakeyaml.error.YAMLException
import org.yaml.snakeyaml.introspector.Property
import org.yaml.snakeyaml.nodes.*
import org.yaml.snakeyaml.util.EnumUtils
import java.lang.reflect.Constructor
import java.math.BigDecimal
import java.math.BigInteger
import java.util.*

/**
 * Construct a custom Java instance.
 */
open class Constructor @JvmOverloads constructor(
    theRoot: TypeDescription?,
    moreTDs: Collection<TypeDescription?>? = null,
    loadingConfig: LoaderOptions? = LoaderOptions()
) : SafeConstructor(loadingConfig) {
    constructor(loadingConfig: LoaderOptions?) : this(Any::class.java, loadingConfig) {}

    /**
     * Create Constructor for the specified class as the root.
     *
     * @param theRoot
     * - the class (usually JavaBean) to be constructed
     */
    @JvmOverloads
    constructor(theRoot: Class<out Any>? = Any::class.java) : this(TypeDescription(checkRoot(theRoot))) {
    }

    constructor(theRoot: Class<out Any>?, loadingConfig: LoaderOptions?) : this(
        TypeDescription(checkRoot(theRoot)),
        loadingConfig
    ) {
    }

    constructor(theRoot: TypeDescription?, loadingConfig: LoaderOptions?) : this(theRoot, null, loadingConfig) {}

    /**
     * Create with all possible arguments
     * @param theRoot - the class (usually JavaBean) to be constructed
     * @param moreTDs - collection of classes used by the root class
     * @param loadingConfig - configuration
     */
    init {
        if (theRoot == null) {
            throw NullPointerException("Root type must be provided.")
        }
        yamlConstructors.put(null, ConstructYamlObject())
        if (Any::class.java != theRoot.type) {
            rootTag = Tag(theRoot.type)
        }
        yamlClassConstructors.put(NodeId.scalar, ConstructScalar())
        yamlClassConstructors.put(NodeId.mapping, ConstructMapping())
        yamlClassConstructors.put(NodeId.sequence, ConstructSequence())
        addTypeDescription(theRoot)
        if (moreTDs != null) {
            for (td in moreTDs) {
                addTypeDescription(td)
            }
        }
    }

    /**
     * Create Constructor for a class which does not have to be in the classpath
     * or for a definition from a Spring ApplicationContext.
     *
     * @param theRoot
     * fully qualified class name of the root class (usually
     * JavaBean)
     * @throws ClassNotFoundException if cannot be loaded by the classloader
     */
    constructor(theRoot: String?) : this(Class.forName(check(theRoot))) {}
    constructor(theRoot: String?, loadingConfig: LoaderOptions?) : this(Class.forName(check(theRoot)), loadingConfig) {}

    /**
     * Construct mapping instance (Map, JavaBean) when the runtime class is
     * known.
     */
    open inner class ConstructMapping : Construct {
        /**
         * Construct JavaBean. If type safe collections are used please look at
         * `TypeDescription`.
         *
         * @param node
         * node where the keys are property names (they can only be
         * `String`s) and values are objects to be created
         * @return constructed JavaBean
         */
        override fun construct(node: Node?): Any? {
            val mnode = node as MappingNode?
            return if (MutableMap::class.java.isAssignableFrom(node!!.type)) {
                if (node.isTwoStepsConstruction) {
                    newMap(mnode!!)
                } else {
                    constructMapping(mnode!!)
                }
            } else if (MutableCollection::class.java.isAssignableFrom(node.type)) {
                if (node.isTwoStepsConstruction) {
                    newSet(mnode!!)
                } else {
                    constructSet(mnode!!)
                }
            } else {
                val obj = this@Constructor.newInstance(mnode!!)
                if (node.isTwoStepsConstruction) {
                    obj
                } else {
                    constructJavaBean2ndStep(mnode, obj)
                }
            }
        }

        override fun construct2ndStep(node: Node?, `object`: Any?) {
            if (MutableMap::class.java.isAssignableFrom(node!!.type)) {
                constructMapping2ndStep((node as MappingNode?)!!, `object` as MutableMap<Any?, Any?>)
            } else if (MutableSet::class.java.isAssignableFrom(node.type)) {
                constructSet2ndStep((node as MappingNode?)!!, `object` as MutableSet<Any?>)
            } else {
                constructJavaBean2ndStep(node as MappingNode?, `object`)
            }
        }

        // protected Object createEmptyJavaBean(MappingNode node) {
        // try {
        // Object instance = Constructor.this.newInstance(node);
        // if (instance != null) {
        // return instance;
        // }
        //
        // /**
        // * Using only default constructor. Everything else will be
        // * initialized on 2nd step. If we do here some partial
        // * initialization, how do we then track what need to be done on
        // * 2nd step? I think it is better to get only object here (to
        // * have it as reference for recursion) and do all other thing on
        // * 2nd step.
        // */
        // java.lang.reflect.Constructor<?> c =
        // node.getType().getDeclaredConstructor();
        // c.setAccessible(true);
        // return c.newInstance();
        // } catch (Exception e) {
        // throw new YAMLException(e);
        // }
        // }
        protected fun constructJavaBean2ndStep(node: MappingNode?, `object`: Any?): Any? {
            flattenMapping(node, true)
            val beanType = node!!.type
            val nodeValue = node.value
            for (tuple in nodeValue) {
                val valueNode = tuple.valueNode
                // flattenMapping enforces keys to be Strings
                val key = constructObject(tuple.keyNode) as String?
                try {
                    val memberDescription = typeDefinitions[beanType]
                    val property =
                        if (memberDescription == null) getProperty(beanType, key) else memberDescription.getProperty(
                            key!!
                        )!!
                    if (!property.isWritable) {
                        throw YAMLException(
                            "No writable property '" + key + "' on class: "
                                    + beanType.name
                        )
                    }
                    valueNode.type = property.type
                    val typeDetected = memberDescription?.setupPropertyType(key, valueNode) ?: false
                    if (!typeDetected && valueNode.nodeId != NodeId.scalar) {
                        // only if there is no explicit TypeDescription
                        val arguments = property.actualTypeArguments
                        if (arguments != null && arguments.size > 0) {
                            // type safe (generic) collection may contain the
                            // proper class
                            if (valueNode.nodeId == NodeId.sequence) {
                                val t = arguments[0]
                                val snode = valueNode as SequenceNode
                                snode.setListType(t)
                            } else if (MutableMap::class.java.isAssignableFrom(valueNode.type)) {
                                val keyType = arguments[0]
                                val valueType = arguments[1]
                                val mnode = valueNode as MappingNode
                                mnode.setTypes(keyType, valueType)
                                mnode.setUseClassConstructor(true)
                            } else if (MutableCollection::class.java.isAssignableFrom(valueNode.type)) {
                                val t = arguments[0]
                                val mnode = valueNode as MappingNode
                                mnode.setOnlyKeyType(t)
                                mnode.setUseClassConstructor(true)
                            }
                        }
                    }
                    var value = memberDescription?.let { newInstance(it, key, valueNode) } ?: constructObject(valueNode)
                    // Correct when the property expects float but double was
                    // constructed
                    if (property.type == java.lang.Float.TYPE || property.type == Float::class.java) {
                        if (value is Double) {
                            value = value.toFloat()
                        }
                    }
                    // Correct when the property a String but the value is binary
                    if (property.type == String::class.java && Tag.BINARY == valueNode.tag && value is ByteArray) {
                        value = String((value as ByteArray?)!!)
                    }
                    if (memberDescription == null
                        || !memberDescription.setProperty(`object`, key, value)
                    ) {
                        property[`object`] = value
                    }
                } catch (e: DuplicateKeyException) {
                    throw e
                } catch (e: Exception) {
                    throw ConstructorException(
                        "Cannot create property=$key for JavaBean=$`object`",
                        node.startMark, e.message, valueNode.startMark, e
                    )
                }
            }
            return `object`
        }

        private fun newInstance(
            memberDescription: TypeDescription, propertyName: String?,
            node: Node
        ): Any? {
            val newInstance = memberDescription.newInstance(propertyName, node)
            if (newInstance != null) {
                constructedObjects[node] = newInstance
                return constructObjectNoCheck(node)
            }
            return constructObject(node)
        }

        protected fun getProperty(type: Class<out Any?>?, name: String?): Property {
            return propertyUtils!!.getProperty(type, name)
        }
    }

    /**
     * Construct an instance when the runtime class is not known but a global
     * tag with a class name is defined. It delegates the construction to the
     * appropriate constructor based on the node kind (scalar, sequence,
     * mapping)
     */
    protected inner class ConstructYamlObject : Construct {
        private fun getConstructor(node: Node?): Construct? {
            val cl = getClassForNode(node)
            node!!.type = cl
            // call the constructor as if the runtime class is defined
            return yamlClassConstructors[node.nodeId]
        }

        override fun construct(node: Node?): Any? {
            return try {
                getConstructor(node)!!.construct(node)
            } catch (e: ConstructorException) {
                throw e
            } catch (e: Exception) {
                throw ConstructorException(
                    null, null, "Can't construct a java object for "
                            + node!!.tag + "; exception=" + e.message, node.startMark, e
                )
            }
        }

        override fun construct2ndStep(node: Node?, `object`: Any?) {
            try {
                getConstructor(node)!!.construct2ndStep(node, `object`)
            } catch (e: Exception) {
                throw ConstructorException(
                    null, null, "Can't construct a second step for a java object for "
                            + node!!.tag + "; exception=" + e.message,
                    node.startMark, e
                )
            }
        }
    }

    /**
     * Construct scalar instance when the runtime class is known. Recursive
     * structures are not supported.
     */
    protected inner class ConstructScalar : AbstractConstruct() {
        override fun construct(nnode: Node?): Any? {
            val node = nnode as ScalarNode?
            val type = node!!.type
            try {
                return newInstance(type, node, false)
            } catch (e1: InstantiationException) {
            }
            val result: Any?
            if (type.isPrimitive || type == String::class.java || Number::class.java.isAssignableFrom(type) || type == Boolean::class.java || Date::class.java.isAssignableFrom(
                    type
                ) || type == Char::class.java || type == BigInteger::class.java || type == BigDecimal::class.java || Enum::class.java.isAssignableFrom(
                    type
                ) || Tag.BINARY == node.tag || Calendar::class.java.isAssignableFrom(type) || type == UUID::class.java
            ) {
                // standard classes created directly
                result = constructStandardJavaInstance(type, node)
            } else {
                // there must be only 1 constructor with 1 argument
                val javaConstructors = type
                    .declaredConstructors
                var oneArgCount = 0
                var javaConstructor: Constructor<*>? = null
                for (c in javaConstructors) {
                    if (c.parameterTypes.size == 1) {
                        oneArgCount++
                        javaConstructor = c
                    }
                }
                val argument: Any?
                if (javaConstructor == null) {
                    return try {
                        newInstance(type, node, false)
                    } catch (ie: InstantiationException) {
                        throw YAMLException(
                            "No single argument constructor found for " + type
                                    + " : " + ie.message
                        )
                    }
                } else if (oneArgCount == 1) {
                    argument = constructStandardJavaInstance(
                        javaConstructor.parameterTypes[0],
                        node
                    )
                } else {
                    // TODO it should be possible to use implicit types instead
                    // of forcing String. Resolver must be available here to
                    // obtain the implicit tag. Then we can set the tag and call
                    // callConstructor(node) to create the argument instance.
                    // On the other hand it may be safer to require a custom
                    // constructor to avoid guessing the argument class
                    argument = constructScalar(node)
                    javaConstructor = try {
                        type.getDeclaredConstructor(String::class.java)
                    } catch (e: Exception) {
                        throw YAMLException(
                            "Can't construct a java object for scalar "
                                    + node.tag + "; No String constructor found. Exception="
                                    + e.message, e
                        )
                    }
                }
                try {
                    javaConstructor!!.isAccessible = true
                    result = javaConstructor.newInstance(argument)
                } catch (e: Exception) {
                    throw ConstructorException(
                        null, null,
                        "Can't construct a java object for scalar " + node.tag
                                + "; exception=" + e.message,
                        node.startMark, e
                    )
                }
            }
            return result
        }

        private fun constructStandardJavaInstance(
            type: Class<*>,
            node: ScalarNode?
        ): Any? {
            var result: Any?
            if (type == String::class.java) {
                val stringConstructor = yamlConstructors[Tag.STR]
                result = stringConstructor!!.construct(node)
            } else if (type == Boolean::class.java || type == java.lang.Boolean.TYPE) {
                val boolConstructor = yamlConstructors[Tag.BOOL]
                result = boolConstructor!!.construct(node)
            } else if (type == Char::class.java || type == Character.TYPE) {
                val charConstructor = yamlConstructors[Tag.STR]
                val ch = charConstructor!!.construct(node) as String?
                result = if (ch!!.length == 0) {
                    null
                } else if (ch.length != 1) {
                    throw YAMLException(
                        "Invalid node Character: '" + ch + "'; length: " + ch.length
                    )
                } else {
                    Character.valueOf(ch[0])
                }
            } else if (Date::class.java.isAssignableFrom(type)) {
                val dateConstructor = yamlConstructors[Tag.TIMESTAMP]
                val date = dateConstructor!!.construct(node) as Date?
                result = if (type == Date::class.java) {
                    date
                } else {
                    try {
                        val constr = type.getConstructor(Long::class.javaPrimitiveType)
                        constr.newInstance(date!!.time)
                    } catch (e: RuntimeException) {
                        throw e
                    } catch (e: Exception) {
                        throw YAMLException("Cannot construct: '$type'")
                    }
                }
            } else if (type == Float::class.java || type == Double::class.java || type == java.lang.Float.TYPE || type == java.lang.Double.TYPE || type == BigDecimal::class.java) {
                if (type == BigDecimal::class.java) {
                    result = BigDecimal(node!!.value)
                } else {
                    val doubleConstructor = yamlConstructors[Tag.FLOAT]
                    result = doubleConstructor!!.construct(node)
                    if (type == Float::class.java || type == java.lang.Float.TYPE) {
                        result = java.lang.Float.valueOf((result as Double?)!!.toFloat())
                    }
                }
            } else if (type == Byte::class.java || type == Short::class.java || type == Int::class.java || type == Long::class.java || type == BigInteger::class.java || type == java.lang.Byte.TYPE || type == java.lang.Short.TYPE || type == Integer.TYPE || type == java.lang.Long.TYPE) {
                val intConstructor = yamlConstructors[Tag.INT]
                result = intConstructor!!.construct(node)
                result = if (type == Byte::class.java || type == java.lang.Byte.TYPE) {
                    Integer.valueOf(result.toString()).toByte()
                } else if (type == Short::class.java || type == java.lang.Short.TYPE) {
                    Integer.valueOf(result.toString()).toShort()
                } else if (type == Int::class.java || type == Integer.TYPE) {
                    result.toString().toInt()
                } else if (type == Long::class.java || type == java.lang.Long.TYPE) {
                    java.lang.Long.valueOf(result.toString())
                } else {
                    // only BigInteger left
                    BigInteger(result.toString())
                }
            } else if (Enum::class.java.isAssignableFrom(type)) {
                val enumValueName = node!!.value
                result = try {
                    if (loadingConfig.isEnumCaseSensitive) {
                        java.lang.Enum.valueOf(type as Class<out Enum<*>>, enumValueName)
                    } else {
                        EnumUtils.findEnumInsensitiveCase(type as Class<out Enum<*>>, enumValueName)
                    }
                } catch (ex: Exception) {
                    throw YAMLException(
                        "Unable to find enum value '" + enumValueName
                                + "' for enum class: " + type.name
                    )
                }
            } else if (Calendar::class.java.isAssignableFrom(type)) {
                val contr = ConstructYamlTimestamp()
                contr.construct(node)
                result = contr.calendar
            } else if (Number::class.java.isAssignableFrom(type)) {
                //since we do not know the exact type we create Float
                val contr: ConstructYamlFloat = ConstructYamlFloat()
                result = contr.construct(node)
            } else if (UUID::class.java == type) {
                result = UUID.fromString(node!!.value)
            } else {
                result = if (yamlConstructors.containsKey(node!!.tag)) {
                    yamlConstructors[node.tag]!!.construct(node)
                } else {
                    throw YAMLException("Unsupported class: $type")
                }
            }
            return result
        }
    }

    /**
     * Construct sequence (List, Array, or immutable object) when the runtime
     * class is known.
     */
    protected inner class ConstructSequence : Construct {
        override fun construct(node: Node?): Any? {
            val snode = node as SequenceNode?
            return if (MutableSet::class.java.isAssignableFrom(node!!.type)) {
                if (node.isTwoStepsConstruction) {
                    throw YAMLException("Set cannot be recursive.")
                } else {
                    constructSet(snode!!)
                }
            } else if (MutableCollection::class.java.isAssignableFrom(node.type)) {
                if (node.isTwoStepsConstruction) {
                    newList(snode!!)
                } else {
                    constructSequence(snode!!)
                }
            } else if (node.type.isArray) {
                if (node.isTwoStepsConstruction) {
                    createArray(node.type, snode!!.value.size)
                } else {
                    constructArray(snode!!)
                }
            } else {
                // create immutable object
                val possibleConstructors: MutableList<Constructor<*>> = ArrayList(
                    snode!!.value.size
                )
                for (constructor in node.type
                    .declaredConstructors) {
                    if (snode.value.size == constructor.parameterTypes.size) {
                        possibleConstructors.add(constructor)
                    }
                }
                if (!possibleConstructors.isEmpty()) {
                    if (possibleConstructors.size == 1) {
                        val argumentList = arrayOfNulls<Any>(snode.value.size)
                        val c = possibleConstructors[0]
                        var index = 0
                        for (argumentNode in snode.value) {
                            val type = c.parameterTypes[index]
                            // set runtime classes for arguments
                            argumentNode.type = type
                            argumentList[index++] = constructObject(argumentNode)
                        }
                        return try {
                            c.isAccessible = true
                            c.newInstance(*argumentList)
                        } catch (e: Exception) {
                            throw YAMLException(e)
                        }
                    }

                    // use BaseConstructor
                    val argumentList = constructSequence(snode)
                    val parameterTypes: Array<Class<*>?> = arrayOfNulls(argumentList.size)
                    var index = 0
                    for (parameter in argumentList) {
                        parameterTypes[index] = parameter!!.javaClass
                        index++
                    }
                    for (c in possibleConstructors) {
                        val argTypes = c.parameterTypes
                        var foundConstructor = true
                        for (i in argTypes.indices) {
                            if (!wrapIfPrimitive(argTypes[i]).isAssignableFrom(parameterTypes[i])) {
                                foundConstructor = false
                                break
                            }
                        }
                        if (foundConstructor) {
                            return try {
                                c.isAccessible = true
                                c.newInstance(*argumentList.toTypedArray())
                            } catch (e: Exception) {
                                throw YAMLException(e)
                            }
                        }
                    }
                }
                throw YAMLException(
                    "No suitable constructor with " + snode.value.size.toString() + " arguments found for " + node.type
                )
            }
        }

        private fun wrapIfPrimitive(clazz: Class<*>): Class<out Any> {
            if (!clazz.isPrimitive) {
                return clazz
            }
            if (clazz == Integer.TYPE) {
                return Int::class.java
            }
            if (clazz == java.lang.Float.TYPE) {
                return Float::class.java
            }
            if (clazz == java.lang.Double.TYPE) {
                return Double::class.java
            }
            if (clazz == java.lang.Boolean.TYPE) {
                return Boolean::class.java
            }
            if (clazz == java.lang.Long.TYPE) {
                return Long::class.java
            }
            if (clazz == Character.TYPE) {
                return Char::class.java
            }
            if (clazz == java.lang.Short.TYPE) {
                return Short::class.java
            }
            if (clazz == java.lang.Byte.TYPE) {
                return Byte::class.java
            }
            throw YAMLException("Unexpected primitive $clazz")
        }

        override fun construct2ndStep(node: Node?, `object`: Any?) {
            val snode = node as SequenceNode?
            if (MutableList::class.java.isAssignableFrom(node!!.type)) {
                val list = `object` as MutableList<Any?>
                constructSequenceStep2(snode!!, list)
            } else if (node.type.isArray) {
                constructArrayStep2(snode!!, `object`!!)
            } else {
                throw YAMLException("Immutable objects cannot be recursive.")
            }
        }
    }

    protected fun getClassForNode(node: Node?): Class<*> {
        val classForTag = typeTags[node!!.tag]
        return if (classForTag == null) {
            val name = node.tag.className
            val cl: Class<*>
            cl = try {
                getClassForName(name)
            } catch (e: ClassNotFoundException) {
                throw YAMLException("Class not found: $name")
            }
            typeTags[node.tag] = cl
            cl
        } else {
            classForTag
        }
    }

    @Throws(ClassNotFoundException::class)
    protected open fun getClassForName(name: String?): Class<*> {
        return try {
            Class.forName(name, true, Thread.currentThread().contextClassLoader)
        } catch (e: ClassNotFoundException) {
            Class.forName(name)
        }
    }

    companion object {
        /**
         * Ugly Java way to check the argument in the constructor
         */
        private fun checkRoot(theRoot: Class<out Any>?): Class<out Any> {
            return theRoot ?: throw NullPointerException("Root class must be provided.")
        }

        private fun check(s: String?): String {
            if (s == null) {
                throw NullPointerException("Root type must be provided.")
            }
            if (s.trim { it <= ' ' }.length == 0) {
                throw YAMLException("Root type must be provided.")
            }
            return s
        }
    }
}
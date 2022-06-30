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
import org.yaml.snakeyaml.composer.Composer
import org.yaml.snakeyaml.error.YAMLException
import org.yaml.snakeyaml.introspector.PropertyUtils
import org.yaml.snakeyaml.nodes.*
import java.lang.reflect.Array
import java.lang.reflect.Modifier
import java.util.*

abstract class BaseConstructor @JvmOverloads constructor(loadingConfig: LoaderOptions = LoaderOptions()) {
    /**
     * It maps the node kind to the the Construct implementation. When the
     * runtime class is known then the implicit tag is ignored.
     */
    @JvmField
    protected val yamlClassConstructors: MutableMap<NodeId, Construct> = EnumMap(
        NodeId::class.java
    )

    /**
     * It maps the (explicit or implicit) tag to the Construct implementation.
     * It is used:
     * 1) explicit tag - if present.
     * 2) implicit tag - when the runtime class of the instance is unknown (the
     * node has the Object.class)
     */
    @JvmField
    protected val yamlConstructors: MutableMap<Tag?, Construct> = HashMap()

    /**
     * It maps the (explicit or implicit) tag to the Construct implementation.
     * It is used when no exact match found.
     */
    protected val yamlMultiConstructors: MutableMap<String, Construct> = HashMap()
    public var composer: Composer? = null
    @JvmField
    val constructedObjects: MutableMap<Node?, Any?>
    private val recursiveObjects: MutableSet<Node?>
    private val maps2fill: ArrayList<RecursiveTuple<MutableMap<Any?, Any?>?, RecursiveTuple<Any?, Any?>?>>
    private val sets2fill: ArrayList<RecursiveTuple<MutableSet<Any?>, Any?>>
    @JvmField
    protected var rootTag: Tag?
    var propertyUtils: PropertyUtils? = null
        get() {
            if (field == null) {
                field = PropertyUtils()
            }
            return field
        }
        set(propertyUtils) {
            field = propertyUtils
            isExplicitPropertyUtils = true
            val tds: Collection<TypeDescription> = typeDefinitions.values
            for (typeDescription in tds) {
                typeDescription.setPropertyUtils(propertyUtils)
            }
        }
    var isExplicitPropertyUtils: Boolean
        private set
    var isAllowDuplicateKeys = true
    var isWrappedToRootException = false
    var isEnumCaseSensitive = false
    @JvmField
    protected val typeDefinitions: MutableMap<Class<out Any>, TypeDescription>
    @JvmField
    protected val typeTags: MutableMap<Tag, Class<out Any>>
    @JvmField
    protected var loadingConfig: LoaderOptions

    init {
        constructedObjects = HashMap()
        recursiveObjects = HashSet()
        maps2fill = ArrayList()
        sets2fill = ArrayList()
        typeDefinitions = HashMap()
        typeTags = HashMap()
        rootTag = null
        isExplicitPropertyUtils = false
        typeDefinitions[SortedMap::class.java] = TypeDescription(
            SortedMap::class.java, Tag.OMAP,
            TreeMap::class.java
        )
        typeDefinitions[SortedSet::class.java] = TypeDescription(
            SortedSet::class.java, Tag.SET,
            TreeSet::class.java
        )
        this.loadingConfig = loadingConfig
    }

//    fun setComposer(composer: Composer?) {
//        this.composer = composer
//    }

    /**
     * Check if more documents available
     *
     * @return true when there are more YAML documents in the stream
     */
    fun checkData(): Boolean {
        // If there are more documents available?
        return composer!!.checkNode()
    }// Construct and return the next document.

    /**
     * Construct and return the next document
     *
     * @return constructed instance
     */
    @get:Throws(NoSuchElementException::class)
    val data: Any?
        get() {
            // Construct and return the next document.
            if (!composer!!.checkNode()) throw NoSuchElementException("No document is available.")
            val node = composer!!.node
            if (rootTag != null) {
                node!!.tag = rootTag
            }
            return constructDocument(node)
        }

    /**
     * Ensure that the stream contains a single document and construct it
     *
     * @param type the class of the instance being created
     * @return constructed instance
     * @throws ComposerException in case there are more documents in the stream
     */
    fun getSingleData(type: Class<*>): Any? {
        // Ensure that the stream contains a single document and construct it
        val node = composer!!.singleNode
        return if (node != null && Tag.NULL != node.tag) {
            if (Any::class.java != type) {
                node.tag = Tag(type)
            } else if (rootTag != null) {
                node.tag = rootTag
            }
            constructDocument(node)
        } else {
            val construct = yamlConstructors[Tag.NULL]
            construct!!.construct(node)
        }
    }

    /**
     * Construct complete YAML document. Call the second step in case of
     * recursive structures. At the end cleans all the state.
     *
     * @param node root Node
     * @return Java instance
     */
    protected fun constructDocument(node: Node?): Any? {
        return try {
            val data = constructObject(node)
            fillRecursive()
            data
        } catch (e: RuntimeException) {
            if (isWrappedToRootException && e !is YAMLException) {
                throw YAMLException(e)
            } else {
                throw e
            }
        } finally {
            //clean up resources
            constructedObjects.clear()
            recursiveObjects.clear()
        }
    }

    /**
     * Fill the recursive structures and clean the internal collections
     */
    private fun fillRecursive() {
        if (!maps2fill.isEmpty()) {
            for (entry in maps2fill) {
                val key_value = entry._2()
                entry._1()!![key_value!!._1()] = key_value._2()
            }
            maps2fill.clear()
        }
        if (!sets2fill.isEmpty()) {
            for (value in sets2fill) {
                value._1().add(value._2())
            }
            sets2fill.clear()
        }
    }

    /**
     * Construct object from the specified Node. Return existing instance if the
     * node is already constructed.
     *
     * @param node Node to be constructed
     * @return Java instance
     */
    protected fun constructObject(node: Node?): Any? {
        return if (constructedObjects.containsKey(node)) {
            constructedObjects[node]
        } else constructObjectNoCheck(node)
    }

    protected fun constructObjectNoCheck(node: Node?): Any? {
        if (recursiveObjects.contains(node)) {
            throw ConstructorException(
                null, null, "found unconstructable recursive node",
                node!!.startMark
            )
        }
        recursiveObjects.add(node)
        val constructor = getConstructor(node)
        val data = if (constructedObjects.containsKey(node)) constructedObjects[node] else constructor!!.construct(node)
        finalizeConstruction(node, data)
        constructedObjects[node] = data
        recursiveObjects.remove(node)
        if (node!!.isTwoStepsConstruction) {
            constructor!!.construct2ndStep(node, data)
        }
        return data
    }

    /**
     * Get the constructor to construct the Node. For implicit tags if the
     * runtime class is known a dedicated Construct implementation is used.
     * Otherwise the constructor is chosen by the tag.
     *
     * @param node [Node] to construct an instance from
     * @return [Construct] implementation for the specified node
     */
    protected open fun getConstructor(node: Node?): Construct? {
        return if (node!!.useClassConstructor()) {
            yamlClassConstructors[node.nodeId]
        } else {
            val constructor = yamlConstructors[node.tag]
            if (constructor == null) {
                for (prefix in yamlMultiConstructors.keys) {
                    if (node.tag.startsWith(prefix)) {
                        return yamlMultiConstructors[prefix]
                    }
                }
                return yamlConstructors[null]
            }
            constructor
        }
    }

    protected fun constructScalar(node: ScalarNode): String {
        return node.value
    }

    // >>>> DEFAULTS >>>>
    protected fun createDefaultList(initSize: Int): MutableList<Any?> {
        return ArrayList(initSize)
    }

    protected fun createDefaultSet(initSize: Int): MutableSet<Any?> {
        return LinkedHashSet(initSize)
    }

    protected fun createDefaultMap(initSize: Int): MutableMap<Any?, Any?> {
        // respect order from YAML document
        return LinkedHashMap(initSize)
    }

    protected fun createArray(type: Class<*>, size: Int): Any {
        return Array.newInstance(type.componentType, size)
    }

    // <<<< DEFAULTS <<<<
    protected fun finalizeConstruction(node: Node?, data: Any?): Any? {
        val type = node!!.type
        return if (typeDefinitions.containsKey(type)) {
            typeDefinitions[type]!!.finalizeConstruction(data!!)
        } else data
    }

    // >>>> NEW instance
    protected fun newInstance(node: Node): Any {
        return try {
            newInstance(Any::class.java, node)
        } catch (e: InstantiationException) {
            throw YAMLException(e)
        }
    }

    @Throws(InstantiationException::class)
    protected fun newInstance(ancestor: Class<*>, node: Node, tryDefault: Boolean = true): Any {
        val type = node.type
        if (typeDefinitions.containsKey(type)) {
            val td = typeDefinitions[type]
            val instance = td!!.newInstance(node)
            if (instance != null) {
                return instance
            }
        }
        if (tryDefault) {
            /*
             * Removed <code> have InstantiationException in case of abstract
             * type
             */
            if (ancestor.isAssignableFrom(type) && !Modifier.isAbstract(type.modifiers)) {
                return try {
                    val c = type.getDeclaredConstructor()
                    c.isAccessible = true
                    c.newInstance()
                } catch (e: NoSuchMethodException) {
                    throw InstantiationException(
                        "NoSuchMethodException:"
                                + e.localizedMessage
                    )
                } catch (e: Exception) {
                    throw YAMLException(e)
                }
            }
        }
        throw InstantiationException()
    }

    protected fun newSet(node: CollectionNode<*>): MutableSet<Any?> {
        return try {
            newInstance(MutableSet::class.java, node) as MutableSet<Any?>
        } catch (e: InstantiationException) {
            createDefaultSet(node.value.size)
        }
    }

    protected fun newList(node: SequenceNode): MutableList<Any?> {
        return try {
            newInstance(MutableList::class.java, node) as MutableList<Any?>
        } catch (e: InstantiationException) {
            createDefaultList(node.value.size)
        }
    }

    protected fun newMap(node: MappingNode): MutableMap<Any?, Any?> {
        return try {
            newInstance(MutableMap::class.java, node) as MutableMap<Any?, Any?>
        } catch (e: InstantiationException) {
            createDefaultMap(node.value.size)
        }
    }

    // <<<< NEW instance
    // >>>> Construct => NEW, 2ndStep(filling)
    protected fun constructSequence(node: SequenceNode): List<Any?> {
        val result = newList(node)
        constructSequenceStep2(node, result)
        return result
    }

    protected fun constructSet(node: SequenceNode): Set<Any?> {
        val result = newSet(node)
        constructSequenceStep2(node, result)
        return result
    }

    protected fun constructArray(node: SequenceNode): Any {
        return constructArrayStep2(node, createArray(node.type, node.value.size))
    }

    protected fun constructSequenceStep2(node: SequenceNode, collection: MutableCollection<Any?>) {
        for (child in node.value) {
            collection.add(constructObject(child))
        }
    }

    protected fun constructArrayStep2(node: SequenceNode, array: Any): Any {
        val componentType = node.type.componentType
        var index = 0
        for (child in node.value) {
            // Handle multi-dimensional arrays...
            if (child.type == Any::class.java) {
                child.type = componentType
            }
            val value = constructObject(child)
            if (componentType.isPrimitive) {
                // Null values are disallowed for primitives
                if (value == null) {
                    throw NullPointerException(
                        "Unable to construct element value for $child"
                    )
                }

                // Primitive arrays require quite a lot of work.
                if (Byte::class.javaPrimitiveType == componentType) {
                    Array.setByte(array, index, (value as Number).toByte())
                } else if (Short::class.javaPrimitiveType == componentType) {
                    Array.setShort(array, index, (value as Number).toShort())
                } else if (Int::class.javaPrimitiveType == componentType) {
                    Array.setInt(array, index, (value as Number).toInt())
                } else if (Long::class.javaPrimitiveType == componentType) {
                    Array.setLong(array, index, (value as Number).toLong())
                } else if (Float::class.javaPrimitiveType == componentType) {
                    Array.setFloat(array, index, (value as Number).toFloat())
                } else if (Double::class.javaPrimitiveType == componentType) {
                    Array.setDouble(array, index, (value as Number).toDouble())
                } else if (Char::class.javaPrimitiveType == componentType) {
                    Array.setChar(array, index, (value as Char))
                } else if (Boolean::class.javaPrimitiveType == componentType) {
                    Array.setBoolean(array, index, (value as Boolean))
                } else {
                    throw YAMLException("unexpected primitive type")
                }
            } else {
                // Non-primitive arrays can simply be assigned:
                Array.set(array, index, value)
            }
            ++index
        }
        return array
    }

    protected fun constructSet(node: MappingNode): Set<Any?> {
        val set = newSet(node)
        constructSet2ndStep(node, set)
        return set
    }

    protected fun constructMapping(node: MappingNode): Map<Any?, Any?> {
        val mapping = newMap(node)
        constructMapping2ndStep(node, mapping)
        return mapping
    }

    protected open fun constructMapping2ndStep(node: MappingNode, mapping: MutableMap<Any?, Any?>) {
        val nodeValue = node.value
        for (tuple in nodeValue) {
            val keyNode = tuple.keyNode
            val valueNode = tuple.valueNode
            val key = constructObject(keyNode)
            if (key != null) {
                try {
                    key.hashCode() // check circular dependencies
                } catch (e: Exception) {
                    throw ConstructorException(
                        "while constructing a mapping",
                        node.startMark, "found unacceptable key $key",
                        tuple.keyNode.startMark, e
                    )
                }
            }
            val value = constructObject(valueNode)
            if (keyNode.isTwoStepsConstruction) {
                if (loadingConfig.allowRecursiveKeys) {
                    postponeMapFilling(mapping, key, value)
                } else {
                    throw YAMLException("Recursive key for mapping is detected but it is not configured to be allowed.")
                }
            } else {
                mapping[key] = value
            }
        }
    }

    /*
     * if keyObject is created it 2 steps we should postpone putting
     * it in map because it may have different hash after
     * initialization compared to clean just created one. And map of
     * course does not observe key hashCode changes.
     */
    protected fun postponeMapFilling(mapping: MutableMap<Any?, Any?>, key: Any?, value: Any?) {
        maps2fill.add(0, RecursiveTuple(mapping, RecursiveTuple(key, value)))
    }

    protected open fun constructSet2ndStep(node: MappingNode, set: MutableSet<Any?>) {
        val nodeValue = node.value
        for (tuple in nodeValue) {
            val keyNode = tuple.keyNode
            val key = constructObject(keyNode)
            if (key != null) {
                try {
                    key.hashCode() // check circular dependencies
                } catch (e: Exception) {
                    throw ConstructorException(
                        "while constructing a Set", node.startMark,
                        "found unacceptable key $key", tuple.keyNode.startMark, e
                    )
                }
            }
            if (keyNode.isTwoStepsConstruction) {
                postponeSetFilling(set, key)
            } else {
                set.add(key)
            }
        }
    }

    /*
     * if keyObject is created it 2 steps we should postpone putting
     * it into the set because it may have different hash after
     * initialization compared to clean just created one. And set of
     * course does not observe value hashCode changes.
     */
    protected fun postponeSetFilling(set: MutableSet<Any?>, key: Any?) {
        sets2fill.add(0, RecursiveTuple(set, key))
    }

    /**
     * Make YAML aware how to parse a custom Class. If there is no root Class
     * assigned in constructor then the 'root' property of this definition is
     * respected.
     *
     * @param definition to be added to the Constructor
     * @return the previous value associated with `definition`, or
     * `null` if there was no mapping for `definition`.
     */
    fun addTypeDescription(definition: TypeDescription?): TypeDescription? {
        if (definition == null) {
            throw NullPointerException("TypeDescription is required.")
        }
        val tag = definition.tag
        typeTags[tag] = definition.type
        definition.setPropertyUtils(propertyUtils)
        return typeDefinitions.put(definition.type, definition)
    }

    private class RecursiveTuple<T, K>(private val _1: T, private val _2: K) {
        fun _2(): K {
            return _2
        }

        fun _1(): T {
            return _1
        }
    }
}
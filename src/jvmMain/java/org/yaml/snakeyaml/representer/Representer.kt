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
package org.yaml.snakeyaml.representer

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.TypeDescription
import org.yaml.snakeyaml.introspector.Property
import org.yaml.snakeyaml.introspector.PropertyUtils
import org.yaml.snakeyaml.nodes.*
import java.util.*

/**
 * Represent JavaBeans
 */
class Representer : SafeRepresenter {
    protected var typeDefinitions = mutableMapOf<Class<out Any>, TypeDescription>()

    constructor() {
        representers[null] = RepresentJavaBean()
    }

    constructor(options: DumperOptions) : super(options) {
        representers[null] = RepresentJavaBean()
    }

    fun addTypeDescription(td: TypeDescription): TypeDescription? {
        if (Collections.EMPTY_MAP === typeDefinitions) {
            typeDefinitions = HashMap()
        }
        if (td.tag != null) {
            addClassTag(td.type, td.tag)
        }
        td.setPropertyUtils(this.propertyUtils)
        return typeDefinitions.put(td.type, td)
    }

    override var propertyUtils: PropertyUtils?
        get() = super.propertyUtils
        set(propertyUtils) {
            super.propertyUtils = propertyUtils
            val tds = typeDefinitions.values
            for (typeDescription in tds) {
                typeDescription.setPropertyUtils(propertyUtils)
            }
        }

    protected inner class RepresentJavaBean : Represent {
        override fun representData(data: Any?): Node? {
            return representJavaBean(getProperties(data!!.javaClass), data)
        }
    }

    /**
     * Tag logic:
     * - explicit root tag is set in serializer
     * - if there is a predefined class tag it is used
     * - a global tag with class name is always used as tag. The JavaBean parent
     * of the specified JavaBean may set another tag (tag:yaml.org,2002:map)
     * when the property class is the same as runtime class
     *
     * @param properties
     * JavaBean getters
     * @param javaBean
     * instance for Node
     * @return Node to get serialized
     */
    protected fun representJavaBean(properties: Set<Property?>?, javaBean: Any?): MappingNode {
        val value: MutableList<NodeTuple?> = ArrayList(
            properties!!.size
        )
        val tag: Tag
        val customTag = classTags[javaBean!!.javaClass]
        tag = customTag ?: Tag(javaBean.javaClass)
        // flow style will be chosen by BaseRepresenter
        val node = MappingNode(tag, value, DumperOptions.FlowStyle.AUTO)
        representedObjects[javaBean] = node
        var bestStyle = DumperOptions.FlowStyle.FLOW
        for (property in properties) {
            val memberValue = property!![javaBean]
            val customPropertyTag = if (memberValue == null) null else classTags[memberValue.javaClass]
            val tuple = representJavaBeanProperty(
                javaBean, property, memberValue,
                customPropertyTag
            ) ?: continue
            if (!(tuple.keyNode as ScalarNode).isPlain) {
                bestStyle = DumperOptions.FlowStyle.BLOCK
            }
            val nodeValue = tuple.valueNode
            if (!(nodeValue is ScalarNode && nodeValue.isPlain)) {
                bestStyle = DumperOptions.FlowStyle.BLOCK
            }
            value.add(tuple)
        }
        if (defaultFlowStyle !== DumperOptions.FlowStyle.AUTO) {
            node.flowStyle = defaultFlowStyle
        } else {
            node.flowStyle = bestStyle
        }
        return node
    }

    /**
     * Represent one JavaBean property.
     *
     * @param javaBean
     * - the instance to be represented
     * @param property
     * - the property of the instance
     * @param propertyValue
     * - value to be represented
     * @param customTag
     * - user defined Tag
     * @return NodeTuple to be used in a MappingNode. Return null to skip the
     * property
     */
    protected fun representJavaBeanProperty(
        javaBean: Any?, property: Property?,
        propertyValue: Any?, customTag: Tag?
    ): NodeTuple {
        val nodeKey = representData(property.getName()) as ScalarNode
        // the first occurrence of the node must keep the tag
        val hasAlias = representedObjects.containsKey(propertyValue)
        val nodeValue = representData(propertyValue)
        if (propertyValue != null && !hasAlias) {
            val nodeId = nodeValue.nodeId
            if (customTag == null) {
                if (nodeId == NodeId.scalar) {
                    //generic Enum requires the full tag
                    if (property.getType() != Enum::class.java) {
                        if (propertyValue is Enum<*>) {
                            nodeValue!!.tag = Tag.Companion.STR
                        }
                    }
                } else {
                    if (nodeId == NodeId.mapping) {
                        if (property.getType() == propertyValue.javaClass) {
                            if (propertyValue !is Map<*, *>) {
                                if (nodeValue!!.tag != Tag.Companion.SET) {
                                    nodeValue.tag = Tag.Companion.MAP
                                }
                            }
                        }
                    }
                    checkGlobalTag(property, nodeValue, propertyValue)
                }
            }
        }
        return NodeTuple(nodeKey, nodeValue)
    }

    /**
     * Remove redundant global tag for a type safe (generic) collection if it is
     * the same as defined by the JavaBean property
     *
     * @param property
     * - JavaBean property
     * @param node
     * - representation of the property
     * @param object
     * - instance represented by the node
     */
    protected fun checkGlobalTag(property: Property?, node: Node?, `object`: Any) {
        // Skip primitive arrays.
        if (`object`.javaClass.isArray && `object`.javaClass.componentType.isPrimitive) {
            return
        }
        val arguments = property.getActualTypeArguments()
        if (arguments != null) {
            if (node.getNodeId() == NodeId.sequence) {
                // apply map tag where class is the same
                val t = arguments[0]
                val snode = node as SequenceNode?
                var memberList: Iterable<Any?> = Collections.EMPTY_LIST
                if (`object`.javaClass.isArray) {
                    memberList = Arrays.asList(*`object` as Array<Any?>)
                } else if (`object` is Iterable<*>) {
                    // list
                    memberList = `object`
                }
                val iter = memberList.iterator()
                if (iter.hasNext()) {
                    for (childNode in snode.getValue()) {
                        val member = iter.next()
                        if (member != null) {
                            if (t == member.javaClass) if (childNode.nodeId == NodeId.mapping) {
                                childNode!!.tag = Tag.Companion.MAP
                            }
                        }
                    }
                }
            } else if (`object` is Set<*>) {
                val t = arguments[0]
                val mnode = node as MappingNode?
                val iter: Iterator<NodeTuple?> = mnode.getValue().iterator()
                for (member in `object`) {
                    val tuple = iter.next()
                    val keyNode = tuple.getKeyNode()
                    if (t == member.javaClass) {
                        if (keyNode.nodeId == NodeId.mapping) {
                            keyNode!!.tag = Tag.Companion.MAP
                        }
                    }
                }
            } else if (`object` is Map<*, *>) { // NodeId.mapping ends-up here
                val keyType = arguments[0]
                val valueType = arguments[1]
                val mnode = node as MappingNode?
                for (tuple in mnode.getValue()) {
                    resetTag(keyType, tuple.keyNode)
                    resetTag(valueType, tuple.valueNode)
                }
            } else {
                // the type for collection entries cannot be
                // detected
            }
        }
    }

    private fun resetTag(type: Class<out Any?>?, node: Node?) {
        val tag = node!!.tag
        if (tag!!.matches(type)) {
            if (Enum::class.java.isAssignableFrom(type)) {
                node.tag = Tag.Companion.STR
            } else {
                node.tag = Tag.Companion.MAP
            }
        }
    }

    /**
     * Get JavaBean properties to be serialised. The order is respected. This
     * method may be overridden to provide custom property selection or order.
     *
     * @param type
     * - JavaBean to inspect the properties
     * @return properties to serialise
     */
    protected fun getProperties(type: Class<out Any>): Set<Property?>? {
        return if (typeDefinitions.containsKey(type)) {
            typeDefinitions[type]!!.getProperties()
        } else getPropertyUtils().getProperties(type)
    }
}
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
package org.yaml.snakeyaml.extensions.compactnotation

import org.yaml.snakeyaml.constructor.Construct
import org.yaml.snakeyaml.constructor.Constructor
import org.yaml.snakeyaml.error.YAMLException
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.ScalarNode
import org.yaml.snakeyaml.nodes.SequenceNode
import java.util.regex.Pattern

/**
 * Construct a custom Java instance out of a compact object notation format.
 */
open class CompactConstructor : Constructor() {
    private var compactConstruct: Construct? = null
        private get() {
            if (field == null) {
                field = createCompactConstruct()
            }
            return field
        }

    protected fun constructCompactFormat(node: ScalarNode?, data: CompactData): Any {
        return try {
            val obj = createInstance(node, data)
            val properties: Map<String?, Any?> = HashMap<String?, Any?>(data.properties)
            setProperties(obj, properties)
            obj
        } catch (e: Exception) {
            throw YAMLException(e)
        }
    }

    @Throws(Exception::class)
    protected fun createInstance(node: ScalarNode?, data: CompactData): Any {
        val clazz = getClassForName(data.prefix)
        val args: Array<Class<*>?> = arrayOfNulls(data.arguments.size)
        for (i in args.indices) {
            // assume all the arguments are Strings
            args[i] = String::class.java
        }
        val c = clazz.getDeclaredConstructor(*args)
        c.isAccessible = true
        return c.newInstance(*data.arguments.toTypedArray())
    }

    @Throws(Exception::class)
    protected fun setProperties(bean: Any, data: Map<String?, Any?>?) {
        if (data == null) {
            throw NullPointerException("Data for Compact Object Notation cannot be null.")
        }
        for ((key, value) in data) {
            val property = propertyUtils!!.getProperty(bean.javaClass, key)
            try {
                property!![bean] = value
            } catch (e: IllegalArgumentException) {
                throw YAMLException(
                    "Cannot set property='" + key + "' with value='"
                            + data[key] + "' (" + data[key]!!.javaClass + ") in " + bean
                )
            }
        }
    }

    fun getCompactData(scalar: String?): CompactData? {
        if (!scalar!!.endsWith(")")) {
            return null
        }
        if (scalar.indexOf('(') < 0) {
            return null
        }
        val m = FIRST_PATTERN.matcher(scalar)
        if (m.matches()) {
            val tag = m.group(1).trim { it <= ' ' }
            val content = m.group(3)
            val data = CompactData(tag)
            if (content.length == 0) return data
            val names = content.split("\\s*,\\s*".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (i in names.indices) {
                val section = names[i]
                if (section.indexOf('=') < 0) {
                    data.arguments.add(section)
                } else {
                    val sm = PROPERTY_NAME_PATTERN.matcher(section)
                    if (sm.matches()) {
                        val name = sm.group(1)
                        val value = sm.group(2).trim { it <= ' ' }
                        data.properties[name] = value
                    } else {
                        return null
                    }
                }
            }
            return data
        }
        return null
    }

    protected fun createCompactConstruct(): Construct {
        return ConstructCompactObject()
    }

    override fun getConstructor(node: Node?): Construct? {
        if (node is MappingNode) {
            val list = node.value
            if (list!!.size == 1) {
                val tuple = list[0]
                val key = tuple.keyNode
                if (key is ScalarNode) {
                    if (GUESS_COMPACT.matcher(
                            key.value
                        ).matches()
                    ) {
                        return compactConstruct
                    }
                }
            }
        } else if (node is ScalarNode) {
            if (GUESS_COMPACT.matcher(node.value).matches()) {
                return compactConstruct
            }
        }
        return super.getConstructor(node)
    }

    inner class ConstructCompactObject : ConstructMapping() {
        override fun construct2ndStep(node: Node?, `object`: Any?) {
            // Compact Object Notation may contain only one entry
            val mnode = node as MappingNode
            val nodeTuple = mnode.value.iterator().next()
            val valueNode = nodeTuple.valueNode
            if (valueNode is MappingNode) {
                valueNode.setType(`object`!!.javaClass)
                constructJavaBean2ndStep(valueNode as MappingNode, `object`)
            } else {
                // value is a list
                applySequence(`object`, constructSequence((valueNode as SequenceNode)))
            }
        }

        /*
         * MappingNode and ScalarNode end up here only they assumed to be a
         * compact object's representation (@see getConstructor(Node) above)
         */
        override fun construct(node: Node?): Any? {
            val tmpNode: ScalarNode?
            tmpNode = if (node is MappingNode) {
                // Compact Object Notation may contain only one entry
                val nodeTuple = node.value.iterator().next()
                node.setTwoStepsConstruction(true)
                nodeTuple.keyNode as ScalarNode
                // return constructScalar((ScalarNode) keyNode);
            } else {
                node as ScalarNode?
            }
            val data = getCompactData(tmpNode.getValue())
                ?: // TODO: Should we throw an exception here ?
                return constructScalar(tmpNode!!)
            return constructCompactFormat(tmpNode, data)
        }
    }

    protected fun applySequence(bean: Any?, value: List<*>?) {
        try {
            val property = propertyUtils!!.getProperty(
                bean!!.javaClass,
                getSequencePropertyName(bean.javaClass)
            )
            property!![bean] = value
        } catch (e: Exception) {
            throw YAMLException(e)
        }
    }

    /**
     * Provide the name of the property which is used when the entries form a
     * sequence. The property must be a List.
     * @param bean the class to provide exactly one List property
     * @return name of the List property
     */
    protected fun getSequencePropertyName(bean: Class<*>): String? {
        val properties = propertyUtils!!.getProperties(bean)
        val iterator = properties!!.iterator()
        while (iterator.hasNext()) {
            val property = iterator.next()
            if (!MutableList::class.java.isAssignableFrom(property.type)) {
                iterator.remove()
            }
        }
        if (properties.size == 0) {
            throw YAMLException("No list property found in $bean")
        } else if (properties.size > 1) {
            throw YAMLException(
                "Many list properties found in "
                        + bean
                        + "; Please override getSequencePropertyName() to specify which property to use."
            )
        }
        return properties.iterator().next().name
    }

    companion object {
        private val GUESS_COMPACT = Pattern
            .compile("\\p{Alpha}.*\\s*\\((?:,?\\s*(?:(?:\\w*)|(?:\\p{Alpha}\\w*\\s*=.+))\\s*)+\\)")
        private val FIRST_PATTERN = Pattern.compile("(\\p{Alpha}.*)(\\s*)\\((.*?)\\)")
        private val PROPERTY_NAME_PATTERN = Pattern
            .compile("\\s*(\\p{Alpha}\\w*)\\s*=(.+)")
    }
}
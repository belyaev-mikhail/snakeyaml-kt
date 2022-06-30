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

import org.yaml.snakeyaml.composer.Composer
import org.yaml.snakeyaml.constructor.BaseConstructor
import org.yaml.snakeyaml.constructor.Constructor
import org.yaml.snakeyaml.emitter.Emitable
import org.yaml.snakeyaml.emitter.Emitter
import org.yaml.snakeyaml.error.YAMLException
import org.yaml.snakeyaml.events.Event
import org.yaml.snakeyaml.introspector.BeanAccess
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.Tag
import org.yaml.snakeyaml.parser.Parser
import org.yaml.snakeyaml.parser.ParserImpl
import org.yaml.snakeyaml.reader.StreamReader
import org.yaml.snakeyaml.reader.UnicodeReader
import org.yaml.snakeyaml.representer.Representer
import org.yaml.snakeyaml.resolver.Resolver
import org.yaml.snakeyaml.serializer.Serializer
import java.io.*
import java.util.regex.Pattern

/**
 * Public YAML interface. This class is not thread-safe. Which means that all the methods of the same
 * instance can be called only by one thread.
 * It is better to create an instance for every YAML stream.
 */
class Yaml constructor(
    constructor: BaseConstructor = Constructor(),
    representer: Representer = Representer(),
    dumperOptions: DumperOptions = DumperOptions(),
    loadingConfig: LoaderOptions = LoaderOptions(),
    resolver: Resolver = Resolver()
) {
    protected val resolver: Resolver
    /**
     * Get a meaningful name. It simplifies debugging in a multi-threaded
     * environment. If nothing is set explicitly the address of the instance is
     * returned.
     *
     * @return human readable name
     */
    /**
     * Set a meaningful name to be shown in toString()
     *
     * @param name human readable name
     */
    var name: String? = null
    protected var constructor: BaseConstructor? = null
    protected var representer: Representer? = null
    protected var dumperOptions: DumperOptions? = null
    protected var loadingConfig: LoaderOptions? = null

    /**
     * Create Yaml instance.
     *
     * @param dumperOptions DumperOptions to configure outgoing objects
     */
    constructor(dumperOptions: DumperOptions) : this(Constructor(), Representer(dumperOptions), dumperOptions) {}

    /**
     * Create Yaml instance.
     *
     * @param loadingConfig LoadingConfig to control load behavior
     */
    constructor(loadingConfig: LoaderOptions) : this(
        Constructor(loadingConfig),
        Representer(),
        DumperOptions(),
        loadingConfig
    ) {
    }

    /**
     * Create Yaml instance.
     *
     * @param representer Representer to emit outgoing objects
     */
    constructor(representer: Representer) : this(Constructor(), representer) {}

    /**
     * Create Yaml instance. It is safe to create a few instances and use them
     * in different Threads.
     *
     * @param representer   Representer to emit outgoing objects
     * @param dumperOptions DumperOptions to configure outgoing objects
     */
    constructor(representer: Representer, dumperOptions: DumperOptions) : this(
        Constructor(), representer, dumperOptions, LoaderOptions(),
        Resolver()
    ) {
    }
    /**
     * Create Yaml instance. It is safe to create a few instances and use them
     * in different Threads.
     *
     * @param constructor   BaseConstructor to construct incoming documents
     * @param representer   Representer to emit outgoing objects
     * @param dumperOptions DumperOptions to configure outgoing objects
     */
    /**
     * Create Yaml instance.
     *
     * @param constructor BaseConstructor to construct incoming documents
     * @param representer Representer to emit outgoing objects
     */
    /**
     * Create Yaml instance.
     *
     * @param constructor BaseConstructor to construct incoming documents
     */
    constructor(
        constructor: BaseConstructor,
        representer: Representer = Representer(),
        dumperOptions: DumperOptions = initDumperOptions(representer)
    ) : this(constructor, representer, dumperOptions, LoaderOptions(), Resolver()) {
    }

    /**
     * Create Yaml instance. It is safe to create a few instances and use them
     * in different Threads.
     *
     * @param constructor   BaseConstructor to construct incoming documents
     * @param representer   Representer to emit outgoing objects
     * @param dumperOptions DumperOptions to configure outgoing objects
     * @param resolver      Resolver to detect implicit type
     */
    constructor(
        constructor: BaseConstructor, representer: Representer, dumperOptions: DumperOptions,
        resolver: Resolver
    ) : this(constructor, representer, dumperOptions, LoaderOptions(), resolver) {
    }
    /**
     * Create Yaml instance. It is safe to create a few instances and use them
     * in different Threads.
     *
     * @param constructor           BaseConstructor to construct incoming documents
     * @param representer           Representer to emit outgoing objects
     * @param dumperOptions         DumperOptions to configure outgoing objects
     * @param loadingConfig         LoadingConfig to control load behavior
     * @param resolver              Resolver to detect implicit type
     */
    /**
     * Create Yaml instance.
     */
    /**
     * Create Yaml instance. It is safe to create a few instances and use them
     * in different Threads.
     *
     * @param constructor   BaseConstructor to construct incoming documents
     * @param representer   Representer to emit outgoing objects
     * @param dumperOptions DumperOptions to configure outgoing objects
     * @param loadingConfig LoadingConfig to control load behavior
     */
    init {
        if (!constructor.isExplicitPropertyUtils) {
            constructor.propertyUtils = representer.propertyUtils
        } else if (!representer.isExplicitPropertyUtils) {
            representer.propertyUtils = constructor.propertyUtils
        }
        this.constructor = constructor
        this.constructor!!.isAllowDuplicateKeys = loadingConfig.isAllowDuplicateKeys
        this.constructor!!.isWrappedToRootException = loadingConfig.isWrappedToRootException
        if (!dumperOptions.indentWithIndicator && dumperOptions.indent <= dumperOptions.indicatorIndent) {
            throw YAMLException("Indicator indent must be smaller then indent.")
        }
        representer.defaultFlowStyle = dumperOptions.defaultFlowStyle
        representer.defaultScalarStyle = dumperOptions.defaultScalarStyle
        representer.propertyUtils.isAllowReadOnlyProperties = dumperOptions.isAllowReadOnlyProperties
        representer.timeZone = dumperOptions.timeZone
        this.representer = representer
        this.dumperOptions = dumperOptions
        this.loadingConfig = loadingConfig
        this.resolver = resolver
        name = "Yaml:" + System.identityHashCode(this)
    }

    /**
     * Serialize a Java object into a YAML String.
     *
     * @param data Java object to be Serialized to YAML
     * @return YAML String
     */
    fun dump(data: Any): String {
        val list: MutableList<Any> = ArrayList(1)
        list.add(data)
        return dumpAll(list.iterator())
    }

    /**
     * Produce the corresponding representation tree for a given Object.
     *
     * @param data instance to build the representation tree for
     * @return representation tree
     * @see [Figure 3.1. Processing
     * Overview](http://yaml.org/spec/1.1/.id859333)
     */
    fun represent(data: Any?): Node {
        return representer!!.represent(data)
    }

    /**
     * Serialize a sequence of Java objects into a YAML String.
     *
     * @param data Iterator with Objects
     * @return YAML String with all the objects in proper sequence
     */
    fun dumpAll(data: Iterator<Any>): String {
        val buffer = StringWriter()
        dumpAll(data, buffer, null)
        return buffer.toString()
    }

    /**
     * Serialize a Java object into a YAML stream.
     *
     * @param data   Java object to be serialized to YAML
     * @param output stream to write to
     */
    fun dump(data: Any, output: Writer) {
        val list: MutableList<Any> = ArrayList(1)
        list.add(data)
        dumpAll(list.iterator(), output, null)
    }

    /**
     * Serialize a sequence of Java objects into a YAML stream.
     *
     * @param data   Iterator with Objects
     * @param output stream to write to
     */
    fun dumpAll(data: Iterator<Any>, output: Writer) {
        dumpAll(data, output, null)
    }

    private fun dumpAll(data: Iterator<Any>, output: Writer, rootTag: Tag?) {
        val serializer = Serializer(
            Emitter(output, dumperOptions), resolver,
            dumperOptions, rootTag
        )
        try {
            serializer.open()
            while (data.hasNext()) {
                val node = representer!!.represent(data.next())
                serializer.serialize(node)
            }
            serializer.close()
        } catch (e: IOException) {
            throw YAMLException(e)
        }
    }

    /**
     *
     *
     * Serialize a Java object into a YAML string. Override the default root tag
     * with `rootTag`.
     *
     *
     *
     *
     * This method is similar to `Yaml.dump(data)` except that the
     * root tag for the whole document is replaced with the given tag. This has
     * two main uses.
     *
     *
     *
     *
     * First, if the root tag is replaced with a standard YAML tag, such as
     * `Tag.MAP`, then the object will be dumped as a map. The root
     * tag will appear as `!!map`, or blank (implicit !!map).
     *
     *
     *
     *
     * Second, if the root tag is replaced by a different custom tag, then the
     * document appears to be a different type when loaded. For example, if an
     * instance of MyClass is dumped with the tag !!YourClass, then it will be
     * handled as an instance of YourClass when loaded.
     *
     *
     * @param data      Java object to be serialized to YAML
     * @param rootTag   the tag for the whole YAML document. The tag should be Tag.MAP
     * for a JavaBean to make the tag disappear (to use implicit tag
     * !!map). If `null` is provided then the standard tag
     * with the full class name is used.
     * @param flowStyle flow style for the whole document. See Chapter 10. Collection
     * Styles http://yaml.org/spec/1.1/#id930798. If
     * `null` is provided then the flow style from
     * DumperOptions is used.
     * @return YAML String
     */
    fun dumpAs(data: Any, rootTag: Tag?, flowStyle: DumperOptions.FlowStyle?): String {
        val oldStyle = representer!!.defaultFlowStyle
        if (flowStyle != null) {
            representer!!.defaultFlowStyle = flowStyle
        }
        val list: MutableList<Any> = ArrayList(1)
        list.add(data)
        val buffer = StringWriter()
        dumpAll(list.iterator(), buffer, rootTag)
        representer!!.defaultFlowStyle = oldStyle
        return buffer.toString()
    }

    /**
     *
     *
     * Serialize a Java object into a YAML string. Override the default root tag
     * with `Tag.MAP`.
     *
     *
     *
     * This method is similar to `Yaml.dump(data)` except that the
     * root tag for the whole document is replaced with `Tag.MAP` tag
     * (implicit !!map).
     *
     *
     *
     * Block Mapping is used as the collection style. See 10.2.2. Block Mappings
     * (http://yaml.org/spec/1.1/#id934537)
     *
     *
     * @param data Java object to be serialized to YAML
     * @return YAML String
     */
    fun dumpAsMap(data: Any): String {
        return dumpAs(data, Tag.MAP, DumperOptions.FlowStyle.BLOCK)
    }

    /**
     * Serialize (dump) a YAML node into a YAML stream.
     *
     * @param node   YAML node to be serialized to YAML
     * @param output stream to write to
     */
    fun serialize(node: Node?, output: Writer?) {
        val serializer = Serializer(
            Emitter(output, dumperOptions), resolver,
            dumperOptions, null
        )
        try {
            serializer.open()
            serializer.serialize(node)
            serializer.close()
        } catch (e: IOException) {
            throw YAMLException(e)
        }
    }

    /**
     * Serialize the representation tree into Events.
     *
     * @param data representation tree
     * @return Event list
     * @see [Processing Overview](http://yaml.org/spec/1.1/.id859333)
     */
    fun serialize(data: Node?): List<Event> {
        val emitter = SilentEmitter()
        val serializer = Serializer(emitter, resolver, dumperOptions, null)
        try {
            serializer.open()
            serializer.serialize(data)
            serializer.close()
        } catch (e: IOException) {
            throw YAMLException(e)
        }
        return emitter.getEvents()
    }

    private class SilentEmitter : Emitable {
        private val events: MutableList<Event> = ArrayList(100)
        fun getEvents(): List<Event> {
            return events
        }

        @Throws(IOException::class)
        override fun emit(event: Event) {
            events.add(event)
        }
    }

    /**
     * Parse the only YAML document in a String and produce the corresponding
     * Java object. (Because the encoding in known BOM is not respected.)
     *
     * @param yaml YAML data to load from (BOM must not be present)
     * @param <T>  the class of the instance to be created
     * @return parsed object
    </T> */
    fun <T> load(yaml: String?): T {
        return loadFromReader(StreamReader(yaml), Any::class.java) as T
    }

    /**
     * Parse the only YAML document in a stream and produce the corresponding
     * Java object.
     *
     * @param io  data to load from (BOM is respected to detect encoding and removed from the data)
     * @param <T> the class of the instance to be created
     * @return parsed object
    </T> */
    fun <T> load(io: InputStream?): T {
        return loadFromReader(StreamReader(UnicodeReader(io)), Any::class.java) as T
    }

    /**
     * Parse the only YAML document in a stream and produce the corresponding
     * Java object.
     *
     * @param io  data to load from (BOM must not be present)
     * @param <T> the class of the instance to be created
     * @return parsed object
    </T> */
    fun <T> load(io: Reader?): T {
        return loadFromReader(StreamReader(io), Any::class.java) as T
    }

    /**
     * Parse the only YAML document in a stream and produce the corresponding
     * Java object.
     *
     * @param <T>  Class is defined by the second argument
     * @param io   data to load from (BOM must not be present)
     * @param type Class of the object to be created
     * @return parsed object
    </T> */
    fun <T> loadAs(io: Reader?, type: Class<T>): T {
        return loadFromReader(StreamReader(io), type) as T
    }

    /**
     * Parse the only YAML document in a String and produce the corresponding
     * Java object. (Because the encoding in known BOM is not respected.)
     *
     * @param <T>  Class is defined by the second argument
     * @param yaml YAML data to load from (BOM must not be present)
     * @param type Class of the object to be created
     * @return parsed object
    </T> */
    fun <T> loadAs(yaml: String?, type: Class<T>): T {
        return loadFromReader(StreamReader(yaml), type) as T
    }

    /**
     * Parse the only YAML document in a stream and produce the corresponding
     * Java object.
     *
     * @param <T>   Class is defined by the second argument
     * @param input data to load from (BOM is respected to detect encoding and removed from the data)
     * @param type  Class of the object to be created
     * @return parsed object
    </T> */
    fun <T> loadAs(input: InputStream?, type: Class<T>): T {
        return loadFromReader(StreamReader(UnicodeReader(input)), type) as T
    }

    private fun loadFromReader(sreader: StreamReader, type: Class<*>): Any {
        val composer = Composer(
            ParserImpl(
                sreader,
                loadingConfig!!.isProcessComments
            ), resolver, loadingConfig!!
        )
        constructor!!.setComposer(composer)
        return constructor!!.getSingleData(type)
    }

    /**
     * Parse all YAML documents in the Reader and produce corresponding Java
     * objects. The documents are parsed only when the iterator is invoked.
     *
     * @param yaml YAML data to load from (BOM must not be present)
     * @return an Iterable over the parsed Java objects in this String in proper
     * sequence
     */
    fun loadAll(yaml: Reader?): Iterable<Any> {
        val composer = Composer(
            ParserImpl(
                StreamReader(yaml),
                loadingConfig!!.isProcessComments
            ), resolver, loadingConfig!!
        )
        constructor!!.setComposer(composer)
        val result: MutableIterator<Any> = object : MutableIterator<Any> {
            override fun hasNext(): Boolean {
                return constructor!!.checkData()
            }

            override fun next(): Any {
                return constructor!!.data
            }

            override fun remove() {
                throw UnsupportedOperationException()
            }
        }
        return YamlIterable(result)
    }

    private class YamlIterable(private val iterator: MutableIterator<Any>) : Iterable<Any> {
        override fun iterator(): MutableIterator<Any> {
            return iterator
        }
    }

    /**
     * Parse all YAML documents in a String and produce corresponding Java
     * objects. (Because the encoding in known BOM is not respected.) The
     * documents are parsed only when the iterator is invoked.
     *
     * @param yaml YAML data to load from (BOM must not be present)
     * @return an Iterable over the parsed Java objects in this String in proper
     * sequence
     */
    fun loadAll(yaml: String?): Iterable<Any> {
        return loadAll(StringReader(yaml))
    }

    /**
     * Parse all YAML documents in a stream and produce corresponding Java
     * objects. The documents are parsed only when the iterator is invoked.
     *
     * @param yaml YAML data to load from (BOM is respected to detect encoding and removed from the data)
     * @return an Iterable over the parsed Java objects in this stream in proper
     * sequence
     */
    fun loadAll(yaml: InputStream?): Iterable<Any> {
        return loadAll(UnicodeReader(yaml))
    }

    /**
     * Parse the first YAML document in a stream and produce the corresponding
     * representation tree. (This is the opposite of the represent() method)
     *
     * @param yaml YAML document
     * @return parsed root Node for the specified YAML document
     * @see [Figure 3.1. Processing
     * Overview](http://yaml.org/spec/1.1/.id859333)
     */
    fun compose(yaml: Reader?): Node? {
        val composer = Composer(
            ParserImpl(
                StreamReader(yaml),
                loadingConfig!!.isProcessComments
            ), resolver, loadingConfig!!
        )
        return composer.singleNode
    }

    /**
     * Parse all YAML documents in a stream and produce corresponding
     * representation trees.
     *
     * @param yaml stream of YAML documents
     * @return parsed root Nodes for all the specified YAML documents
     * @see [Processing Overview](http://yaml.org/spec/1.1/.id859333)
     */
    fun composeAll(yaml: Reader?): Iterable<Node> {
        val composer = Composer(
            ParserImpl(
                StreamReader(yaml),
                loadingConfig!!.isProcessComments
            ), resolver, loadingConfig!!
        )
        val result: MutableIterator<Node> = object : MutableIterator<Node> {
            override fun hasNext(): Boolean {
                return composer.checkNode()
            }

            override fun next(): Node {
                val node = composer.node
                return node ?: throw NoSuchElementException("No Node is available.")
            }

            override fun remove() {
                throw UnsupportedOperationException()
            }
        }
        return NodeIterable(result)
    }

    private class NodeIterable(private val iterator: MutableIterator<Node>) : Iterable<Node> {
        override fun iterator(): MutableIterator<Node> {
            return iterator
        }
    }

    /**
     * Add an implicit scalar detector. If an implicit scalar value matches the
     * given regexp, the corresponding tag is assigned to the scalar.
     *
     * @param tag    tag to assign to the node
     * @param regexp regular expression to match against
     * @param first  a sequence of possible initial characters or null (which means
     * any).
     */
    fun addImplicitResolver(tag: Tag?, regexp: Pattern?, first: String?) {
        resolver.addImplicitResolver(tag, regexp, first)
    }

    /**
     * Add an implicit scalar detector. If an implicit scalar value matches the
     * given regexp, the corresponding tag is assigned to the scalar.
     *
     * @param tag    tag to assign to the node
     * @param regexp regular expression to match against
     * @param first  a sequence of possible initial characters or null (which means
     * any).
     * @param limit the max length of the value which may match the regular expression
     */
    fun addImplicitResolver(tag: Tag?, regexp: Pattern?, first: String?, limit: Int) {
        resolver.addImplicitResolver(tag, regexp, first, limit)
    }

    override fun toString(): String {
        return name!!
    }

    /**
     * Parse a YAML stream and produce parsing events.
     *
     * @param yaml YAML document(s)
     * @return parsed events
     * @see [Processing Overview](http://yaml.org/spec/1.1/.id859333)
     */
    fun parse(yaml: Reader?): Iterable<Event> {
        val parser: Parser = ParserImpl(
            StreamReader(yaml),
            loadingConfig!!.isProcessComments
        )
        val result: MutableIterator<Event> = object : MutableIterator<Event> {
            override fun hasNext(): Boolean {
                return parser.peekEvent() != null
            }

            override fun next(): Event {
                val event = parser.event
                return event ?: throw NoSuchElementException("No Event is available.")
            }

            override fun remove() {
                throw UnsupportedOperationException()
            }
        }
        return EventIterable(result)
    }

    private class EventIterable(private val iterator: MutableIterator<Event>) : Iterable<Event> {
        override fun iterator(): MutableIterator<Event> {
            return iterator
        }
    }

    fun setBeanAccess(beanAccess: BeanAccess?) {
        constructor!!.propertyUtils.setBeanAccess(beanAccess)
        representer!!.propertyUtils.setBeanAccess(beanAccess)
    }

    fun addTypeDescription(td: TypeDescription?) {
        constructor!!.addTypeDescription(td)
        representer!!.addTypeDescription(td)
    }

    companion object {
        private fun initDumperOptions(representer: Representer): DumperOptions {
            val dumperOptions = DumperOptions()
            dumperOptions.defaultFlowStyle = representer.defaultFlowStyle
            dumperOptions.defaultScalarStyle = representer.defaultScalarStyle
            dumperOptions.isAllowReadOnlyProperties = representer.propertyUtils.isAllowReadOnlyProperties
            dumperOptions.timeZone = representer.timeZone
            return dumperOptions
        }
    }
}
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
import org.yaml.snakeyaml.error.YAMLException
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.Tag
import org.yaml.snakeyaml.reader.StreamReader
import java.io.UnsupportedEncodingException
import java.math.BigInteger
import java.util.*
import java.util.regex.Pattern

/**
 * Represent standard Java classes
 */
open class SafeRepresenter @JvmOverloads constructor(options: DumperOptions = DumperOptions()) : BaseRepresenter() {
    protected var classTags: MutableMap<Class<out Any>, Tag>
    var timeZone: TimeZone? = null
    protected var nonPrintableStyle: DumperOptions.NonPrintableStyle
    protected fun getTag(clazz: Class<*>, defaultTag: Tag?): Tag? {
        return if (classTags.containsKey(clazz)) {
            classTags[clazz]
        } else {
            defaultTag
        }
    }

    /**
     * Define a tag for the `Class` to serialize.
     *
     * @param clazz
     * `Class` which tag is changed
     * @param tag
     * new tag to be used for every instance of the specified
     * `Class`
     * @return the previous tag associated with the `Class`
     */
    fun addClassTag(clazz: Class<out Any>, tag: Tag?): Tag? {
        if (tag == null) {
            throw NullPointerException("Tag must be provided.")
        }
        return classTags.put(clazz, tag)
    }

    protected inner class RepresentNull : Represent {
        override fun representData(data: Any?): Node? {
            return representScalar(Tag.Companion.NULL, "null")
        }
    }

    init {
        nullRepresenter = RepresentNull()
        representers[String::class.java] = RepresentString()
        representers[Boolean::class.java] = RepresentBoolean()
        representers[Char::class.java] = RepresentString()
        representers[UUID::class.java] = RepresentUuid()
        representers[ByteArray::class.java] = RepresentByteArray()
        val primitiveArray: Represent = RepresentPrimitiveArray()
        representers[ShortArray::class.java] = primitiveArray
        representers[IntArray::class.java] = primitiveArray
        representers[LongArray::class.java] = primitiveArray
        representers[FloatArray::class.java] = primitiveArray
        representers[DoubleArray::class.java] = primitiveArray
        representers[CharArray::class.java] = primitiveArray
        representers[BooleanArray::class.java] = primitiveArray
        multiRepresenters[Number::class.java] = RepresentNumber()
        multiRepresenters[MutableList::class.java] = RepresentList()
        multiRepresenters[MutableMap::class.java] = RepresentMap()
        multiRepresenters[MutableSet::class.java] = RepresentSet()
        multiRepresenters[MutableIterator::class.java] =
            RepresentIterator()
        multiRepresenters[arrayOfNulls<Any>(0).javaClass] = RepresentArray()
        multiRepresenters[Date::class.java] = RepresentDate()
        multiRepresenters[Enum::class.java] = RepresentEnum()
        multiRepresenters[Calendar::class.java] = RepresentDate()
        classTags = HashMap()
        nonPrintableStyle = options.nonPrintableStyle
    }

    protected inner class RepresentString : Represent {
        override fun representData(data: Any?): Node? {
            var tag: Tag = Tag.Companion.STR
            var style: DumperOptions.ScalarStyle? = null //not defined
            var value = data.toString()
            if (nonPrintableStyle === DumperOptions.NonPrintableStyle.BINARY && !StreamReader.Companion.isPrintable(
                    value
                )
            ) {
                tag = Tag.Companion.BINARY
                val binary: CharArray
                try {
                    val bytes = value.toByteArray(charset("UTF-8"))
                    // sometimes above will just silently fail - it will return incomplete data
                    // it happens when String has invalid code points
                    // (for example half surrogate character without other half)
                    val checkValue = String(bytes, "UTF-8")
                    if (checkValue != value) {
                        throw YAMLException("invalid string value has occurred")
                    }
                    binary = encode(bytes)
                } catch (e: UnsupportedEncodingException) {
                    throw YAMLException(e)
                }
                value = String(binary)
                style = DumperOptions.ScalarStyle.LITERAL
            }
            // if no other scalar style is explicitly set, use literal style for
            // multiline scalars
            if (defaultScalarStyle === DumperOptions.ScalarStyle.PLAIN && MULTILINE_PATTERN.matcher(value).find()) {
                style = DumperOptions.ScalarStyle.LITERAL
            }
            return representScalar(tag, value, style)
        }
    }

    protected inner class RepresentBoolean : Represent {
        override fun representData(data: Any?): Node? {
            val value: String
            value = if (java.lang.Boolean.TRUE == data) {
                "true"
            } else {
                "false"
            }
            return representScalar(Tag.Companion.BOOL, value)
        }
    }

    protected inner class RepresentNumber : Represent {
        override fun representData(data: Any?): Node? {
            val tag: Tag
            val value: String
            if (data is Byte || data is Short || data is Int
                || data is Long || data is BigInteger
            ) {
                tag = Tag.Companion.INT
                value = data.toString()
            } else {
                val number = data as Number?
                tag = Tag.Companion.FLOAT
                value = if (number == Double.NaN) {
                    ".NaN"
                } else if (number == Double.POSITIVE_INFINITY) {
                    ".inf"
                } else if (number == Double.NEGATIVE_INFINITY) {
                    "-.inf"
                } else {
                    number.toString()
                }
            }
            return representScalar(getTag(data!!.javaClass, tag), value)
        }
    }

    protected inner class RepresentList : Represent {
        override fun representData(data: Any?): Node? {
            return representSequence(
                getTag(data!!.javaClass, Tag.Companion.SEQ),
                (data as List<Any?>?)!!,
                DumperOptions.FlowStyle.AUTO
            )
        }
    }

    protected inner class RepresentIterator : Represent {
        override fun representData(data: Any?): Node? {
            val iter = (data as MutableIterator<Any>?)!!
            return representSequence(
                getTag(data!!.javaClass, Tag.Companion.SEQ), IteratorWrapper(iter),
                DumperOptions.FlowStyle.AUTO
            )
        }
    }

    private class IteratorWrapper(private val iter: MutableIterator<Any>) : Iterable<Any?> {
        override fun iterator(): MutableIterator<Any> {
            return iter
        }
    }

    protected inner class RepresentArray : Represent {
        override fun representData(data: Any?): Node? {
            val array = data as Array<Any>?
            val list = Arrays.asList(*array)
            return representSequence(Tag.Companion.SEQ, list, DumperOptions.FlowStyle.AUTO)
        }
    }

    /**
     * Represents primitive arrays, such as short[] and float[], by converting
     * them into equivalent List<Short> and List<Float> using the appropriate
     * autoboxing type.
    </Float></Short> */
    protected inner class RepresentPrimitiveArray : Represent {
        override fun representData(data: Any?): Node? {
            val type = data!!.javaClass.componentType
            if (Byte::class.javaPrimitiveType == type) {
                return representSequence(Tag.Companion.SEQ, asByteList(data), DumperOptions.FlowStyle.AUTO)
            } else if (Short::class.javaPrimitiveType == type) {
                return representSequence(Tag.Companion.SEQ, asShortList(data), DumperOptions.FlowStyle.AUTO)
            } else if (Int::class.javaPrimitiveType == type) {
                return representSequence(Tag.Companion.SEQ, asIntList(data), DumperOptions.FlowStyle.AUTO)
            } else if (Long::class.javaPrimitiveType == type) {
                return representSequence(Tag.Companion.SEQ, asLongList(data), DumperOptions.FlowStyle.AUTO)
            } else if (Float::class.javaPrimitiveType == type) {
                return representSequence(Tag.Companion.SEQ, asFloatList(data), DumperOptions.FlowStyle.AUTO)
            } else if (Double::class.javaPrimitiveType == type) {
                return representSequence(Tag.Companion.SEQ, asDoubleList(data), DumperOptions.FlowStyle.AUTO)
            } else if (Char::class.javaPrimitiveType == type) {
                return representSequence(Tag.Companion.SEQ, asCharList(data), DumperOptions.FlowStyle.AUTO)
            } else if (Boolean::class.javaPrimitiveType == type) {
                return representSequence(Tag.Companion.SEQ, asBooleanList(data), DumperOptions.FlowStyle.AUTO)
            }
            throw YAMLException("Unexpected primitive '" + type.canonicalName + "'")
        }

        private fun asByteList(`in`: Any?): List<Byte?> {
            val array = `in` as ByteArray?
            val list: MutableList<Byte?> = ArrayList(array!!.size)
            for (i in array.indices) list.add(array[i])
            return list
        }

        private fun asShortList(`in`: Any?): List<Short?> {
            val array = `in` as ShortArray?
            val list: MutableList<Short?> = ArrayList(array!!.size)
            for (i in array.indices) list.add(array[i])
            return list
        }

        private fun asIntList(`in`: Any?): List<Int?> {
            val array = `in` as IntArray?
            val list: MutableList<Int?> = ArrayList(array!!.size)
            for (i in array.indices) list.add(array[i])
            return list
        }

        private fun asLongList(`in`: Any?): List<Long?> {
            val array = `in` as LongArray?
            val list: MutableList<Long?> = ArrayList(array!!.size)
            for (i in array.indices) list.add(array[i])
            return list
        }

        private fun asFloatList(`in`: Any?): List<Float?> {
            val array = `in` as FloatArray?
            val list: MutableList<Float?> = ArrayList(array!!.size)
            for (i in array.indices) list.add(array[i])
            return list
        }

        private fun asDoubleList(`in`: Any?): List<Double?> {
            val array = `in` as DoubleArray?
            val list: MutableList<Double?> = ArrayList(array!!.size)
            for (i in array.indices) list.add(array[i])
            return list
        }

        private fun asCharList(`in`: Any?): List<Char?> {
            val array = `in` as CharArray?
            val list: MutableList<Char?> = ArrayList(array!!.size)
            for (i in array.indices) list.add(array[i])
            return list
        }

        private fun asBooleanList(`in`: Any?): List<Boolean?> {
            val array = `in` as BooleanArray?
            val list: MutableList<Boolean?> = ArrayList(array!!.size)
            for (i in array.indices) list.add(array[i])
            return list
        }
    }

    protected inner class RepresentMap : Represent {
        override fun representData(data: Any?): Node? {
            return representMapping(
                getTag(data!!.javaClass, Tag.Companion.MAP), (data as Map<Any?, Any?>?)!!,
                DumperOptions.FlowStyle.AUTO
            )
        }
    }

    protected inner class RepresentSet : Represent {
        override fun representData(data: Any?): Node? {
            val value: MutableMap<Any?, Any?> = LinkedHashMap()
            val set = data as Set<Any>?
            for (key in set!!) {
                value[key] = null
            }
            return representMapping(getTag(data!!.javaClass, Tag.Companion.SET), value, DumperOptions.FlowStyle.AUTO)
        }
    }

    protected inner class RepresentDate : Represent {
        override fun representData(data: Any?): Node? {
            // because SimpleDateFormat ignores timezone we have to use Calendar
            val calendar: Calendar
            if (data is Calendar) {
                calendar = data
            } else {
                calendar = Calendar.getInstance(if (timeZone == null) TimeZone.getTimeZone("UTC") else timeZone)
                calendar.time = data as Date?
            }
            val years = calendar[Calendar.YEAR]
            val months = calendar[Calendar.MONTH] + 1 // 0..12
            val days = calendar[Calendar.DAY_OF_MONTH] // 1..31
            val hour24 = calendar[Calendar.HOUR_OF_DAY] // 0..24
            val minutes = calendar[Calendar.MINUTE] // 0..59
            val seconds = calendar[Calendar.SECOND] // 0..59
            val millis = calendar[Calendar.MILLISECOND]
            val buffer = StringBuilder(years.toString())
            while (buffer.length < 4) {
                // ancient years
                buffer.insert(0, "0")
            }
            buffer.append("-")
            if (months < 10) {
                buffer.append("0")
            }
            buffer.append(months.toString())
            buffer.append("-")
            if (days < 10) {
                buffer.append("0")
            }
            buffer.append(days.toString())
            buffer.append("T")
            if (hour24 < 10) {
                buffer.append("0")
            }
            buffer.append(hour24.toString())
            buffer.append(":")
            if (minutes < 10) {
                buffer.append("0")
            }
            buffer.append(minutes.toString())
            buffer.append(":")
            if (seconds < 10) {
                buffer.append("0")
            }
            buffer.append(seconds.toString())
            if (millis > 0) {
                if (millis < 10) {
                    buffer.append(".00")
                } else if (millis < 100) {
                    buffer.append(".0")
                } else {
                    buffer.append(".")
                }
                buffer.append(millis.toString())
            }

            // Get the offset from GMT taking DST into account
            var gmtOffset = calendar.timeZone.getOffset(calendar.time.time)
            if (gmtOffset == 0) {
                buffer.append('Z')
            } else {
                if (gmtOffset < 0) {
                    buffer.append('-')
                    gmtOffset *= -1
                } else {
                    buffer.append('+')
                }
                val minutesOffset = gmtOffset / (60 * 1000)
                val hoursOffset = minutesOffset / 60
                val partOfHour = minutesOffset % 60
                if (hoursOffset < 10) {
                    buffer.append('0')
                }
                buffer.append(hoursOffset)
                buffer.append(':')
                if (partOfHour < 10) {
                    buffer.append('0')
                }
                buffer.append(partOfHour)
            }
            return representScalar(
                getTag(data!!.javaClass, Tag.Companion.TIMESTAMP),
                buffer.toString(),
                DumperOptions.ScalarStyle.PLAIN
            )
        }
    }

    protected inner class RepresentEnum : Represent {
        override fun representData(data: Any?): Node? {
            val tag = Tag(data!!.javaClass)
            return representScalar(getTag(data.javaClass, tag), (data as Enum<*>?)!!.name)
        }
    }

    protected inner class RepresentByteArray : Represent {
        override fun representData(data: Any?): Node? {
            val binary: CharArray = encode(data as ByteArray?)
            return representScalar(Tag.Companion.BINARY, String(binary), DumperOptions.ScalarStyle.LITERAL)
        }
    }

    protected inner class RepresentUuid : Represent {
        override fun representData(data: Any?): Node? {
            return representScalar(getTag(data!!.javaClass, Tag(UUID::class.java)), data.toString())
        }
    }

    companion object {
        private val MULTILINE_PATTERN = Pattern.compile("\n|\u0085|\u2028|\u2029")
    }
}
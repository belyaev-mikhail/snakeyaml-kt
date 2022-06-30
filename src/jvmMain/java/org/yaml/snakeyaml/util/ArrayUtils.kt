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
package org.yaml.snakeyaml.util

import java.util.*

object ArrayUtils {
    /**
     * Returns an unmodifiable `List` backed by the given array. The method doesn't copy the array, so the changes
     * to the array will affect the `List` as well.
     * @param <E> class of the elements in the array
     * @param elements - array to convert
     * @return `List` backed by the given array
    </E> */
    fun <E> toUnmodifiableList(elements: Array<E>): List<E> {
        return if (elements.size == 0) emptyList() else UnmodifiableArrayList(elements)
    }

    /**
     * Returns an unmodifiable `List` containing the second array appended to the first one. The method doesn't copy
     * the arrays, so the changes to the arrays will affect the `List` as well.
     * @param <E> class of the elements in the array
     * @param array1 - the array to extend
     * @param array2 - the array to add to the first
     * @return `List` backed by the given arrays
    </E> */
    fun <E> toUnmodifiableCompositeList(array1: Array<E>, array2: Array<E>): List<E> {
        val result: List<E>
        result = if (array1.size == 0) {
            toUnmodifiableList(array2)
        } else if (array2.size == 0) {
            toUnmodifiableList(array1)
        } else {
            CompositeUnmodifiableArrayList(array1, array2)
        }
        return result
    }

    private class UnmodifiableArrayList<E> internal constructor(private val array: Array<E>) : AbstractList<E>() {
        override fun get(index: Int): E {
            if (index >= array.size) {
                throw IndexOutOfBoundsException("Index: $index, Size: $size")
            }
            return array[index]
        }

        override val size
            get() = array.size
    }

    private class CompositeUnmodifiableArrayList<E> internal constructor(
        private val array1: Array<E>,
        private val array2: Array<E>
    ) : AbstractList<E>() {
        override fun get(index: Int): E {
            val element: E
            element = if (index < array1.size) {
                array1[index]
            } else if (index - array1.size < array2.size) {
                array2[index - array1.size]
            } else {
                throw IndexOutOfBoundsException("Index: $index, Size: $size")
            }
            return element
        }

        override val size: Int
            get() = array1.size + array2.size
    }
}
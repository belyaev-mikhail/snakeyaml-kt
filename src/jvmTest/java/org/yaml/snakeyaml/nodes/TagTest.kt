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
package org.yaml.snakeyaml.nodes

import junit.framework.TestCase

class TagTest : TestCase() {
    fun testCreate() {
        try {
            Tag(null as String?)
            fail()
        } catch (e: Exception) {
            assertEquals("Tag must be provided.", e.message)
        }
        try {
            Tag("")
            fail()
        } catch (e: Exception) {
            assertEquals("Tag must not be empty.", e.message)
        }
        try {
            Tag("!Dice ")
            fail()
        } catch (e: Exception) {
            assertEquals("Tag must not contain leading or trailing spaces.", e.message)
        }
        val tag = Tag(TagTest::class.java)
        assertEquals(Tag.PREFIX + "org.yaml.snakeyaml.nodes.TagTest", tag.value)
    }

    fun testCreate2() {
        try {
            Tag(null as Class<*>?)
            fail()
        } catch (e: Exception) {
            assertEquals("Class for tag must be provided.", e.message)
        }
    }

    fun testGetClassName() {
        val tag = Tag(Tag.PREFIX + "org.yaml.snakeyaml.nodes.TagTest")
        assertEquals("org.yaml.snakeyaml.nodes.TagTest", tag.className)
    }

    fun testGetClassNameError() {
        try {
            val tag = Tag("!TagTest")
            tag.className
            fail("Class name is only available for global tag")
        } catch (e: Exception) {
            assertEquals("Invalid tag: !TagTest", e.message)
        }
    }

    fun testToString() {
        val tag = Tag("!car")
        assertEquals("!car", tag.toString())
    }

    fun testUri1() {
        val tag = Tag("!Académico")
        assertEquals("!Acad%C3%A9mico", tag.toString())
    }

    fun testUri2() {
        val tag = Tag("!ruby/object:Test::Module::Sub2")
        assertEquals("!ruby/object:Test::Module::Sub2", tag.value)
    }

    fun testEqualsObject() {
        val tag = Tag("!car")
        assertEquals(tag, tag)
        assertEquals(tag, Tag("!car"))
        assertFalse(tag.equals(Tag("!!str")))
        assertFalse(tag.equals(null))
        assertFalse(tag.equals(25))
    }
}

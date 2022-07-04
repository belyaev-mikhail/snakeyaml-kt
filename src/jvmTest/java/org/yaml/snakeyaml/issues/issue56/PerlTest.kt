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
package org.yaml.snakeyaml.issues.issue56

import junit.framework.TestCase
import org.yaml.snakeyaml.TypeDescription
import org.yaml.snakeyaml.Util
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Construct
import org.yaml.snakeyaml.constructor.Constructor
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.Tag

class PerlTest : TestCase() {
    fun testMaps() {
        val yaml: Yaml = Yaml(CustomConstructor())
        val input = Util.getLocalResource("issues/issue56-1.yaml")
        var counter = 0
        for (obj in yaml.loadAll(input)) {
            // System.out.println(obj);
            val map = obj as Map<String, Any>
            val oid = map["oid"] as Int?
            if (oid == 123058) {
                val a = map["sequences"] as ArrayList<*>?
                val b = a!![0] as LinkedHashMap<*, *>
                val c = b["atc"] as LinkedHashMap<*, *>?
                val d = c!!["name"] as LinkedHashMap<*, *>?
                val e = d!!["canonical"] as LinkedHashMap<*, *>?
                val acidNameDe = e!!.entries.toTypedArray()[1].toString()
                assertEquals(
                    "Unicode escaped sequence must be decoded.",
                    ":de=AcetylsalicylsÃ¤ure", acidNameDe
                )
            }
            assertTrue(oid!! > 10000)
            counter++
        }
        assertEquals(4, counter)
        assertEquals(0, CodeBean.counter)
    }

    private inner class CustomConstructor : SafeConstructor() {
        init {
            // define tags which begin with !org.yaml.
            val prefix = "!de.oddb.org,2007/ODDB"
            yamlMultiConstructors.put(prefix, ConstructYamlMap())
        }
    }

    fun testJavaBeanWithTypeDescription() {
        val c: Constructor = CustomBeanConstructor()
        val descr = TypeDescription(
            CodeBean::class.java, Tag(
                "!de.oddb.org,2007/ODDB::Util::Code"
            )
        )
        c.addTypeDescription(descr)
        val yaml = Yaml(c)
        val input = Util.getLocalResource("issues/issue56-1.yaml")
        var counter = 0
        for (obj in yaml.loadAll(input)) {
            // System.out.println(obj);
            val map = obj as Map<String, Any>
            val oid = map["oid"] as Int?
            assertTrue(oid!! > 10000)
            counter++
        }
        assertEquals(4, counter)
        assertEquals(55, CodeBean.counter)
    }

    fun testJavaBean() {
        val c: Constructor = CustomBeanConstructor()
        val yaml = Yaml(c)
        val input = Util.getLocalResource("issues/issue56-1.yaml")
        var counter = 0
        for (obj in yaml.loadAll(input)) {
            // System.out.println(obj);
            val map = obj as Map<String, Any>
            val oid = map["oid"] as Int?
            assertTrue(oid!! > 10000)
            counter++
        }
        assertEquals(4, counter)
        assertEquals(55, CodeBean.counter)
    }

    private inner class CustomBeanConstructor : Constructor() {
        init {
            // define tags which begin with !org.yaml.
            val prefix = "!de.oddb.org,2007/ODDB"
            yamlMultiConstructors.put(prefix, ConstructYamlMap())
        }

        override fun getConstructor(node: Node): Construct? {
            if (node.tag.equals(Tag("!de.oddb.org,2007/ODDB::Util::Code"))) {
                node.useClassConstructor = true
                node.type = CodeBean::class.java
            }
            return super.getConstructor(node)
        }
    }

    override fun setUp() {
        CodeBean.counter = 0
    }
}

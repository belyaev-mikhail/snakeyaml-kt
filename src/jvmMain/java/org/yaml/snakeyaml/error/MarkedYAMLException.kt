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
package org.yaml.snakeyaml.error

open class MarkedYAMLException protected constructor(
    val context: String?, val contextMark: Mark?, problem: String,
    problemMark: Mark?, note: String? = null, cause: Throwable? = null
) : YAMLException(
    "$context; $problem; $problemMark", cause
) {
    val problem: String?
    val problemMark: Mark?
    private val note: String?

    init {
        this.problem = problem
        this.problemMark = problemMark
        this.note = note
    }

    protected constructor(
        context: String?, contextMark: Mark?, problem: String,
        problemMark: Mark?, cause: Throwable?
    ) : this(context, contextMark, problem, problemMark, null, cause) {
    }

    override val message: String
        get() = toString()

    override fun toString(): String {
        val lines = StringBuilder()
        if (context != null) {
            lines.append(context)
            lines.append("\n")
        }
        if (contextMark != null && (problem == null || problemMark == null || contextMark.name == problemMark.name || contextMark.line != problemMark.line || contextMark
                .column != problemMark.column)
        ) {
            lines.append(contextMark.toString())
            lines.append("\n")
        }
        if (problem != null) {
            lines.append(problem)
            lines.append("\n")
        }
        if (problemMark != null) {
            lines.append(problemMark.toString())
            lines.append("\n")
        }
        if (note != null) {
            lines.append(note)
            lines.append("\n")
        }
        return lines.toString()
    }

    companion object {
        private const val serialVersionUID = -9119388488683035101L
    }
}
package org.yaml.snakeyaml.error

import kotlin.jvm.JvmOverloads

open class MarkedYAMLException @JvmOverloads constructor(
    val context: String?, val contextMark: Mark?, val problem: String?,
    val problemMark: Mark?, private val note: String? = null, cause: Throwable? = null
) : YAMLException(
    "$context; $problem; $problemMark", cause
) {
    protected constructor(
        context: String?, contextMark: Mark?, problem: String?,
        problemMark: Mark?, cause: Throwable?
    ) : this(context, contextMark, problem, problemMark, null, cause)

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
}

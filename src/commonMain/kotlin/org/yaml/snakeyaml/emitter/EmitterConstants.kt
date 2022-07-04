package org.yaml.snakeyaml.emitter

import org.yaml.snakeyaml.tagPrefix
import kotlin.jvm.JvmStatic

object EmitterConstants {
    const val MIN_INDENT = 1
    const val MAX_INDENT = 10
    internal val SPACE = charArrayOf(' ')
    internal val SPACES_PATTERN = Regex("\\s")
    internal val INVALID_ANCHOR: MutableSet<Char> = HashSet()

    init {
        INVALID_ANCHOR.add('[')
        INVALID_ANCHOR.add(']')
        INVALID_ANCHOR.add('{')
        INVALID_ANCHOR.add('}')
        INVALID_ANCHOR.add(',')
        INVALID_ANCHOR.add('*')
        INVALID_ANCHOR.add('&')
    }

    internal val ESCAPE_REPLACEMENTS: MutableMap<Char, String> = HashMap()

    init {
        ESCAPE_REPLACEMENTS['\u0000'] = "0"
        ESCAPE_REPLACEMENTS['\u0007'] = "a"
        ESCAPE_REPLACEMENTS['\u0008'] = "b"
        ESCAPE_REPLACEMENTS['\u0009'] = "t"
        ESCAPE_REPLACEMENTS['\n'] = "n"
        ESCAPE_REPLACEMENTS['\u000B'] = "v"
        ESCAPE_REPLACEMENTS['\u000C'] = "f"
        ESCAPE_REPLACEMENTS['\r'] = "r"
        ESCAPE_REPLACEMENTS['\u001B'] = "e"
        ESCAPE_REPLACEMENTS['"'] = "\""
        ESCAPE_REPLACEMENTS['\\'] = "\\"
        ESCAPE_REPLACEMENTS['\u0085'] = "N"
        ESCAPE_REPLACEMENTS['\u00A0'] = "_"
        ESCAPE_REPLACEMENTS['\u2028'] = "L"
        ESCAPE_REPLACEMENTS['\u2029'] = "P"
    }

    internal val DEFAULT_TAG_PREFIXES: MutableMap<String, String> = LinkedHashMap()

    init {
        DEFAULT_TAG_PREFIXES["!"] = "!"
        DEFAULT_TAG_PREFIXES[tagPrefix] = "!!"
    }

    internal val HANDLE_FORMAT = Regex("^![-_\\w]*!$")
    @JvmStatic
    fun prepareAnchor(anchor: String): String {
        if (anchor.length == 0) {
            throw EmitterException("anchor must not be empty")
        }
        for (invalid in INVALID_ANCHOR) {
            if (invalid in anchor) {
                throw EmitterException("Invalid character '$invalid' in the anchor: $anchor")
            }
        }
        val matcher = SPACES_PATTERN.find(anchor)
        if (matcher != null) {
            throw EmitterException("Anchor may not contain spaces: $anchor")
        }
        return anchor
    }
}

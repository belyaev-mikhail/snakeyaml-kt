package org.yaml.snakeyaml.tokens

import org.yaml.snakeyaml.error.Mark
import org.yaml.snakeyaml.error.YAMLException

class DirectiveToken<T>(val name: String?, value: List<T>?, startMark: Mark, endMark: Mark) :
    Token(startMark, endMark) {
    val value: List<T>?

    init {
        if (value != null && value.size != 2) {
            throw YAMLException(
                "Two strings must be provided instead of "
                        + value.size.toString()
            )
        }
        this.value = value
    }

    override val tokenId: ID
        get() = ID.Directive
}

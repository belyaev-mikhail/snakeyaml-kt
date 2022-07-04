package org.yaml.snakeyaml.tokens

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.error.Mark

class ScalarToken(
    val value: String?,
    val plain: Boolean,
    startMark: Mark,
    endMark: Mark,
    val style: DumperOptions.ScalarStyle
) : Token(startMark, endMark) {

    constructor(value: String?, startMark: Mark, endMark: Mark, plain: Boolean) : this(
        value,
        plain,
        startMark,
        endMark,
        DumperOptions.ScalarStyle.PLAIN
    )

    override val tokenId: ID
        get() = ID.Scalar
}

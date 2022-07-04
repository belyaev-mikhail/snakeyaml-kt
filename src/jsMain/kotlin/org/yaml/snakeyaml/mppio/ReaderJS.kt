package org.yaml.snakeyaml.mppio

actual abstract class Reader {
    actual abstract fun read(destination: CharArray, offset: Int, limit: Int): Int
}

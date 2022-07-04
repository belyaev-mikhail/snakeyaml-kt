package org.yaml.snakeyaml.mppio

expect abstract class Reader {
    abstract fun read(destination: CharArray, offset: Int, limit: Int): Int
}

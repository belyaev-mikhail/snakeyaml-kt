package org.yaml.snakeyaml.mppio

actual class StringReader actual constructor(val s: String): Reader() {
    private var currentIndex = 0
    override fun read(destination: CharArray, offset: Int, limit: Int): Int {
        val startedAt = currentIndex
        for (dstIx in offset until (offset + limit)) {
            if (currentIndex >= s.length) break
            if (dstIx >= destination.size) break
            destination[dstIx] = s[currentIndex++]
        }
        return currentIndex - startedAt
    }
}

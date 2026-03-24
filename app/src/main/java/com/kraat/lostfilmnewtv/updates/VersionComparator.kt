package com.kraat.lostfilmnewtv.updates

object VersionComparator {

    fun isNewerThan(version: String, other: String): Boolean {
        val versionParts = extractParts(version)
        val otherParts = extractParts(other)
        val maxSize = maxOf(versionParts.size, otherParts.size)
        for (index in 0 until maxSize) {
            val versionPart = versionParts.getOrElse(index) { 0 }
            val otherPart = otherParts.getOrElse(index) { 0 }
            if (versionPart != otherPart) {
                return versionPart > otherPart
            }
        }
        return false
    }

    private fun extractParts(version: String): List<Int> =
        Regex("""\d+""")
            .findAll(version)
            .map { match -> match.value.toInt() }
            .toList()
}

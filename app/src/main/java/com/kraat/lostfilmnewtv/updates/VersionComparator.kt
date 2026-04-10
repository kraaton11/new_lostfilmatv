package com.kraat.lostfilmnewtv.updates

object VersionComparator {

    /**
     * Возвращает true если [version] новее чем [other].
     *
     * Сравнивает числовые компоненты слева направо: "1.10.0" > "1.9.0".
     * Нечисловые суффиксы (rc, beta, -SNAPSHOT) игнорируются — учитываются
     * только цифровые группы. Пример: "2.0.0-beta" == "2.0.0".
     */
    fun isNewerThan(version: String, other: String): Boolean {
        val a = numericParts(version)
        val b = numericParts(other)
        val len = maxOf(a.size, b.size)
        for (i in 0 until len) {
            val diff = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (diff != 0) return diff > 0
        }
        return false
    }

    private fun numericParts(version: String): List<Int> =
        Regex("""\d+""").findAll(version).map { it.value.toInt() }.toList()
}

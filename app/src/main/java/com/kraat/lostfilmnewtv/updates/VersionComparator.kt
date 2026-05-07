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
            val left = a.getOrElse(i) { 0L }
            val right = b.getOrElse(i) { 0L }
            if (left != right) return left > right
        }
        return false
    }

    private fun numericParts(version: String): List<Long> =
        Regex("""\d+""").findAll(version).map { match ->
            match.value.toLongOrNull() ?: Long.MAX_VALUE
        }.toList()
}

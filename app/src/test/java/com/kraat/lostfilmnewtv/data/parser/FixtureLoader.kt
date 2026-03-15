package com.kraat.lostfilmnewtv.data.parser

private const val FIXTURES_ROOT = "fixtures"

fun fixture(name: String): String {
    val resource = checkNotNull(object {}.javaClass.classLoader.getResource("$FIXTURES_ROOT/$name")) {
        "Fixture not found: $name"
    }

    return resource.readText()
}

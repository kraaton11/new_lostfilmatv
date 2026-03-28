package com.kraat.lostfilmnewtv.data.parser

private const val FIXTURES_ROOT = "fixtures"

fun fixture(name: String): String {
    val classLoader = checkNotNull(object {}.javaClass.classLoader) {
        "Class loader is not available"
    }
    val resource = checkNotNull(classLoader.getResource("$FIXTURES_ROOT/$name")) {
        "Fixture not found: $name"
    }

    return resource.readText()
}
